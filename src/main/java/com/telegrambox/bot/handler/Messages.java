package com.telegrambox.bot.handler;

import java.util.HashMap;
import java.util.Map;

/**
 * 双语文案：中文为主（与截图一致），英文保留。缺英文 key 时回落中文。
 */
public final class Messages {

    private static final Map<String, String> ZH = new HashMap<>();
    private static final Map<String, String> EN = new HashMap<>();

    static {
        // ---- 主菜单 ----
        ZH.put("menu.shop", "🛍 商店");
        ZH.put("menu.orders", "📦 我的订单");
        ZH.put("menu.wallet", "💳 钱包");
        ZH.put("menu.announcements", "📣 最新动态");
        ZH.put("menu.invite", "🤝 邀请赚钱");
        ZH.put("menu.favorites", "❤️ 收藏");
        ZH.put("menu.help", "🆘 帮助");
        ZH.put("menu.api", "🔌 API 权限");
        ZH.put("menu.settings", "⚙️ 设置");
        ZH.put("menu.admin", "🛠 管理后台");
        ZH.put("menu.back", "⬅️ 返回主页");
        ZH.put("menu.back_shop", "⬅️ 返回商店");
        ZH.put("menu.back_orders", "⬅️ 返回订单");

        // ---- 资料卡 / 欢迎 ----
        ZH.put("profile.title", "👤 个人中心");
        ZH.put("profile.level", "等级");
        ZH.put("profile.balance", "余额");
        ZH.put("profile.spent", "累计消费");
        ZH.put("profile.to_next", "距离%s还差 %s");
        ZH.put("profile.max_level", "已是最高等级");
        ZH.put("profile.orders", "订单");
        ZH.put("profile.pending", "待处理");
        ZH.put("profile.awaiting", "处理中");
        ZH.put("profile.paid", "已确认");
        ZH.put("profile.delivered", "已发货");
        ZH.put("profile.invite_link", "邀请链接");

        // ---- 商店 ----
        ZH.put("shop.title", "🛍 商店\n请选择分类：");
        ZH.put("shop.empty_cat", "该分类下暂无商品。");
        ZH.put("shop.product_stock", "库存：%d 件");
        ZH.put("shop.no_stock", "暂无库存，请稍后再来或联系客服。");
        ZH.put("shop.buy", "🛒 购买");
        ZH.put("shop.fav_add", "❤️ 收藏");
        ZH.put("shop.fav_remove", "💔 取消收藏");
        ZH.put("shop.fav_added", "已加入收藏。");
        ZH.put("shop.fav_removed", "已取消收藏。");

        // ---- 订单 ----
        ZH.put("order.created", "✅ 订单已创建\n\n订单号：#%d\n商品：%s（%s）\n金额：%s\n\n请选择付款方式：");
        ZH.put("order.pay_wallet", "💳 钱包余额支付（%s）");
        ZH.put("order.pay_usdt", "₮ USDT 转账支付");
        ZH.put("order.wallet_paid", "✅ 已用钱包余额支付，订单进入已确认，等待发货。");
        ZH.put("order.wallet_insufficient", "余额不足，请先充值。当前余额：%s，订单金额：%s。");
        ZH.put("order.usdt_instructions", "请向以下地址转账 %s USDT（TRC20）：\n\n`%s`\n\n转账后请把 TxID 发给本 bot，管理员确认到账后订单进入已确认。\n订单号：#%d");
        ZH.put("order.usdt_pending", "✅ 已收到您的转账申请，订单进入处理中，等待管理员确认到账。");
        ZH.put("order.detail", "📦 订单 #%d\n\n商品：%s（%s）\n金额：%s\n状态：%s\n付款方式：%s\n创建时间：%s%s");
        ZH.put("order.delivered_content", "\n\n🎁 发货内容：\n`%s`");
        ZH.put("order.cancelled", "订单 #%d 已取消。");
        ZH.put("order.cancel", "❌ 取消订单");
        ZH.put("order.not_found", "订单不存在。");
        ZH.put("order.list_title", "📦 我的订单");
        ZH.put("order.list_empty", "暂无订单，先去商店看看吧。");
        ZH.put("order.status.pending_payment", "待处理");
        ZH.put("order.status.awaiting_confirm", "处理中");
        ZH.put("order.status.paid", "已确认");
        ZH.put("order.status.delivered", "已发货");
        ZH.put("order.status.rejected", "已拒绝");
        ZH.put("order.status.cancelled", "已取消");
        ZH.put("order.pay_method.wallet", "钱包余额");
        ZH.put("order.pay_method.usdt", "USDT 转账");

        // ---- 钱包 ----
        ZH.put("wallet.title", "💳 钱包\n\n余额：%s\n币种：%s\n\n充值方式：USDT-TRC20 人工转账。向收款地址转账后，发送 TxID 申请充值，管理员确认到账后入账。");
        ZH.put("wallet.topup", "➕ 充值");
        ZH.put("wallet.tx", "📜 交易记录");
        ZH.put("wallet.topup_prompt_amount", "请发送充值金额（USDT 数字，例如 10）：");
        ZH.put("wallet.topup_prompt_txid", "请发送本次转账的 TxID（交易哈希）：");
        ZH.put("wallet.topup_submitted", "✅ 充值申请已提交：%s USDT\nTxID：%s\n等待管理员确认到账。");
        ZH.put("wallet.topup_invalid_amount", "金额格式不对，请输入数字，例如 10。");
        ZH.put("wallet.tx_empty", "暂无交易记录。");
        ZH.put("wallet.tx_title", "📜 最近交易记录");

        // ---- 邀请 ----
        ZH.put("invite.title", "🤝 邀请赚钱\n\n您的邀请链接：\n%s\n\n好友通过链接注册并消费后，您可获得其每笔订单 %s%% 的返佣，直接进入钱包余额。\n\n累计邀请：%d 人\n累计返佣：%s");
        ZH.put("invite.commission_rate", "5");

        // ---- 收藏 / 公告 / API / 设置 / 帮助 ----
        ZH.put("favorites.title", "❤️ 我的收藏");
        ZH.put("favorites.empty", "暂无收藏。在商品详情页点「收藏」即可加入。");
        ZH.put("ann.title", "📣 最新动态");
        ZH.put("ann.empty", "暂无公告。");
        ZH.put("api.title", "🔌 API 权限\n\n您的 API Key：\n`%s`\n\n请勿泄露给他人。重置后旧 Key 立即失效。");
        ZH.put("api.reset", "🔄 重置 Key");
        ZH.put("api.reset_done", "✅ API Key 已重置。");
        ZH.put("settings.title", "⚙️ 设置");
        ZH.put("settings.lang", "🌐 语言：%s");
        ZH.put("settings.notif", "🔔 订单通知：%s");
        ZH.put("settings.on", "开");
        ZH.put("settings.off", "关");
        ZH.put("help.text", "🆘 帮助\n\n购买流程：商店选商品 → 下单 → 钱包余额支付或 USDT 转账 → 管理员确认 → 发货。\n\n充值：钱包 → 充值 → 按提示转账并发送 TxID，管理员确认后入账。\n\n有问题请联系客服：%s");

        // ---- 管理端 ----
        ZH.put("admin.title", "🛠 管理后台");
        ZH.put("admin.products", "📦 商品管理");
        ZH.put("admin.orders", "📋 订单管理");
        ZH.put("admin.topups", "💰 充值审核");
        ZH.put("admin.ann", "📣 公告管理");
        ZH.put("admin.stats", "📊 统计");
        ZH.put("admin.broadcast", "📢 广播");
        ZH.put("admin.not_admin", "您不是管理员。");
        ZH.put("admin.stats_text", "📊 统计\n\n用户数：%d\n已发货总收入：%s\n待审核充值：%d 笔\n待确认订单：%d 笔\n待发货订单：%d 笔");
        ZH.put("admin.topups_empty", "暂无待审核充值。");
        ZH.put("admin.topup_detail", "💰 充值 #%d\n\n用户：%d\n金额：%s USDT\nTxID：`%s`");
        ZH.put("admin.topup_ok", "✅ 已确认充值 #%d 到账，余额已入账。");
        ZH.put("admin.topup_no", "充值 #%d 已拒绝。");
        ZH.put("admin.orders_empty", "暂无该状态订单。");
        ZH.put("admin.confirm_ok", "✅ 订单 #%d 已确认收款。");
        ZH.put("admin.ship_ok", "✅ 订单 #%d 已发货。");
        ZH.put("admin.ship_no_stock", "⚠️ 订单 #%d 无库存可发，请先添加库存。");
        ZH.put("admin.reject_ok", "订单 #%d 已拒绝。");
        ZH.put("admin.broadcast_prompt", "请发送要广播给全部用户的消息文本：");
        ZH.put("admin.broadcast_sent", "✅ 广播已发送给 %d 位用户。");
        ZH.put("admin.ann_prompt", "请发送公告内容，格式：标题换行正文，例如：\n上新通知\nChatGPT Plus 已补货");
        ZH.put("admin.ann_created", "✅ 公告已发布。");

        // ---- 通用 ----
        ZH.put("common.unknown", "未知操作，请返回主页重试。");
        ZH.put("common.error", "出错了，请稍后重试。");
        ZH.put("common.send_text", "请直接发送文字。");
        ZH.put("common.back_main", "已返回主页。");

        // ---- 英文（主界面与核心流程） ----
        EN.put("menu.shop", "🛍 Shop");
        EN.put("menu.orders", "📦 My Orders");
        EN.put("menu.wallet", "💳 Wallet");
        EN.put("menu.announcements", "📣 News");
        EN.put("menu.invite", "🤝 Invite & Earn");
        EN.put("menu.favorites", "❤️ Favorites");
        EN.put("menu.help", "🆘 Help");
        EN.put("menu.api", "🔌 API Access");
        EN.put("menu.settings", "⚙️ Settings");
        EN.put("menu.admin", "🛠 Admin");
        EN.put("menu.back", "⬅️ Back to Home");
        EN.put("menu.back_shop", "⬅️ Back to Shop");
        EN.put("menu.back_orders", "⬅️ Back to Orders");
        EN.put("profile.title", "👤 Profile");
        EN.put("profile.level", "Level");
        EN.put("profile.balance", "Balance");
        EN.put("profile.spent", "Total Spent");
        EN.put("profile.to_next", "%s left to %s");
        EN.put("profile.max_level", "Max level reached");
        EN.put("profile.orders", "Orders");
        EN.put("profile.pending", "Pending");
        EN.put("profile.awaiting", "Processing");
        EN.put("profile.paid", "Confirmed");
        EN.put("profile.delivered", "Delivered");
        EN.put("profile.invite_link", "Invite Link");
        EN.put("shop.title", "🛍 Shop\nChoose a category:");
        EN.put("shop.empty_cat", "No products in this category.");
        EN.put("shop.product_stock", "Stock: %d");
        EN.put("shop.no_stock", "Out of stock. Please come back later or contact support.");
        EN.put("shop.buy", "🛒 Buy");
        EN.put("shop.fav_add", "❤️ Favorite");
        EN.put("shop.fav_remove", "💔 Unfavorite");
        EN.put("shop.fav_added", "Added to favorites.");
        EN.put("shop.fav_removed", "Removed from favorites.");
        EN.put("order.created", "✅ Order created\n\nOrder: #%d\nProduct: %s (%s)\nAmount: %s\n\nChoose a payment method:");
        EN.put("order.pay_wallet", "💳 Pay with Wallet (%s)");
        EN.put("order.pay_usdt", "₮ Pay with USDT Transfer");
        EN.put("order.wallet_paid", "✅ Paid with wallet balance. Order confirmed, waiting for delivery.");
        EN.put("order.wallet_insufficient", "Insufficient balance. Current: %s, order: %s. Please top up first.");
        EN.put("order.usdt_instructions", "Please transfer %s USDT (TRC20) to:\n\n`%s`\n\nThen send the TxID to this bot. The admin will confirm receipt and your order will be confirmed.\nOrder: #%d");
        EN.put("order.usdt_pending", "✅ Transfer request received. Order is processing, waiting for admin confirmation.");
        EN.put("order.detail", "📦 Order #%d\n\nProduct: %s (%s)\nAmount: %s\nStatus: %s\nPayment: %s\nCreated: %s%s");
        EN.put("order.delivered_content", "\n\n🎁 Delivery:\n`%s`");
        EN.put("order.cancelled", "Order #%d cancelled.");
        EN.put("order.cancel", "❌ Cancel Order");
        EN.put("order.not_found", "Order not found.");
        EN.put("order.list_title", "📦 My Orders");
        EN.put("order.list_empty", "No orders yet. Visit the shop first.");
        EN.put("order.status.pending_payment", "Pending");
        EN.put("order.status.awaiting_confirm", "Processing");
        EN.put("order.status.paid", "Confirmed");
        EN.put("order.status.delivered", "Delivered");
        EN.put("order.status.rejected", "Rejected");
        EN.put("order.status.cancelled", "Cancelled");
        EN.put("order.pay_method.wallet", "Wallet");
        EN.put("order.pay_method.usdt", "USDT Transfer");
        EN.put("wallet.title", "💳 Wallet\n\nBalance: %s\nCurrency: %s\n\nTop up with USDT-TRC20 manual transfer. Transfer first, then send the TxID. The admin confirms receipt and credits your balance.");
        EN.put("wallet.topup", "➕ Top Up");
        EN.put("wallet.tx", "📜 Transactions");
        EN.put("wallet.topup_prompt_amount", "Send the top-up amount in USDT (a number, e.g. 10):");
        EN.put("wallet.topup_prompt_txid", "Send the TxID (transaction hash) of your transfer:");
        EN.put("wallet.topup_submitted", "✅ Top-up request submitted: %s USDT\nTxID: %s\nWaiting for admin confirmation.");
        EN.put("wallet.topup_invalid_amount", "Invalid amount. Send a number, e.g. 10.");
        EN.put("wallet.tx_empty", "No transactions yet.");
        EN.put("wallet.tx_title", "📜 Recent Transactions");
        EN.put("invite.title", "🤝 Invite & Earn\n\nYour invite link:\n%s\n\nWhen friends register via your link and buy, you earn %s%% of each of their orders, credited to your wallet.\n\nInvited: %d\nTotal commission: %s");
        EN.put("invite.commission_rate", "5");
        EN.put("favorites.title", "❤️ My Favorites");
        EN.put("favorites.empty", "No favorites yet. Tap Favorite on a product page to add one.");
        EN.put("ann.title", "📣 News");
        EN.put("ann.empty", "No announcements.");
        EN.put("api.title", "🔌 API Access\n\nYour API Key:\n`%s`\n\nDo not share it. Resetting invalidates the old key immediately.");
        EN.put("api.reset", "🔄 Reset Key");
        EN.put("api.reset_done", "✅ API Key reset.");
        EN.put("settings.title", "⚙️ Settings");
        EN.put("settings.lang", "🌐 Language: %s");
        EN.put("settings.notif", "🔔 Order notifications: %s");
        EN.put("settings.on", "On");
        EN.put("settings.off", "Off");
        EN.put("help.text", "🆘 Help\n\nHow to buy: Shop → pick a product → place order → pay with wallet or USDT transfer → admin confirms → delivery.\n\nTop up: Wallet → Top Up → transfer and send the TxID; the admin credits your balance after confirming receipt.\n\nSupport: %s");
        EN.put("admin.title", "🛠 Admin Panel");
        EN.put("admin.products", "📦 Products");
        EN.put("admin.orders", "📋 Orders");
        EN.put("admin.topups", "💰 Top-up Review");
        EN.put("admin.ann", "📣 Announcements");
        EN.put("admin.stats", "📊 Stats");
        EN.put("admin.broadcast", "📢 Broadcast");
        EN.put("admin.not_admin", "You are not an admin.");
        EN.put("admin.stats_text", "📊 Stats\n\nUsers: %d\nDelivered revenue: %s\nPending top-ups: %d\nOrders awaiting payment confirmation: %d\nOrders awaiting delivery: %d");
        EN.put("admin.topups_empty", "No pending top-ups.");
        EN.put("admin.topup_detail", "💰 Top-up #%d\n\nUser: %d\nAmount: %s USDT\nTxID: `%s`");
        EN.put("admin.topup_ok", "✅ Top-up #%d confirmed and credited.");
        EN.put("admin.topup_no", "Top-up #%d rejected.");
        EN.put("admin.orders_empty", "No orders in this status.");
        EN.put("admin.confirm_ok", "✅ Order #%d payment confirmed.");
        EN.put("admin.ship_ok", "✅ Order #%d shipped.");
        EN.put("admin.ship_no_stock", "⚠️ Order #%d has no stock. Add stock first.");
        EN.put("admin.reject_ok", "Order #%d rejected.");
        EN.put("admin.broadcast_prompt", "Send the message text to broadcast to all users:");
        EN.put("admin.broadcast_sent", "✅ Broadcast sent to %d users.");
        EN.put("admin.ann_prompt", "Send the announcement as: title line, then body. Example:\nNew stock\nChatGPT Plus restocked");
        EN.put("admin.ann_created", "✅ Announcement published.");
        EN.put("common.unknown", "Unknown action. Please go back home and try again.");
        EN.put("common.error", "Something went wrong. Please try again later.");
        EN.put("common.send_text", "Please send text.");
        EN.put("common.back_main", "Back to home.");
    }

    private Messages() {
    }

    /** 取文案：优先用户语言，英文缺 key 时回落中文。 */
    public static String t(String lang, String key, Object... args) {
        Map<String, String> map = "en".equals(lang) ? EN : ZH;
        String pattern = map.get(key);
        if (pattern == null) {
            pattern = ZH.get(key);
        }
        if (pattern == null) {
            return key;
        }
        return args.length == 0 ? pattern : String.format(pattern, args);
    }
}
