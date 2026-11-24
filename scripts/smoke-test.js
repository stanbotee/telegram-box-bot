// 冒烟测试：不连 Telegram，只验证数据库 + 种子 + 发货 + 钱包 + 返佣 + 等级逻辑。
// 跑法：npm run smoke
// 通过标准见各断言。
const fs = require('fs');
const os = require('os');
const path = require('path');
const assert = require('assert');
const { StoreDB } = require('../src/db');
const { seedIfEmpty, seedAll, SEED_PRODUCTS } = require('../src/seed');
const { deliverOrder, payWithWallet } = require('../src/payments');
const { levelFor, nextLevel, fmtMoney, fmtUsd } = require('../src/levels');

async function main() {
  const tmpDb = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'jkbot-')), 'test.db');
  const db = new StoreDB(tmpDb);
  const config = { referralRate: 0.05, walletCurrency: 'USDT' };

  // 1. 种子
  const n = seedIfEmpty(db);
  assert.strictEqual(n, SEED_PRODUCTS.length, `种子商品数应为 ${SEED_PRODUCTS.length}`);
  assert.strictEqual(db.countProducts(), SEED_PRODUCTS.length);
  const seeded = seedAll(db);
  assert.strictEqual(db.countAnnouncements(), 1, '应有 1 条占位公告');
  console.log(`✓ 种子商品 ${n} 个，公告 ${seeded.announcements} 条`);

  // 2. 等级与金额格式（按截图数值验证）
  assert.strictEqual(levelFor(11.502).name, 'Bronze');
  assert.strictEqual(nextLevel(11.502).name, 'Silver');
  assert.strictEqual(nextLevel(11.502).min, 100);
  assert.strictEqual(fmtMoney(100 - 11.502), '88.498', '距 Silver 应差 88.498');
  assert.strictEqual(fmtMoney(8.9983), '8.9983');
  assert.strictEqual(fmtMoney(11.502), '11.502');
  assert.strictEqual(fmtUsd(20), '$20');
  console.log('✓ 等级推断与金额格式：Bronze，距 Silver 差 $88.498');

  // 3. 用户 + 商品 + 库存
  const user = db.upsertUser(111222333, 'testuser', 'Test');
  assert.strictEqual(user.id, 111222333);
  const product = db.listProductsByCategory('ai')[0];
  assert.ok(product, 'AI 分类应有商品');
  assert.strictEqual(db.stockCount(product.id), 0, '初始库存应为 0');
  const added = db.addStockItems(product.id, [
    'buyer1@example.com:passAAA',
    'buyer2@example.com:passBBB',
  ]);
  assert.strictEqual(added, 2);
  assert.strictEqual(db.stockCount(product.id), 2);
  console.log(`✓ 库存写入 2 条，商品：${product.name}`);

  // 4. 下单 + USDT 人工确认 → 发货（用假 bot，不发真实 Telegram 消息）
  const order = db.createOrder(user.id, product.id, product.price);
  assert.strictEqual(order.status, 'pending_payment');
  db.setOrderPaymentMethod(order.id, 'usdt');
  db.setOrderPaymentRef(order.id, 'txid-test-123');
  assert.strictEqual(db.getOrder(order.id).status, 'awaiting_confirm');
  // 管理员确认收款（处理中 → 已确认），再发货
  db.updateOrderStatus(order.id, 'paid');
  assert.strictEqual(db.getOrder(order.id).status, 'paid');

  const sent = [];
  const fakeBot = {
    telegram: {
      sendMessage: async (chatId, text) => {
        sent.push({ chatId, text });
        return { message_id: 1 };
      },
    },
  };
  const result = await deliverOrder(db, fakeBot, db.getOrder(order.id), config);
  assert.strictEqual(result.ok, true, '发货应成功');
  assert.strictEqual(result.item.content, 'buyer1@example.com:passAAA', '应按顺序发第一条库存');
  assert.strictEqual(db.getOrder(order.id).status, 'delivered');
  assert.strictEqual(db.stockCount(product.id), 1, '发货后可用库存应剩 1');
  assert.strictEqual(sent.length, 1, '应给用户发 1 条发货私信');
  assert.ok(sent[0].text.includes('buyer1@example.com:passAAA'), '发货内容应包含账号密码');
  // 发货后累计消费应增加
  assert.strictEqual(db.getUser(user.id).total_spent, product.price);
  console.log('✓ 下单 → 付款确认 → 发货全流程通过，累计消费已记');

  // 5. 统计
  const stats = db.stats();
  assert.strictEqual(stats.users, 1);
  assert.ok(stats.revenue > 0, '已发货订单应计入收入');
  console.log(`✓ 统计通过：用户 ${stats.users} 人，收入 $${stats.revenue}`);

  // 6. 库存耗尽时发货应失败
  const order2 = db.createOrder(user.id, product.id, product.price);
  db.setOrderPaymentRef(order2.id, 'txid-test-456');
  db.updateOrderStatus(order2.id, 'paid'); // 管理员确认收款
  await deliverOrder(db, fakeBot, db.getOrder(order2.id), config); // 发掉最后 1 条
  const order3 = db.createOrder(user.id, product.id, product.price);
  db.setOrderPaymentRef(order3.id, 'txid-test-789');
  db.updateOrderStatus(order3.id, 'paid'); // 管理员确认收款
  const empty = await deliverOrder(db, fakeBot, db.getOrder(order3.id), config);
  assert.strictEqual(empty.ok, false, '库存为 0 时发货应失败');
  assert.strictEqual(empty.reason, 'out_of_stock');
  console.log('✓ 库存耗尽保护通过');

  // 7. 钱包充值 → 余额支付 → 返佣
  const referrer = db.upsertUser(999888777, 'referrer', 'Ref');
  const buyer = db.upsertUser(555666777, 'buyer', 'Buyer');
  db.setReferrer(buyer.id, referrer.id);
  assert.strictEqual(db.getUser(buyer.id).referrer_id, referrer.id);
  assert.strictEqual(db.setReferrer(buyer.id, referrer.id), false, '推荐人只能绑定一次');
  assert.strictEqual(db.setReferrer(buyer.id, buyer.id), false, '不能推荐自己');
  // 充值 50 USDT（模拟管理员确认到账）
  const topup = db.createTopup(buyer.id, 50, 'USDT', 'txid-topup-1');
  assert.strictEqual(topup.status, 'pending');
  assert.strictEqual(db.listPendingTopups().length, 1);
  db.setTopupStatus(topup.id, 'confirmed');
  db.addBalance(buyer.id, 50);
  db.addTransaction(buyer.id, 'topup', 50, 'USDT', `topup #${topup.id}`);
  assert.strictEqual(db.getUser(buyer.id).balance, 50);
  // 补库存并用钱包买
  db.addStockItems(product.id, ['buyer3@example.com:passCCC']);
  const order4 = db.createOrder(buyer.id, product.id, product.price);
  const paid = payWithWallet(db, order4, config);
  assert.strictEqual(paid.ok, true, '余额充足时钱包支付应成功');
  assert.strictEqual(db.getOrder(order4.id).status, 'paid', '钱包支付后订单应为已确认');
  assert.strictEqual(db.getUser(buyer.id).balance, 50 - product.price);
  assert.strictEqual(db.listTransactions(buyer.id).length, 2, '应有充值+消费两条流水');
  // 发货 → 推荐人拿 5% 返佣
  const r4 = await deliverOrder(db, fakeBot, db.getOrder(order4.id), config);
  assert.strictEqual(r4.ok, true);
  assert.strictEqual(db.getUser(referrer.id).balance, product.price * 0.05, '推荐人应拿到 5% 返佣');
  const refStats = db.referralStats(referrer.id);
  assert.strictEqual(refStats.invited, 1);
  assert.strictEqual(refStats.earned, product.price * 0.05);
  // 钱包余额不足时支付失败
  const order5 = db.createOrder(buyer.id, product.id, 9999);
  assert.strictEqual(payWithWallet(db, order5, config).ok, false);
  console.log('✓ 钱包充值/支付/流水、推荐绑定与 5% 返佣通过');

  // 8. 收藏 + 资料卡订单计数
  assert.strictEqual(db.toggleFavorite(buyer.id, product.id), true);
  assert.strictEqual(db.isFavorite(buyer.id, product.id), true);
  assert.strictEqual(db.listFavorites(buyer.id).length, 1);
  assert.strictEqual(db.toggleFavorite(buyer.id, product.id), false);
  const counts = db.countOrdersByStatus(buyer.id);
  assert.deepStrictEqual(
    counts,
    { pending: 1, processing: 0, confirmed: 0, delivered: 1 },
    '买家应有 1 待处理（9999 订单）+ 1 已发货'
  );
  console.log('✓ 收藏切换与资料卡四种订单计数通过');

  // 9. 重复支付 / 重复发货防护
  db.addStockItems(product.id, ['buyer4@example.com:passDDD']);
  const order6 = db.createOrder(buyer.id, product.id, product.price);
  const paid6 = payWithWallet(db, order6, config);
  assert.strictEqual(paid6.ok, true, '首次钱包支付应成功');
  const balanceAfterFirstPay = db.getUser(buyer.id).balance;
  const paid6Again = payWithWallet(db, order6, config);
  assert.strictEqual(paid6Again.ok, false, '重复钱包支付应被拒绝');
  assert.strictEqual(paid6Again.reason, 'not_pending');
  assert.strictEqual(
    db.getUser(buyer.id).balance,
    balanceAfterFirstPay,
    '重复支付不应二次扣款'
  );
  const ship6 = await deliverOrder(db, fakeBot, db.getOrder(order6.id), config);
  assert.strictEqual(ship6.ok, true, '首次发货应成功');
  const spentAfterFirstShip = db.getUser(buyer.id).total_spent;
  const ship6Again = await deliverOrder(db, fakeBot, db.getOrder(order6.id), config);
  assert.strictEqual(ship6Again.ok, false, '重复发货应被拒绝');
  assert.strictEqual(ship6Again.reason, 'not_paid');
  assert.strictEqual(
    db.getUser(buyer.id).total_spent,
    spentAfterFirstShip,
    '重复发货不应二次记消费'
  );
  console.log('✓ 重复支付 / 重复发货防护通过');

  db.close();
  console.log('\n全部冒烟测试通过 ✅');
}

main().catch((e) => {
  console.error('冒烟测试失败 ❌', e);
  process.exit(1);
});
