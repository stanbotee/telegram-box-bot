// SQLite 数据层（Node.js 内置 node:sqlite，同步 API，无需原生编译）。
// 要求 Node.js >= 22.5（Bonto 上请选 Node 24；若用 Node 22 可能需加 --experimental-sqlite）。
// 表：users / products / stock_items / orders / wallet_transactions / topups
//     / favorites / announcements / referrals，首次启动自动建表；
// 已存在的旧库用下面的 MIGRATIONS 补列，不丢数据。
const fs = require('fs');
const path = require('path');
const { DatabaseSync } = require('node:sqlite');

const SCHEMA = `
CREATE TABLE IF NOT EXISTS users (
  id          INTEGER PRIMARY KEY,   -- Telegram 用户数字 ID
  username    TEXT,
  first_name  TEXT,
  language    TEXT NOT NULL DEFAULT 'zh',
  balance     REAL NOT NULL DEFAULT 0,    -- 钱包余额（USD，显示 4 位小数）
  total_spent REAL NOT NULL DEFAULT 0,    -- 累计消费（决定等级）
  referrer_id INTEGER,                    -- 推荐人 Telegram ID（start=ref_xxx 绑定）
  api_key     TEXT,                       -- 分销 API Key（占位功能）
  notifications INTEGER NOT NULL DEFAULT 1, -- 通知开关：1 开 / 0 关
  created_at  TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS products (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  category    TEXT NOT NULL,         -- ai | vpn | streaming | learning（可扩展）
  name        TEXT NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  price       REAL NOT NULL,         -- 占位价格，单位见 README（默认 USD）
  duration    TEXT NOT NULL DEFAULT '',  -- 例如：1 个月
  is_active   INTEGER NOT NULL DEFAULT 1,
  sort_order  INTEGER NOT NULL DEFAULT 0,
  created_at  TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS stock_items (
  id               INTEGER PRIMARY KEY AUTOINCREMENT,
  product_id       INTEGER NOT NULL REFERENCES products(id),
  content          TEXT NOT NULL,    -- 一条库存就是一行：账号密码 / 激活码 / 卡密
  is_used          INTEGER NOT NULL DEFAULT 0,
  used_by_order_id INTEGER,
  created_at       TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS orders (
  id              INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id         INTEGER NOT NULL,
  product_id      INTEGER NOT NULL REFERENCES products(id),
  qty             INTEGER NOT NULL DEFAULT 1,
  total           REAL NOT NULL,
  status          TEXT NOT NULL DEFAULT 'pending_payment',
                  -- pending_payment 待处理 | awaiting_confirm 处理中
                  -- paid 已确认 | delivered 已发货 | rejected 已拒绝 | cancelled 已取消
  payment_method  TEXT NOT NULL DEFAULT '',  -- usdt | wallet | stars
  payment_ref     TEXT NOT NULL DEFAULT '',  -- 用户提交的交易哈希 / 付款凭证说明
  stock_item_id   INTEGER,
  created_at      TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at      TEXT NOT NULL DEFAULT (datetime('now'))
);

-- 钱包流水：topup 充值 / purchase 消费 / commission 返佣 / refund 退款
CREATE TABLE IF NOT EXISTS wallet_transactions (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id    INTEGER NOT NULL,
  type       TEXT NOT NULL,
  amount     REAL NOT NULL,   -- 正数入账，负数出账
  currency   TEXT NOT NULL DEFAULT 'USDT',
  ref        TEXT NOT NULL DEFAULT '',
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

-- 充值申请：用户发 TxID 后待管理员确认，确认后余额到账
CREATE TABLE IF NOT EXISTS topups (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id    INTEGER NOT NULL,
  amount     REAL NOT NULL,
  currency   TEXT NOT NULL DEFAULT 'USDT',
  txid       TEXT NOT NULL DEFAULT '',
  status     TEXT NOT NULL DEFAULT 'pending',  -- pending | confirmed | rejected
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

-- 收藏：用户 × 商品
CREATE TABLE IF NOT EXISTS favorites (
  user_id    INTEGER NOT NULL,
  product_id INTEGER NOT NULL REFERENCES products(id),
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  PRIMARY KEY (user_id, product_id)
);

-- 公告：最新动态
CREATE TABLE IF NOT EXISTS announcements (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  title      TEXT NOT NULL,
  body       TEXT NOT NULL DEFAULT '',
  is_active  INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

-- 返佣记录：被推荐人订单发货后产生
CREATE TABLE IF NOT EXISTS referrals (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  referrer_id  INTEGER NOT NULL,
  referred_id  INTEGER NOT NULL,
  order_id     INTEGER NOT NULL,
  commission   REAL NOT NULL,
  created_at   TEXT NOT NULL DEFAULT (datetime('now'))
);
`;

