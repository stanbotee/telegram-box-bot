package com.telegrambox.bot.handler;

import com.telegrambox.bot.config.AppConfig;
import com.telegrambox.bot.db.StoreDb;
import com.telegrambox.bot.model.*;
import com.telegrambox.bot.service.FormatUtil;
import com.telegrambox.bot.service.LevelService;
import com.telegrambox.bot.service.StoreService;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Telegram bot 主处理器：/start 资料卡、9 个主菜单、商店、下单、钱包、
 * 邀请、收藏、公告、API Key、设置、管理端。全部状态走 StoreDb。
 */
public class BotHandler extends TelegramLongPollingBot {

    private final AppConfig config;
    private final StoreDb db;
    private final StoreService store;
    private final LevelService levels;

    /** 待输入状态：userId -> "状态"（冒号后带参数，如 topup:txid:10.5）。 */
    private final Map<Long, String> pendingInput = new HashMap<>();

    public BotHandler(AppConfig config, StoreDb db, StoreService store, LevelService levels) {
        // telegrambots 6.9.7.1：DefaultAbsSender 自带 OkHttp 客户端，execute() 无需先注册
        super();
        this.config = config;
        this.db = db;
        this.store = store;
        this.levels = levels;
    }

    @Override
    public String getBotUsername() {
        return config.botUsername == null ? "" : config.botUsername;
    }

