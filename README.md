# Jk Digital 风格 Telegram 数字商店 Bot（代码骨架 v0.2）

仿照 https://t.me/jk_digital_store_bot（Jk Digital）做的 Telegram 数字商店 bot。
**当前是本地代码骨架，不含任何真实 token / 密码 / 钱包地址**，全部用环境变量占位。
**没有推到 GitHub**（仓库 https://github.com/stanbotee/telegram-box-bot.git 已知，等用户授权后再推）。

## 已按主菜单截图对齐（v0.2）

用户发来了目标 bot 的 `/start` 主菜单截图，已按截图实现：

### 资料卡（`/start` / `menu` 显示，照抄格式）
```
👤 名字
🏅 等级：Bronze
🆔 ID：<用户数字 ID>
👤 用户名：@xxx（无则显示 —）

💰 余额：$8.9983
💸 累计消费：$11.502
📈 距离 Silver 还差 $88.498

📦 订单：处理中：0 · 已发货：0 · 已确认：1 · 待处理：1

🔗 您的邀请链接：
https://t.me/<bot用户名>?start=ref_<用户ID>
```
- 金额最多 4 位小数、去掉末尾多余的 0，所以 `$8.9983`、`$11.502`、`$88.498` 都能原样显示
- 等级按累计消费算，配置在 `src/levels.js` + 环境变量：
  `Bronze 0` · `Silver 100`（由截图推断：累计消费 $11.502 时距 Silver 还差 $88.498）·
  `Gold 500`（占位）· `Platinum 1000`（占位）
- 四种订单状态：待处理（下单未付款）· 处理中（已付款待确认）· 已确认（已确认待发货）· 已发货
- 邀请链接格式 `start=ref_<用户ID>`，新用户点链接进 bot 自动绑定推荐人

### 主菜单按钮（照抄，9 个，布局一致）
```
🛍 商店（全宽）
📦 我的订单 | 💳 钱包
📣 最新动态 | 🤝 邀请赚钱
❤️ 收藏 | 🆘 帮助
🔌 API 权限（全宽）
⚙️ 设置（全宽）
```

## 功能清单

### 用户侧
- `/start` 资料卡 + 主菜单（见上）
- 🛍 商店：分类浏览（🤖 AI 订阅、🛡️ VPN、🎬 流媒体、📚 学习），商品详情（价格、时长、实时库存），❤️ 收藏/取消收藏
- 下单：选商品 → 选付款方式 → 生成订单
- 付款统一走虚拟货币（默认 USDT，占位可配）：
  - 💳 钱包余额支付（余额够直接扣，订单进「已确认」等发货）
  - 💵 USDT-TRC20 人工转账（发 TxID → 管理员确认收款 → 发货）
  - ⭐ Telegram Stars 为预留接口，尚未接通
- 💳 钱包：余额、充值（发金额 → 转 USDT → 发 TxID → 管理员确认到账）、交易记录（充值/消费/返佣/退款）
- 🤝 邀请赚钱：邀请链接、已邀请人数、累计返佣；返佣比例占位 5%，被推荐人订单发货后到账
- 📣 最新动态：公告列表
- ❤️ 收藏：收藏的商品列表
- 🔌 API 权限：分销 API Key 生成 / 查看完整 Key / 重置（只做 Key 管理，HTTP API 接口未实现）
- ⚙️ 设置：语言（中文/英文）、通知开关
- 🆘 帮助：使用流程 + 客服账号

### 管理侧（只有 `ADMIN_IDS` 里的 Telegram 数字 ID 能进）
- `/admin` 管理面板
- 📦 商品管理：添加、改价、上架/下架、删除
- 📥 库存管理：按商品查看库存、批量加库存（一行一条）
- 🧾 待确认订单：查看付款凭证，确认收款（→已确认）/ 拒绝
- 📦 已确认待发货：发货（自动取库存、累计消费、记返佣）
- 💳 充值审核：确认到账（余额+流水）/ 拒绝
- 📣 添加公告 / 📊 统计 / 📣 广播

### 订单状态机
```
待处理 pending_payment → 处理中 awaiting_confirm → 已确认 paid → 已发货 delivered
（钱包支付直接进已确认；任意一步可拒绝 → 已拒绝 rejected）
```

### 数据
SQLite（Node.js 内置 `node:sqlite`），首次启动自动建表，已存在的旧库自动补列：
`users`（用户/余额/累计消费/推荐人/API Key/通知开关）、`products`、`stock_items`、
`orders`、`wallet_transactions`（钱包流水）、`topups`（充值申请）、
`favorites`（收藏）、`announcements`（公告）、`referrals`（返佣记录）。