// 已存在旧库的补列迁移（新库建表时已包含，跳过）
const MIGRATIONS = [
  ['users', 'balance', 'REAL NOT NULL DEFAULT 0'],
  ['users', 'total_spent', 'REAL NOT NULL DEFAULT 0'],
  ['users', 'referrer_id', 'INTEGER'],
  ['users', 'api_key', 'TEXT'],
  ['users', 'notifications', 'INTEGER NOT NULL DEFAULT 1'],
];

class StoreDB {
  constructor(dbPath) {
    const resolved = path.isAbsolute(dbPath)
      ? dbPath
      : path.resolve(process.cwd(), dbPath);
    fs.mkdirSync(path.dirname(resolved), { recursive: true });
    this.db = new DatabaseSync(resolved);
    this.db.exec('PRAGMA journal_mode = WAL');
    this.db.exec(SCHEMA);
    this.migrate();
  }

  migrate() {
    for (const [table, column, ddl] of MIGRATIONS) {
      const cols = this.db.prepare(`PRAGMA table_info(${table})`).all();
      if (!cols.some((c) => c.name === column)) {
        this.db.exec(`ALTER TABLE ${table} ADD COLUMN ${column} ${ddl}`);
      }
    }
  }

  close() {
    this.db.close();
  }

  // ---------- users ----------
  upsertUser(id, username, firstName) {
    this.db
      .prepare(
        `INSERT INTO users (id, username, first_name) VALUES (?, ?, ?)
         ON CONFLICT(id) DO UPDATE SET username = excluded.username, first_name = excluded.first_name`
      )
      .run(id, username || '', firstName || '');
    return this.getUser(id);
  }

  getUser(id) {
    return this.db.prepare('SELECT * FROM users WHERE id = ?').get(id);
  }

  setUserLanguage(id, lang) {
    this.db.prepare('UPDATE users SET language = ? WHERE id = ?').run(lang, id);
  }

  setNotifications(id, on) {
    this.db.prepare('UPDATE users SET notifications = ? WHERE id = ?').run(on ? 1 : 0, id);
  }

  setReferrer(id, referrerId) {
    // 只绑定一次，且不能推荐自己
    const u = this.getUser(id);
    if (!u || u.referrer_id || referrerId === id) return false;
    this.db.prepare('UPDATE users SET referrer_id = ? WHERE id = ?').run(referrerId, id);
    return true;
  }

  addBalance(id, amount) {
    this.db.prepare('UPDATE users SET balance = balance + ? WHERE id = ?').run(amount, id);
  }

  addTotalSpent(id, amount) {
    this.db.prepare('UPDATE users SET total_spent = total_spent + ? WHERE id = ?').run(amount, id);
  }

  setApiKey(id, key) {
    this.db.prepare('UPDATE users SET api_key = ? WHERE id = ?').run(key, id);
  }

  countUsers() {
    return this.db.prepare('SELECT COUNT(*) AS n FROM users').get().n;
  }