    @Override
    public String getBotToken() {
        return config.botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasCallbackQuery()) {
                handleCallback(update);
            } else if (update.hasMessage() && update.getMessage().hasText()) {
                handleMessage(update);
            }
        } catch (Exception e) {
            // 不让一次异常杀掉整个更新循环
            e.printStackTrace();
        }
    }

    // ---------------- 入口 ----------------

    private void handleMessage(Update update) throws Exception {
        var msg = update.getMessage();
        long chatId = msg.getChatId();
        long userId = msg.getFrom().getId();
        String text = msg.getText().trim();

        User user = ensureUser(msg);
        String lang = user.language();

        if (text.startsWith("/start")) {
            String payload = text.length() > 7 ? text.substring(7).trim() : "";
            bindReferrer(user, payload);
            sendMainMenu(chatId, user);
            return;
        }
        if (text.equals("/admin")) {
            if (config.isAdmin(userId)) {
                sendText(chatId, Messages.t(lang, "admin.title"), Keyboards.adminMenu(lang));
            } else {
                sendText(chatId, Messages.t(lang, "admin.not_admin"), Keyboards.backMain(lang));
            }
            return;
        }

        String pending = pendingInput.get(userId);
        if (pending != null) {
            handlePendingInput(chatId, user, pending, text);
            return;
        }
        // 其他文字：回主页
        sendMainMenu(chatId, user);
    }

    private User ensureUser(org.telegram.telegrambots.meta.api.objects.Message msg) throws SQLException {
        var from = msg.getFrom();
        User user = db.upsertUser(from.getId(),
                from.getUserName(),
                from.getFirstName());
        return user;
    }

    /** /start ref_<id> 绑定推荐人：不能推荐自己、只能绑定一次、推荐人必须已存在。 */
    private void bindReferrer(User user, String payload) throws SQLException {
        if (payload == null || !payload.startsWith("ref_")) {
            return;
        }
        try {
            long referrerId = Long.parseLong(payload.substring(4));
            if (referrerId == user.id() || user.referrerId() != null) {
                return;
            }
            if (db.getUser(referrerId) == null) {
                return;
            }
            db.setReferrer(user.id(), referrerId);
        } catch (NumberFormatException ignored) {
        }
    }

    // ---------------- 主菜单 / 资料卡 ----------------

    /** 资料卡文本：与截图格式一致。 */
    String profileCard(User user) throws SQLException {
        String lang = user.language();
        LevelService.Level level = levels.levelFor(user.totalSpent());
        Double remaining = levels.remainingToNextLevel(user.totalSpent());
        String nextLine = remaining == null
                ? Messages.t(lang, "profile.max_level")
                : Messages.t(lang, "profile.to_next",
                levels.nextLevelName(user.totalSpent()),
                FormatUtil.fmtUsd(remaining));
        int pending = db.countOrdersByStatus(user.id(), Order.PENDING_PAYMENT);
        int awaiting = db.countOrdersByStatus(user.id(), Order.AWAITING_CONFIRM);
        int paid = db.countOrdersByStatus(user.id(), Order.PAID);
        int delivered = db.countOrdersByStatus(user.id(), Order.DELIVERED);
        String inviteLink = "https://t.me/" + config.botUsername + "?start=ref_" + user.id();
        return Messages.t(lang, "profile.title") + "\n\n"
                + "👤 " + (user.firstName() != null ? user.firstName() : "") + "\n"
                + Messages.t(lang, "profile.level") + "：" + level.name() + "\n"
                + "ID：" + user.id() + "\n"
                + "用户名：" + (user.username() != null ? "@" + user.username() : "—") + "\n"
                + "💰 " + Messages.t(lang, "profile.balance") + "：" + FormatUtil.fmtUsd(user.balance()) + "\n"
                + "💸 " + Messages.t(lang, "profile.spent") + "：" + FormatUtil.fmtUsd(user.totalSpent()) + "\n"
                + nextLine + "\n"
                + "📦 " + Messages.t(lang, "profile.orders") + "："
                + Messages.t(lang, "profile.pending") + "：" + pending + " · "
                + Messages.t(lang, "profile.awaiting") + "：" + awaiting + " · "
                + Messages.t(lang, "profile.paid") + "：" + paid + " · "
                + Messages.t(lang, "profile.delivered") + "：" + delivered + "\n"
                + "🔗 " + Messages.t(lang, "profile.invite_link") + "：" + inviteLink;
    }

    private void sendMainMenu(long chatId, User user) throws Exception {
        sendText(chatId, profileCard(user),
                Keyboards.mainMenu(user.language(), config.isAdmin(user.id())));
    }

    // ---------------- 待输入 ----------------

    private void handlePendingInput(long chatId, User user, String pending, String text) throws Exception {
        String lang = user.language();
        if (pending.equals("topup:amount")) {
            double amount;
            try {
                amount = Double.parseDouble(text);
                if (amount <= 0) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException e) {
                sendText(chatId, Messages.t(lang, "wallet.topup_invalid_amount"), null);
                return;
            }
            pendingInput.put(user.id(), "topup:txid:" + amount);
            sendText(chatId, Messages.t(lang, "wallet.topup_prompt_txid"), null);
            return;
        }
        if (pending.startsWith("topup:txid:")) {
            double amount = Double.parseDouble(pending.substring("topup:txid:".length()));
            long topupId = db.createTopup(user.id(), amount, text);
            pendingInput.remove(user.id());
            sendText(chatId, Messages.t(lang, "wallet.topup_submitted",
                    FormatUtil.fmtMoney(amount), text), Keyboards.backMain(lang));
            return;
        }
        if (pending.startsWith("usdt:txid:")) {
            long orderId = Long.parseLong(pending.substring("usdt:txid:".length()));
            Order order = db.getOrder(orderId);
            pendingInput.remove(user.id());
            if (order == null || order.userId() != user.id()
                    || !Order.PENDING_PAYMENT.equals(order.status())) {
                sendText(chatId, Messages.t(lang, "order.not_found"), Keyboards.backMain(lang));
                return;
            }
            db.setOrderPaymentRef(orderId, text);
            db.updateOrderStatus(orderId, Order.PENDING_PAYMENT, Order.AWAITING_CONFIRM);
            sendText(chatId, Messages.t(lang, "order.usdt_pending"), Keyboards.backMain(lang));
            return;
        }
        if (pending.equals("broadcast")) {
            pendingInput.remove(user.id());
            List<Long> ids = db.listAllUserIds();
            int sent = 0;
            for (Long id : ids) {
                try {
                    sendText(id, text, null);
                    sent++;
                } catch (Exception ignored) {
                }
            }
            sendText(chatId, Messages.t(lang, "admin.broadcast_sent", sent), Keyboards.adminMenu(lang));
            return;
        }
        if (pending.equals("announce")) {
            pendingInput.remove(user.id());
            String[] parts = text.split("\n", 2);
            String title = parts[0].trim();
            String body = parts.length > 1 ? parts[1].trim() : "";
            db.createAnnouncement(title, body);
            sendText(chatId, Messages.t(lang, "admin.ann_created"), Keyboards.adminMenu(lang));
            return;
        }
        if (pending.startsWith("stock:")) {
            long productId = Long.parseLong(pending.substring("stock:".length()));
            pendingInput.remove(user.id());
            int added = 0;
            for (String line : text.split("\n")) {
                String item = line.trim();
                if (!item.isEmpty()) {
                    db.addStock(productId, item);
                    added++;
                }
            }
            sendText(chatId, "✅ 已添加 " + added + " 条库存。", Keyboards.adminMenu(lang));
            return;
        }
        pendingInput.remove(user.id());
        sendMainMenu(chatId, user);
    }

    // ---------------- 回调 ----------------

    private void handleCallback(Update update) throws Exception {
        var cq = update.getCallbackQuery();
        long chatId = cq.getMessage().getChatId();
        int messageId = cq.getMessage().getMessageId();
        long userId = cq.getFrom().getId();
        String data = cq.getData();

        User user = db.upsertUser(userId, cq.getFrom().getUserName(), cq.getFrom().getFirstName());
        String lang = user.language();
        // 每个回调只回答一次（Node 版曾因回答两次抛 query is too old）。
        // 收藏分支要在回答里带提示文本，所以这里先跳过，由分支自己回答。
        boolean deferAnswer = data.startsWith("shop:fav:");
        if (!deferAnswer) {
            answerCallback(cq.getId(), null);
        }

        String[] parts = data.split(":", -1);
        String section = parts[0];
        try {
            switch (section) {
                case "menu" -> handleMenuCallback(chatId, messageId, user, parts);
                case "shop" -> handleShopCallback(chatId, messageId, user, parts, cq);
                case "order" -> handleOrderCallback(chatId, messageId, user, parts);
                case "wallet" -> handleWalletCallback(chatId, messageId, user, parts);
                case "api" -> handleApiCallback(chatId, messageId, user, parts);
                case "settings" -> handleSettingsCallback(chatId, messageId, user, parts);
                case "admin" -> handleAdminCallback(chatId, messageId, user, parts);
                default -> editText(chatId, messageId, Messages.t(lang, "common.unknown"),
                        Keyboards.backMain(lang));
            }
        } catch (SQLException e) {
            editText(chatId, messageId, Messages.t(lang, "common.error"), Keyboards.backMain(lang));
        }
    }

    private void handleMenuCallback(long chatId, int messageId, User user, String[] parts) throws Exception {
        String lang = user.language();
        String action = parts.length > 1 ? parts[1] : "main";
        switch (action) {
            case "main" -> editText(chatId, messageId, profileCard(user),
                    Keyboards.mainMenu(lang, config.isAdmin(user.id())));
            case "shop" -> {
                List<String> cats = db.listCategories();
                editText(chatId, messageId, Messages.t(lang, "shop.title"),
                        Keyboards.categories(lang, cats));
            }
            case "orders" -> {
                List<Order> orders = db.listUserOrders(user.id(), 10);
                if (orders.isEmpty()) {
                    editText(chatId, messageId, Messages.t(lang, "order.list_empty"),
                            Keyboards.backMain(lang));
                } else {
                    editText(chatId, messageId, Messages.t(lang, "order.list_title"),
                            Keyboards.ordersList(lang, orders));
                }
            }
            case "wallet" -> editText(chatId, messageId,
                    Messages.t(lang, "wallet.title",
                            FormatUtil.fmtUsd(user.balance()), config.walletCurrency),
                    Keyboards.wallet(lang));
            case "announcements" -> {
                List<Announcement> anns = db.listActiveAnnouncements(5);
                if (anns.isEmpty()) {
                    editText(chatId, messageId, Messages.t(lang, "ann.empty"), Keyboards.backMain(lang));
                } else {
                    StringBuilder sb = new StringBuilder(Messages.t(lang, "ann.title")).append("\n");
                    for (Announcement a : anns) {
                        sb.append("\n\n📌 ").append(a.title());
                        if (a.body() != null && !a.body().isBlank()) {
                            sb.append("\n").append(a.body());
                        }
                    }
                    editText(chatId, messageId, sb.toString(), Keyboards.backMain(lang));
                }
            }
            case "invite" -> {
                String link = "https://t.me/" + config.botUsername + "?start=ref_" + user.id();
                List<Referral> refs = db.listReferralsByReferrer(user.id(), 1000);
                int invited = refs.stream().map(Referral::referredId).collect(java.util.stream.Collectors.toSet()).size();
                double total = db.referralTotal(user.id());
                editText(chatId, messageId, Messages.t(lang, "invite.title", link,
                        Messages.t(lang, "invite.commission_rate"), invited, FormatUtil.fmtUsd(total)),
                        Keyboards.backMain(lang));
            }
            case "favorites" -> {
                List<Product> favs = db.listFavorites(user.id());
                if (favs.isEmpty()) {
                    editText(chatId, messageId, Messages.t(lang, "favorites.empty"), Keyboards.backMain(lang));
                } else {
                    editText(chatId, messageId, Messages.t(lang, "favorites.title"),
                            Keyboards.favorites(lang, favs));
                }
            }
            case "help" -> editText(chatId, messageId,
                    Messages.t(lang, "help.text",
                            config.supportUsername.isBlank() ? "—" : "@" + config.supportUsername),
                    Keyboards.backMain(lang));
            case "api" -> editText(chatId, messageId,
                    Messages.t(lang, "api.title", user.apiKey()), Keyboards.apiKey(lang));
            case "settings" -> editText(chatId, messageId, Messages.t(lang, "settings.title"),
                    Keyboards.settings(lang, user.notifications()));
            case "admin" -> {
                if (!config.isAdmin(user.id())) {
                    editText(chatId, messageId, Messages.t(lang, "admin.not_admin"), Keyboards.backMain(lang));
                } else {
                    editText(chatId, messageId, Messages.t(lang, "admin.title"), Keyboards.adminMenu(lang));
                }
            }
            default -> editText(chatId, messageId, Messages.t(lang, "common.unknown"), Keyboards.backMain(lang));
        }
    }

    private void handleShopCallback(long chatId, int messageId, User user, String[] parts,
                                    org.telegram.telegrambots.meta.api.objects.CallbackQuery cq) throws Exception {
        String lang = user.language();
        String action = parts[1];
        switch (action) {
            case "cat" -> {
                String category = parts[2];
                List<Product> products = db.listProductsByCategory(category);
                if (products.isEmpty()) {
                    editText(chatId, messageId, Messages.t(lang, "shop.empty_cat"),
                            Keyboards.categories(lang, db.listCategories()));
                } else {
                    editText(chatId, messageId, "📂 " + category, Keyboards.products(lang, category, products));
                }
            }
            case "prod" -> {
                Product p = db.getProduct(Long.parseLong(parts[2]));
                if (p == null) {
                    editText(chatId, messageId, Messages.t(lang, "common.error"), Keyboards.backMain(lang));
                    return;
                }
                int stock = db.availableStockCount(p.id());
                boolean fav = db.isFavorite(user.id(), p.id());
                String text = "🛍 " + p.name() + "\n\n"
                        + (p.description() != null ? p.description() + "\n" : "")
                        + "💲 价格：" + FormatUtil.fmtUsd(p.price()) + "\n"
                        + "⏱ 时长：" + p.duration() + "\n"
                        + Messages.t(lang, "shop.product_stock", stock);
                editText(chatId, messageId, text, Keyboards.productDetail(lang, p, stock, fav));
            }
            case "fav" -> {
                long pid = Long.parseLong(parts[2]);
                boolean nowFav = db.toggleFavorite(user.id(), pid);
                // 只更新提示，不重渲染整页（避免重复回答回调）
                answerCallback(cq.getId(), nowFav
                        ? Messages.t(lang, "shop.fav_added") : Messages.t(lang, "shop.fav_removed"));
                Product p = db.getProduct(pid);
                if (p != null) {
                    int stock = db.availableStockCount(p.id());
                    String text = "🛍 " + p.name() + "\n\n"
                            + (p.description() != null ? p.description() + "\n" : "")
                            + "💲 价格：" + FormatUtil.fmtUsd(p.price()) + "\n"
                            + "⏱ 时长：" + p.duration() + "\n"
                            + Messages.t(lang, "shop.product_stock", stock);
                    editText(chatId, messageId, text, Keyboards.productDetail(lang, p, stock, nowFav));
                }
            }
            case "buy" -> {
                Product p = db.getProduct(Long.parseLong(parts[2]));
                if (p == null) {
                    editText(chatId, messageId, Messages.t(lang, "common.error"), Keyboards.backMain(lang));
                    return;
                }
                int stock = db.availableStockCount(p.id());
                if (stock <= 0) {
                    answerCallback(cq.getId(), Messages.t(lang, "shop.no_stock"));
                    return;
                }
                long orderId = db.createOrder(user.id(), p.id(), 1, p.price(), "pending", null);
                Order order = db.getOrder(orderId);
                editText(chatId, messageId,
                        Messages.t(lang, "order.created", orderId, p.name(), p.duration(),
                                FormatUtil.fmtUsd(p.price())),
                        Keyboards.payOptions(lang, order, user.balance()));
            }
            case "pay" -> {
                long orderId = Long.parseLong(parts[2]);
                String method = parts[3];
                Order order = db.getOrder(orderId);
                if (order == null || order.userId() != user.id()
                        || !Order.PENDING_PAYMENT.equals(order.status())) {
                    editText(chatId, messageId, Messages.t(lang, "order.not_found"), Keyboards.backMain(lang));
                    return;
                }
                if ("wallet".equals(method)) {
                    db.setOrderPayment(orderId, "wallet");
                    User fresh = db.getUser(user.id());
                    if (fresh.balance() < order.total()) {
                        editText(chatId, messageId,
                                Messages.t(lang, "order.wallet_insufficient",
                                        FormatUtil.fmtUsd(fresh.balance()), FormatUtil.fmtUsd(order.total())),
                                Keyboards.backMain(lang));
                        return;
                    }
                    if (!store.payWithWallet(orderId)) {
                        editText(chatId, messageId, Messages.t(lang, "common.error"),
                                Keyboards.backMain(lang));
                        return;
                    }
                    editText(chatId, messageId, Messages.t(lang, "order.wallet_paid"),
                            Keyboards.backMain(lang));
                } else {
                    db.setOrderPayment(orderId, "usdt");
                    String addr = config.usdtAddress.isBlank() ? "（待管理员配置收款地址）" : config.usdtAddress;
                    editText(chatId, messageId,
                            Messages.t(lang, "order.usdt_instructions",
                                    FormatUtil.fmtMoney(order.total()), addr, orderId),
                            Keyboards.backMain(lang));
                    pendingInput.put(user.id(), "usdt:txid:" + orderId);
                }
            }
            default -> editText(chatId, messageId, Messages.t(lang, "common.unknown"), Keyboards.backMain(lang));
        }
    }

    private void handleOrderCallback(long chatId, int messageId, User user, String[] parts) throws Exception {
        String lang = user.language();
        String action = parts[1];
        if ("detail".equals(action)) {
            Order order = db.getOrder(Long.parseLong(parts[2]));
            if (order == null || order.userId() != user.id()) {
                editText(chatId, messageId, Messages.t(lang, "order.not_found"), Keyboards.backMain(lang));
                return;
            }
            editText(chatId, messageId, orderDetailText(user, order), Keyboards.orderDetail(lang, order));
        } else if ("cancel".equals(action)) {
            long orderId = Long.parseLong(parts[2]);
            if (store.cancelOrder(orderId, user.id())) {
                editText(chatId, messageId, Messages.t(lang, "order.cancelled", orderId),
                        Keyboards.backMain(lang));
            } else {
                editText(chatId, messageId, Messages.t(lang, "order.not_found"), Keyboards.backMain(lang));
            }
        }
    }

    private String orderDetailText(User user, Order order) throws SQLException {
        String lang = user.language();
        String content = "";
        if (Order.DELIVERED.equals(order.status()) && order.stockItemId() != null) {
            StockItem item = db.getStockItem(order.stockItemId());
            if (item != null) {
                content = Messages.t(lang, "order.delivered_content", item.content());
            }
        }
        return Messages.t(lang, "order.detail", order.id(),
                order.productName() != null ? order.productName() : "",
                order.productDuration() != null ? order.productDuration() : "",
                FormatUtil.fmtUsd(order.total()),
                Messages.t(lang, "order.status." + order.status()),
                Messages.t(lang, "order.pay_method." + ("wallet".equals(order.paymentMethod()) ? "wallet" : "usdt")),
                order.createdAt(), content);
    }

    private void handleWalletCallback(long chatId, int messageId, User user, String[] parts) throws Exception {
        String lang = user.language();
        String action = parts[1];
        if ("topup".equals(action)) {
            pendingInput.put(user.id(), "topup:amount");
            editText(chatId, messageId, Messages.t(lang, "wallet.topup_prompt_amount"),
                    Keyboards.backMain(lang));
        } else if ("tx".equals(action)) {
            List<WalletTransaction> txs = db.listWalletTx(user.id(), 10);
            if (txs.isEmpty()) {
                editText(chatId, messageId, Messages.t(lang, "wallet.tx_empty"), Keyboards.backMain(lang));
                return;
            }
            StringBuilder sb = new StringBuilder(Messages.t(lang, "wallet.tx_title")).append("\n");
            for (WalletTransaction t : txs) {
                String sign = t.amount() >= 0 ? "+" : "";
                sb.append("\n").append(t.type()).append(" ").append(sign)
                        .append(FormatUtil.fmtUsd(t.amount())).append(" · ").append(t.createdAt());
            }
            editText(chatId, messageId, sb.toString(), Keyboards.backMain(lang));
        }
    }

    private void handleApiCallback(long chatId, int messageId, User user, String[] parts) throws Exception {
        String lang = user.language();
        if ("reset".equals(parts[1])) {
            String key = db.regenerateApiKey(user.id());
            editText(chatId, messageId, Messages.t(lang, "api.reset_done") + "\n\n"
                    + Messages.t(lang, "api.title", key), Keyboards.apiKey(lang));
        }
    }

    private void handleSettingsCallback(long chatId, int messageId, User user, String[] parts) throws Exception {
        String lang = user.language();
        if ("lang".equals(parts[1])) {
            String newLang = parts[2];
            db.setLanguage(user.id(), newLang);
            User fresh = db.getUser(user.id());
            editText(chatId, messageId, Messages.t(newLang, "settings.title"),
                    Keyboards.settings(newLang, fresh.notifications()));
        } else if ("notif".equals(parts[1])) {
            boolean on = "on".equals(parts[2]);
            db.setNotifications(user.id(), on);
            User fresh = db.getUser(user.id());
            editText(chatId, messageId, Messages.t(lang, "settings.title"),
                    Keyboards.settings(lang, fresh.notifications()));
        }
    }

    private void handleAdminCallback(long chatId, int messageId, User user, String[] parts) throws Exception {
        String lang = user.language();
        if (!config.isAdmin(user.id())) {
            editText(chatId, messageId, Messages.t(lang, "admin.not_admin"), Keyboards.backMain(lang));
            return;
        }
        String action = parts[1];
        switch (action) {
            case "products" -> {
                List<Product> products = db.listAllProducts();
                editText(chatId, messageId, Messages.t(lang, "admin.products"),
                        Keyboards.adminProducts(lang, products));
            }
            case "product" -> {
                Product p = db.getProduct(Long.parseLong(parts[2]));
                if (p == null) {
                    editText(chatId, messageId, Messages.t(lang, "common.error"), Keyboards.adminMenu(lang));
                    return;
                }
                int stock = db.availableStockCount(p.id());
                String text = "📦 " + p.name() + "\n\n价格：" + FormatUtil.fmtUsd(p.price())
                        + "\n时长：" + p.duration() + "\n分类：" + p.category()
                        + "\n状态：" + (p.active() ? "上架" : "下架")
                        + "\n" + Messages.t(lang, "shop.product_stock", stock);
                List<List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> rows = new java.util.ArrayList<>();
                rows.add(List.of(
                        btn("➕ 添加库存", "admin:addstock:" + p.id()),
                        btn(p.active() ? "⏸ 下架" : "▶️ 上架", "admin:toggle:" + p.id())));
                rows.add(List.of(btn(Messages.t(lang, "menu.back"), "admin:products")));
                var markup = new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
                markup.setKeyboard(rows);
                editText(chatId, messageId, text, markup);
            }
            case "addstock" -> {
                pendingInput.put(user.id(), "stock:" + parts[2]);
                editText(chatId, messageId, "请发送库存内容，每行一条（账号密码 / 激活码 / 卡密）：",
                        Keyboards.backMain(lang));
            }
            case "toggle" -> {
                Product p = db.getProduct(Long.parseLong(parts[2]));
                if (p != null) {
                    db.setProductActive(p.id(), !p.active());
                }
                List<Product> products = db.listAllProducts();
                editText(chatId, messageId, Messages.t(lang, "admin.products"),
                        Keyboards.adminProducts(lang, products));
            }
            case "orders" -> {
                // 默认看待确认的，其次待发货
                List<Order> orders = db.listOrdersByStatus(Order.AWAITING_CONFIRM, 10);
                if (orders.isEmpty()) {
                    orders = db.listOrdersByStatus(Order.PAID, 10);
                }
                if (orders.isEmpty()) {
                    editText(chatId, messageId, Messages.t(lang, "admin.orders_empty"),
                            Keyboards.adminMenu(lang));
                } else {
                    editText(chatId, messageId, Messages.t(lang, "admin.orders"),
                            Keyboards.adminOrders(lang, orders));
                }
            }
            case "order" -> {
                Order order = db.getOrder(Long.parseLong(parts[2]));
                if (order == null) {
                    editText(chatId, messageId, Messages.t(lang, "order.not_found"), Keyboards.adminMenu(lang));
                    return;
                }
                String text = "📦 订单 #" + order.id() + "\n\n用户：" + order.userId()
                        + "\n商品：" + (order.productName() != null ? order.productName() : "")
                        + "\n金额：" + FormatUtil.fmtUsd(order.total())
                        + "\n状态：" + Messages.t(lang, "order.status." + order.status())
                        + "\n付款方式：" + order.paymentMethod()
                        + (order.paymentRef() != null ? "\n付款凭证：" + order.paymentRef() : "");
                editText(chatId, messageId, text, Keyboards.adminOrderDetail(lang, order));
            }
            case "confirm" -> {
                long orderId = Long.parseLong(parts[2]);
                if (store.confirmPayment(orderId)) {
                    editText(chatId, messageId, Messages.t(lang, "admin.confirm_ok", orderId),
                            Keyboards.adminMenu(lang));
                } else {
                    editText(chatId, messageId, Messages.t(lang, "common.error"), Keyboards.adminMenu(lang));
                }
            }
            case "ship" -> {
                long orderId = Long.parseLong(parts[2]);
                Order order = db.getOrder(orderId);
                StockItem item = store.shipOrder(orderId);
                if (item == null) {
                    editText(chatId, messageId, Messages.t(lang, "admin.ship_no_stock", orderId),
                            Keyboards.adminMenu(lang));
                } else {
                    editText(chatId, messageId, Messages.t(lang, "admin.ship_ok", orderId),
                            Keyboards.adminMenu(lang));
                    // 把发货内容直接发给买家
                    sendText(order.userId(),
                            "🎁 订单 #" + orderId + " 已发货\n\n"
                                    + (order.productName() != null ? order.productName() : "") + "\n"
                                    + "发货内容：\n`" + item.content() + "`", null);
                }
            }
            case "reject" -> {
                long orderId = Long.parseLong(parts[2]);
                if (store.rejectOrder(orderId)) {
                    editText(chatId, messageId, Messages.t(lang, "admin.reject_ok", orderId),
                            Keyboards.adminMenu(lang));
                } else {
                    editText(chatId, messageId, Messages.t(lang, "common.error"), Keyboards.adminMenu(lang));
                }
            }
            case "topups" -> {
                List<Topup> topups = db.listPendingTopups(10);
                if (topups.isEmpty()) {
                    editText(chatId, messageId, Messages.t(lang, "admin.topups_empty"),
                            Keyboards.adminMenu(lang));
                } else {
                    editText(chatId, messageId, Messages.t(lang, "admin.topups"),
                            Keyboards.adminTopups(lang, topups));
                }
            }
            case "topup" -> {
                Topup t = db.getTopup(Long.parseLong(parts[2]));
                if (t == null) {
                    editText(chatId, messageId, Messages.t(lang, "common.error"), Keyboards.adminMenu(lang));
                    return;
                }
                editText(chatId, messageId,
                        Messages.t(lang, "admin.topup_detail", t.id(), t.userId(),
                                FormatUtil.fmtMoney(t.amount()), t.txid()),
                        Keyboards.adminTopupDetail(lang, t.id()));
            }
            case "topup_ok" -> {
                long topupId = Long.parseLong(parts[2]);
                if (store.confirmTopup(topupId)) {
                    editText(chatId, messageId, Messages.t(lang, "admin.topup_ok", topupId),
                            Keyboards.adminMenu(lang));
                } else {
                    editText(chatId, messageId, Messages.t(lang, "common.error"), Keyboards.adminMenu(lang));
                }
            }
            case "topup_no" -> {
                long topupId = Long.parseLong(parts[2]);
                if (store.rejectTopup(topupId)) {
                    editText(chatId, messageId, Messages.t(lang, "admin.topup_no", topupId),
                            Keyboards.adminMenu(lang));
                } else {
                    editText(chatId, messageId, Messages.t(lang, "common.error"), Keyboards.adminMenu(lang));
                }
            }
            case "ann" -> {
                pendingInput.put(user.id(), "announce");
                editText(chatId, messageId, Messages.t(lang, "admin.ann_prompt"), Keyboards.backMain(lang));
            }
            case "stats" -> {
                int users = db.countUsers();
                double revenue = db.totalRevenue();
                int pendingTopups = db.listPendingTopups(1000).size();
                int awaiting = db.listOrdersByStatus(Order.AWAITING_CONFIRM, 1000).size();
                int paid = db.listOrdersByStatus(Order.PAID, 1000).size();
                editText(chatId, messageId,
                        Messages.t(lang, "admin.stats_text", users, FormatUtil.fmtUsd(revenue),
                                pendingTopups, awaiting, paid),
                        Keyboards.adminMenu(lang));
            }
            case "broadcast" -> {
                pendingInput.put(user.id(), "broadcast");
                editText(chatId, messageId, Messages.t(lang, "admin.broadcast_prompt"),
                        Keyboards.backMain(lang));
            }
            default -> editText(chatId, messageId, Messages.t(lang, "common.unknown"), Keyboards.adminMenu(lang));
        }
    }

    // ---------------- 发送工具 ----------------

    private org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton btn(
            String text, String callbackData) {
        var b = new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton();
        b.setText(text);
        b.setCallbackData(callbackData);
        return b;
    }

    public void sendText(long chatId, String text,
                         org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup markup)
            throws TelegramApiException {
        SendMessage msg = new SendMessage();
        msg.setChatId(String.valueOf(chatId));
        msg.setText(text);
        msg.setParseMode("Markdown");
        if (markup != null) {
            msg.setReplyMarkup(markup);
        }
        execute(msg);
    }

    private void editText(long chatId, int messageId, String text,
                          org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup markup)
            throws TelegramApiException {
        EditMessageText edit = new EditMessageText();
        edit.setChatId(String.valueOf(chatId));
        edit.setMessageId(messageId);
        edit.setText(text);
        edit.setParseMode("Markdown");
        if (markup != null) {
            edit.setReplyMarkup(markup);
        }
        execute(edit);
    }

    private void answerCallback(String callbackQueryId, String text) throws TelegramApiException {
        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackQueryId);
        if (text != null) {
            answer.setText(text);
        }
        execute(answer);
    }
}
