// 用户侧流程：/start 资料卡 + 主菜单 / 商店浏览 / 下单 / 付款 / 订单查询
//            / 钱包（余额·充值·交易记录）/ 最新动态 / 邀请赚钱 / 收藏 / 帮助
//            / API 权限 / 设置 / 语言 / 客服
// 主菜单与资料卡严格按用户提供的截图对齐（见 README）。
const { t, statusLabel } = require('../i18n');
const kb = require('../keyboards');
const { safeEdit, fmtPrice, notifyAdmins } = require('../util');
const { levelFor, nextLevel, fmtUsd } = require('../levels');
const {
  usdtInstructions,
  topupInstructions,
  starsInstructions,
  payWithWallet,
} = require('../payments');

function getLang(ctx, db) {
  if (ctx.session && ctx.session.lang) return ctx.session.lang;
  const u = ctx.from ? db.getUser(ctx.from.id) : null;
  return (u && u.language) || 'zh';
}

function inviteLink(config, userId) {
  const name = config.botUsername || 'your_bot_username';
  return `https://t.me/${name}?start=ref_${userId}`;
}

// /start 资料卡（照抄截图格式）
function profileCard(db, config, user, lang) {
  const spent = user.total_spent || 0;
  const level = levelFor(spent);
  const next = nextLevel(spent);
  const counts = db.countOrdersByStatus(user.id);
  const username = user.username ? `@${user.username}` : '—';
  const lines = [
    `👤 ${user.first_name || ''}`,
    t(lang, 'profileLevel', { level: level.name }),
    `🆔 ID：${user.id}`,
    `👤 用户名：${username}`,
    '',
    `💰 余额：${fmtUsd(user.balance || 0)}`,
    `💸 累计消费：${fmtUsd(spent)}`,
    next
      ? t(lang, 'profileToNext', { next: next.name, amount: fmtUsd(next.min - spent) })
      : t(lang, 'profileMaxLevel'),
    '',
    t(lang, 'profileOrders', counts),
    '',
    t(lang, 'profileInvite', { link: inviteLink(config, user.id) }),
  ];
  return lines.join('\n');
}

async function showMainMenu(ctx, db, config) {
  const lang = getLang(ctx, db);
  const user = db.getUser(ctx.from.id);
  await safeEdit(ctx, profileCard(db, config, user, lang), kb.mainMenu(lang));
}

