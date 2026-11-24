// 收款与发货。
// 支付统一走虚拟货币（默认 USDT，占位可配）：
//   - 钱包余额支付：直接扣余额，订单进「已确认」，管理员发货
//   - USDT-TRC20 人工转账：用户发 TxID，管理员确认收款后发货
//   - Telegram Stars：只留接口占位，未接通
const crypto = require('crypto');
const { t } = require('./i18n');
const { fmtUsd } = require('./levels');

function usdtInstructions(order, config, lang) {
  if (!config.usdtAddress) return t(lang, 'usdtNoAddress', { id: order.id });
  return t(lang, 'usdtPayText', {
    total: fmtUsd(order.total),
    address: config.usdtAddress,
    id: order.id,
  });
}

function topupInstructions(amount, config, lang) {
  if (!config.usdtAddress) return t(lang, 'usdtNoAddress', { id: '' });
  return t(lang, 'topupPayText', {
    total: fmtUsd(amount),
    address: config.usdtAddress,
  });
}

// 预留：Telegram Stars 支付接口。
// 接通时在这里调 Telegram Bot API 的 sendInvoice / createInvoiceLink，
// 并在 webhook 里处理 pre_checkout_query 与 successful_payment。
function starsInstructions(lang) {
  return t(lang, 'starsSoon');
}

// 钱包余额支付订单：扣余额、记流水，订单直接进「已确认」等发货。
function payWithWallet(db, order, config) {
  const user = db.getUser(order.user_id);
  if (!user || user.balance < order.total) {
    return { ok: false, reason: 'insufficient' };
  }
  db.addBalance(order.user_id, -order.total);
  db.addTransaction(order.user_id, 'purchase', -order.total, config.walletCurrency, `order #${order.id}`);
  db.setOrderPaymentMethod(order.id, 'wallet');
  db.updateOrderStatus(order.id, 'paid');
  return { ok: true };
}

// 管理员确认后发货：取一条未用库存发给用户，发完减库存；
// 同时累计用户消费，并给推荐人记返佣（比例见 config.referralRate，占位）。
// 返回 { ok: true, item } 或 { ok: false, reason: 'out_of_stock' }。
async function deliverOrder(db, bot, order, config = {}) {
  const item = db.takeStockItem(order.product_id);
  if (!item) return { ok: false, reason: 'out_of_stock' };

  const detail = db.getOrderDetail(order.id);
  db.markStockUsed(item.id, order.id);
  db.markOrderDelivered(order.id, item.id);
  db.addTotalSpent(order.user_id, order.total);

  // 返佣：被推荐人订单发货后，推荐人按比例拿返佣
  const rate = config.referralRate ?? 0.05;
  const buyer = db.getUser(order.user_id);
  if (buyer && buyer.referrer_id && rate > 0) {
    const commission = Math.round(order.total * rate * 10000) / 10000;
    if (commission > 0) {
      db.addBalance(buyer.referrer_id, commission);
      db.addTransaction(
        buyer.referrer_id,
        'commission',
        commission,
        config.walletCurrency || 'USDT',
        `referral order #${order.id}`
      );
      db.recordReferral(buyer.referrer_id, order.user_id, order.id, commission);
    }
  }

  const user = db.getUser(order.user_id);
  const lang = (user && user.language) || 'zh';
  try {
    await bot.telegram.sendMessage(
      order.user_id,
      t(lang, 'orderDelivered', {
        id: order.id,
        product: detail.product_name,
        content: item.content,
      }),
      { parse_mode: 'Markdown' }
    );
  } catch (e) {
    // 用户可能屏蔽了 bot：订单仍标记已发货，管理员在面板里能看到库存内容
    console.error(`发货消息发送失败 order=${order.id}:`, e.message);
    return { ok: true, item, dmFailed: true };
  }
  return { ok: true, item };
}

// 分销 API Key 生成（占位功能：只做 Key 的生成/查看/重置，HTTP API 接口未实现）
function generateApiKey() {
  return crypto.randomBytes(24).toString('hex');
}

function maskApiKey(key) {
  if (!key) return '';
  return key.length > 12 ? `${key.slice(0, 8)}****${key.slice(-4)}` : '****';
}

module.exports = {
  usdtInstructions,
  topupInstructions,
  starsInstructions,
  payWithWallet,
  deliverOrder,
  generateApiKey,
  maskApiKey,
};
