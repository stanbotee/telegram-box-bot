package com.telegrambox.bot;

import com.telegrambox.bot.config.AppConfig;
import com.telegrambox.bot.db.StoreDb;
import com.telegrambox.bot.handler.BotHandler;
import com.telegrambox.bot.model.User;
import com.telegrambox.bot.service.LevelService;
import com.telegrambox.bot.service.StoreService;
import org.eclipse.jetty.server.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Webhook 端点测试：启动真实 Jetty 服务器（随机端口），用假 token 的 BotHandler，
 * 不走 Telegram 网络，只验证路由与健康检查、Update 解析、API Key 鉴权。
 */
class WebhookTest {

    private Server server;
    private StoreDb db;
    private int port;
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() throws Exception {
        db = new StoreDb("jdbc:sqlite::memory:");
        AppConfig config = AppConfig.testConfig(0, "testbot");
        BotHandler bot = new BotHandler(config, db, new StoreService(db, config), new LevelService(config));
        server = BotApp.buildServer(config, db, bot);
        server.start();
        port = server.getURI().getPort();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) {
            server.stop();
        }
        db.close();
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://[::1]:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void healthEndpoints() throws Exception {
        HttpResponse<String> root = get("/");
        assertEquals(200, root.statusCode());
        assertEquals("ok", root.body());

        HttpResponse<String> health = get("/healthz");
        assertEquals(200, health.statusCode());
        assertTrue(health.body().contains("\"status\":\"ok\""));
        assertTrue(health.body().contains("\"mode\":\"webhook\""));
    }

    @Test
    void webhookAcceptsStartUpdate() throws Exception {
        String updateJson = "{"
                + "\"update_id\":1,"
                + "\"message\":{"
                + "\"message_id\":1,"
                + "\"from\":{\"id\":777001,\"is_bot\":false,\"first_name\":\"Test\",\"username\":\"tester\"},"
                + "\"chat\":{\"id\":777001,\"type\":\"private\"},"
                + "\"date\":1730000000,"
                + "\"text\":\"/start\""
                + "}}";
        HttpResponse<String> resp = http.send(HttpRequest.newBuilder(
                        URI.create("http://[::1]:" + port + "/telegram-webhook"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(updateJson)).build(),
                HttpResponse.BodyHandlers.ofString());
        // 200 表示 Update 解析成功并进入处理；发消息用假 token 失败会被处理器内部捕获
        assertEquals(200, resp.statusCode());
        // /start 已在库里建好用户
        User user = db.getUser(777001);
        assertNotNull(user);
        assertEquals("tester", user.username());
    }

    @Test
    void webhookRejectsBadJson() throws Exception {
        HttpResponse<String> resp = http.send(HttpRequest.newBuilder(
                        URI.create("http://[::1]:" + port + "/telegram-webhook"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{not json")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(400, resp.statusCode());
    }

    @Test
    void apiKeyEndpoint() throws Exception {
        User user = db.upsertUser(777002, "apitest", "Api");
        HttpResponse<String> ok = get("/api/v1/me?key=" + user.apiKey());
        assertEquals(200, ok.statusCode());
        assertTrue(ok.body().contains("\"id\":777002"));
        assertTrue(ok.body().contains("\"level\":\"Bronze\""));

        HttpResponse<String> bad = get("/api/v1/me?key=tb_wrongkey");
        assertEquals(401, bad.statusCode());

        HttpResponse<String> missing = get("/api/v1/me");
        assertEquals(401, missing.statusCode());
    }
}
