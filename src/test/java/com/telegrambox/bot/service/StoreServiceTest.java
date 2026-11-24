package com.telegrambox.bot.service;

import com.telegrambox.bot.config.AppConfig;
import com.telegrambox.bot.db.StoreDb;
import com.telegrambox.bot.model.Order;
import com.telegrambox.bot.model.StockItem;
import com.telegrambox.bot.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 业务服务层测试（内存 SQLite，不走网络、不需要真实 token）。
 * 覆盖：订单状态机、钱包支付、充值审核、发货与库存分配、返佣、拒绝退款。
 */
class StoreServiceTest {

    private StoreDb db;
    private StoreService store;

    @BeforeEach
    void setUp() throws SQLException {
        db = new StoreDb("jdbc:sqlite::memory:");
        store = new StoreService(db, AppConfig.load());
    }

    @AfterEach
    void tearDown() throws SQLException {
        db.close();
    }

    private User newUser(long id) throws SQLException {
        return db.upsertUser(id, "user" + id, "User" + id);
    }

    private long walletTopup(long userId, double amount) throws SQLException {
        long topupId = db.createTopup(userId, amount, "tx-test-" + userId);
        assertTrue(store.confirmTopup(topupId));
        return topupId;
    }

    @Test
    void seedProductsContainNine() throws SQLException {
        assertEquals(9, db.listAllProducts().size());
        assertEquals(4, db.listCategories().size());
        assertNotNull(db.getProduct(1));
    }

    @Test
    void walletPayInsufficientBalance() throws SQLException {
        User u = newUser(1001);
        long orderId = db.createOrder(u.id(), 1, 1, 20.0, "pending", null);
        assertFalse(store.payWithWallet(orderId));
        assertEquals(Order.PENDING_PAYMENT, db.getOrder(orderId).status());
        assertEquals(0, db.getUser(u.id()).balance(), 1e-9);
    }

    @Test
    void walletPaySuccessAndDoublePayGuard() throws SQLException {
        User u = newUser(1002);
        walletTopup(u.id(), 50.0);
        long orderId = db.createOrder(u.id(), 1, 1, 20.0, "pending", null);
        assertTrue(store.payWithWallet(orderId));
        assertEquals(Order.PAID, db.getOrder(orderId).status());
        assertEquals(30.0, db.getUser(u.id()).balance(), 1e-9);
        assertEquals(20.0, db.getUser(u.id()).totalSpent(), 1e-9);
        // 第二次支付必须失败（防重复扣款）
        assertFalse(store.payWithWallet(orderId));
        assertEquals(30.0, db.getUser(u.id()).balance(), 1e-9);
    }

    @Test
    void topupConfirmDoubleGuard() throws SQLException {
        User u = newUser(1003);
        long topupId = db.createTopup(u.id(), 10.0, "tx-abc");
        assertTrue(store.confirmTopup(topupId));
        assertEquals(10.0, db.getUser(u.id()).balance(), 1e-9);
        // 重复确认必须失败（防重复入账）
        assertFalse(store.confirmTopup(topupId));
        assertEquals(10.0, db.getUser(u.id()).balance(), 1e-9);
        assertFalse(store.rejectTopup(topupId));
    }

    @Test
    void topupReject() throws SQLException {
        User u = newUser(1004);
        long topupId = db.createTopup(u.id(), 10.0, "tx-def");
        assertTrue(store.rejectTopup(topupId));
        assertEquals(0, db.getUser(u.id()).balance(), 1e-9);
        assertEquals("rejected", db.getTopup(topupId).status());
    }

    @Test
    void usdtOrderConfirmThenShip() throws SQLException {
        User u = newUser(1005);
        db.addStock(1, "acc1@example.com / pass1");
        long orderId = db.createOrder(u.id(), 1, 1, 20.0, "usdt", "tx-order-1");
        assertTrue(db.updateOrderStatus(orderId, Order.PENDING_PAYMENT, Order.AWAITING_CONFIRM));
        assertTrue(store.confirmPayment(orderId));
        assertEquals(Order.PAID, db.getOrder(orderId).status());
        assertEquals(20.0, db.getUser(u.id()).totalSpent(), 1e-9);
        // 重复确认收款必须失败
        assertFalse(store.confirmPayment(orderId));

        StockItem item = store.shipOrder(orderId);
        assertNotNull(item);
        assertEquals("acc1@example.com / pass1", item.content());
        assertEquals(Order.DELIVERED, db.getOrder(orderId).status());
        assertEquals(0, db.availableStockCount(1));
        // 重复发货必须失败
        assertNull(store.shipOrder(orderId));
    }

