// 种子商品：按 Jk Digital 公开描述的商品清单建的占位数据。
// ⚠️ 价格、时长全部是占位符，不是 Jk Digital 的真实售价；
// 库存默认为 0，管理员需在 /admin 里自行加库存才能卖。
// 等用户确认真实价格表后，改这里或直接在管理面板里改价。
const SEED_PRODUCTS = [
  // AI 订阅
  { category: 'ai', name: 'ChatGPT Plus', description: 'ChatGPT Plus 订阅账号', price: 20, duration: '1 个月', sort_order: 1 },
  { category: 'ai', name: 'Claude Pro', description: 'Claude Pro 订阅账号', price: 20, duration: '1 个月', sort_order: 2 },
  { category: 'ai', name: 'Gemini Advanced', description: 'Gemini Advanced 订阅账号', price: 20, duration: '1 个月', sort_order: 3 },
  { category: 'ai', name: 'Muse Pro', description: 'Muse Pro 订阅账号', price: 10, duration: '1 个月', sort_order: 4 },
  // VPN
  { category: 'vpn', name: 'Surfshark', description: 'Surfshark VPN 账号', price: 3, duration: '1 个月', sort_order: 1 },
  { category: 'vpn', name: 'NordVPN', description: 'NordVPN 账号', price: 4, duration: '1 个月', sort_order: 2 },
  // 流媒体
  { category: 'streaming', name: 'YouTube Premium', description: 'YouTube Premium 账号', price: 5, duration: '1 个月', sort_order: 1 },
  { category: 'streaming', name: 'Netflix Premium', description: 'Netflix Premium 账号', price: 6, duration: '1 个月', sort_order: 2 },
  // 学习
  { category: 'learning', name: 'Duolingo Super', description: 'Duolingo Super 订阅账号', price: 5, duration: '1 个月', sort_order: 1 },
];

function seedIfEmpty(db) {
  if (db.countProducts() > 0) return 0;
  for (const p of SEED_PRODUCTS) {
    db.addProduct({ ...p, sort_order: p.sort_order });
  }
  return SEED_PRODUCTS.length;
}

// 占位公告（1 条，内容可改；等用户确认真实公告后再换）
const SEED_ANNOUNCEMENTS = [
  {
    title: '🎉 商店开业',
    body: '欢迎来到本店！AI 订阅、VPN、流媒体、学习账号均已上架，付款后自动发货。',
  },
];

function seedAnnouncementsIfEmpty(db) {
  if (db.countAnnouncements() > 0) return 0;
  for (const a of SEED_ANNOUNCEMENTS) db.addAnnouncement(a.title, a.body);
  return SEED_ANNOUNCEMENTS.length;
}

function seedAll(db) {
  return {
    products: seedIfEmpty(db),
    announcements: seedAnnouncementsIfEmpty(db),
  };
}

module.exports = { SEED_PRODUCTS, seedIfEmpty, SEED_ANNOUNCEMENTS, seedAnnouncementsIfEmpty, seedAll };

// 直接运行 node src/seed.js 时手动补种（不清空已有商品）
if (require.main === module) {
  const config = require('./config');
  const { StoreDB } = require('./db');
  const db = new StoreDB(config.dbPath);
  const n = seedIfEmpty(db);
  console.log(n === 0 ? '商品已存在，跳过。' : `已种入 ${n} 个占位商品。`);
  db.close();
}
