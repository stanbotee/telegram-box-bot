// 入口：Express（webhook + 健康检查）+ Telegraf。
// Bonto 适配：webhook 模式，不用 polling（polling 会一直占着进程、烧 75 小时/月时长）。
// 本地调试没有公网地址时，可用 BOT_MODE=polling（npm run dev），线上不要用。
require('dotenv').config();
const express = require('express');
const { Telegraf, session } = require('telegraf');
const config = require('./config');
const { StoreDB } = require('./db');
const { seedAll } = require('./seed');
const { registerUserHandlers, handleUserTextFlow } = require('./handlers/user');
const { registerAdminHandlers, handleAdminTextFlow } = require('./handlers/admin');

async function main() {
  if (!config.botToken) {
    console.error('❌ 缺少 BOT_TOKEN：请复制 .env.example 为 .env 并填入 BotFather 给的 token。');
    process.exit(1);
  }
  if (config.adminIds.length === 0) {
    console.warn('⚠️ 警告：ADMIN_IDS 未设置，/admin 管理面板谁都进不去，请在 .env 里填管理员 Telegram 数字 ID。');
  }
  if (config.botMode === 'webhook' && !config.webhookUrl) {
    console.warn('⚠️ 警告：BOT_MODE=webhook 但 WEBHOOK_URL 未设置，bot 收不到消息；本地调试请用 BOT_MODE=polling。');
  }

  const db = new StoreDB(config.dbPath);
  const seeded = seedAll(db);
  if (seeded.products > 0) console.log(`已种入 ${seeded.products} 个占位商品（价格为占位符，库存为 0）。`);
  if (seeded.announcements > 0) console.log(`已种入 ${seeded.announcements} 条占位公告。`);

  const bot = new Telegraf(config.botToken);
  // 取 bot 用户名，用于生成邀请链接 https://t.me/<bot>?start=ref_<id>；
  // 拿不到时用 BOT_USERNAME 或占位符，不阻塞启动
  try {
    const me = await Promise.race([
      bot.telegram.getMe(),
      new Promise((_, reject) => setTimeout(() => reject(new Error('timeout')), 5000)),
    ]);
    if (me.username && !config.botUsername) config.botUsername = me.username;
  } catch (e) {
    console.warn('获取 bot 用户名失败（邀请链接将用占位符）:', e.message);
  }
  bot.use(session());
  const deps = { db, config, bot };

  registerUserHandlers(bot, deps);
  registerAdminHandlers(bot, deps);

  // 纯文本路由：先管理员对话流，再用户对话流；/ 开头的命令不走这里
  bot.on('text', async (ctx) => {
    if (ctx.message.text.startsWith('/')) return;
    if (await handleAdminTextFlow(ctx, deps)) return;
    if (await handleUserTextFlow(ctx, deps)) return;
    // 其他闲聊文本不处理，避免误触发
  });

  bot.catch((err, ctx) => {
    console.error('Bot 出错:', err);
    if (ctx) ctx.reply('出错了，请稍后再试。').catch(() => {});
  });

  const app = express();
  app.use(express.json());
  // 健康检查：Bonto / 浏览器访问根路径应返回 ok
  app.get('/', (req, res) => res.send('ok'));
  app.get('/healthz', (req, res) => res.json({ status: 'ok', mode: config.botMode }));

  if (config.botMode === 'webhook') {
    app.use(bot.webhookCallback(config.webhookPath));
    if (config.webhookUrl) {
      await bot.telegram.setWebhook(`${config.webhookUrl}${config.webhookPath}`);
      console.log(`Webhook 已设置：${config.webhookUrl}${config.webhookPath}`);
    }
  } else if (config.botMode === 'polling') {
    await bot.launch();
    console.log('Polling 模式已启动（仅本地调试用）。');
  } else {
    console.error(`❌ 未知的 BOT_MODE：${config.botMode}，请用 webhook 或 polling。`);
    process.exit(1);
  }

  const server = app.listen(config.port, () => {
    console.log(`HTTP 服务运行在端口 ${config.port}（模式：${config.botMode}）`);
  });

  const shutdown = async (signal) => {
    console.log(`收到 ${signal}，正在关闭…`);
    server.close();
    // webhook 模式下 bot 未经 launch()，stop() 会抛 "Bot is not running!"，忽略即可
    try {
      bot.stop(signal);
    } catch (e) {
      /* ignore */
    }
    db.close();
    process.exit(0);
  };
  process.once('SIGINT', () => shutdown('SIGINT'));
  process.once('SIGTERM', () => shutdown('SIGTERM'));
}

main().catch((e) => {
  console.error('启动失败:', e);
  process.exit(1);
});
