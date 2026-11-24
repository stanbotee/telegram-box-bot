package com.telegrambox.bot.db;

import com.telegrambox.bot.model.*;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * SQLite 数据层（org.xerial:sqlite-jdbc）。一个实例持有一条连接，方法全部
 * synchronized（SQLite 单写者）。测试时可传入 jdbc:sqlite::memory:。
 */
public class StoreDb implements AutoCloseable {

    private final Connection conn;

    public StoreDb(String jdbcUrl) throws SQLException {
        this.conn = DriverManager.getConnection(jdbcUrl);
        init();
    }

    /** 默认构造：按 DB_PATH 建文件库。 */
    public StoreDb(String dbPath, boolean file) throws SQLException {
        this("jdbc:sqlite:" + dbPath);
    }

    private void init() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS users ("
                    + "id INTEGER PRIMARY KEY, username TEXT, first_name TEXT, "
                    + "language TEXT DEFAULT 'zh', balance REAL DEFAULT 0, "
                    + "total_spent REAL DEFAULT 0, referrer_id INTEGER, api_key TEXT, "
                    + "notifications INTEGER DEFAULT 1, created_at TEXT)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS products ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, category TEXT, name TEXT, "
                    + "description TEXT, price REAL, duration TEXT, active INTEGER DEFAULT 1, "
                    + "sort_order INTEGER DEFAULT 0, created_at TEXT)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS stock_items ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, product_id INTEGER, "
                    + "content TEXT, used INTEGER DEFAULT 0, used_by_order_id INTEGER, "
                    + "created_at TEXT)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS orders ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER, "
                    + "product_id INTEGER, qty INTEGER DEFAULT 1, total REAL, "
                    + "status TEXT DEFAULT 'pending_payment', payment_method TEXT, "
                    + "payment_ref TEXT, stock_item_id INTEGER, "
                    + "created_at TEXT, updated_at TEXT)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS topups ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER, amount REAL, "
                    + "currency TEXT DEFAULT 'USDT', txid TEXT, status TEXT DEFAULT 'pending', "
                    + "created_at TEXT)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS wallet_transactions ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER, type TEXT, "
                    + "amount REAL, currency TEXT DEFAULT 'USDT', ref TEXT, created_at TEXT)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS announcements ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, body TEXT, "
                    + "active INTEGER DEFAULT 1, created_at TEXT)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS referrals ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, referrer_id INTEGER, "
                    + "referred_id INTEGER, order_id INTEGER, commission REAL, created_at TEXT)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS favorites ("
                    + "user_id INTEGER, product_id INTEGER, created_at TEXT, "
                    + "PRIMARY KEY (user_id, product_id))");
        }
        seedProducts();
    }

    private static String now() {
        return Instant.now().toString();
    }

    // ---------------- 用户 ----------------

    public synchronized User getUser(long userId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM users WHERE id = ?")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapUser(rs) : null;
            }
        }
    }

    /** 用户不存在则创建（首次 /start 时调用）。 */
    public synchronized User upsertUser(long id, String username, String firstName) throws SQLException {
        User existing = getUser(id);
        if (existing != null) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE users SET username = ?, first_name = ? WHERE id = ?")) {
                ps.setString(1, username);
                ps.setString(2, firstName);
                ps.setLong(3, id);
                ps.executeUpdate();
            }
            return getUser(id);
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO users (id, username, first_name, language, balance, total_spent, "
                        + "api_key, notifications, created_at) VALUES (?, ?, ?, 'zh', 0, 0, ?, 1, ?)")) {
            ps.setLong(1, id);
            ps.setString(2, username);
            ps.setString(3, firstName);
            ps.setString(4, generateApiKey());
            ps.setString(5, now());
            ps.executeUpdate();
        }
        return getUser(id);
    }

    /** 余额增减（delta 可为负；调用方保证不透支）。 */
    public synchronized void changeBalance(long userId, double delta) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE users SET balance = balance + ? WHERE id = ?")) {
            ps.setDouble(1, delta);
            ps.setLong(2, userId);
            ps.executeUpdate();
        }
    }

    public synchronized void addSpent(long userId, double amount) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE users SET total_spent = total_spent + ? WHERE id = ?")) {
            ps.setDouble(1, amount);
            ps.setLong(2, userId);
            ps.executeUpdate();
        }
    }

    public synchronized void setLanguage(long userId, String lang) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE users SET language = ? WHERE id = ?")) {
            ps.setString(1, lang);
            ps.setLong(2, userId);
            ps.executeUpdate();
        }
    }

    public synchronized void setNotifications(long userId, boolean on) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE users SET notifications = ? WHERE id = ?")) {
            ps.setInt(1, on ? 1 : 0);
            ps.setLong(2, userId);
            ps.executeUpdate();
        }
    }

    public synchronized void setReferrer(long userId, long referrerId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE users SET referrer_id = ? WHERE id = ? AND referrer_id IS NULL")) {
            ps.setLong(1, referrerId);
            ps.setLong(2, userId);
            ps.executeUpdate();
        }
    }

    public synchronized String regenerateApiKey(long userId) throws SQLException {
        String key = generateApiKey();
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE users SET api_key = ? WHERE id = ?")) {
            ps.setString(1, key);
            ps.setLong(2, userId);
            ps.executeUpdate();
        }
        return key;
    }

    public synchronized User getUserByApiKey(String apiKey) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM users WHERE api_key = ?")) {
            ps.setString(1, apiKey);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapUser(rs) : null;
            }
        }
    }

    public synchronized int countUsers() throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static String generateApiKey() {
        return "tb_" + UUID.randomUUID().toString().replace("-", "");
    }

    // ---------------- 商品 ----------------

    public synchronized long addProduct(String category, String name, String description,
                                        double price, String duration, int sortOrder) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO products (category, name, description, price, duration, active, sort_order, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, 1, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, category);
            ps.setString(2, name);
            ps.setString(3, description);
            ps.setDouble(4, price);
            ps.setString(5, duration);
            ps.setInt(6, sortOrder);
            ps.setString(7, now());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    public synchronized Product getProduct(long productId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM products WHERE id = ?")) {
            ps.setLong(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapProduct(rs) : null;
            }
        }
    }

    public synchronized List<String> listCategories() throws SQLException {
        List<String> cats = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT category FROM products WHERE active = 1 GROUP BY category ORDER BY MIN(sort_order)")) {
            while (rs.next()) {
                cats.add(rs.getString(1));
            }
        }
        return cats;
    }

    public synchronized List<Product> listProductsByCategory(String category) throws SQLException {
        List<Product> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM products WHERE category = ? AND active = 1 ORDER BY sort_order, id")) {
            ps.setString(1, category);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapProduct(rs));
                }
            }
        }
        return out;
    }

    public synchronized List<Product> listAllProducts() throws SQLException {
        List<Product> out = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM products ORDER BY sort_order, id")) {
            while (rs.next()) {
                out.add(mapProduct(rs));
            }
        }
        return out;
    }

    public synchronized void setProductActive(long productId, boolean active) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE products SET active = ? WHERE id = ?")) {
            ps.setInt(1, active ? 1 : 0);
            ps.setLong(2, productId);
            ps.executeUpdate();
        }
    }

    // ---------------- 库存 ----------------

    public synchronized long addStock(long productId, String content) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO stock_items (product_id, content, used, created_at) VALUES (?, ?, 0, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, productId);
            ps.setString(2, content);
            ps.setString(3, now());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    public synchronized StockItem getStockItem(long stockItemId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM stock_items WHERE id = ?")) {
            ps.setLong(1, stockItemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new StockItem(
                        rs.getLong("id"),
                        rs.getLong("product_id"),
                        rs.getString("content"),
                        rs.getInt("used") == 1,
                        rs.getObject("used_by_order_id") == null ? null : rs.getLong("used_by_order_id"),
                        rs.getString("created_at"));
            }
        }
    }

    public synchronized int availableStockCount(long productId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM stock_items WHERE product_id = ? AND used = 0")) {
            ps.setLong(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * 取一条未用库存并标记为该订单占用；无库存返回 null。
     * 同一事务内执行，保证不重复发货。
     */
    public synchronized StockItem takeStockItem(long productId, long orderId) throws SQLException {
        boolean oldAuto = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            Long itemId = null;
            String content = null;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, content FROM stock_items WHERE product_id = ? AND used = 0 ORDER BY id LIMIT 1")) {
                ps.setLong(1, productId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        itemId = rs.getLong(1);
                        content = rs.getString(2);
                    }
                }
            }
            if (itemId == null) {
                conn.rollback();
                return null;
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE stock_items SET used = 1, used_by_order_id = ? WHERE id = ? AND used = 0")) {
                ps.setLong(1, orderId);
                ps.setLong(2, itemId);
                if (ps.executeUpdate() != 1) {
                    conn.rollback();
                    return null;
                }
            }
            conn.commit();
            return new StockItem(itemId, productId, content, true, orderId, null);
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(oldAuto);
        }
    }

    // ---------------- 订单 ----------------

    public synchronized long createOrder(long userId, long productId, int qty, double total,
                                         String paymentMethod, String paymentRef) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO orders (user_id, product_id, qty, total, status, payment_method, payment_ref, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'pending_payment', ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, userId);
            ps.setLong(2, productId);
            ps.setInt(3, qty);
            ps.setDouble(4, total);
            ps.setString(5, paymentMethod);
            ps.setString(6, paymentRef);
            ps.setString(7, now());
            ps.setString(8, now());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    public synchronized Order getOrder(long orderId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT o.*, p.name AS pname, p.duration AS pdur FROM orders o "
                        + "LEFT JOIN products p ON p.id = o.product_id WHERE o.id = ?")) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapOrder(rs) : null;
            }
        }
    }

    public synchronized List<Order> listUserOrders(long userId, int limit) throws SQLException {
        List<Order> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT o.*, p.name AS pname, p.duration AS pdur FROM orders o "
                        + "LEFT JOIN products p ON p.id = o.product_id "
                        + "WHERE o.user_id = ? ORDER BY o.id DESC LIMIT ?")) {
            ps.setLong(1, userId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapOrder(rs));
                }
            }
        }
        return out;
    }

    public synchronized List<Order> listOrdersByStatus(String status, int limit) throws SQLException {
        List<Order> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT o.*, p.name AS pname, p.duration AS pdur FROM orders o "
                        + "LEFT JOIN products p ON p.id = o.product_id "
                        + "WHERE o.status = ? ORDER BY o.id DESC LIMIT ?")) {
            ps.setString(1, status);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapOrder(rs));
                }
            }
        }
        return out;
    }

    public synchronized int countOrdersByStatus(long userId, String status) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM orders WHERE user_id = ? AND status = ?")) {
            ps.setLong(1, userId);
            ps.setString(2, status);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** 仅当订单当前是 from 状态时才改状态；返回是否成功（防重复确认/重复发货）。 */
    public synchronized boolean updateOrderStatus(long orderId, String fromStatus, String toStatus) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE orders SET status = ?, updated_at = ? WHERE id = ? AND status = ?")) {
            ps.setString(1, toStatus);
            ps.setString(2, now());
            ps.setLong(3, orderId);
            ps.setString(4, fromStatus);
            return ps.executeUpdate() == 1;
        }
    }

    public synchronized void updateOrderStockItem(long orderId, long stockItemId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE orders SET stock_item_id = ?, updated_at = ? WHERE id = ?")) {
            ps.setLong(1, stockItemId);
            ps.setString(2, now());
            ps.setLong(3, orderId);
            ps.executeUpdate();
        }
    }

    /** 设置订单付款方式（下单时先占位，付款选择时改）。 */
    public synchronized void setOrderPayment(long orderId, String paymentMethod) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE orders SET payment_method = ?, updated_at = ? WHERE id = ?")) {
            ps.setString(1, paymentMethod);
            ps.setString(2, now());
            ps.setLong(3, orderId);
            ps.executeUpdate();
        }
    }

    /** 设置订单付款凭证（USDT 转账的 TxID）。 */
    public synchronized void setOrderPaymentRef(long orderId, String paymentRef) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE orders SET payment_ref = ?, updated_at = ? WHERE id = ?")) {
            ps.setString(1, paymentRef);
            ps.setString(2, now());
            ps.setLong(3, orderId);
            ps.executeUpdate();
        }
    }

    /** 全部用户 ID（广播用）。 */
    public synchronized List<Long> listAllUserIds() throws SQLException {
        List<Long> out = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id FROM users ORDER BY id")) {
            while (rs.next()) {
                out.add(rs.getLong(1));
            }
        }
        return out;
    }

    public synchronized double totalRevenue() throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COALESCE(SUM(total), 0) FROM orders WHERE status = 'delivered'")) {
            return rs.next() ? rs.getDouble(1) : 0;
        }
    }

    // ---------------- 充值 ----------------

    public synchronized long createTopup(long userId, double amount, String txid) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO topups (user_id, amount, currency, txid, status, created_at) "
                        + "VALUES (?, ?, 'USDT', ?, 'pending', ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, userId);
            ps.setDouble(2, amount);
            ps.setString(3, txid);
            ps.setString(4, now());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    public synchronized Topup getTopup(long topupId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM topups WHERE id = ?")) {
            ps.setLong(1, topupId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapTopup(rs) : null;
            }
        }
    }

    public synchronized List<Topup> listPendingTopups(int limit) throws SQLException {
        List<Topup> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM topups WHERE status = 'pending' ORDER BY id LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapTopup(rs));
                }
            }
        }
        return out;
    }

    public synchronized boolean updateTopupStatus(long topupId, String fromStatus, String toStatus) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE topups SET status = ? WHERE id = ? AND status = ?")) {
            ps.setString(1, toStatus);
            ps.setLong(2, topupId);
            ps.setString(3, fromStatus);
            return ps.executeUpdate() == 1;
        }
    }

    // ---------------- 钱包流水 / 返佣 ----------------

    public synchronized void addWalletTx(long userId, String type, double amount, String ref) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO wallet_transactions (user_id, type, amount, currency, ref, created_at) "
                        + "VALUES (?, ?, ?, 'USDT', ?, ?)")) {
            ps.setLong(1, userId);
            ps.setString(2, type);
            ps.setDouble(3, amount);
            ps.setString(4, ref);
            ps.setString(5, now());
            ps.executeUpdate();
        }
    }

    public synchronized List<WalletTransaction> listWalletTx(long userId, int limit) throws SQLException {
        List<WalletTransaction> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM wallet_transactions WHERE user_id = ? ORDER BY id DESC LIMIT ?")) {
            ps.setLong(1, userId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapWalletTx(rs));
                }
            }
        }
        return out;
    }

    public synchronized void addReferral(long referrerId, long referredId, long orderId, double commission) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO referrals (referrer_id, referred_id, order_id, commission, created_at) "
                        + "VALUES (?, ?, ?, ?, ?)")) {
            ps.setLong(1, referrerId);
            ps.setLong(2, referredId);
            ps.setLong(3, orderId);
            ps.setDouble(4, commission);
            ps.setString(5, now());
            ps.executeUpdate();
        }
    }

    public synchronized List<Referral> listReferralsByReferrer(long referrerId, int limit) throws SQLException {
        List<Referral> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM referrals WHERE referrer_id = ? ORDER BY id DESC LIMIT ?")) {
            ps.setLong(1, referrerId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapReferral(rs));
                }
            }
        }
        return out;
    }

    public synchronized double referralTotal(long referrerId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COALESCE(SUM(commission), 0) FROM referrals WHERE referrer_id = ?")) {
            ps.setLong(1, referrerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0;
            }
        }
    }

    // ---------------- 收藏 ----------------

    /** 切换收藏：已收藏则取消，返回收藏后的状态（true=已收藏）。 */
    public synchronized boolean toggleFavorite(long userId, long productId) throws SQLException {
        boolean exists;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM favorites WHERE user_id = ? AND product_id = ?")) {
            ps.setLong(1, userId);
            ps.setLong(2, productId);
            try (ResultSet rs = ps.executeQuery()) {
                exists = rs.next();
            }
        }
        if (exists) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM favorites WHERE user_id = ? AND product_id = ?")) {
                ps.setLong(1, userId);
                ps.setLong(2, productId);
                ps.executeUpdate();
            }
            return false;
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO favorites (user_id, product_id, created_at) VALUES (?, ?, ?)")) {
            ps.setLong(1, userId);
            ps.setLong(2, productId);
            ps.setString(3, now());
            ps.executeUpdate();
        }
        return true;
    }

    public synchronized boolean isFavorite(long userId, long productId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM favorites WHERE user_id = ? AND product_id = ?")) {
            ps.setLong(1, userId);
            ps.setLong(2, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public synchronized List<Product> listFavorites(long userId) throws SQLException {
        List<Product> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT p.* FROM products p JOIN favorites f ON f.product_id = p.id "
                        + "WHERE f.user_id = ? AND p.active = 1 ORDER BY f.created_at DESC")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapProduct(rs));
                }
            }
        }
        return out;
    }

    // ---------------- 公告 ----------------

    public synchronized long createAnnouncement(String title, String body) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO announcements (title, body, active, created_at) VALUES (?, ?, 1, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, title);
            ps.setString(2, body);
            ps.setString(3, now());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    public synchronized List<Announcement> listActiveAnnouncements(int limit) throws SQLException {
        List<Announcement> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM announcements WHERE active = 1 ORDER BY id DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapAnnouncement(rs));
                }
            }
        }
        return out;
    }

    public synchronized void setAnnouncementActive(long id, boolean active) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE announcements SET active = ? WHERE id = ?")) {
            ps.setInt(1, active ? 1 : 0);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    // ---------------- 种子商品 ----------------

    private void seedProducts() throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM products")) {
            if (rs.next() && rs.getInt(1) > 0) {
                return;
            }
        }
        // 占位价格，等真实供货价替换；时长档位按 Jk Digital 清单的常见规格
        Object[][] seed = {
                {"AI 订阅", "ChatGPT Plus", "OpenAI ChatGPT Plus 会员", 20.0, "30 天", 1},
                {"AI 订阅", "Claude Pro", "Anthropic Claude Pro 会员", 18.0, "30 天", 2},
                {"AI 订阅", "Gemini Advanced", "Google Gemini Advanced 会员", 19.99, "30 天", 3},
                {"AI 订阅", "Muse", "Anthropic Muse 订阅", 10.0, "30 天", 4},
                {"VPN", "Surfshark", "Surfshark VPN 账号", 9.99, "30 天", 5},
                {"VPN", "NordVPN", "NordVPN 账号", 12.0, "30 天", 6},
                {"流媒体", "YouTube Premium", "YouTube Premium 会员", 8.0, "30 天", 7},
                {"流媒体", "Netflix Premium", "Netflix Premium 会员", 12.0, "30 天", 8},
                {"学习", "Duolingo Super", "Duolingo Super 会员", 6.0, "30 天", 9},
        };
        for (Object[] p : seed) {
            addProduct((String) p[0], (String) p[1], (String) p[2],
                    (Double) p[3], (String) p[4], (Integer) p[5]);
        }
    }

    // ---------------- 行映射 ----------------

    private static User mapUser(ResultSet rs) throws SQLException {
        return new User(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("first_name"),
                rs.getString("language"),
                rs.getDouble("balance"),
                rs.getDouble("total_spent"),
                rs.getObject("referrer_id") == null ? null : rs.getLong("referrer_id"),
                rs.getString("api_key"),
                rs.getInt("notifications") == 1,
                rs.getString("created_at"));
    }

    private static Product mapProduct(ResultSet rs) throws SQLException {
        return new Product(
                rs.getLong("id"),
                rs.getString("category"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getDouble("price"),
                rs.getString("duration"),
                rs.getInt("active") == 1,
                rs.getInt("sort_order"),
                rs.getString("created_at"));
    }

    private static Order mapOrder(ResultSet rs) throws SQLException {
        String pname = null;
        String pdur = null;
        try {
            pname = rs.getString("pname");
            pdur = rs.getString("pdur");
        } catch (SQLException ignored) {
            // 无联表字段时忽略
        }
        return new Order(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getLong("product_id"),
                rs.getInt("qty"),
                rs.getDouble("total"),
                rs.getString("status"),
                rs.getString("payment_method"),
                rs.getString("payment_ref"),
                rs.getObject("stock_item_id") == null ? null : rs.getLong("stock_item_id"),
                rs.getString("created_at"),
                rs.getString("updated_at"),
                pname,
                pdur);
    }

    private static Topup mapTopup(ResultSet rs) throws SQLException {
        return new Topup(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getDouble("amount"),
                rs.getString("currency"),
                rs.getString("txid"),
                rs.getString("status"),
                rs.getString("created_at"));
    }

    private static WalletTransaction mapWalletTx(ResultSet rs) throws SQLException {
        return new WalletTransaction(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getString("type"),
                rs.getDouble("amount"),
                rs.getString("currency"),
                rs.getString("ref"),
                rs.getString("created_at"));
    }

    private static Announcement mapAnnouncement(ResultSet rs) throws SQLException {
        return new Announcement(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("body"),
                rs.getInt("active") == 1,
                rs.getString("created_at"));
    }

    private static Referral mapReferral(ResultSet rs) throws SQLException {
        return new Referral(
                rs.getLong("id"),
                rs.getLong("referrer_id"),
                rs.getLong("referred_id"),
                rs.getLong("order_id"),
                rs.getDouble("commission"),
                rs.getString("created_at"));
    }

    @Override
    public void close() throws SQLException {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
    }
}
