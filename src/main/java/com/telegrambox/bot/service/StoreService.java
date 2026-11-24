package com.telegrambox.bot.service;

import com.telegrambox.bot.config.AppConfig;
import com.telegrambox.bot.db.StoreDb;
import com.telegrambox.bot.model.Order;
import com.telegrambox.bot.model.StockItem;
import com.telegrambox.bot.model.Topup;
import com.telegrambox.bot.model.User;

import java.sql.SQLException;

/**
 * 业务服务层：订单状态机、钱包支付、充值审核、发货与返佣。
 * 所有方法都通过 StoreDb 的条件更新防重复操作（二次确认 / 二次发货 / 二次扣款）。
 * 不依赖 Telegram API，可直接单元测试。
 */
public class StoreService {

    private final StoreDb db;
    private final AppConfig config;

    public StoreService(StoreDb db, AppConfig config) {
        this.db = db;
        this.config = config;
    }

    // ---------------- 钱包支付 ----------------

    /**
     * 钱包余额支付订单：仅待处理订单可付；余额不足返回 false；
     * 扣款后订单直接进入已确认，并计入累计消费。防重复支付。
     */
    public boolean payWithWallet(long orderId) throws SQLException {
        Order order = db.getOrder(orderId);
        if (order == null || !Order.PENDING_PAYMENT.equals(order.status())) {
            return false;
        }
        User user = db.getUser(order.userId());
        if (user == null || user.balance() < order.total()) {
            return false;
        }
        if (!db.updateOrderStatus(orderId, Order.PENDING_PAYMENT, Order.PAID)) {
            return false; // 并发下已被别人改过状态
        }
        db.changeBalance(order.userId(), -order.total());
        db.addSpent(order.userId(), order.total());
        db.addWalletTx(order.userId(), "purchase", -order.total(), "order:" + orderId);
        return true;
    }

    /** 取消待处理订单（仅订单本人）。 */
    public boolean cancelOrder(long orderId, long userId) throws SQLException {
        Order order = db.getOrder(orderId);
        if (order == null || order.userId() != userId) {
            return false;
        }
        return db.updateOrderStatus(orderId, Order.PENDING_PAYMENT, Order.CANCELLED);
    }

    // ---------------- 充值审核 ----------------

    /**
     * 管理员确认充值到账：仅待审核可确认；确认后余额入账并记流水。防重复确认。
     */
    public boolean confirmTopup(long topupId) throws SQLException {
        Topup topup = db.getTopup(topupId);
        if (topup == null || !"pending".equals(topup.status())) {
            return false;
        }
        if (!db.updateTopupStatus(topupId, "pending", "confirmed")) {
            return false;
        }
        db.changeBalance(topup.userId(), topup.amount());
        db.addWalletTx(topup.userId(), "topup", topup.amount(), "topup:" + topupId);
        return true;
    }

    /** 管理员拒绝充值申请（仅待审核）。 */
    public boolean rejectTopup(long topupId) throws SQLException {
        Topup topup = db.getTopup(topupId);
        if (topup == null || !"pending".equals(topup.status())) {
            return false;
        }
        return db.updateTopupStatus(topupId, "pending", "rejected");
    }

    // ---------------- 订单确认 / 发货 / 拒绝 ----------------

    /**
     * 管理员确认收到 USDT（人工转账订单）：处理中 → 已确认，并计入累计消费。
     * 钱包支付的订单已是已确认，不走这里。
     */
    public boolean confirmPayment(long orderId) throws SQLException {
        Order order = db.getOrder(orderId);
        if (order == null || !Order.AWAITING_CONFIRM.equals(order.status())) {
            return false;
        }
        if (!db.updateOrderStatus(orderId, Order.AWAITING_CONFIRM, Order.PAID)) {
            return false;
        }
        db.addSpent(order.userId(), order.total());
        return true;
    }

    /**
     * 管理员发货：已确认 → 已发货，取一条库存逐条发货并减库存；
     * 无库存时返回 null 且订单保持已确认（不发空货）。
     * 发货成功后若买家有推荐人，按比例给推荐人钱包发返佣（只发一次）。
     */
    public StockItem shipOrder(long orderId) throws SQLException {
        Order order = db.getOrder(orderId);
        if (order == null || !Order.PAID.equals(order.status())) {
            return null;
        }
        StockItem item = db.takeStockItem(order.productId(), orderId);
        if (item == null) {
            return null;
        }
        if (!db.updateOrderStatus(orderId, Order.PAID, Order.DELIVERED)) {
            return null;
        }
        db.updateOrderStockItem(orderId, item.id());
        payReferralCommission(order);
        return item;
    }

    /**
     * 管理员拒绝订单：处理中 → 已拒绝（USDT 未收款，无退款）；
     * 已确认且为钱包支付的订单被拒绝时，退回余额并记 refund 流水。
     */
    public boolean rejectOrder(long orderId) throws SQLException {
        Order order = db.getOrder(orderId);
        if (order == null) {
            return false;
        }
        if (Order.AWAITING_CONFIRM.equals(order.status())) {
            return db.updateOrderStatus(orderId, Order.AWAITING_CONFIRM, Order.REJECTED);
        }
        if (Order.PAID.equals(order.status()) && "wallet".equals(order.paymentMethod())) {
            if (!db.updateOrderStatus(orderId, Order.PAID, Order.REJECTED)) {
                return false;
            }
            db.changeBalance(order.userId(), order.total());
            db.addWalletTx(order.userId(), "refund", order.total(), "order:" + orderId);
            return true;
        }
        return false;
    }

    // ---------------- 返佣 ----------------

    private void payReferralCommission(Order order) throws SQLException {
        User buyer = db.getUser(order.userId());
        if (buyer == null || buyer.referrerId() == null) {
            return;
        }
        User referrer = db.getUser(buyer.referrerId());
        if (referrer == null || referrer.id() == buyer.id()) {
            return;
        }
        double commission = FormatUtil.commissionFor(order.total(), config.referralRate);
        if (commission <= 0) {
            return;
        }
        db.changeBalance(referrer.id(), commission);
        db.addWalletTx(referrer.id(), "commission", commission, "order:" + order.id());
        db.addReferral(referrer.id(), buyer.id(), order.id(), commission);
    }
}
