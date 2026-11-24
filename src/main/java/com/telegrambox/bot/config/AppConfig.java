package com.telegrambox.bot.config;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 环境变量配置。所有敏感值（token / 收款地址 / 管理员 ID）只从环境变量读，
 * 代码里不写任何真实凭证。
 */
public final class AppConfig {

    public final String botToken;
    /** 管理员 Telegram 数字 ID 列表 */
    public final List<Long> adminIds;
    /** webhook（线上）或 polling（仅本地调试） */
    public final String botMode;
    /** Render 上填 https://<服务名>.onrender.com */
    public final String webhookUrl;
    public final String webhookPath;
    /** Render 会注入 $PORT */
    public final int port;
    /** USDT-TRC20 收款地址（占位符，等用户确认后填） */
    public final String usdtAddress;
    /** 客服 Telegram 用户名（不带 @，占位符） */
    public final String supportUsername;
    /** bot 用户名（不带 @），用于生成邀请链接；启动时用 getMe() 自动补齐 */
    public String botUsername;
    /** 钱包币种（占位，默认 USDT） */
    public final String walletCurrency;
    /** 邀请返佣比例（占位）：推荐人从被推荐人每笔已发货订单实付中拿 5% */
    public final double referralRate;
    /**
     * 等级门槛（累计消费，单位 USD）：Bronze 0 固定；
     * Silver 由截图推断为 $100（距 Silver 还差 $88.498 时累计消费 $11.502）；
     * Gold / Platinum 阈值先占位，等用户确认
     */
    public final double levelSilverMin;
    public final double levelGoldMin;
    public final double levelPlatinumMin;
    public final String dbPath;
    public final String defaultLang;

    private AppConfig(String botToken, List<Long> adminIds, String botMode,
                      String webhookUrl, String webhookPath, int port,
                      String usdtAddress, String supportUsername, String botUsername,
                      String walletCurrency, double referralRate,
                      double levelSilverMin, double levelGoldMin, double levelPlatinumMin,
                      String dbPath, String defaultLang) {
        this.botToken = botToken;
        this.adminIds = adminIds;
        this.botMode = botMode;
        this.webhookUrl = webhookUrl;
        this.webhookPath = webhookPath;
        this.port = port;
        this.usdtAddress = usdtAddress;
        this.supportUsername = supportUsername;
        this.botUsername = botUsername;
        this.walletCurrency = walletCurrency;
        this.referralRate = referralRate;
        this.levelSilverMin = levelSilverMin;
        this.levelGoldMin = levelGoldMin;
        this.levelPlatinumMin = levelPlatinumMin;
        this.dbPath = dbPath;
        this.defaultLang = defaultLang;
    }

    public static AppConfig load() {
        return new AppConfig(
                env("BOT_TOKEN", ""),
                parseAdminIds(env("ADMIN_IDS", "")),
                env("BOT_MODE", "webhook"),
                // Render 上可不填 WEBHOOK_URL，自动用平台注入的 RENDER_EXTERNAL_URL
                firstNonBlank(env("WEBHOOK_URL", ""), env("RENDER_EXTERNAL_URL", "")).replaceAll("/$", ""),
                env("WEBHOOK_PATH", "/telegram-webhook"),
                parseInt(env("PORT", "3000"), 3000),
                env("USDT_TRC20_ADDRESS", ""),
                env("SUPPORT_USERNAME", ""),
                env("BOT_USERNAME", ""),
                env("WALLET_CURRENCY", "USDT"),
                parseDouble(env("REFERRAL_RATE", "0.05"), 0.05),
                parseDouble(env("LEVEL_SILVER_MIN", "100"), 100),
                parseDouble(env("LEVEL_GOLD_MIN", "500"), 500),
                parseDouble(env("LEVEL_PLATINUM_MIN", "1000"), 1000),
                env("DB_PATH", "./data/store.db"),
                env("DEFAULT_LANG", "zh"));
    }

    /** 测试专用：默认配置，只改端口与 bot 用户名。 */
    public static AppConfig testConfig(int port, String botUsername) {
        AppConfig c = load();
        AppConfig out = new AppConfig(c.botToken, c.adminIds, c.botMode, c.webhookUrl,
                c.webhookPath, port, c.usdtAddress, c.supportUsername, botUsername,
                c.walletCurrency, c.referralRate, c.levelSilverMin, c.levelGoldMin,
                c.levelPlatinumMin, c.dbPath, c.defaultLang);
        return out;
    }

    public boolean isAdmin(long userId) {
        return adminIds.contains(userId);
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    private static String env(String name, String def) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? def : v.trim();
    }

    private static List<Long> parseAdminIds(String raw) {
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    try {
                        return Long.parseLong(s);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(n -> n != null)
                .collect(Collectors.toList());
    }

    private static int parseInt(String raw, int def) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static double parseDouble(String raw, double def) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
