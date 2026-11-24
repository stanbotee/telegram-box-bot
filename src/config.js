// 环境变量配置。所有敏感值（token / 收款地址 / 管理员 ID）只从环境变量读，
// 代码里不写任何真实凭证。
require('dotenv').config();
const path = require('path');

function parseAdminIds(raw) {
  return (raw || '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
    .map((s) => Number(s))
    .filter((n) => Number.isFinite(n));
}

const config = {
  // BotFather 给的 token
  botToken: process.env.BOT_TOKEN || '',
  // 管理员 Telegram 数字 ID 列表
  adminIds: parseAdminIds(process.env.ADMIN_IDS),
  // webhook（线上）或 polling（仅本地调试）
  botMode: process.env.BOT_MODE || 'webhook',
  // Bonto 上填 https://<应用名>.bonto.page
  webhookUrl: (process.env.WEBHOOK_URL || '').replace(/\/$/, ''),
  webhookPath: process.env.WEBHOOK_PATH || '/telegram-webhook',
  // Bonto 会注入 $PORT
  port: parseInt(process.env.PORT || '3000', 10),
  // USDT-TRC20 收款地址（占位符，等用户确认后填）
  usdtAddress: process.env.USDT_TRC20_ADDRESS || '',
  // 客服 Telegram 用户名（不带 @，占位符）
  supportUsername: process.env.SUPPORT_USERNAME || '',
  // bot 用户名（不带 @），用于生成邀请链接；启动时用 getMe() 自动补齐
  botUsername: process.env.BOT_USERNAME || '',
  // 钱包 / 支付：统一用虚拟货币，默认 USDT（占位可配）
  walletCurrency: process.env.WALLET_CURRENCY || 'USDT',
  // 邀请返佣比例（占位）：推荐人从被推荐人每笔已发货订单实付中拿 5%
  referralRate: parseFloat(process.env.REFERRAL_RATE || '0.05'),
  // 等级门槛（累计消费，单位 USD）：Bronze 0 固定；
  // Silver 由截图推断为 $100（距 Silver 还差 $88.498 时累计消费 $11.502）；
  // Gold / Platinum 阈值先占位，等用户确认
  levelSilverMin: parseFloat(process.env.LEVEL_SILVER_MIN || '100'),
  levelGoldMin: parseFloat(process.env.LEVEL_GOLD_MIN || '500'),
  levelPlatinumMin: parseFloat(process.env.LEVEL_PLATINUM_MIN || '1000'),
  dbPath:
    process.env.DB_PATH ||
    path.join(__dirname, '..', 'data', 'store.db'),
  defaultLang: process.env.DEFAULT_LANG || 'zh',
};

module.exports = config;