## 技术栈

Node.js 22+、Telegraf 4、Express 4、Node.js 内置 `node:sqlite`（无需原生编译，
`better-sqlite3` 在无编译环境的沙箱/Bonto 上装不上，已弃用；Node 24 可直接用，
Bonto 上请选 Node 24，若用 Node 22 可能需加 `--experimental-sqlite` 启动参数）。

**模式说明：** 线上用 **webhook**（Express 接收 Telegram 的 POST，`GET /` 返回 `ok` 做健康检查）。
不要用 polling 跑线上——polling 会让进程一直占着，在 Bonto 上会烧光免费时长。
本地调试没有公网地址时才用 polling（`npm run dev`）。

## 快速开始

### 1. 在 BotFather 建 bot 拿 token
1. Telegram 里打开 [@BotFather](https://t.me/BotFather)，发 `/newbot`
2. 按提示起名字和用户名（用户名必须以 `bot` 结尾，例如 `jk_digital_store_bot` 已被占用，起个自己的）
3. BotFather 会返回一串 token，形如 `123456:ABC-DEF...`，复制保存好，**不要发给别人、不要提交到 GitHub**
4. （可选）在 BotFather 里发 `/setcommands` 给 bot 设命令菜单：
   ```
   start - 开始使用
   menu - 主菜单
   admin - 管理面板（仅管理员）
   ```

### 2. 查自己的 Telegram 数字 ID（填 ADMIN_IDS 用）
给 [@userinfobot](https://t.me/userinfobot) 发任意消息，它会返回你的 `id`（一串数字）。

### 3. 本地运行
```bash
cd jk-digital-store-bot
npm install
cp .env.example .env
# 编辑 .env，填 BOT_TOKEN 和 ADMIN_IDS（本地调试可先只填这两项）
npm run smoke     # 冒烟测试：不连 Telegram，验证数据库 + 发货 + 钱包 + 返佣 + 等级
npm run dev       # 本地调试：BOT_MODE=polling 模式启动
```
然后在 Telegram 里给你的 bot 发 `/start` 测试：资料卡、菜单、浏览商店、下单、充值、邀请链接；
再用管理员账号发 `/admin` 测试商品/库存/订单确认/充值审核。

本地跑起来后，如需 webhook 测试，需要一个公网地址（例如用 ngrok 把本地端口暴露出去，
再把 `WEBHOOK_URL` 设成那个地址）。

### 4. 部署到 Bonto（仅测试用）
Bonto 免费档：$0 免卡，每月 75 小时，0.5 CPU / 512MB / 256MB 存储，只能跑 Node.js，
给一个 `*.bonto.page` 域名，15–30 分钟无请求就休眠。

1. 在 [bonto.dev](https://bonto.dev) 注册登录，建一个 Node.js 应用（浏览器编辑器，不用 GitHub）
2. 把本项目的文件传上去（`src/`、`package.json`，不要传 `node_modules/`、`.env`、`data/`）
3. 在应用的 Environment / Settings 里设环境变量：
   - `BOT_TOKEN`（BotFather 的 token）
   - `ADMIN_IDS`（管理员数字 ID，逗号分隔）
   - `BOT_MODE=webhook`
   - `WEBHOOK_URL=https://<你的应用名>.bonto.page`
   - `USDT_TRC20_ADDRESS`（收款地址，等确认后填）
   - `SUPPORT_USERNAME`（客服用户名，不带 @）
   - `PORT` 由 Bonto 自动注入，不用手动设
4. 启动命令：`node src/index.js`（或 `npm start`，会先 `npm install`）
5. 验证：浏览器打开 `https://<你的应用名>.bonto.page/` 应返回 `ok`；
   Telegram 里给 bot 发 `/start`，应出现资料卡和主菜单

**注意：Bonto 只能做测试。** 免费档每月只有 75 小时、且会休眠，不适合 24 小时在线跑商店；
正式跑需要一台长期在线的服务器/VPS，或 Render / Railway 等平台的付费档。

## 环境变量清单

| 变量 | 必填 | 说明 |
|---|---|---|
| `BOT_TOKEN` | ✅ | BotFather 给的 token |
| `ADMIN_IDS` | ✅ | 管理员 Telegram 数字 ID，逗号分隔 |
| `BOT_MODE` | — | `webhook`（默认，线上）或 `polling`（仅本地调试） |
| `WEBHOOK_URL` | webhook 时必填 | 公网地址，如 `https://xxx.bonto.page`，末尾不要带 `/` |
| `WEBHOOK_PATH` | — | 默认 `/telegram-webhook` |
| `PORT` | — | Bonto 自动注入；本地默认 3000 |
| `BOT_USERNAME` | — | bot 用户名，不带 @；留空则启动时自动获取 |
| `USDT_TRC20_ADDRESS` | 占位 | USDT-TRC20 收款地址，等用户确认后填 |
| `WALLET_CURRENCY` | — | 钱包币种，默认 `USDT` |
| `REFERRAL_RATE` | — | 邀请返佣比例，默认 `0.05`（5%，占位） |
| `LEVEL_SILVER_MIN` | — | Silver 门槛，默认 `100`（截图推断） |
| `LEVEL_GOLD_MIN` | — | Gold 门槛，默认 `500`（占位） |
| `LEVEL_PLATINUM_MIN` | — | Platinum 门槛，默认 `1000`（占位） |
| `SUPPORT_USERNAME` | 占位 | 客服用户名，不带 @ |
| `DB_PATH` | — | 默认 `./data/store.db` |
| `DEFAULT_LANG` | — | 默认 `zh` |

## 占位符与待确认项（做到 1:1 之前必须填）

- [ ] **商店内部截图**：商品分类、商品列表、商品详情页、价格表、时长档位——
      等用户发商店截图后再对齐。目前分类和 9 个占位商品（价格 USD）沿用 Jk Digital 公开描述的清单
- [ ] **收款地址**：`USDT_TRC20_ADDRESS` 还没填；确认是否只收 USDT，还是也要 Telegram Stars / 其他币
- [ ] **客服账号**：`SUPPORT_USERNAME` 还没填
- [ ] **返佣比例**：目前占位 5%，需确认真实比例和结算规则
- [ ] **等级门槛**：Silver $100 由截图推断；Gold / Platinum 阈值（500 / 1000）是占位，需确认
- [ ] **API 接口**：目前只有 Key 的生成/查看/重置，真正的分销 HTTP API（查价、下单、查订单）还没写，需确认接口形态
- [ ] **语言**：默认中文+英文；目标 bot 是中文界面还是英文/多语言？
- [ ] **发货内容格式**：账号密码是一行一条？还是卡密？还是发文件/链接？目前按一行一条处理
- [ ] **最新动态内容**：目前只有 1 条占位开业公告

## 文件结构

```
jk-digital-store-bot/
├── package.json          依赖与脚本（start / dev / seed / smoke）
├── .env.example          环境变量模板（复制为 .env 后填）
├── .gitignore            不提交 node_modules / .env / data/
├── README.md             本文件
├── scripts/
│   └── smoke-test.js     冒烟测试：不连 Telegram，验证数据库 + 发货 + 钱包 + 返佣 + 等级
└── src/
    ├── index.js          入口：Express + webhook/polling + 取 bot 用户名 + 信号处理
    ├── config.js         环境变量解析（等级门槛/返佣/钱包币种等）
    ├── levels.js         等级配置与金额格式化（4 位小数去尾零）
    ├── db.js             SQLite（node:sqlite）：建表 + 迁移 + 全部数据操作
    ├── seed.js           种子商品（9 个占位商品）+ 占位公告
    ├── i18n.js           中英文文案
    ├── keyboards.js      全部 inline 键盘（用户主菜单 9 按钮 + 管理）
    ├── payments.js       USDT 说明 + 钱包支付 + Stars 预留 + 自动发货 + 返佣 + API Key 生成
    └── handlers/
        ├── user.js       用户侧：资料卡/菜单/商店/下单/付款/钱包/邀请/收藏/公告/API/设置
        └── admin.js      管理侧：商品/库存/订单确认+发货/充值审核/公告/统计/广播
```

## 安全注意

- `.env` 里有 token，永远不要提交到 GitHub、不要发到聊天记录
- 库存内容（账号密码）只存在 SQLite 里，不要写进代码或聊天记录
- 管理员身份只认 `ADMIN_IDS` 数字 ID，不要用用户名判断
- API Key 明文存在数据库里（占位实现），正式用之前应改成只存哈希
- 发货内容通过 Telegram 私信发给用户，用户屏蔽 bot 时发货会失败，订单仍会标记已发货，
  管理员可在订单里看到库存内容并手动联系
