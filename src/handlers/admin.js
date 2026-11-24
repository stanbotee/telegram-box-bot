// 管理侧流程（仅 ADMIN_IDS 里的 Telegram 数字 ID 可用）：
// /admin 面板：商品管理 / 库存管理 / 待确认订单 / 已确认待发货 / 充值审核
//            / 添加公告 / 统计 / 广播。
// 订单流程：待确认（awaiting_confirm）→ 确认收款（paid，已确认）→ 发货（delivered）。
// 多步输入用 ctx.session.flow 记录，避免额外依赖。
const { t, statusLabel } = require('../i18n');
const kb = require('../keyboards');
const { safeEdit, fmtPrice, isAdmin, sleep, notifyAdmins } = require('../util');
const { deliverOrder } = require('../payments');
const { fmtUsd } = require('../levels');

function guard(ctx, config) {
  if (isAdmin(ctx, config)) return true;
  ctx.reply(t('zh', 'needAdmin'));
  return false;
}

function registerAdminHandlers(bot, deps) {
  const { db, config } = deps;

  const panelText = () =>
    '🛠 管理面板\n\n商品 / 库存 / 订单 / 充值 / 公告 / 统计 / 广播都在这里。';

  bot.command('admin', async (ctx) => {
    if (!guard(ctx, config)) return;
    await ctx.reply(panelText(), kb.adminPanelKb());
  });

  bot.action('admin', async (ctx) => {
    if (!guard(ctx, config)) return;
    await ctx.answerCbQuery();
    await safeEdit(ctx, panelText(), kb.adminPanelKb());
  });

  // ---------- 商品管理 ----------
  bot.action('admin:products', async (ctx) => {
    if (!guard(ctx, config)) return;
    await ctx.answerCbQuery();
    const products = db.listAllProducts();
    await safeEdit(
      ctx,
      `📦 商品管理（${products.length} 个）\n点一个商品改价 / 上下架 / 加库存 / 删除。`,
      kb.adminProductsKb(products)
    );
  });

  bot.action(/^admin:prod:(\d+)$/, async (ctx) => {
    if (!guard(ctx, config)) return;
    const p = db.getProduct(Number(ctx.match[1]));
    await ctx.answerCbQuery();
    if (!p) return;
    const stock = db.stockCount(p.id);
    const text = [
      `📦 ${p.name}`,
      `分类：${kb.catLabel(p.category, 'zh')} (${p.category})`,
      `价格：${fmtPrice(p.price)}`,
      `时长：${p.duration || '-'}`,
      `状态：${p.is_active ? '✅ 上架' : '🚫 下架'}`,
      `库存：${stock} 件`,
    ].join('\n');
    await safeEdit(ctx, text, kb.adminProductDetailKb(p));
  });

  bot.action('admin:prod:add', async (ctx) => {
    if (!guard(ctx, config)) return;
    await ctx.answerCbQuery();
    ctx.session.flow = { type: 'admin_add_product', step: 'name', data: {} };
    await ctx.reply('➕ 添加商品\n\n第 1 步：发商品名（例如 ChatGPT Plus）。');
  });

  bot.action(/^admin:prod:toggle:(\d+)$/, async (ctx) => {
    if (!guard(ctx, config)) return;
    const p = db.toggleProductActive(Number(ctx.match[1]));
    await ctx.answerCbQuery(p.is_active ? '已上架' : '已下架');
    await safeEdit(
      ctx,
      `📦 ${p.name}\n状态：${p.is_active ? '✅ 上架' : '🚫 下架'}`,
      kb.adminProductDetailKb(p)
    );
  });

  bot.action(/^admin:prod:del:(\d+)$/, async (ctx) => {
    if (!guard(ctx, config)) return;
    const p = db.getProduct(Number(ctx.match[1]));
    await ctx.answerCbQuery();
    if (!p) return;
    const { Markup } = require('telegraf');
    await safeEdit(
      ctx,
      `🗑 确定删除商品「${p.name}」吗？它的库存也会一起删掉。`,
      Markup.inlineKeyboard([
        [
          Markup.button.callback('✅ 确定删除', `admin:prod:delcf:${p.id}`),
          Markup.button.callback('⬅️ 取消', `admin:prod:${p.id}`),
        ],
      ])
    );
  });

  bot.action(/^admin:prod:delcf:(\d+)$/, async (ctx) => {
    if (!guard(ctx, config)) return;
    const p = db.getProduct(Number(ctx.match[1]));
    await ctx.answerCbQuery();
    if (!p) return;
    db.deleteProduct(p.id);
    await safeEdit(ctx, `已删除商品「${p.name}」。`, kb.adminProductsKb(db.listAllProducts()));
  });

  bot.action(/^admin:prod:editprice:(\d+)$/, async (ctx) => {
    if (!guard(ctx, config)) return;
    await ctx.answerCbQuery();
    ctx.session.flow = { type: 'admin_edit_price', productId: Number(ctx.match[1]) };
    await ctx.reply('💲 改价：直接发新价格数字（例如 19.99）。');
  });

  // ---------- 库存管理 ----------
  bot.action('admin:stock', async (ctx) => {
    if (!guard(ctx, config)) return;
    await ctx.answerCbQuery();
    const products = db.listAllProducts();
    await safeEdit(ctx, '📥 库存管理：选一个商品查看 / 加库存。', kb.adminStockListKb(products));
  });

  bot.action(/^admin:stock:(\d+)$/, async (ctx) => {
    if (!guard(ctx, config)) return;
    const p = db.getProduct(Number(ctx.match[1]));
    await ctx.answerCbQuery();
    if (!p) return;
    const count = db.stockCount(p.id);
    const items = db.listStock(p.id, 5);
    const preview = items
      .map((it) => `• #${it.id} ${it.is_used ? '(已用)' : '(可用)'} ${it.content.slice(0, 24)}…`)
      .join('\n');
    const { Markup } = require('telegraf');
    await safeEdit(
      ctx,
      `📥 ${p.name}\n可用库存：${count} 件\n\n最近库存：\n${preview || '（无）'}`,
      Markup.inlineKeyboard([
        [Markup.button.callback('➕ 加库存', `admin:stock:add:${p.id}`)],
        [Markup.button.callback('⬅️ 库存管理', 'admin:stock')],
      ])
    );
  });

  bot.action(/^admin:stock:add:(\d+)$/, async (ctx) => {
    if (!guard(ctx, config)) return;
    await ctx.answerCbQuery();
    ctx.session.flow = { type: 'admin_add_stock', productId: Number(ctx.match[1]) };
    await ctx.reply(
      '📥 加库存：直接发库存内容，一行一条。\n每行就是发货时发给用户的一条，例如：\n`user@example.com:password123`\n多行一次加多条。',
      { parse_mode: 'Markdown' }
    );
  });

  // ---------- 待确认订单 → 确认收款 ----------
  bot.action('admin:pending', async (ctx) => {
    if (!guard(ctx, config)) return;
    await ctx.answerCbQuery();
    const orders = db.listPendingOrders();
    if (orders.length === 0) {
      await safeEdit(ctx, '🧾 当前没有待确认订单。', kb.adminPanelKb());
      return;
    }
    await safeEdit(
      ctx,
      `🧾 待确认订单（${orders.length} 个）：点一个查看并确认收款 / 拒绝。`,
      kb.adminOrderListKb(orders, 'admin:order')
    );
  });

  // ---------- 已确认待发货 ----------
  bot.action('admin:confirmed', async (ctx) => {
    if (!guard(ctx, config)) return;
    await ctx.answerCbQuery();
    const orders = db.listConfirmedOrders();
    if (orders.length === 0) {
      await safeEdit(ctx, '📦 当前没有已确认待发货的订单。', kb.adminPanelKb());
      return;
    }
    await safeEdit(
      ctx,
      `📦 已确认待发货（${orders.length} 个）：点一个发货。`,
      kb.adminOrderListKb(orders, 'admin:order')
    );
  });

  bot.action(/^admin:order:(\d+)$/, async (ctx) => {
    if (!guard(ctx, config)) return;
    const o = db.getOrderDetail(Number(ctx.match[1]));
    await ctx.answerCbQuery();
    if (!o) return;
    const text = [
      `🧾 订单 #${o.id}`,
      `商品：${o.product_name}（${o.product_duration || '-'}）`,
      `金额：${fmtPrice(o.total)}`,
      `用户 ID：${o.user_id}`,
      `付款方式：${o.payment_method || '-'}`,
      `付款凭证：${o.payment_ref || '-'}`,
      `状态：${statusLabel('zh', o.status)}`,
      `库存余量：${db.stockCount(o.product_id)} 件`,
    ].join('\n');
    await safeEdit(ctx, text, kb.adminOrderDetailKb(o));
  });

  // 确认收款：awaiting_confirm → paid（已确认），再由发货步骤发货
  bot.action(/^admin:order:confirm:(\d+)$/, async (ctx) => {
    if (!guard(ctx, config)) return;
    const order = db.getOrder(Number(ctx.match[1]));
    await ctx.answerCbQuery();
    if (!order || order.status !== 'awaiting_confirm') {
      await ctx.reply('该订单已不是待确认状态。');
      return;
    }
    db.updateOrderStatus(order.id, 'paid');
    const user = db.getUser(order.user_id);
    const lang = (user && user.language) || 'zh';
    try {
      await bot.telegram.sendMessage(order.user_id, t(lang, 'orderConfirmed', { id: order.id }));
    } catch (e) {
      console.error(`确认通知发送失败 order=${order.id}:`, e.message);
    }
    await safeEdit(ctx, `✅ 订单 #${order.id} 已确认收款，去「已确认待发货」发货。`, kb.adminPanelKb());
  });

  // 发货：paid → delivered（自动取库存、累计消费、记返佣）
  bot.action(/^admin:order:ship:(\d+)$/, async (ctx) => {
    if (!guard(ctx, config)) return;
    const order = db.getOrder(Number(ctx.match[1]));
    await ctx.answerCbQuery();
    if (!order || (order.status !== 'paid' && order.status !== 'awaiting_confirm')) {
      await ctx.reply('该订单当前不能发货。');
      return;
    }
    const result = await deliverOrder(db, bot, order, config);
    if (!result.ok) {
      await ctx.reply(
        `⚠️ 库存不足，无法发货！请先给商品加库存，再回来发货订单 #${order.id}。`
      );
      return;
    }
    await safeEdit(
      ctx,
      `✅ 订单 #${order.id} 已发货。${result.dmFailed ? '（注意：发货私信没发出去，用户可能屏蔽了 bot，请手动联系。）' : ''}`,
      kb.adminPanelKb()
    );
  });

  bot.action(/^admin:order:reject:(\d+)$/, async (ctx) => {
    if (!guard(ctx, config)) return;
    await ctx.answerCbQuery();
    ctx.session.flow = { type: 'admin_reject_order', orderId: Number(ctx.match[1]) };
    await ctx.reply('❌ 拒绝订单：发拒绝原因（会通知用户，例如“未收到转账”）。');
  });

  // ---------- 充值审核 ----------
  bot.action('admin:topups', async (ctx) => {
    if (!guard(ctx, config)) return;
    await ctx.answerCbQuery();
    const topups = db.listPendingTopups();
    if (topups.length === 0) {
      await safeEdit(ctx, '💳 当前没有待确认的充值。', kb.adminPanelKb());
      return;
    }
    await safeEdit(
      ctx,
      `💳 待确认充值（${topups.length} 笔）：点一笔确认到账 / 拒绝。`,
      kb.adminTopupListKb(topups)
    );
  });

  bot.action(/^admin:topup:(\d+)$/, async (ctx) => {
    if (!guard(ctx, config)) return;
    const tp = db.getTopup(Number(ctx.match[1]));
    await ctx.answerCbQuery();
    if (!tp) return;
    const text = [
      `💳 充值 #${tp.id}`,
      `金额：${fmtUsd(tp.amount)} ${tp.currency}`,
      `用户 ID：${tp.user_id}`,
      `TxID：${tp.txid || '-'}`,
      `状态：${tp.status}`,
    ].join('\n');
    await safeEdit(ctx, text, kb.adminTopupDetailKb(tp.id));
  });

  bot.action(/^admin:topup:confirm:(\d+)$/, async (ctx) => {
    if (!guard(ctx, config)) return;
    const tp = db.getTopup(Number(ctx.match[1]));
    await ctx.answerCbQuery();
    if (!tp || tp.status !== 'pending') {
      await ctx.reply('该充值已不是待确认状态。');
      return;
    }
    db.setTopupStatus(tp.id, 'confirmed');
    db.addBalance(tp.user_id, tp.amount);
    db.addTransaction(tp.user_id, 'topup', tp.amount, tp.currency, `topup #${tp.id}`);
    const user = db.getUser(tp.user_id);
    const lang = (user && user.language) || 'zh';
    const fresh = db.getUser(tp.user_id);
    try {
      await bot.telegram.sendMessage(
        tp.user_id,
        t(lang, 'topupConfirmed', {
          amount: fmtUsd(tp.amount),
          currency: tp.currency,
          balance: fmtUsd(fresh.balance || 0),
        })
      );
    } catch (e) {
      console.error(`充值到账通知发送失败 topup=${tp.id}:`, e.message);
    }
    await safeEdit(ctx, `✅ 充值 #${tp.id} 已确认到账（${fmtUsd(tp.amount)}）。`, kb.adminPanelKb());
  });

  bot.action(/^admin:topup:reject:(\d+)$/, async (ctx) => {
    if (!guard(ctx, config)) return;
    await ctx.answerCbQuery();
    ctx.session.flow = { type: 'admin_reject_topup', topupId: Number(ctx.match[1]) };
    await ctx.reply('❌ 拒绝充值：发拒绝原因（会通知用户，例如“未收到转账”）。');
  });

  // ---------- 添加公告 ----------
  bot.action('admin:announce', async (ctx) => {
    if (!guard(ctx, config)) return;
    await ctx.answerCbQuery();
    ctx.session.flow = { type: 'admin_add_announce', step: 'title' };
    await ctx.reply('📣 添加公告\n\n第 1 步：发公告标题。');
  });

  // ---------- 统计 ----------
  bot.action('admin:stats', async (ctx) => {
    if (!guard(ctx, config)) return;
    await ctx.answerCbQuery();
    const s = db.stats();
    const byStatus = s.byStatus.map((r) => `${statusLabel('zh', r.status)}：${r.n}`).join('\n');
    await safeEdit(
      ctx,
      `📊 统计\n\n用户数：${s.users}\n待确认订单：${s.pending}\n已确认收入：${fmtPrice(s.revenue)}\n\n各状态订单：\n${byStatus || '（无订单）'}`,
      kb.adminPanelKb()
    );
  });

  // ---------- 广播 ----------
  bot.action('admin:broadcast', async (ctx) => {
    if (!guard(ctx, config)) return;
    await ctx.answerCbQuery();
    ctx.session.flow = { type: 'admin_broadcast' };
    await ctx.reply('📣 广播：直接发要群发的消息内容，会发给所有用过 bot 的用户。');
  });
}