function registerUserHandlers(bot, deps) {
  const { db, config } = deps;

  bot.start(async (ctx) => {
    const isNew = !db.getUser(ctx.from.id);
    const user = db.upsertUser(ctx.from.id, ctx.from.username, ctx.from.first_name);
    ctx.session.lang = user.language || config.defaultLang;
    // 推荐绑定：邀请链接格式 https://t.me/<bot>?start=ref_<用户ID>
    const payload = ctx.startPayload || '';
    const m = payload.match(/^ref_(\d+)$/);
    if (m && isNew) db.setReferrer(ctx.from.id, Number(m[1]));
    const fresh = db.getUser(ctx.from.id);
    await ctx.reply(profileCard(db, config, fresh, ctx.session.lang), kb.mainMenu(ctx.session.lang));
  });

  bot.command('menu', async (ctx) => {
    const lang = getLang(ctx, db);
    const user = db.upsertUser(ctx.from.id, ctx.from.username, ctx.from.first_name);
    await ctx.reply(profileCard(db, config, user, lang), kb.mainMenu(lang));
  });

  bot.action('menu', async (ctx) => {
    await ctx.answerCbQuery();
    await showMainMenu(ctx, db, config);
  });

  // ---------- 商店 ----------
  bot.action('shop', async (ctx) => {
    const lang = getLang(ctx, db);
    await ctx.answerCbQuery();
    await safeEdit(ctx, t(lang, 'chooseCategory'), kb.categoriesKb(lang));
  });

  bot.action(/^cat:(.+)$/, async (ctx) => {
    const lang = getLang(ctx, db);
    const category = ctx.match[1];
    const products = db.listProductsByCategory(category, true);
    await ctx.answerCbQuery();
    if (products.length === 0) {
      await safeEdit(ctx, t(lang, 'noProducts'), kb.categoriesKb(lang));
      return;
    }
    await safeEdit(
      ctx,
      `${kb.catLabel(category, lang)}\n${t(lang, 'chooseProduct')}`,
      kb.productsKb(products, lang)
    );
  });

  bot.action(/^prod:(\d+)$/, async (ctx) => {
    const lang = getLang(ctx, db);
    const product = db.getProduct(Number(ctx.match[1]));
    await ctx.answerCbQuery();
    if (!product || !product.is_active) {
      await safeEdit(ctx, t(lang, 'noProducts'), kb.categoriesKb(lang));
      return;
    }
    const stock = db.stockCount(product.id);
    const isFav = db.isFavorite(ctx.from.id, product.id);
    const text = [
      `📦 ${product.name}`,
      product.description || '',
      t(lang, 'price', { p: fmtPrice(product.price) }),
      product.duration ? t(lang, 'duration', { d: product.duration }) : '',
      t(lang, 'stockLeft', { n: stock }),
    ]
      .filter(Boolean)
      .join('\n');
    await safeEdit(ctx, text, kb.productDetailKb(product, stock, isFav, lang));
    if (stock === 0) {
      await ctx.reply(t(lang, 'outOfStock'));
    }
  });

  bot.action(/^fav:(\d+)$/, async (ctx) => {
    const lang = getLang(ctx, db);
    const product = db.getProduct(Number(ctx.match[1]));
    if (!product) {
      await ctx.answerCbQuery();
      return;
    }
    const added = db.toggleFavorite(ctx.from.id, product.id);
    await ctx.answerCbQuery(
      added ? t(lang, 'favAdded', { name: product.name }) : t(lang, 'favRemoved', { name: product.name })
    );
    const stock = db.stockCount(product.id);
    const text = [
      `📦 ${product.name}`,
      product.description || '',
      t(lang, 'price', { p: fmtPrice(product.price) }),
      product.duration ? t(lang, 'duration', { d: product.duration }) : '',
      t(lang, 'stockLeft', { n: stock }),
    ]
      .filter(Boolean)
      .join('\n');
    await safeEdit(ctx, text, kb.productDetailKb(product, stock, added, lang));
  });

  bot.action(/^buy:(\d+)$/, async (ctx) => {
    const lang = getLang(ctx, db);
    const product = db.getProduct(Number(ctx.match[1]));
    await ctx.answerCbQuery();
    if (!product || !product.is_active) {
      await safeEdit(ctx, t(lang, 'noProducts'), kb.categoriesKb(lang));
      return;
    }
    if (db.stockCount(product.id) === 0) {
      await ctx.reply(t(lang, 'outOfStock'));
      return;
    }
    db.upsertUser(ctx.from.id, ctx.from.username, ctx.from.first_name);
    const order = db.createOrder(ctx.from.id, product.id, product.price);
    const user = db.getUser(ctx.from.id);
    const canPayWithWallet = (user.balance || 0) >= order.total;
    await safeEdit(
      ctx,
      t(lang, 'choosePayment', {
        id: order.id,
        product: product.name,
        total: fmtPrice(order.total),
      }),
      kb.paymentKb(order, canPayWithWallet, lang)
    );
  });

  bot.action(/^pay:wallet:(\d+)$/, async (ctx) => {
    const lang = getLang(ctx, db);
    const order = db.getOrder(Number(ctx.match[1]));
    await ctx.answerCbQuery();
    if (!order || order.user_id !== ctx.from.id) return;
    const result = payWithWallet(db, order, config);
    if (!result.ok) {
      const user = db.getUser(ctx.from.id);
      await ctx.answerCbQuery(
        t(lang, 'walletInsufficient', {
          balance: fmtUsd(user.balance || 0),
          total: fmtUsd(order.total),
        }),
        { show_alert: true }
      );
      return;
    }
    const detail = db.getOrderDetail(order.id);
    await ctx.reply(t(lang, 'walletPaid', { id: order.id, total: fmtUsd(order.total) }));
    await notifyAdmins(
      bot,
      config,
      `💳 钱包支付订单待发货\n订单 #${order.id}\n商品：${detail.product_name}\n金额：${fmtUsd(order.total)}\n用户：@${ctx.from.username || ctx.from.id}\n\n请进 /admin → 已确认待发货 处理。`
    );
    await showMainMenu(ctx, db, config);
  });

  bot.action(/^pay:usdt:(\d+)$/, async (ctx) => {
    const lang = getLang(ctx, db);
    const order = db.getOrder(Number(ctx.match[1]));
    await ctx.answerCbQuery();
    if (!order || order.user_id !== ctx.from.id) return;
    db.setOrderPaymentMethod(order.id, 'usdt');
    ctx.session.flow = { type: 'await_txid', orderId: order.id };
    await safeEdit(
      ctx,
      usdtInstructions(order, config, lang),
      { parse_mode: 'Markdown', ...kb.backToMenu(lang) }
    );
  });

  bot.action(/^pay:stars:(\d+)$/, async (ctx) => {
    const lang = getLang(ctx, db);
    await ctx.answerCbQuery(starsInstructions(lang), { show_alert: true });
  });

  bot.action('orders', async (ctx) => {
    const lang = getLang(ctx, db);
    const orders = db.listOrdersByUser(ctx.from.id, 10);
    await ctx.answerCbQuery();
    if (orders.length === 0) {
      await safeEdit(ctx, t(lang, 'noOrders'), kb.backToMenu(lang));
      return;
    }
    const lines = orders.map(
      (o) =>
        `#${o.id} ${o.product_name} — ${fmtPrice(o.total)} — ${statusLabel(lang, o.status)}`
    );
    await safeEdit(ctx, `${t(lang, 'yourOrders')}\n\n${lines.join('\n')}`, kb.backToMenu(lang));
  });

  // ---------- 钱包 ----------
  bot.action('wallet', async (ctx) => {
    const lang = getLang(ctx, db);
    const user = db.getUser(ctx.from.id);
    await ctx.answerCbQuery();
    await safeEdit(
      ctx,
      t(lang, 'walletTitle', {
        balance: fmtUsd(user.balance || 0),
        currency: config.walletCurrency,
        spent: fmtUsd(user.total_spent || 0),
      }),
      kb.walletKb(lang)
    );
  });

  bot.action('wallet:topup', async (ctx) => {
    const lang = getLang(ctx, db);
    await ctx.answerCbQuery();
    ctx.session.flow = { type: 'topup_amount' };
    await ctx.reply(t(lang, 'topupAskAmount'));
  });

  bot.action('wallet:txns', async (ctx) => {
    const lang = getLang(ctx, db);
    const txns = db.listTransactions(ctx.from.id, 10);
    await ctx.answerCbQuery();
    if (txns.length === 0) {
      await safeEdit(ctx, t(lang, 'noTransactions'), kb.walletKb(lang));
      return;
    }
    const typeLabel = {
      topup: t(lang, 'txnTopup'),
      purchase: t(lang, 'txnPurchase'),
      commission: t(lang, 'txnCommission'),
      refund: t(lang, 'txnRefund'),
    };
    const lines = txns.map(
      (x) =>
        `${typeLabel[x.type] || x.type} ${x.amount > 0 ? '+' : ''}${fmtUsd(x.amount)} ${x.currency}${x.ref ? `（${x.ref}）` : ''}`
    );
    await safeEdit(ctx, `${t(lang, 'yourTransactions')}\n\n${lines.join('\n')}`, kb.walletKb(lang));
  });

  // ---------- 最新动态 ----------
  bot.action('news', async (ctx) => {
    const lang = getLang(ctx, db);
    const items = db.listAnnouncements(10);
    await ctx.answerCbQuery();
    if (items.length === 0) {
      await safeEdit(ctx, t(lang, 'noNews'), kb.backToMenu(lang));
      return;
    }
    const text = [t(lang, 'newsTitle'), ''].concat(
      items.map((a) => `📌 ${a.title}\n${a.body}`)
    ).join('\n\n');
    await safeEdit(ctx, text, kb.backToMenu(lang));
  });

  // ---------- 邀请赚钱 ----------
  bot.action('invite', async (ctx) => {
    const lang = getLang(ctx, db);
    const stats = db.referralStats(ctx.from.id);
    await ctx.answerCbQuery();
    await safeEdit(
      ctx,
      t(lang, 'inviteText', {
        link: inviteLink(config, ctx.from.id),
        invited: stats.invited,
        earned: fmtUsd(stats.earned),
        rate: `${Math.round(config.referralRate * 100)}%`,
      }),
      kb.backToMenu(lang)
    );
  });

  // ---------- 收藏 ----------
  bot.action('favorites', async (ctx) => {
    const lang = getLang(ctx, db);
    const products = db.listFavorites(ctx.from.id);
    await ctx.answerCbQuery();
    if (products.length === 0) {
      await safeEdit(ctx, t(lang, 'noFavorites'), kb.backToMenu(lang));
      return;
    }
    await safeEdit(ctx, t(lang, 'yourFavorites'), kb.productsKb(products, lang));
  });

  // ---------- 帮助 / 客服 ----------
  bot.action('support', async (ctx) => {
    const lang = getLang(ctx, db);
    await ctx.answerCbQuery();
    const handle = config.supportUsername ? `@${config.supportUsername}` : '—';
    await safeEdit(ctx, t(lang, 'helpText', { handle }), kb.backToMenu(lang));
  });

  // ---------- API 权限 ----------
  bot.action('api', async (ctx) => {
    const lang = getLang(ctx, db);
    const user = db.getUser(ctx.from.id);
    await ctx.answerCbQuery();
    const { maskApiKey } = require('../payments');
    await safeEdit(
      ctx,
      t(lang, 'apiText', { key: user.api_key ? maskApiKey(user.api_key) : t(lang, 'apiNoKey') }),
      kb.apiKb(!!user.api_key, lang)
    );
  });

  bot.action('api:regen', async (ctx) => {
    const lang = getLang(ctx, db);
    const { generateApiKey } = require('../payments');
    const key = generateApiKey();
    db.setApiKey(ctx.from.id, key);
    await ctx.answerCbQuery(t(lang, 'apiRegenerated'));
    const { maskApiKey } = require('../payments');
    await safeEdit(
      ctx,
      t(lang, 'apiText', { key: maskApiKey(key) }),
      kb.apiKb(true, lang)
    );
  });

  bot.action('api:show', async (ctx) => {
    const lang = getLang(ctx, db);
    const user = db.getUser(ctx.from.id);
    await ctx.answerCbQuery();
    if (!user.api_key) return;
    await ctx.reply(t(lang, 'apiKeyIs', { key: user.api_key }), { parse_mode: 'Markdown' });
  });

  // ---------- 设置 ----------
  bot.action('settings', async (ctx) => {
    const lang = getLang(ctx, db);
    const user = db.getUser(ctx.from.id);
    await ctx.answerCbQuery();
    await safeEdit(
      ctx,
      t(lang, 'settingsText', {
        lang: lang === 'zh' ? '中文' : 'English',
        notif: user.notifications ? '开' : '关',
      }),
      kb.settingsKb(!!user.notifications, lang)
    );
  });

  bot.action('settings:notif', async (ctx) => {
    const lang = getLang(ctx, db);
    const user = db.getUser(ctx.from.id);
    const next = !user.notifications;
    db.setNotifications(ctx.from.id, next);
    await ctx.answerCbQuery(t(lang, 'notifSet', { state: next ? '开启' : '关闭' }));
    await safeEdit(
      ctx,
      t(lang, 'settingsText', {
        lang: lang === 'zh' ? '中文' : 'English',
        notif: next ? '开' : '关',
      }),
      kb.settingsKb(next, lang)
    );
  });

  bot.action('lang', async (ctx) => {
    const lang = getLang(ctx, db);
    await ctx.answerCbQuery();
    await safeEdit(ctx, t(lang, 'chooseLanguage'), kb.languageKb());
  });

  bot.action(/^lang:(zh|en)$/, async (ctx) => {
    const lang = ctx.match[1];
    db.upsertUser(ctx.from.id, ctx.from.username, ctx.from.first_name);
    db.setUserLanguage(ctx.from.id, lang);
    ctx.session.lang = lang;
    await ctx.answerCbQuery();
    const user = db.getUser(ctx.from.id);
    await safeEdit(ctx, profileCard(db, config, user, lang), kb.mainMenu(lang));
  });
}

