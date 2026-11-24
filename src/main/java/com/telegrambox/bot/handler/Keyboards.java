package com.telegrambox.bot.handler;

import com.telegrambox.bot.model.Order;
import com.telegrambox.bot.model.Product;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

/** 内联键盘工厂。回调数据格式与 Node 版一致（回调数据限 64 字节）。 */
public final class Keyboards {

    private Keyboards() {
    }

    private static InlineKeyboardButton btn(String text, String callbackData) {
        InlineKeyboardButton b = new InlineKeyboardButton();
        b.setText(text);
        b.setCallbackData(callbackData);
        return b;
    }

    private static InlineKeyboardMarkup markup(List<List<InlineKeyboardButton>> rows) {
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(rows);
        return m;
    }

    /** 主菜单：截图里的 9 个按钮布局（商店独占一行，API / 设置独占一行）。 */
    public static InlineKeyboardMarkup mainMenu(String lang, boolean isAdmin) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(btn(Messages.t(lang, "menu.shop"), "menu:shop")));
        rows.add(List.of(
                btn(Messages.t(lang, "menu.orders"), "menu:orders"),
                btn(Messages.t(lang, "menu.wallet"), "menu:wallet")));
        rows.add(List.of(
                btn(Messages.t(lang, "menu.announcements"), "menu:announcements"),
                btn(Messages.t(lang, "menu.invite"), "menu:invite")));
        rows.add(List.of(
                btn(Messages.t(lang, "menu.favorites"), "menu:favorites"),
                btn(Messages.t(lang, "menu.help"), "menu:help")));
        rows.add(List.of(btn(Messages.t(lang, "menu.api"), "menu:api")));
        rows.add(List.of(btn(Messages.t(lang, "menu.settings"), "menu:settings")));
        if (isAdmin) {
            rows.add(List.of(btn(Messages.t(lang, "menu.admin"), "menu:admin")));
        }
        return markup(rows);
    }

    public static InlineKeyboardMarkup backMain(String lang) {
        return markup(List.of(
                List.of(btn(Messages.t(lang, "menu.back"), "menu:main"))));
    }

    public static InlineKeyboardMarkup categories(String lang, List<String> categories) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (String c : categories) {
            rows.add(List.of(btn("📂 " + c, "shop:cat:" + c)));
        }
        rows.add(List.of(btn(Messages.t(lang, "menu.back"), "menu:main")));
        return markup(rows);
    }

    public static InlineKeyboardMarkup products(String lang, String category, List<Product> products) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Product p : products) {
            rows.add(List.of(btn(p.name() + " · $" + p.price() + " · " + p.duration(),
                    "shop:prod:" + p.id())));
        }
        rows.add(List.of(btn(Messages.t(lang, "menu.back_shop"), "menu:shop")));
        return markup(rows);
    }

    public static InlineKeyboardMarkup productDetail(String lang, Product p, int stock, boolean isFav) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        if (stock > 0) {
            rows.add(List.of(btn(Messages.t(lang, "shop.buy"), "shop:buy:" + p.id())));
        }
        rows.add(List.of(btn(isFav ? Messages.t(lang, "shop.fav_remove") : Messages.t(lang, "shop.fav_add"),
                "shop:fav:" + p.id())));
        rows.add(List.of(btn(Messages.t(lang, "menu.back_shop"), "shop:cat:" + p.category())));
        return markup(rows);
    }

    /** 下单后的付款方式选择。 */
    public static InlineKeyboardMarkup payOptions(String lang, Order order, double balance) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(btn(Messages.t(lang, "order.pay_wallet",
                "$" + com.telegrambox.bot.service.FormatUtil.fmtMoney(balance)),
                "shop:pay:" + order.id() + ":wallet")));
        rows.add(List.of(btn(Messages.t(lang, "order.pay_usdt"),
                "shop:pay:" + order.id() + ":usdt")));
        rows.add(List.of(btn(Messages.t(lang, "order.cancel"), "order:cancel:" + order.id())));
        return markup(rows);
    }

    public static InlineKeyboardMarkup orderDetail(String lang, Order order) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        if (Order.PENDING_PAYMENT.equals(order.status())) {
            rows.add(List.of(btn(Messages.t(lang, "order.cancel"), "order:cancel:" + order.id())));
        }
        rows.add(List.of(btn(Messages.t(lang, "menu.back_orders"), "menu:orders")));
        return markup(rows);
    }

    public static InlineKeyboardMarkup ordersList(String lang, List<Order> orders) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Order o : orders) {
            String label = "#" + o.id() + " " + (o.productName() != null ? o.productName() : "")
                    + " · $" + com.telegrambox.bot.service.FormatUtil.fmtMoney(o.total())
                    + " · " + Messages.t(lang, "order.status." + o.status());
            rows.add(List.of(btn(label, "order:detail:" + o.id())));
        }
        rows.add(List.of(btn(Messages.t(lang, "menu.back"), "menu:main")));
        return markup(rows);
    }

    public static InlineKeyboardMarkup wallet(String lang) {
        return markup(List.of(
                List.of(
                        btn(Messages.t(lang, "wallet.topup"), "wallet:topup"),
                        btn(Messages.t(lang, "wallet.tx"), "wallet:tx")),
                List.of(btn(Messages.t(lang, "menu.back"), "menu:main"))));
    }

    public static InlineKeyboardMarkup apiKey(String lang) {
        return markup(List.of(
                List.of(btn(Messages.t(lang, "api.reset"), "api:reset")),
                List.of(btn(Messages.t(lang, "menu.back"), "menu:main"))));
    }

    public static InlineKeyboardMarkup settings(String lang, boolean notifications) {
        String langLabel = "en".equals(lang) ? "English" : "中文";
        String notifLabel = notifications
                ? Messages.t(lang, "settings.on") : Messages.t(lang, "settings.off");
        return markup(List.of(
                List.of(btn(Messages.t(lang, "settings.lang", langLabel),
                        "settings:lang:" + ("en".equals(lang) ? "zh" : "en"))),
                List.of(btn(Messages.t(lang, "settings.notif", notifLabel),
                        "settings:notif:" + (notifications ? "off" : "on"))),
                List.of(btn(Messages.t(lang, "menu.back"), "menu:main"))));
    }

    public static InlineKeyboardMarkup favorites(String lang, List<Product> products) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Product p : products) {
            rows.add(List.of(btn(p.name() + " · $" + p.price(), "shop:prod:" + p.id())));
        }
        rows.add(List.of(btn(Messages.t(lang, "menu.back"), "menu:main")));
        return markup(rows);
    }

    // ---------------- 管理端 ----------------

    public static InlineKeyboardMarkup adminMenu(String lang) {
        return markup(List.of(
                List.of(
                        btn(Messages.t(lang, "admin.products"), "admin:products"),
                        btn(Messages.t(lang, "admin.orders"), "admin:orders")),
                List.of(
                        btn(Messages.t(lang, "admin.topups"), "admin:topups"),
                        btn(Messages.t(lang, "admin.ann"), "admin:ann")),
                List.of(
                        btn(Messages.t(lang, "admin.stats"), "admin:stats"),
                        btn(Messages.t(lang, "admin.broadcast"), "admin:broadcast")),
                List.of(btn(Messages.t(lang, "menu.back"), "menu:main"))));
    }

    public static InlineKeyboardMarkup adminProducts(String lang, List<Product> products) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Product p : products) {
            rows.add(List.of(btn((p.active() ? "✅ " : "🚫 ") + p.name() + " · $" + p.price(),
                    "admin:product:" + p.id())));
        }
        rows.add(List.of(btn(Messages.t(lang, "menu.back"), "menu:admin")));
        return markup(rows);
    }

    public static InlineKeyboardMarkup adminOrders(String lang, List<Order> orders) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Order o : orders) {
            String label = "#" + o.id() + " " + (o.productName() != null ? o.productName() : "")
                    + " · $" + com.telegrambox.bot.service.FormatUtil.fmtMoney(o.total())
                    + " · " + Messages.t(lang, "order.status." + o.status());
            rows.add(List.of(btn(label, "admin:order:" + o.id())));
        }
        rows.add(List.of(btn(Messages.t(lang, "menu.back"), "menu:admin")));
        return markup(rows);
    }

    /** 管理员订单详情：按状态给确认收款 / 发货 / 拒绝按钮。 */
    public static InlineKeyboardMarkup adminOrderDetail(String lang, Order order) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        if (Order.AWAITING_CONFIRM.equals(order.status())) {
            rows.add(List.of(
                    btn("✅ 确认收款", "admin:confirm:" + order.id()),
                    btn("❌ 拒绝", "admin:reject:" + order.id())));
        } else if (Order.PAID.equals(order.status())) {
            rows.add(List.of(
                    btn("🚚 发货", "admin:ship:" + order.id()),
                    btn("❌ 拒绝并退款", "admin:reject:" + order.id())));
        }
        rows.add(List.of(btn(Messages.t(lang, "menu.back"), "admin:orders")));
        return markup(rows);
    }

    public static InlineKeyboardMarkup adminTopups(String lang, List<com.telegrambox.bot.model.Topup> topups) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (com.telegrambox.bot.model.Topup t : topups) {
            rows.add(List.of(btn("#" + t.id() + " $" + t.amount() + " · 用户 " + t.userId(),
                    "admin:topup:" + t.id())));
        }
        rows.add(List.of(btn(Messages.t(lang, "menu.back"), "menu:admin")));
        return markup(rows);
    }

    public static InlineKeyboardMarkup adminTopupDetail(String lang, long topupId) {
        return markup(List.of(
                List.of(
                        btn("✅ 确认到账", "admin:topup_ok:" + topupId),
                        btn("❌ 拒绝", "admin:topup_no:" + topupId)),
                List.of(btn(Messages.t(lang, "menu.back"), "admin:topups"))));
    }
}
