# Telegram 商店 Bot（Java 版）

1:1 还原 [Jk Digital](https://t.me/jk_digital_store_bot) 的 Telegram 数字商品商店 bot。
Java 17 + Maven + telegrambots 6.9.7.1 + SQLite + 内嵌 Jetty（webhook 模式），
部署目标为 **Render 免费档 Docker**（Bonto 只能跑 Node.js，跑不了 Java）。

> 之前 Node.js 版已作废（设计保留、代码重写），git 历史里仍可查到。

## 功能清单（全部已实现并测试通过）

- **/start 资料卡**：头像名、等级、用户 ID、用户名、余额（最多 4 位小数，去尾零）、
  累计消费、距下一等级还差多少、四种订单数（待处理 / 处理中 / 已确认 / 已发货）、
  邀请链接 `https://t.me/<bot>?start=ref_<用户ID>`
- **等级**：Bronze 0、Silver 100、Gold 500、Platinum 1000（累计消费 USD）。
  Silver 100 由截图推断（累计消费 $11.502 时距 Silver 还差 $88.498）
- **9 个主菜单按钮**（与截图一致的布局）：🛍 商店 / 📦 我的订单 / 💳 钱包 /
  📣 最新动态 / 🤝 邀请赚钱 / ❤️ 收藏 / 🆘 帮助 / 🔌 API 权限 / ⚙️ 设置
  （管理员额外看到 🛠 管理后台）
- **商店**：分类浏览（AI 订阅 / VPN / 流媒体 / 学习）、商品详情（价格、时长、实时库存）、收藏切换
- **下单与付款**：下单后选钱包余额支付（立即确认）或 USDT 转账（发 TxID，管理员确认到账）；
  订单四态：待处理 → 处理中 → 已确认 → 已发货（另有已拒绝 / 已取消）；
  待处理订单可由本人取消；确认收款与发货是分开的两步
- **钱包**：余额查询、USDT-TRC20 人工充值（发金额 → 发 TxID → 管理员审核入账）、交易记录
- **邀请返佣**：推荐人拿被推荐人每笔已发货订单实付的 **5%**，直接进推荐人钱包余额；
  返佣记录可在邀请页查看累计人数与累计返佣
- **收藏**：商品详情页收藏 / 取消，收藏列表可直接进商品页
- **公告**：管理员发布（标题 + 正文），用户在最新动态查看
- **API Key**：查看 / 重置；HTTP 接口 `GET /api/v1/me?key=...` 返回用户信息
  （id、username、balance、totalSpent、level），Key 错误返回 401
- **设置**：语言切换（中文 / English）、订单通知开关
- **管理端**（仅 ADMIN_IDS）：商品管理（上下架、添加库存，每行一条账号密码 / 激活码 / 卡密）、
  订单管理（确认收款 / 发货 / 拒绝；钱包支付订单被拒绝时自动退回余额）、
  充值审核（确认到账 / 拒绝）、公告发布、统计（用户数、已发货收入、待审核充值、待确认 / 待发货订单）、广播
- **防重复操作**：钱包支付、充值确认、确认收款、发货、拒绝全部用条件更新防重，
  二次点击不会重复扣款 / 入账 / 发货；每个回调查询只回答一次
- **Telegram Stars**：只留接口，未接通

## 占位商品（价格等真实供货价替换）

| 分类 | 商品 | 时长 | 占位价（USD） |
| --- | --- | --- | --- |
| AI 订阅 | ChatGPT Plus | 30 天 | 20.00 |
| AI 订阅 | Claude Pro | 30 天 | 18.00 |
| AI 订阅 | Gemini Advanced | 30 天 | 19.99 |
| AI 订阅 | Muse | 30 天 | 10.00 |
| VPN | Surfshark | 30 天 | 9.99 |
| VPN | NordVPN | 30 天 | 12.00 |
| 流媒体 | YouTube Premium | 30 天 | 8.00 |
| 流媒体 | Netflix Premium | 30 天 | 12.00 |
| 学习 | Duolingo Super | 30 天 | 6.00 |

库存初始为 0，需管理员在管理端添加后才能发货；无库存时订单保持已确认，不发空货。

## 环境变量

| 变量 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `BOT_TOKEN` | 是 | — | BotFather 的 bot token |
| `ADMIN_IDS` | 否 | — | 管理员 Telegram 数字 ID，多个用逗号分隔 |
| `BOT_MODE` | 否 | `webhook` | `webhook`（线上）或 `polling`（仅本地调试） |
| `WEBHOOK_URL` | webhook 模式必填 | — | 如 `https://xxx.onrender.com`；Render 上可不填，自动用 `RENDER_EXTERNAL_URL` |
| `WEBHOOK_PATH` | 否 | `/telegram-webhook` | Telegram 回调路径 |
| `PORT` | 否 | `3000` | 监听端口（Render 会注入） |
| `USDT_TRC20_ADDRESS` | 否 | — | 收款 USDT-TRC20 地址（占位，等用户确认） |
| `SUPPORT_USERNAME` | 否 | — | 客服 Telegram 用户名（不带 @） |
| `BOT_USERNAME` | 否 | — | bot 用户名（不带 @）；不填则启动时用 getMe() 自动补齐 |
| `WALLET_CURRENCY` | 否 | `USDT` | 钱包币种 |
| `REFERRAL_RATE` | 否 | `0.05` | 返佣比例（占位 5%） |
| `LEVEL_SILVER_MIN` / `LEVEL_GOLD_MIN` / `LEVEL_PLATINUM_MIN` | 否 | `100` / `500` / `1000` | 等级门槛（USD） |
| `DB_PATH` | 否 | `./data/store.db` | SQLite 文件路径 |
| `DEFAULT_LANG` | 否 | `zh` | 默认语言 |

本地配置示例见 `.env.example`（只放占位符，不放真实凭证）。

## 本地运行

```bash
# 需要 JDK 17 + Maven 3.9+
mvn test          # 跑全部测试（不需要真实 token）
mvn package       # 打 fat jar：target/telegram-box-bot-1.0.0-jar-with-dependencies.jar

# 本地调试（polling 模式，不用配 webhook）
BOT_TOKEN=<你的token> BOT_MODE=polling ADMIN_IDS=<你的Telegram数字ID> \
  java -jar target/telegram-box-bot-1.0.0-jar-with-dependencies.jar

# webhook 模式本地跑（需要公网地址才能被 Telegram 回调，仅作演示）
BOT_TOKEN=<你的token> WEBHOOK_URL=https://<你的域名> PORT=3000 \
  java -jar target/telegram-box-bot-1.0.0-jar-with-dependencies.jar
```

健康检查：`GET /` 返回 `ok`；`GET /healthz` 返回 `{"status":"ok","mode":"webhook"}`。

## 测试

```bash
mvn test
```

28 个测试，全部通过（2026-11-24）：

| 测试类 | 数量 | 覆盖内容 |
| --- | --- | --- |
| `LevelServiceTest` | 4 | 等级推断、截图用例（11.502 → Bronze，距 Silver 差 88.498）、阈值边界、最高级 |
| `FormatUtilTest` | 6 | 金额格式化（4 位小数去尾零）、返佣 5% 计算与 2 位小数取整 |
| `StoreServiceTest` | 14 | 订单状态机、钱包支付（余额不足 / 成功 / 防重复扣款）、充值确认与防重复入账、USDT 订单确认→发货、库存分配、无库存不发货、返佣只发一次、拒绝钱包订单自动退款、取消订单（仅本人）、收藏、API Key 重置 |
| `WebhookTest` | 4 | 真实 Jetty 服务器：`GET /`、`GET /healthz`、POST webhook 接收 /start 更新、API Key 接口（正确 200 / 错误 401） |

另做过 fat jar 冒烟启动：`/`、`/healthz`、webhook POST 均返回 ok。

## 部署（Render 免费档 Docker）

已核实 Render 免费档支持 Dockerfile 部署：Runtime Docker、Free 实例、512 MB、
750 实例小时/月、空闲约 15 分钟后休眠、冷启动约 30–60 秒、无需信用卡。
Java 应用在 Render 上必须走 Docker（官方文档明确要求）。

步骤：

1. 把本仓库推到 GitHub（`stanbotee/telegram-box-bot`，push 由用户方处理）
2. Render 控制台 → New → Blueprint → 选仓库（仓库里已有 `render.yaml`）
3. 在 Render 控制台填环境变量：`BOT_TOKEN`、`ADMIN_IDS`、`USDT_TRC20_ADDRESS`、
   `SUPPORT_USERNAME`、`BOT_USERNAME`（`WEBHOOK_URL` 不用填，自动用 Render 域名）
4. 部署完成后在 Telegram 里打开 bot，`/start` 应显示资料卡

**免费档限制（诚实说明）：**

- 空闲约 15 分钟后休眠，下一次请求需 30–60 秒唤醒；只适合测试，不适合 7×24 生产
- **无持久磁盘**：SQLite 文件在重启 / 重新部署后会被清空，用户、订单、余额都会丢失；
  正式用需要接托管数据库（Render PostgreSQL 免费档或外部库）再把数据层换掉
- 750 实例小时/月，一个服务常驻约够用一个月

## 仍是占位、需要用户最后确认的清单

- 商品真实价格与时长档位（当前 9 个商品全是占位价）
- 收款 USDT-TRC20 地址（`USDT_TRC20_ADDRESS`）
- 客服账号（`SUPPORT_USERNAME`）
- BotFather token（`BOT_TOKEN`）
- 返佣比例确认（当前 5%）
- 等级门槛确认：Silver 100 是截图推断，Gold 500 / Platinum 1000 是占位
- API Key 目前明文存在数据库里，正式用前应改成哈希存储
- Telegram Stars 未接通（只留了接口）；分销商 HTTP API 也只做了 Key 生成 / 查看 / 重置，
  真实供货方接口形状未定

## 项目结构

```
src/main/java/com/telegrambox/bot/
  BotApp.java            # 启动入口：Jetty webhook / polling、健康检查、API Key 接口
  config/AppConfig.java  # 环境变量配置
  db/StoreDb.java        # SQLite 数据层（用户/商品/库存/订单/充值/流水/公告/返佣/收藏）
  model/                 # User / Product / StockItem / Order / Topup / WalletTransaction / Announcement / Referral
  service/
    LevelService.java    # 等级推断
    FormatUtil.java      # 金额格式化、返佣计算
    StoreService.java    # 订单状态机、钱包支付、充值审核、发货、返佣
  handler/
    BotHandler.java      # Telegram 消息与回调处理（资料卡、9 菜单、商店、钱包、管理端）
    Keyboards.java       # 内联键盘
    Messages.java        # 中英文案
src/test/java/com/telegrambox/bot/
  service/LevelServiceTest.java / FormatUtilTest.java / StoreServiceTest.java
  WebhookTest.java
Dockerfile               # 多阶段构建：Maven 打包 + JRE 17 slim 运行
render.yaml              # Render Blueprint（免费档 Docker）
.env.example             # 环境变量模板（占位符）
```

## 开发环境注意

- 本项目在沙箱里开发时遇到过 Maven 连不上 Maven Central：
  沙箱防火墙只放行 IPv6，Java 默认走 IPv4 会被拦截。
  解决办法是在 Maven 启动参数里加 `-Djava.net.preferIPv6Addresses=true`
  （沙箱里的 `~/.tools/mvnw` 已配好）；正常网络环境不需要这个参数。
- webhook 测试里用 `[::1]` 而不用 `127.0.0.1`，也是同一原因。