// 处理用户在对话流里发的纯文本：提交订单 TxID / 充值金额 / 充值 TxID。
// 返回 true 表示已消费这条消息。
async function handleUserTextFlow(ctx, deps) {
  const { db, config, bot } = deps;
  const flow = ctx.session && ctx.session.flow;
  if (!flow) return false;

  if (flow.type === 'await_txid') {
    const order = db.getOrder(flow.orderId);
    ctx.session.flow = null;
    if (!order || order.user_id !== ctx.from.id || order.status !== 'pending_payment') {
      return true;
    }
    const txid = ctx.message.text.trim();
    db.setOrderPaymentRef(order.id, txid);
    const detail = db.getOrderDetail(order.id);
    const lang = getLang(ctx, db);
    await ctx.reply(t(lang, 'txidReceived', { id: order.id }));
    await notifyAdmins(
      bot,
      config,
      `🧾 新付款待确认\n订单 #${order.id}\n商品：${detail.product_name}\n金额：${fmtUsd(order.total)}\n用户：@${ctx.from.username || ctx.from.id}\nTxID：${txid}\n\n请进 /admin → 待确认订单 处理。`
    );
    return true;
  }

  if (flow.type === 'topup_amount') {
    const amount = Number(ctx.message.text.trim());
    const lang = getLang(ctx, db);
    if (!Number.isFinite(amount) || amount <= 0) {
      await ctx.reply(t(lang, 'topupAskAmount'));
      return true;
    }
    if (!config.usdtAddress) {
      ctx.session.flow = null;
      await ctx.reply(t(lang, 'usdtNoAddress', { id: '' }));
      return true;
    }
    ctx.session.flow = { type: 'await_topup_txid', amount };
    await ctx.reply(topupInstructions(amount, config, lang), { parse_mode: 'Markdown' });
    return true;
  }

  if (flow.type === 'await_topup_txid') {
    const lang = getLang(ctx, db);
    const txid = ctx.message.text.trim();
    const topup = db.createTopup(ctx.from.id, flow.amount, config.walletCurrency, txid);
    ctx.session.flow = null;
    await ctx.reply(t(lang, 'topupReceived'));
    await notifyAdmins(
      bot,
      config,
      `💳 新充值待确认\n充值 #${topup.id}\n金额：${fmtUsd(flow.amount)} ${config.walletCurrency}\n用户：@${ctx.from.username || ctx.from.id}\nTxID：${txid}\n\n请进 /admin → 充值审核 处理。`
    );
    return true;
  }

  return false;
}

module.exports = { registerUserHandlers, handleUserTextFlow, getLang, profileCard, inviteLink };
