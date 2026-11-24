// Inline 键盘构建。回调数据约定：
// 用户主菜单（按截图，9 个按钮）：
//   shop（全宽）/ orders | wallet / news | invite / favorites | support
//   api（全宽）/ settings（全宽）
// 商店：cat:<分类> / prod:<id> / buy:<id> / fav:<id>（收藏切换）
//       pay:usdt:<订单id> / pay:wallet:<订单id> / pay:stars:<订单id>
// 钱包：wallet / wallet:topup / wallet:txns
// 其他：news / invite / favorites / support / api / api:regen / api:show
//       settings / settings:notif / lang / lang:zh / lang:en / menu
// 管理：admin / admin:products / admin:prod:<id> / admin:prod:add / admin:prod:toggle:<id>
//       admin:prod:del:<id> / admin:prod:delcf:<id> / admin:prod:editprice:<id>
//       admin:stock / admin:stock:<id> / admin:stock:add:<id>
//       admin:pending / admin:confirmed / admin:order:<id>
//       admin:order:confirm:<id> / admin:order:ship:<id> / admin:order:reject:<id>
//       admin:topups / admin:topup:<id> / admin:topup:confirm:<id> / admin:topup:reject:<id>
//       admin:announce / admin:stats / admin:broadcast
const { Markup } = require('telegraf');
const { t } = require('./i18n');

const CATEGORIES = [
  { key: 'ai', zh: '🤖 AI 订阅', en: '🤖 AI Subscriptions' },
  { key: 'vpn', zh: '🛡️ VPN', en: '🛡️ VPN' },
  { key: 'streaming', zh: '🎬 流媒体', en: '🎬 Streaming' },
  { key: 'learning', zh: '📚 学习', en: '📚 Learning' },
];

function catLabel(key, lang) {
  const c = CATEGORIES.find((x) => x.key === key);
  if (!c) return key;
  return lang === 'en' ? c.en : c.zh;
}

// 主菜单：严格按截图布局，共 9 个按钮
function mainMenu(lang) {
  return Markup.inlineKeyboard([
    [Markup.button.callback(t(lang, 'shop'), 'shop')],
    [
      Markup.button.callback(t(lang, 'myOrders'), 'orders'),
      Markup.button.callback(t(lang, 'wallet'), 'wallet'),
    ],
    [
      Markup.button.callback(t(lang, 'news'), 'news'),
      Markup.button.callback(t(lang, 'invite'), 'invite'),
    ],
    [
      Markup.button.callback(t(lang, 'favorites'), 'favorites'),
      Markup.button.callback(t(lang, 'help'), 'support'),
    ],
    [Markup.button.callback(t(lang, 'apiAccess'), 'api')],
    [Markup.button.callback(t(lang, 'settings'), 'settings')],
  ]);
}

function backToMenu(lang) {
  return Markup.inlineKeyboard([
    [Markup.button.callback('⬅️ ' + t(lang, 'menu'), 'menu')],
  ]);
}

function categoriesKb(lang) {
  const rows = CATEGORIES.map((c) => [
    Markup.button.callback(lang === 'en' ? c.en : c.zh, `cat:${c.key}`),
  ]);
  rows.push([Markup.button.callback('⬅️ ' + t(lang, 'menu'), 'menu')]);
  return Markup.inlineKeyboard(rows);
}

function productsKb(products, lang) {
  const rows = products.map((p) => [
    Markup.button.callback(`${p.name} — $${Number(p.price).toFixed(2)}`, `prod:${p.id}`),
  ]);
  rows.push([Markup.button.callback('⬅️ ' + t(lang, 'shop'), 'shop')]);
  return Markup.inlineKeyboard(rows);
}

function productDetailKb(product, stockCount, isFav, lang) {
  const rows = [];
  if (stockCount > 0) {
    rows.push([Markup.button.callback(t(lang, 'buy'), `buy:${product.id}`)]);
  }
  rows.push([
    Markup.button.callback(
      isFav ? t(lang, 'unfavorite') : t(lang, 'favorite'),
      `fav:${product.id}`
    ),
  ]);
  rows.push([Markup.button.callback('⬅️ ' + t(lang, 'shop'), 'shop')]);
  return Markup.inlineKeyboard(rows);
}

function paymentKb(order, canPayWithWallet, lang) {
  const rows = [];
  if (canPayWithWallet) {
    rows.push([Markup.button.callback(t(lang, 'payWallet'), `pay:wallet:${order.id}`)]);
  }
  rows.push([Markup.button.callback(t(lang, 'payUsdt'), `pay:usdt:${order.id}`)]);
  rows.push([Markup.button.callback(t(lang, 'payStars'), `pay:stars:${order.id}`)]);
  rows.push([Markup.button.callback('⬅️ ' + t(lang, 'menu'), 'menu')]);
  return Markup.inlineKeyboard(rows);
}

function walletKb(lang) {
  return Markup.inlineKeyboard([
    [Markup.button.callback(t(lang, 'topup'), 'wallet:topup')],
    [Markup.button.callback(t(lang, 'transactions'), 'wallet:txns')],
    [Markup.button.callback('⬅️ ' + t(lang, 'menu'), 'menu')],
  ]);
}

function languageKb() {
  return Markup.inlineKeyboard([
    [
      Markup.button.callback('🇨🇳 中文', 'lang:zh'),
      Markup.button.callback('🇬🇧 English', 'lang:en'),
    ],
    [Markup.button.callback('⬅️ 菜单', 'menu')],
  ]);
}