    @Test
    void shipWithoutStockKeepsPaid() throws SQLException {
        User u = newUser(1006);
        walletTopup(u.id(), 50.0);
        long orderId = db.createOrder(u.id(), 1, 1, 20.0, "pending", null);
        assertTrue(store.payWithWallet(orderId));
        assertNull(store.shipOrder(orderId));
        assertEquals(Order.PAID, db.getOrder(orderId).status());
    }

    @Test
    void referralCommissionOnce() throws SQLException {
        User referrer = newUser(2001);
        User buyer = newUser(2002);
        db.setReferrer(buyer.id(), referrer.id());
        walletTopup(buyer.id(), 50.0);
        db.addStock(1, "acc2@example.com / pass2");
        long orderId = db.createOrder(buyer.id(), 1, 1, 20.0, "pending", null);
        assertTrue(store.payWithWallet(orderId));
        assertNotNull(store.shipOrder(orderId));
        // 5% 返佣：20 * 0.05 = 1.0
        assertEquals(1.0, db.getUser(referrer.id()).balance(), 1e-9);
        assertEquals(1, db.listReferralsByReferrer(referrer.id(), 10).size());
        assertEquals(1.0, db.referralTotal(referrer.id()), 1e-9);
    }

    @Test
    void rejectWalletPaidOrderRefunds() throws SQLException {
        User u = newUser(1007);
        walletTopup(u.id(), 50.0);
        long orderId = db.createOrder(u.id(), 1, 1, 20.0, "pending", null);
        assertTrue(store.payWithWallet(orderId));
        assertEquals(30.0, db.getUser(u.id()).balance(), 1e-9);
        assertTrue(store.rejectOrder(orderId));
        assertEquals(Order.REJECTED, db.getOrder(orderId).status());
        assertEquals(50.0, db.getUser(u.id()).balance(), 1e-9);
        // 重复拒绝必须失败
        assertFalse(store.rejectOrder(orderId));
    }

    @Test
    void rejectAwaitingOrderNoRefund() throws SQLException {
        User u = newUser(1008);
        long orderId = db.createOrder(u.id(), 1, 1, 20.0, "usdt", "tx-order-2");
        assertTrue(db.updateOrderStatus(orderId, Order.PENDING_PAYMENT, Order.AWAITING_CONFIRM));
        assertTrue(store.rejectOrder(orderId));
        assertEquals(Order.REJECTED, db.getOrder(orderId).status());
        assertEquals(0, db.getUser(u.id()).balance(), 1e-9);
    }

    @Test
    void cancelPendingOrderOnlyByOwner() throws SQLException {
        User u = newUser(1009);
        long orderId = db.createOrder(u.id(), 1, 1, 20.0, "pending", null);
        assertFalse(store.cancelOrder(orderId, 9999)); // 不是本人
        assertTrue(store.cancelOrder(orderId, u.id()));
        assertEquals(Order.CANCELLED, db.getOrder(orderId).status());
        assertFalse(store.cancelOrder(orderId, u.id())); // 已取消不能再取消
    }

    @Test
    void orderCountsForProfileCard() throws SQLException {
        User u = newUser(1010);
        walletTopup(u.id(), 100.0);
        long o1 = db.createOrder(u.id(), 1, 1, 20.0, "pending", null);
        long o2 = db.createOrder(u.id(), 2, 1, 18.0, "pending", null);
        long o3 = db.createOrder(u.id(), 3, 1, 19.99, "pending", null);
        assertTrue(store.payWithWallet(o1));
        assertTrue(db.updateOrderStatus(o2, Order.PENDING_PAYMENT, Order.AWAITING_CONFIRM));
        assertEquals(1, db.countOrdersByStatus(u.id(), Order.PENDING_PAYMENT));
        assertEquals(1, db.countOrdersByStatus(u.id(), Order.AWAITING_CONFIRM));
        assertEquals(1, db.countOrdersByStatus(u.id(), Order.PAID));
        assertEquals(0, db.countOrdersByStatus(u.id(), Order.DELIVERED));
    }

    @Test
    void favoritesToggle() throws SQLException {
        User u = newUser(1011);
        assertFalse(db.isFavorite(u.id(), 1));
        assertTrue(db.toggleFavorite(u.id(), 1));
        assertTrue(db.isFavorite(u.id(), 1));
        assertEquals(1, db.listFavorites(u.id()).size());
        assertFalse(db.toggleFavorite(u.id(), 1));
        assertTrue(db.listFavorites(u.id()).isEmpty());
    }

    @Test
    void apiKeyRegenerate() throws SQLException {
        User u = newUser(1012);
        String oldKey = u.apiKey();
        assertNotNull(oldKey);
        assertEquals(u.id(), db.getUserByApiKey(oldKey).id());
        String newKey = db.regenerateApiKey(u.id());
        assertNotEquals(oldKey, newKey);
        assertNull(db.getUserByApiKey(oldKey));
        assertEquals(u.id(), db.getUserByApiKey(newKey).id());
    }
}
