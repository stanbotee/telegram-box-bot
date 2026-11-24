package com.telegrambox.bot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telegrambox.bot.config.AppConfig;
import com.telegrambox.bot.db.StoreDb;
import com.telegrambox.bot.handler.BotHandler;
import com.telegrambox.bot.model.User;
import com.telegrambox.bot.service.LevelService;
import com.telegrambox.bot.service.StoreService;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.GetMe;
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 启动入口。webhook 模式（默认，Render 部署）：内嵌 Jetty 监听 $PORT，
 * GET / 与 /healthz 做健康检查，POST $WEBHOOK_PATH 接 Telegram 更新，
 * GET /api/v1/me?key=... 供 API Key 调用。
 * polling 模式（仅本地调试）：BOT_MODE=polling。
 */
public class BotApp {

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.load();
        if (config.botToken.isBlank()) {
            System.err.println("BOT_TOKEN 未设置，请先在环境变量里配置（见 .env.example）。");
            System.exit(1);
        }

        File dbFile = new File(config.dbPath);
        File parent = dbFile.getAbsoluteFile().getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        StoreDb db = new StoreDb("jdbc:sqlite:" + config.dbPath);
        LevelService levels = new LevelService(config);
        StoreService store = new StoreService(db, config);
        BotHandler bot = new BotHandler(config, db, store, levels);

        // BOT_USERNAME 未填时用 getMe() 自动补齐（邀请链接需要它）
        if (config.botUsername.isBlank()) {
            try {
                config.botUsername = bot.execute(new GetMe()).getUserName();
            } catch (Exception e) {
                System.err.println("getMe 失败，BOT_USERNAME 留空，邀请链接将不完整：" + e.getMessage());
            }
        }

        if ("polling".equalsIgnoreCase(config.botMode)) {
            System.out.println("polling 模式启动...");
            TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
            api.registerBot(bot);
            System.out.println("Bot 已启动（polling）。");
            return;
        }

        // webhook 模式
        if (config.webhookUrl.isBlank()) {
            System.err.println("webhook 模式需要 WEBHOOK_URL（如 https://xxx.onrender.com）。");
            System.exit(1);
        }
        Server server = buildServer(config, db, bot);
        server.start();
        // 注册 webhook：失败只告警并后台重试，不退出进程。
        // 否则 Telegram 短暂不可达（或 token 未生效）会导致服务反复崩溃重启。
        Thread webhookThread = new Thread(() -> registerWebhookWithRetry(bot, config), "webhook-register");
        webhookThread.setDaemon(true);
        webhookThread.start();
        System.out.println("Bot 已启动（webhook），监听端口 " + config.port
                + "，webhook=" + config.webhookUrl + config.webhookPath);
        server.join();
    }

    /** 注册 webhook，失败重试 5 次（退避 10s/20s/30s/40s），最终失败也不退出进程。 */
    private static void registerWebhookWithRetry(BotHandler bot, AppConfig config) {
        String url = config.webhookUrl + config.webhookPath;
        for (int attempt = 1; attempt <= 5; attempt++) {
            try {
                SetWebhook setWebhook = new SetWebhook();
                setWebhook.setUrl(url);
                bot.execute(setWebhook);
                System.out.println("Telegram webhook 已注册：" + url);
                return;
            } catch (Exception e) {
                System.err.println("设置 Telegram webhook 失败（第 " + attempt + " 次，" + url + ")：" + e.getMessage());
                if (attempt < 5) {
                    try {
                        Thread.sleep(10_000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
        System.err.println("Telegram webhook 注册多次失败，服务继续运行；收到 Telegram 更新前请检查 BOT_TOKEN 与 WEBHOOK_URL 是否正确。");
    }

    static Server buildServer(AppConfig config, StoreDb db, BotHandler bot) {
        Server server = new Server(config.port);
        ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        ctx.setContextPath("/");
        server.setHandler(ctx);

        ObjectMapper mapper = new ObjectMapper();

        ctx.addServlet(new ServletHolder(new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                resp.setStatus(200);
                resp.getWriter().print("ok");
            }
        }), "/");

        ctx.addServlet(new ServletHolder(new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                resp.setContentType("application/json");
                resp.setStatus(200);
                resp.getWriter().print("{\"status\":\"ok\",\"mode\":\"webhook\"}");
            }
        }), "/healthz");

        ctx.addServlet(new ServletHolder(new HttpServlet() {
            @Override
            protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                String body = new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                try {
                    Update update = mapper.readValue(body, Update.class);
                    bot.onUpdateReceived(update);
                    resp.setStatus(200);
                    resp.getWriter().print("ok");
                } catch (Exception e) {
                    e.printStackTrace();
                    resp.setStatus(400);
                    resp.getWriter().print("bad update");
                }
            }
        }), config.webhookPath);

        ctx.addServlet(new ServletHolder(new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                resp.setContentType("application/json");
                String key = req.getParameter("key");
                try {
                    User user = key == null ? null : db.getUserByApiKey(key);
                    if (user == null) {
                        resp.setStatus(401);
                        resp.getWriter().print("{\"error\":\"invalid api key\"}");
                        return;
                    }
                    LevelService levels = new LevelService(config);
                    resp.setStatus(200);
                    resp.getWriter().print("{"
                            + "\"id\":" + user.id() + ","
                            + "\"username\":" + jsonStr(user.username()) + ","
                            + "\"balance\":" + user.balance() + ","
                            + "\"totalSpent\":" + user.totalSpent() + ","
                            + "\"level\":\"" + levels.levelFor(user.totalSpent()).name() + "\""
                            + "}");
                } catch (Exception e) {
                    resp.setStatus(500);
                    resp.getWriter().print("{\"error\":\"internal\"}");
                }
            }
        }), "/api/v1/me");

        return server;
    }

    private static String jsonStr(String s) {
        if (s == null) {
            return "null";
        }
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