// 处理管理员在对话流里发的纯文本。返回 true 表示已消费。
async function handleAdminTextFlow(ctx, deps) {
  const { db, config, bot } = deps;
  const flow = ctx.session && ctx.session.flow;
  if (!flow || !String(flow.type || '').startsWith('admin_')) return false;
  if (!isAdmin(ctx, config)) {
    ctx.session.flow = null;
    return true;
  }
  const text = ctx.message.text.trim();

  if (flow.type === 'admin_add_product') {
    if (flow.step === 'name') {
      flow.data.name = text;
      flow.step = 'category';
      await ctx.reply(
        '第 2 步：发分类 key，只能是 ai / vpn / streaming / learning 其中一个。\n（新分类等对齐后再扩展。）'
      );
    } else if (flow.step === 'category') {
      if (!['ai', 'vpn', 'streaming', 'learning'].includes(text)) {
        await ctx.reply('分类不对，请发 ai / vpn / streaming / learning 其中一个。');
        return true;
      }
      flow.data.category = text;
      flow.step = 'price';
      await ctx.reply('第 3 步：发价格数字（例如 19.99，单位 USD 占位）。');
    } else if (flow.step === 'price') {
      const price = Number(text);
      if (!Number.isFinite(price) || price < 0) {
        await ctx.reply('价格不对，请发数字，例如 19.99。');
        return true;
      }
      flow.data.price = price;
      flow.step = 'duration';
      await ctx.reply('第 4 步：发时长（例如 1 个月；没有就发 -）。');
    } else if (flow.step === 'duration') {
      flow.data.duration = text === '-' ? '' : text;
      const p = db.addProduct({
        category: flow.data.category,
        name: flow.data.name,
        description: '',
        price: flow.data.price,
        duration: flow.data.duration,
      });
      ctx.session.flow = null;
      await ctx.reply(
        `✅ 已添加商品「${p.name}」（${p.category}，${fmtPrice(p.price)}，${p.duration || '-'}）。\n记得去库存管理给它加库存，否则用户买不了。`
      );
    }
    return true;
  }

  if (flow.type === 'admin_edit_price') {
    const price = Number(text);
    if (!Number.isFinite(price) || price < 0) {
      await ctx.reply('价格不对，请发数字，例如 19.99。');
      return true;
    }
    db.updateProductPrice(flow.productId, price);
    ctx.session.flow = null;
    const p = db.getProduct(flow.productId);
    await ctx.reply(`✅「${p.name}」价格已改为 ${fmtPrice(price)}。`);
    return true;
  }

  if (flow.type === 'admin_add_stock') {
    const lines = text.split('\n').map((l) => l.trim()).filter(Boolean);
    if (lines.length === 0) {
      await ctx.reply('没收到有效内容，请重新发，一行一条。');
      return true;
    }
    const n = db.addStockItems(flow.productId, lines);
    ctx.session.flow = null;
    const p = db.getProduct(flow.productId);
    await ctx.reply(
      `✅ 已给「${p.name}」加了 ${n} 条库存，当前可用 ${db.stockCount(p.id)} 件。`
    );
    return true;
  }

  if (flow.type === 'admin_reject_order') {
    const order = db.getOrder(flow.orderId);
    ctx.session.flow = null;
    if (!order) return true;
    db.updateOrderStatus(order.id, 'rejected');
    const user = db.getUser(order.user_id);
    const lang = (user && user.language) || 'zh';
    try {
      await bot.telegram.sendMessage(
        order.user_id,
        t(lang, 'orderRejected', { id: order.id, reason: text })
      );
    } catch (e) {
      console.error(`拒绝通知发送失败 order=${order.id}:`, e.message);
    }
    await ctx.reply(`❌ 订单 #${order.id} 已拒绝，用户已收到通知。`);
    return true;
  }

  if (flow.type === 'admin_reject_topup') {
    const tp = db.getTopup(flow.topupId);
    ctx.session.flow = null;
    if (!tp) return true;
    db.setTopupStatus(tp.id, 'rejected');
    const user = db.getUser(tp.user_id);
    const lang = (user && user.language) || 'zh';
    try {
      await bot.telegram.sendMessage(
        tp.user_id,
        t(lang, 'topupRejected', { reason: text })
      );
    } catch (e) {
      console.error(`充值拒绝通知发送失败 topup=${tp.id}:`, e.message);
    }
    await ctx.reply(`❌ 充值 #${tp.id} 已拒绝，用户已收到通知。`);
    return true;
  }

  if (flow.type === 'admin_add_announce') {
    if (flow.step === 'title') {
      flow.title = text;
      flow.step = 'body';
      await ctx.reply('第 2 步：发公告正文。');
      return true;
    }
    const a = db.addAnnouncement(flow.title, text);
    ctx.session.flow = null;
    await ctx.reply(`✅ 公告已发布：「${a.title}」。`);
    return true;
  }

  if (flow.type === 'admin_broadcast') {
    ctx.session.flow = null;
    const ids = db.listAllUserIds();
    let ok = 0;
    let fail = 0;
    for (const id of ids) {
      try {
        await bot.telegram.sendMessage(id, text);
        ok += 1;
      } catch (e) {
        fail += 1;
      }
      await sleep(50); // 避免触发 Telegram 限流
    }
    await ctx.reply(`📣 广播完成：成功 ${ok} 人，失败 ${fail} 人（共 ${ids.length} 人）。`);
    return true;
  }

  return false;
}

module.exports = { registerAdminHandlers, handleAdminTextFlow };