  listAllUserIds() {
    return this.db
      .prepare('SELECT id FROM users')
      .all()
      .map((r) => r.id);
  }

  // ---------- products ----------
  listCategories() {
    return this.db
      .prepare(
        `SELECT DISTINCT category FROM products WHERE is_active = 1 ORDER BY category`
      )
      .all()
      .map((r) => r.category);
  }

  listProductsByCategory(category, onlyActive = true) {
    const where = onlyActive
      ? 'category = ? AND is_active = 1'
      : 'category = ?';
    return this.db
      .prepare(
        `SELECT * FROM products WHERE ${where} ORDER BY sort_order, id`
      )
      .all(category);
  }

  listAllProducts() {
    return this.db
      .prepare('SELECT * FROM products ORDER BY category, sort_order, id')
      .all();
  }

  getProduct(id) {
    return this.db.prepare('SELECT * FROM products WHERE id = ?').get(id);
  }

  countProducts() {
    return this.db.prepare('SELECT COUNT(*) AS n FROM products').get().n;
  }

  addProduct({ category, name, description, price, duration }) {
    const info = this.db
      .prepare(
        'INSERT INTO products (category, name, description, price, duration) VALUES (?, ?, ?, ?, ?)'
      )
      .run(category, name, description || '', price, duration || '');
    return this.getProduct(Number(info.lastInsertRowid));
  }

  updateProductPrice(id, price) {
    this.db.prepare('UPDATE products SET price = ? WHERE id = ?').run(price, id);
  }

  toggleProductActive(id) {
    this.db
      .prepare('UPDATE products SET is_active = 1 - is_active WHERE id = ?')
      .run(id);
    return this.getProduct(id);
  }

  deleteProduct(id) {
    this.db.prepare('DELETE FROM favorites WHERE product_id = ?').run(id);
    this.db.prepare('DELETE FROM stock_items WHERE product_id = ?').run(id);
    this.db.prepare('DELETE FROM products WHERE id = ?').run(id);
  }

  // ---------- stock ----------
  stockCount(productId, onlyUnused = true) {
    const where = onlyUnused
      ? 'product_id = ? AND is_used = 0'
      : 'product_id = ?';
    return this.db
      .prepare(`SELECT COUNT(*) AS n FROM stock_items WHERE ${where}`)
      .get(productId).n;
  }

  addStockItems(productId, lines) {
    const stmt = this.db.prepare(
      'INSERT INTO stock_items (product_id, content) VALUES (?, ?)'
    );
    const clean = lines.map((l) => l.trim()).filter(Boolean);
    // node:sqlite 没有 transaction() 助手，手动 BEGIN/COMMIT
    this.db.exec('BEGIN');
    try {
      for (const line of clean) stmt.run(productId, line);
      this.db.exec('COMMIT');
    } catch (e) {
      this.db.exec('ROLLBACK');
      throw e;
    }
    return clean.length;
  }

  listStock(productId, limit = 20) {
    return this.db
      .prepare(
        'SELECT * FROM stock_items WHERE product_id = ? ORDER BY id DESC LIMIT ?'
      )
      .all(productId, limit);
  }

  // 取一条未用库存（发货时调用）
  takeStockItem(productId) {
    return this.db
      .prepare(
        'SELECT * FROM stock_items WHERE product_id = ? AND is_used = 0 ORDER BY id LIMIT 1'
      )
      .get(productId);
  }

  markStockUsed(stockItemId, orderId) {
    this.db
      .prepare(
        'UPDATE stock_items SET is_used = 1, used_by_order_id = ? WHERE id = ?'
      )
      .run(orderId, stockItemId);
  }

  getStockItem(id) {
    return this.db.prepare('SELECT * FROM stock_items WHERE id = ?').get(id);
  }