function apiKb(hasKey, lang) {
  const rows = [];
  if (hasKey) {
    rows.push([Markup.button.callback(t(lang, 'apiShow'), 'api:show')]);
  }
  rows.push([
    Markup.button.callback(hasKey ? t(lang, 'apiRegen') : t(lang, 'apiGenerate'), 'api:regen'),
  ]);
  rows.push([Markup.button.callback('⬅️ ' + t(lang, 'menu'), 'menu')]);
  return Markup.inlineKeyboard(rows);
}

function settingsKb(notificationsOn, lang) {
  return Markup.inlineKeyboard([
    [Markup.button.callback(t(lang, 'language'), 'lang')],
    [
      Markup.button.callback(
        notificationsOn ? t(lang, 'notifOn') : t(lang, 'notifOff'),
        'settings:notif'
      ),
    ],
    [Markup.button.callback('⬅️ ' + t(lang, 'menu'), 'menu')],
  ]);
}

// ---------- 管理员键盘 ----------
function adminPanelKb() {
  return Markup.inlineKeyboard([
    [Markup.button.callback('📦 商品管理', 'admin:products')],
    [Markup.button.callback('📥 库存管理', 'admin:stock')],
    [
      Markup.button.callback('🧾 待确认订单', 'admin:pending'),
      Markup.button.callback('📦 已确认待发货', 'admin:confirmed'),
    ],
    [
      Markup.button.callback('💳 充值审核', 'admin:topups'),
      Markup.button.callback('📣 添加公告', 'admin:announce'),
    ],
    [
      Markup.button.callback('📊 统计', 'admin:stats'),
      Markup.button.callback('📣 广播', 'admin:broadcast'),
    ],
  ]);
}

function adminProductsKb(products) {
  const rows = products.map((p) => [
    Markup.button.callback(
      `${p.is_active ? '✅' : '🚫'} ${p.name} $${Number(p.price).toFixed(2)}`,
      `admin:prod:${p.id}`
    ),
  ]);
  rows.push([Markup.button.callback('➕ 添加商品', 'admin:prod:add')]);
  rows.push([Markup.button.callback('⬅️ 管理面板', 'admin')]);
  return Markup.inlineKeyboard(rows);
}

function adminProductDetailKb(product) {
  return Markup.inlineKeyboard([
    [
      Markup.button.callback('💲 改价', `admin:prod:editprice:${product.id}`),
      Markup.button.callback(
        product.is_active ? '🚫 下架' : '✅ 上架',
        `admin:prod:toggle:${product.id}`
      ),
    ],
    [
      Markup.button.callback('📥 加库存', `admin:stock:add:${product.id}`),
      Markup.button.callback('🗑 删除', `admin:prod:del:${product.id}`),
    ],
    [Markup.button.callback('⬅️ 商品管理', 'admin:products')],
  ]);
}

function adminStockListKb(products) {
  const rows = products.map((p) => [
    Markup.button.callback(`${p.name}`, `admin:stock:${p.id}`),
  ]);
  rows.push([Markup.button.callback('⬅️ 管理面板', 'admin')]);
  return Markup.inlineKeyboard(rows);
}

function adminOrderListKb(orders, actionPrefix) {
  const rows = orders.map((o) => [
    Markup.button.callback(
      `#${o.id} ${o.product_name} $${Number(o.total).toFixed(2)} @${o.username || o.user_id}`,
      `${actionPrefix}:${o.id}`
    ),
  ]);
  rows.push([Markup.button.callback('⬅️ 管理面板', 'admin')]);
  return Markup.inlineKeyboard(rows);
}

function adminOrderDetailKb(order) {
  const rows = [];
  if (order.status === 'awaiting_confirm') {
    rows.push([
      Markup.button.callback('✅ 确认收款', `admin:order:confirm:${order.id}`),
      Markup.button.callback('❌ 拒绝', `admin:order:reject:${order.id}`),
    ]);
    rows.push([Markup.button.callback('⬅️ 待确认订单', 'admin:pending')]);
  } else if (order.status === 'paid') {
    rows.push([
      Markup.button.callback('📦 发货', `admin:order:ship:${order.id}`),
      Markup.button.callback('❌ 拒绝', `admin:order:reject:${order.id}`),
    ]);
    rows.push([Markup.button.callback('⬅️ 已确认待发货', 'admin:confirmed')]);
  } else {
    rows.push([Markup.button.callback('⬅️ 管理面板', 'admin')]);
  }
  return Markup.inlineKeyboard(rows);
}

function adminTopupListKb(topups) {
  const rows = topups.map((tp) => [
    Markup.button.callback(
      `#${tp.id} $${Number(tp.amount).toFixed(2)} @${tp.username || tp.user_id}`,
      `admin:topup:${tp.id}`
    ),
  ]);
  rows.push([Markup.button.callback('⬅️ 管理面板', 'admin')]);
  return Markup.inlineKeyboard(rows);
}

function adminTopupDetailKb(topupId) {
  return Markup.inlineKeyboard([
    [
      Markup.button.callback('✅ 确认到账', `admin:topup:confirm:${topupId}`),
      Markup.button.callback('❌ 拒绝', `admin:topup:reject:${topupId}`),
    ],
    [Markup.button.callback('⬅️ 充值审核', 'admin:topups')],
  ]);
}

module.exports = {
  CATEGORIES,
  catLabel,
  mainMenu,
  backToMenu,
  categoriesKb,
  productsKb,
  productDetailKb,
  paymentKb,
  walletKb,
  languageKb,
  apiKb,
  settingsKb,
  adminPanelKb,
  adminProductsKb,
  adminProductDetailKb,
  adminStockListKb,
  adminOrderListKb,
  adminOrderDetailKb,
  adminTopupListKb,
  adminTopupDetailKb,
};
