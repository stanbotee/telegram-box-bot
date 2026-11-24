// 界面文案：中文 / 英文。用户语言存在 users 表，默认 zh。
// 扩展新语言时只加一个 key 即可；缺失时回落到中文。
const STRINGS = {
  zh: {
    welcome:
      '👋 欢迎来到数字商店！\n\n在这里购买 AI 订阅、VPN、流媒体和学习类账号，付款后管理员确认即自动发货。',
    menu: '🏠 主菜单',
    shop: '🛍 商店',
    myOrders: '📦 我的订单',
    wallet: '💳 钱包',
    news: '📣 最新动态',
    invite: '🤝 邀请赚钱',
    favorites: '❤️ 收藏',
    help: '🆘 帮助',
    apiAccess: '🔌 API 权限',
    settings: '⚙️ 设置',
    support: '💬 客服',
    language: '🌐 语言 / Language',
    chooseCategory: '请选择分类：',
    chooseProduct: '请选择商品：',
    noProducts: '该分类下暂无上架商品。',
    stockLeft: '库存：{n} 件',
    price: '价格：{p}',
    duration: '时长：{d}',
    buy: '🛒 立即购买',
    favorite: '❤️ 收藏',
    unfavorite: '💔 取消收藏',
    favAdded: '✅ 已收藏「{name}」。',
    favRemoved: '已取消收藏「{name}」。',
    noFavorites: '你还没有收藏任何商品。去商店逛逛吧。',
    yourFavorites: '❤️ 我的收藏：',
    outOfStock: '❌ 库存不足，暂时无法购买。',
    choosePayment: '请选择付款方式（订单 #{id}，{product}，应付 {total}）：',
    payWallet: '💳 钱包余额支付（余额 {balance}）',
    payUsdt: '💵 USDT-TRC20（人工转账）',
    payStars: '⭐ Telegram Stars（即将上线）',
    starsSoon:
      '⭐ Telegram Stars 支付接口尚未接通（预留），请先使用 USDT-TRC20 或钱包余额付款。',
    usdtPayText:
      '请向以下 TRC20 地址转账 {total} USDT：\n\n`{address}`\n\n转账完成后，把交易哈希（TxID）直接发给本 bot，管理员确认后自动发货。\n订单号：#{id}',
    usdtNoAddress:
      '⚠️ 收款地址尚未配置，请联系客服。订单号：#{id}',
    txidReceived:
      '✅ 已收到你的付款凭证（订单 #{id}），正在等待管理员确认，确认后会自动发货到这里。',
    walletPaid:
      '✅ 已用钱包余额支付订单 #{id}（{total}），管理员确认后发货。',
    walletInsufficient:
      '❌ 钱包余额不足（余额 {balance}，需付 {total}），请先充值。',
    orderCreated: '订单 #{id} 已创建：{product}，应付 {total}。',
    orderNotPending: '订单 #{id} 已不是待付款状态，请在「我的订单」里查看。',
    orderConfirmed:
      '✅ 订单 #{id} 已确认收款，正在安排发货，发货后会通知你。',
    noOrders: '你还没有订单。',
    yourOrders: '📦 我的订单（最近 10 条）：',
    supportText:
      '💬 客服：{handle}\n如需帮助、退款或换货，请直接联系客服并附上订单号。',
    supportNoHandle: '💬 客服账号尚未配置，请稍后再试。',
    helpText:
      '🆘 帮助\n\n1. 在 🛍 商店 里选商品下单\n2. 用钱包余额或 USDT 付款\n3. 付款后把交易哈希发给 bot，管理员确认后自动发货\n4. 订单状态在 📦 我的订单 里查看\n\n有问题请联系客服：{handle}',
    chooseLanguage: '请选择语言 / Choose language：',
    langSet: '✅ 语言已切换为中文。',
    needAdmin: '⛔ 只有管理员可以使用此功能。',
    orderDelivered:
      '🎉 订单 #{id} 已发货！\n\n商品：{product}\n发货内容：\n`{content}`\n\n请妥善保管，不要泄露给他人。',
    orderRejected:
      '❌ 订单 #{id} 未通过确认：{reason}\n请联系客服处理。',
    statusPendingPayment: '待处理',
    statusAwaitingConfirm: '处理中',
    statusPaid: '已确认',
    statusDelivered: '已发货',
    statusRejected: '已拒绝',
    statusCancelled: '已取消',
    // 钱包
    walletTitle: '💳 我的钱包\n\n余额：{balance} {currency}\n累计消费：{spent}',
    topup: '💰 充值',
    transactions: '📜 交易记录',
    topupAskAmount: '💰 充值：请发充值金额数字（例如 10，单位 USDT）。',
    topupPayText:
      '请向以下 TRC20 地址转账 {total} USDT：\n\n`{address}`\n\n转账完成后，把交易哈希（TxID）直接发给本 bot，管理员确认后余额到账。',
    topupReceived: '✅ 已收到你的充值凭证，正在等待管理员确认到账。',
    topupConfirmed: '✅ 充值已到账：{amount} {currency}，当前余额 {balance}。',
    topupRejected: '❌ 你的充值申请未通过：{reason}\n请联系客服处理。',
    noTransactions: '暂无交易记录。',
    yourTransactions: '📜 交易记录（最近 10 条）：',
    txnTopup: '充值',
    txnPurchase: '消费',
    txnCommission: '返佣',
    txnRefund: '退款',
    // 邀请
    inviteText:
      '🤝 邀请赚钱\n\n你的邀请链接：\n{link}\n\n已邀请：{invited} 人 · 累计返佣：{earned}\n返佣比例：{rate}（占位配置，返佣在被推荐人订单发货后到账）',
    // 公告
    newsTitle: '📣 最新动态',
    noNews: '暂无公告。',
    // API
    apiText:
      '🔌 API 权限\n\n分销 API Key：{key}\n\nAPI 接口即将上线，Key 可先生成备用；生成后请妥善保管，泄露请点重置。',
    apiNoKey: '尚未生成',
    apiGenerate: '🔑 生成 API Key',
    apiRegen: '🔄 重置 API Key',
    apiShow: '👁 查看完整 Key',
    apiRegenerated: '✅ 新的 API Key 已生成，请妥善保管。',
    apiKeyIs: '🔑 你的 API Key：\n`{key}`\n\n请勿泄露给他人。',
    // 设置
    settingsText: '⚙️ 设置\n\n语言：{lang}\n通知：{notif}',
    notifOn: '🔔 通知：开',
    notifOff: '🔕 通知：关',
    notifSet: '✅ 通知已{state}。',
    // 资料卡
    profileLevel: '🏅 等级：{level}',
    profileToNext: '📈 距离 {next} 还差 {amount}',
    profileMaxLevel: '📈 已达到最高等级',
    profileOrders: '📦 订单：处理中：{processing} · 已发货：{delivered} · 已确认：{confirmed} · 待处理：{pending}',
    profileInvite: '🔗 您的邀请链接：\n{link}',
  },
  en: {
    welcome:
      '👋 Welcome to the digital store!\n\nBuy AI subscriptions, VPNs, streaming and learning accounts here. Delivery is automatic after the admin confirms your payment.',
    menu: '🏠 Main menu',
    shop: '🛍 Shop',
    myOrders: '📦 My orders',
    wallet: '💳 Wallet',
    news: '📣 News',
    invite: '🤝 Invite & Earn',
    favorites: '❤️ Favorites',
    help: '🆘 Help',
    apiAccess: '🔌 API Access',
    settings: '⚙️ Settings',
    support: '💬 Support',
    language: '🌐 Language',
    chooseCategory: 'Choose a category:',
    chooseProduct: 'Choose a product:',
    noProducts: 'No products in this category yet.',
    stockLeft: 'Stock: {n} left',
    price: 'Price: {p}',
    duration: 'Duration: {d}',
    buy: '🛒 Buy now',
    favorite: '❤️ Favorite',
    unfavorite: '💔 Unfavorite',
    favAdded: '✅ Added 「{name}」 to favorites.',
    favRemoved: 'Removed 「{name}」 from favorites.',
    noFavorites: 'You have no favorites yet.',
    yourFavorites: '❤️ My favorites:',
    outOfStock: '❌ Out of stock.',
    choosePayment: 'Choose a payment method (Order #{id}, {product}, total {total}):',
    payWallet: '💳 Pay with wallet balance ({balance})',
    payUsdt: '💵 USDT-TRC20 (manual transfer)',
    payStars: '⭐ Telegram Stars (coming soon)',
    starsSoon:
      '⭐ Telegram Stars is not connected yet (reserved). Please pay with USDT-TRC20 or wallet balance for now.',
    usdtPayText:
      'Please send {total} USDT to this TRC20 address:\n\n`{address}`\n\nAfter the transfer, send the transaction hash (TxID) to this bot. The admin will confirm and delivery is automatic.\nOrder: #{id}',
    usdtNoAddress:
      '⚠️ Payment address is not configured yet. Please contact support. Order: #{id}',
    txidReceived:
      '✅ Payment proof received (Order #{id}). Waiting for admin confirmation; delivery will arrive here automatically.',
    walletPaid:
      '✅ Paid order #{id} with wallet balance ({total}). Delivery follows after admin confirmation.',
    walletInsufficient:
      '❌ Insufficient wallet balance ({balance}, need {total}). Please top up first.',
    orderCreated: 'Order #{id} created: {product}, total {total}.',
    orderNotPending: 'Order #{id} is no longer pending payment. Check it in My orders.',
    orderConfirmed:
      '✅ Order #{id} payment confirmed. Delivery is being arranged.',
    noOrders: 'You have no orders yet.',
    yourOrders: '📦 My orders (latest 10):',
    supportText:
      '💬 Support: {handle}\nFor help, refunds or replacements, contact support with your order number.',
    supportNoHandle: '💬 Support is not configured yet. Please try again later.',
    helpText:
      '🆘 Help\n\n1. Pick a product in the 🛍 Shop and place an order\n2. Pay with wallet balance or USDT\n3. Send the TxID to the bot; delivery is automatic after admin confirmation\n4. Check order status in 📦 My orders\n\nSupport: {handle}',
    chooseLanguage: 'Choose language:',
    langSet: '✅ Language set to English.',
    needAdmin: '⛔ Admin only.',
    orderDelivered:
      '🎉 Order #{id} delivered!\n\nProduct: {product}\nDelivery:\n`{content}`\n\nKeep it safe and do not share it.',
    orderRejected:
      '❌ Order #{id} was not confirmed: {reason}\nPlease contact support.',
    statusPendingPayment: 'Pending',
    statusAwaitingConfirm: 'Processing',
    statusPaid: 'Confirmed',
    statusDelivered: 'Delivered',
    statusRejected: 'Rejected',
    statusCancelled: 'Cancelled',
    walletTitle: '💳 My wallet\n\nBalance: {balance} {currency}\nTotal spent: {spent}',
    topup: '💰 Top up',
    transactions: '📜 Transactions',
    topupAskAmount: '💰 Top up: send the amount (e.g. 10, in USDT).',
    topupPayText:
      'Please send {total} USDT to this TRC20 address:\n\n`{address}`\n\nAfter the transfer, send the TxID to this bot. The balance will be credited after admin confirmation.',
    topupReceived: '✅ Top-up proof received. Waiting for admin confirmation.',
    topupConfirmed: '✅ Top-up credited: {amount} {currency}. Current balance: {balance}.',
    topupRejected: '❌ Your top-up was not confirmed: {reason}\nPlease contact support.',
    noTransactions: 'No transactions yet.',
    yourTransactions: '📜 Transactions (latest 10):',
    txnTopup: 'Top-up',
    txnPurchase: 'Purchase',
    txnCommission: 'Commission',
    txnRefund: 'Refund',
    inviteText:
      '🤝 Invite & Earn\n\nYour invite link:\n{link}\n\nInvited: {invited} · Total commission: {earned}\nCommission rate: {rate} (placeholder; credited after the referred order is delivered)',
    newsTitle: '📣 News',
    noNews: 'No announcements yet.',
    apiText:
      '🔌 API Access\n\nReseller API key: {key}\n\nThe API is coming soon; you can generate a key in advance. Keep it safe; reset it if leaked.',
    apiNoKey: 'Not generated yet',
    apiGenerate: '🔑 Generate API key',
    apiRegen: '🔄 Reset API key',
    apiShow: '👁 Show full key',
    apiRegenerated: '✅ A new API key has been generated. Keep it safe.',
    apiKeyIs: '🔑 Your API key:\n`{key}`\n\nDo not share it.',
    settingsText: '⚙️ Settings\n\nLanguage: {lang}\nNotifications: {notif}',
    notifOn: '🔔 Notifications: On',
    notifOff: '🔕 Notifications: Off',
    notifSet: '✅ Notifications {state}.',
    profileLevel: '🏅 Level: {level}',
    profileToNext: '📈 {amount} to {next}',
    profileMaxLevel: '📈 Max level reached',
    profileOrders: '📦 Orders: Processing: {processing} · Delivered: {delivered} · Confirmed: {confirmed} · Pending: {pending}',
    profileInvite: '🔗 Your invite link:\n{link}',
  },
};

function t(lang, key, vars = {}) {
  const dict = STRINGS[lang] || STRINGS.zh;
  let s = dict[key] ?? STRINGS.zh[key] ?? key;
  for (const [k, v] of Object.entries(vars)) {
    s = s.replaceAll(`{${k}}`, String(v));
  }
  return s;
}

function statusLabel(lang, status) {
  const map = {
    pending_payment: 'statusPendingPayment',
    awaiting_confirm: 'statusAwaitingConfirm',
    paid: 'statusPaid',
    delivered: 'statusDelivered',
    rejected: 'statusRejected',
    cancelled: 'statusCancelled',
  };
  return t(lang, map[status] || status);
}

module.exports = { t, statusLabel, STRINGS };