  // ---------- orders ----------
  createOrder(userId, productId, total) {
    const info = this.db
      .prepare(
        'INSERT INTO orders (user_id, product_id, qty, total) VALUES (?, ?, 1, ?)'
      )
      .run(userId, productId, total);
    return this.getOrder(Number(info.lastInsertRowid));
  }

  getOrder(id) {
    return this.db.prepare('SELECT * FROM orders WHERE id = ?').get(id);
  }

  getOrderDetail(id) {
    return this.db
      .prepare(
        `SELECT o.*, p.name AS product_name, p.duration AS product_duration
         FROM orders o JOIN products p ON p.id = o.product_id WHERE o.id = ?`
      )
      .get(id);
  }

  listOrdersByUser(userId, limit = 10) {
    return this.db
      .prepare(
        `SELECT o.*, p.name AS product_name FROM orders o
         JOIN products p ON p.id = o.product_id
         WHERE o.user_id = ? ORDER BY o.id DESC LIMIT ?`
      )
      .all(userId, limit);
  }

  listPendingOrders() {
    return this.db
      .prepare(
        `SELECT o.*, p.name AS product_name, u.username AS username
         FROM orders o
         JOIN products p ON p.id = o.product_id
         LEFT JOIN users u ON u.id = o.user_id
         WHERE o.status = 'awaiting_confirm' ORDER BY o.id`
      )
      .all();
  }

  // 已确认、待发货的订单
  listConfirmedOrders() {
    return this.db
      .prepare(
        `SELECT o.*, p.name AS product_name, u.username AS username
         FROM orders o
         JOIN products p ON p.id = o.product_id
         LEFT JOIN users u ON u.id = o.user_id
         WHERE o.status = 'paid' ORDER BY o.id`
      )
      .all();
  }

  setOrderPaymentMethod(orderId, method) {
    this.db
      .prepare(
        "UPDATE orders SET payment_method = ?, updated_at = datetime('now') WHERE id = ?"
      )
      .run(method, orderId);
  }

  setOrderPaymentRef(orderId, ref) {
    this.db
      .prepare(
        "UPDATE orders SET payment_ref = ?, status = 'awaiting_confirm', updated_at = datetime('now') WHERE id = ?"
      )
      .run(ref, orderId);
  }

  updateOrderStatus(orderId, status) {
    this.db
      .prepare(
        "UPDATE orders SET status = ?, updated_at = datetime('now') WHERE id = ?"
      )
      .run(status, orderId);
  }

  markOrderDelivered(orderId, stockItemId) {
    this.db
      .prepare(
        "UPDATE orders SET status = 'delivered', stock_item_id = ?, updated_at = datetime('now') WHERE id = ?"
      )
      .run(stockItemId, orderId);
  }

  // 资料卡用的四种订单数：待处理 / 处理中 / 已确认 / 已发货
  countOrdersByStatus(userId) {
    const rows = this.db
      .prepare(
        'SELECT status, COUNT(*) AS n FROM orders WHERE user_id = ? GROUP BY status'
      )
      .all(userId);
    const map = { pending_payment: 0, awaiting_confirm: 0, paid: 0, delivered: 0 };
    for (const r of rows) {
      if (r.status in map) map[r.status] = r.n;
    }
    return {
      pending: map.pending_payment,
      processing: map.awaiting_confirm,
      confirmed: map.paid,
      delivered: map.delivered,
    };
  }

  stats() {
    const users = this.countUsers();
    const byStatus = this.db
      .prepare('SELECT status, COUNT(*) AS n FROM orders GROUP BY status')
      .all();
    const revenue = this.db
      .prepare(
        "SELECT COALESCE(SUM(total), 0) AS s FROM orders WHERE status IN ('paid', 'delivered')"
      )
      .get().s;
    const pending = this.db
      .prepare("SELECT COUNT(*) AS n FROM orders WHERE status = 'awaiting_confirm'")
      .get().n;
    return { users, byStatus, revenue, pending };
  }

  // ---------- 钱包 ----------
  addTransaction(userId, type, amount, currency, ref = '') {
    this.db
      .prepare(
        'INSERT INTO wallet_transactions (user_id, type, amount, currency, ref) VALUES (?, ?, ?, ?, ?)'
      )
      .run(userId, type, amount, currency, ref);
  }

  listTransactions(userId, limit = 10) {
    return this.db
      .prepare(
        'SELECT * FROM wallet_transactions WHERE user_id = ? ORDER BY id DESC LIMIT ?'
      )
      .all(userId, limit);
  }

  // ---------- 充值 ----------
  createTopup(userId, amount, currency, txid) {
    const info = this.db
      .prepare(
        'INSERT INTO topups (user_id, amount, currency, txid) VALUES (?, ?, ?, ?)'
      )
      .run(userId, amount, currency, txid);
    return this.db.prepare('SELECT * FROM topups WHERE id = ?').get(Number(info.lastInsertRowid));
  }

  getTopup(id) {
    return this.db.prepare('SELECT * FROM topups WHERE id = ?').get(id);
  }

  listPendingTopups() {
    return this.db
      .prepare(
        `SELECT t.*, u.username AS username FROM topups t
         LEFT JOIN users u ON u.id = t.user_id
         WHERE t.status = 'pending' ORDER BY t.id`
      )
      .all();
  }

  setTopupStatus(id, status) {
    this.db.prepare("UPDATE topups SET status = ? WHERE id = ?").run(status, id);
  }

  // ---------- 收藏 ----------
  toggleFavorite(userId, productId) {
    const exists = this.db
      .prepare('SELECT 1 AS x FROM favorites WHERE user_id = ? AND product_id = ?')
      .get(userId, productId);
    if (exists) {
      this.db
        .prepare('DELETE FROM favorites WHERE user_id = ? AND product_id = ?')
        .run(userId, productId);
      return false;
    }
    this.db
      .prepare('INSERT INTO favorites (user_id, product_id) VALUES (?, ?)')
      .run(userId, productId);
    return true;
  }

  isFavorite(userId, productId) {
    return !!this.db
      .prepare('SELECT 1 AS x FROM favorites WHERE user_id = ? AND product_id = ?')
      .get(userId, productId);
  }

  listFavorites(userId) {
    return this.db
      .prepare(
        `SELECT p.* FROM favorites f JOIN products p ON p.id = f.product_id
         WHERE f.user_id = ? ORDER BY f.created_at DESC`
      )
      .all(userId);
  }

  // ---------- 公告 ----------
  listAnnouncements(limit = 10) {
    return this.db
      .prepare(
        'SELECT * FROM announcements WHERE is_active = 1 ORDER BY id DESC LIMIT ?'
      )
      .all(limit);
  }

  countAnnouncements() {
    return this.db.prepare('SELECT COUNT(*) AS n FROM announcements').get().n;
  }

  addAnnouncement(title, body) {
    const info = this.db
      .prepare('INSERT INTO announcements (title, body) VALUES (?, ?)')
      .run(title, body);
    return this.db.prepare('SELECT * FROM announcements WHERE id = ?').get(Number(info.lastInsertRowid));
  }

  // ---------- 推荐 ----------
  recordReferral(referrerId, referredId, orderId, commission) {
    this.db
      .prepare(
        'INSERT INTO referrals (referrer_id, referred_id, order_id, commission) VALUES (?, ?, ?, ?)'
      )
      .run(referrerId, referredId, orderId, commission);
  }

  referralStats(referrerId) {
    const invited = this.db
      .prepare('SELECT COUNT(*) AS n FROM users WHERE referrer_id = ?')
      .get(referrerId).n;
    const earned = this.db
      .prepare('SELECT COALESCE(SUM(commission), 0) AS s FROM referrals WHERE referrer_id = ?')
      .get(referrerId).s;
    return { invited, earned };
  }
}

module.exports = { StoreDB };
