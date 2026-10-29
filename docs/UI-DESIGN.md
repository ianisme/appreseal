# AppReseal 界面设计说明书（v1）

> 当前实现已经改成团队自用：没有钱包、价格和充值。演示应用、开发者和包名都是虚构的。下面从「计费」开始的段落是最初的设计草案，不要当成现在的产品说明。现在的安装和使用说明以仓库根目录的 README 为准。

> 产品：AppReseal —— iOS IPA 脱壳服务
> 技术栈：Vue 3（前端） / Spring Boot（后端） / MySQL（数据库）
> 能力层：`fairplay-tools/appget.sh`（多 Apple ID 下载 + FairPlay 离线解密）
> 本文档是第一轮交付物：信息架构、视觉规范、页面线框、交互状态、计费规则、数据与接口草案。

---

## 1. 产品定位

**一句话**：把 App Store 的加密 IPA，变成可以直接丢进 IDA / Hopper / class-dump 的明文包。

| 角色 | 需求 | 产品回应 |
|---|---|---|
| 安全研究员 | 快速拿到明文二进制，不想折腾越狱和凭证 | 网页下单，排队、进度、结果全自动 |
| 逆向初学者 | 没有越狱设备 / 不会抓 FairPlay 凭证 | 平台代持 Apple ID 池与凭证，开箱即用 |
| 团队采购 | 统一开票、统一账单 | 余额钱包 + 消费流水 + 充值订单 |
| 平台运营（管理员） | 控价、控量、看异常 | 后台：定价、任务、用户、Apple ID 池、审计 |

**非目标（v1 不做）**：不做在线反编译/源码分析、不做 App 分发、不做团队多成员子账号。

---

## 2. 品牌与视觉规范（亮色 Apple 风）

### 2.1 关键词

克制、留白、大字号标题、单色强调、细腻的层级阴影。接近 apple.com 的产品页 + App Store Connect 的后台质感。

### 2.2 设计令牌

```css
/* 色彩 */
--bg:            #ffffff;   /* 页面底 */
--bg-subtle:     #f5f5f7;   /* 区块底、后台内容区 */
--surface:       #ffffff;   /* 卡片 */
--text:          #1d1d1f;   /* 主文字 */
--text-2:        #6e6e73;   /* 次级文字 */
--text-3:        #86868b;   /* 辅助说明 */
--line:          #d2d2d7;   /* 输入框边框 */
--line-soft:     #e8e8ed;   /* 分割线 */
--blue:          #0071e3;   /* 主行动色 */
--blue-hover:    #0077ed;
--blue-soft:     #e8f2fd;   /* 选中底 */
--green:         #248a3d;   /* 成功 */
--green-soft:    #e7f6ec;
--orange:        #b25e09;   /* 处理中 */
--orange-soft:   #fdf1e3;
--red:           #d70015;   /* 失败/危险 */
--red-soft:      #fdecee;
--purple:        #5e5ce6;   /* 管理员标识 */

/* 圆角 */
--r-sm: 10px;  --r-md: 14px;  --r-lg: 20px;  --r-pill: 980px;

/* 阴影 */
--sh-sm: 0 1px 2px rgba(0,0,0,.04), 0 4px 14px rgba(0,0,0,.04);
--sh-md: 0 8px 30px rgba(0,0,0,.08);
--sh-lg: 0 24px 60px rgba(0,0,0,.14);

/* 字体 */
font-family: -apple-system, BlinkMacSystemFont, "SF Pro Text", "SF Pro SC",
             "PingFang SC", "Helvetica Neue", "Microsoft YaHei", sans-serif;

/* 栅格 */
内容最大宽 1180px；后台侧栏 236px；页面左右留白 24 / 40px。
```

### 2.3 排版

| 用途 | 字号 / 字重 / 行高 |
|---|---|
| Hero 主标题 | 56px / 700 / 1.08，`letter-spacing:-0.02em` |
| 区块标题 | 32px / 700 / 1.2 |
| 卡片标题 | 19px / 600 |
| 正文 | 15px / 400 / 1.6 |
| 表格 | 13.5px / 400 |
| 说明 | 12.5px / 400，`--text-3` |

### 2.4 组件规范

- **按钮**：胶囊（`--r-pill`），高 44/36/28 三档；主按钮蓝底白字，次按钮白底 1px 边框，文字按钮无底。
- **卡片**：白底 + 1px `--line-soft` + `--sh-sm`，hover 抬升到 `--sh-md`（仅可点击卡片）。
- **输入**：高 44，1px `--line`，聚焦时 2px `--blue` 外发光。
- **状态胶囊**：底色用 *-soft，文字用主色，8px 圆点 + 文案。
- **表格**：表头 12px 全大写灰字，行高 52，hover `#fafafa`，无斑马纹。
- **动效**：150–220ms `cubic-bezier(.2,.8,.2,1)`；页面切换淡入 8px 上移。

---

## 3. 信息架构

```
/                        首页（产品介绍 + 推荐应用 + 价格预告）
/market                  应用市场（已脱壳成品）
/market/:bundleId        应用详情（购买/下载）
/pricing                 价格与充值套餐
/docs                    使用文档 / FAQ
/login  /register        登录 / 注册
/app/*                   用户控制台（需登录）
  dashboard              概览：余额、最近任务、用量
  new                    新建脱壳（App Store 应用 / 上传 IPA）
  tasks                  任务列表
  tasks/:id              任务详情（进度、日志、结果、重试）
  library                我的应用（已购/已脱壳，可重复下载）
  wallet                 钱包：余额、充值、流水
  settings               账号设置
/admin/*                 管理后台（需 ADMIN）
  overview               运营概览
  users                  用户管理
  tasks                  脱壳任务管理
  apps                   应用库管理
  orders                 订单与流水
  pricing                定价与充值套餐
  apple-ids              Apple ID 池（凭证/会话状态）
  system                 系统设置（并发、公告、维护）
  logs                   审计日志
```

### 3.1 权限矩阵

| 能力 | 游客 | 用户 | 管理员 |
|---|:--:|:--:|:--:|
| 浏览市场 / 详情 | ✅ | ✅ | ✅ |
| 下载成品（付费） | ❌ 引导登录 | ✅ | ✅ |
| 发起脱壳 | ❌ | ✅ | ✅ |
| 充值 | ❌ | ✅ | ✅ |
| 任务重试/取消 | ❌ | 仅自己 | 全部 |
| 调价 / 上下架 | ❌ | ❌ | ✅ |
| 用户封禁 / 改余额 | ❌ | ❌ | ✅ |
| Apple ID 池管理 | ❌ | ❌ | ✅ |
| 审计日志 | ❌ | ❌ | ✅ |

---

## 4. 核心流程

### 4.1 脱壳（新建任务）

```
选来源 ─┬─ App Store 应用：输入名称 / bundle id / 链接 → 选中候选
        └─ 上传 IPA：拖拽 .ipa → 解析 Info.plist 得到 bundle/版本/大小
   ↓
确认页：显示 目标 App、版本、预计耗时、扣费 ¥X、当前余额 ¥Y
   ↓  余额不足 → 充值弹窗（充值后原路返回，表单不丢）
确认下单 → 创建任务（预扣费） → /app/tasks/:id
   ↓
队列 → 下载(Apple ID 池) → 解密(FairPlay) → 校验(cryptid=0) → 完成
   ↓                                          └→ 失败：自动全额退款 + 重试按钮
结果卡：下载按钮（24h 内首次免费）+ 校验信息（SHA256、大小、cryptid）
```

**关键规则**：下单即预扣；失败自动退款；同一 App 已有成功任务时提示「直接下载，节省 ¥X」。

### 4.2 市场下载

```
市场卡片 → 详情页 → 点击「下载 ¥3.00」
   ↓ 未登录 → 登录后返回原页
   ↓ 余额不足 → 充值
确认扣费（弹窗展示 App、版本、价格、余额变化）
   ↓
扣费成功 → 生成 15 分钟有效的下载链接 → 浏览器开始下载
   ↓ 同时写入「我的应用」，有效期内可无限次重复下载
```

### 4.3 充值

```
钱包 → 选套餐（¥50 / ¥100 送 5 / ¥300 送 20 / ¥500 送 50）
   ↓ 选支付方式（支付宝 / 微信）
   ↓ 二维码弹窗（v1 为模拟支付，2s 后成功）
余额到账 → 流水 +1（type=RECHARGE）→ 提示可继续下单
```

### 4.4 管理

```
管理员登录 → /admin/overview
   ├ 看队列与失败率 → 进任务页重试/取消
   ├ 改价 → 定价页（即时生效，已有任务不受影响）
   ├ 上架新成品 → 应用库（关联已完成任务产物）
   └ Apple ID 池异常 → 查看凭证/会话状态 → 重新登录（跳转运维指引）
```

---

## 5. 页面线框与说明

> 线框仅表达层级与信息密度，具体视觉以 Vue 实现为准。

### 5.1 首页 `/`

```
┌──────────────────────────────────────────────────────────────┐
│ AppReseal     市场   价格   文档            [登录] [免费注册] │  ← 半透明吸顶
├──────────────────────────────────────────────────────────────┤
│                                                              │
│        把 App Store 的加密包                                   │
│        变成可分析的明文                    ┌───────────────┐  │
│                                            │ 终端风演示卡  │  │
│        多 Apple ID 池 · 离线 FairPlay 解密  │ cryptid 1 → 0 │  │
│        下单即排队，完成通知                   └───────────────┘  │
│        [ 开始脱壳 ]  [ 逛逛市场 ]                              │
│                                                              │
├──────────────────────────────────────────────────────────────┤
│  12,480 次脱壳    |    99.2% 成功率    |    3 分钟 平均耗时    │  ← 数据条
├──────────────────────────────────────────────────────────────┤
│  三步完成                                                     │
│  ① 选应用/上传 IPA   ② 排队解密   ③ 下载明文包                 │
├──────────────────────────────────────────────────────────────┤
│  热门成品                                    查看全部 →       │
│  [App 卡] [App 卡] [App 卡] [App 卡]                          │
├──────────────────────────────────────────────────────────────┤
│  价格透明：脱壳 ¥6 起 / 下载 ¥3 起      [查看价格]             │
├──────────────────────────────────────────────────────────────┤
│  FAQ 手风琴                                                   │
│  底部：产品 / 文档 / 条款 / © AppReseal                       │
└──────────────────────────────────────────────────────────────┘
```

要点：
- Hero 右侧用「终端窗口」静态演示 `SC_Info → decrypt → cryptid=0`，强化技术可信度；
- 数据条为真实运营数据（后台可配置），不是装饰；
- 市场卡展示：图标、名称、版本、大小、下载价、脱壳时间。

### 5.2 应用市场 `/market`

```
标题：应用市场        副标题：已完成脱壳与校验的 IPA，即买即下
[搜索框................]  [全部分类 ▾]  [最新 ▾]
分类 chips: 全部 · 社交 · 工具 · 影音 · 效率 · 开发 · 游戏
┌─────────┬─────────┬─────────┬─────────┐
│ 卡片    │ 卡片    │ 卡片    │ 卡片    │
│ 图标 名称│ ...     │         │         │
│ 版本/大小│         │         │         │
│ ¥3.00  ↓ │         │         │         │
└─────────┴─────────┴─────────┴─────────┘
分页 / 加载更多
```

状态：加载骨架卡 ×8；空态「没有找到相关应用，试试其他关键词」；错误态可重试。

### 5.3 应用详情 `/market/:bundleId`

```
← 返回市场
┌───────────────────────────────────────────────┬──────────────┐
│ [大图标] 青笺                                   │ 价格卡       │
│ 青屿工作室 · 社交                               │              │
│ v8.0.54 · 268 MB · iOS 13.0+                   │ 下载成品     │
│ [iOS 15 已校验] [已脱壳] [支持 arm64]            │ ¥3.00        │
│                                               │ [立即下载]    │
│ 简介文字…                                       │ 余额 ¥42.00  │
│                                               │ ───────────  │
│ 截图横向滚动（渐变占位）                          │ 或重新脱壳   │
│                                               │ ¥6.00        │
│ 技术信息表                                      │ [发起脱壳]   │
│ bundle id / 版本 / 可执行文件 / 大小 / SHA256    │              │
│ 脱壳时间 / 校验状态 / 下载次数                    │ 最近更新 …   │
└───────────────────────────────────────────────┴──────────────┘
相关推荐 [卡] [卡] [卡]
```

### 5.4 价格 `/pricing`

```
标题：按次计费，充多少用多少
[充值卡 ¥50] [¥100 送¥5 推荐] [¥300 送¥20] [¥500 送¥50]
计费表：脱壳 ¥6/次（按 App 可单独定价） · 成品下载 ¥3/次
说明：失败自动退款；脱壳成功 24h 内首次下载免费；余额永久有效
FAQ
```

### 5.5 登录 / 注册

左侧品牌面板（深色渐变 + 终端片段），右侧表单。字段：邮箱、密码；注册：邮箱、密码、确认密码、验证码（图形/邮箱二选一，v1 图形）、同意条款。错误内联提示；提交时按钮 loading；支持「演示账号」一键填充（仅开发环境）。

### 5.6 控制台概览 `/app/dashboard`

```
┌ 侧栏 ────────┬──────────────────────────────────────────────┐
│ 概览          │ 下午好，Yin            [新建脱壳]             │
│ 新建脱壳      │ ┌ 余额 ¥42.00 [充值] ┐ ┌ 进行中 1 ┐ ┌ 本月 12 次 ┐│
│ 任务          │ 最近任务（表格：App/状态/进度/时间/操作）        │
│ 我的应用      │ 用量趋势（14 天柱状图）                        │
│ 钱包          │ 公告 / 快捷入口                                │
│ 设置          │                                              │
└──────────────┴──────────────────────────────────────────────┘
```

### 5.7 新建脱壳 `/app/new`

```
标题：新建脱壳
[ App Store 应用 ] [ 上传 IPA ]        ← 分段控件
App Store：搜索框（防抖 300ms）→ 候选列表（图标/名称/开发者/bundle）
上传：虚线拖拽区（≤ 2GB），上传后显示解析结果（bundle/版本/大小/架构）
────────────────────────────────────────
订单摘要：目标 / 版本 / 预计耗时 / 费用 ¥6.00 / 余额 ¥42.00 → 下单后 ¥36.00
[ 确认下单 ]（余额不足时按钮变「充值 ¥X」）
```

### 5.8 任务列表 `/app/tasks`

筛选 tabs：全部 / 进行中 / 成功 / 失败。表格列：App、状态、进度、费用、创建时间、操作（查看/重试/取消）。进行中行内进度条 + 阶段文案。

### 5.9 任务详情 `/app/tasks/:id`

```
App 头（图标/名称/版本）            [状态胶囊]
步骤条：排队 → 下载 → 解密 → 校验 → 完成
实时日志（等宽字体，自动滚动，可复制）
结果卡（成功）：文件大小 / SHA256 / cryptid=0 / [下载 IPA]（24h 内免费）
结果卡（失败）：失败原因 + 已退款 ¥6.00 + [重试]
```

### 5.10 钱包 `/app/wallet`

余额大卡（¥42.00，累计充值/消费）+ [充值]；套餐选择；支付二维码弹窗（模拟）；流水表（时间/类型/金额/余额/备注/关联任务）。

### 5.11 我的应用 `/app/library`

卡片或表格：App、版本、来源（脱壳/购买）、获得时间、剩余免费下载期、[下载] [查看任务]。

### 5.12 管理后台

统一布局：左侧 236px 侧栏（深色 #1d1d1f 反白，区别于用户端），顶部面包屑 + 管理员头像；内容区 `--bg-subtle`。

- **概览**：4 KPI（今日收入 / 今日脱壳 / 成功率 / 活跃用户）+ 14 天收入折线 + 任务状态分布 + 最近订单 + 失败任务 TOP。
- **用户**：搜索、角色、状态、余额；行操作：调整余额（弹窗，必填备注）、封禁/解封、设为管理员。
- **任务**：全量筛选（状态/用户/时间）、失败原因聚合、重试、取消、下载产物。
- **应用库**：表格 + 新建/编辑抽屉（名称、bundle、版本、分类、图标色、下载价、状态、关联任务）；上下架开关。
- **订单**：充值订单（待支付/已支付/已退款）与消费流水两个 tab；退款操作带二次确认。
- **定价**：默认脱壳价、默认下载价、充值套餐（金额/赠送/推荐位）、并发上限；保存后展示「生效时间」。
- **Apple ID 池**：表格（邮箱、优先级、凭证、会话、可用、最近使用、绑定 App 数），[同步状态] 调后端读取 `appget.sh account list`；异常行标红并给运维指引。
- **系统**：维护模式开关（前台显示公告条）、公告内容、单用户并发、存储配额、清理策略。
- **日志**：审计表格（时间/操作者/IP/动作/对象/结果），支持按动作筛选。

---

## 6. 组件清单

| 组件 | 说明 |
|---|---|
| `AppHeader` | 公共导航，滚动后毛玻璃 + 阴影 |
| `AppFooter` | 分组链接 + 备案位 |
| `ConsoleLayout` | 用户侧栏 + 顶栏（余额、头像菜单） |
| `AdminLayout` | 深色侧栏 + 面包屑 |
| `UiButton` | primary / secondary / ghost / danger，三档尺寸，loading |
| `UiCard` | 基础卡片，可选 hoverable |
| `UiInput` / `UiTextarea` | 带 label、错误、前后缀 |
| `UiSelect` | 原生封装，统一箭头与高度 |
| `UiTabs` / `UiSegmented` | 下划线 tabs / 胶囊分段 |
| `UiModal` | 遮罩 + 卡片，ESC 关闭，滚动锁 |
| `UiTable` | 列定义 + 空态 + 加载态，插槽自定义单元格 |
| `StatusPill` | 任务/订单状态映射 |
| `UiProgress` | 线性进度（含阶段文案） |
| `Sparkline` / `BarChart` | 纯 SVG，无图表库依赖 |
| `EmptyState` | 插画位（CSS）+ 主/次操作 |
| `UiToast` | 右上角堆叠，3s 自动消失 |
| `AppIcon` | 线性 SVG 图标集（20+） |

---

## 7. 状态与异常设计

| 场景 | 表现 |
|---|---|
| 列表加载 | 骨架屏，保持布局不跳动 |
| 空数据 | 插画 + 一句解释 + 主动作（如「去市场看看」） |
| 接口错误 | 区块内错误条 + 重试，不整页白屏 |
| 余额不足 | 按钮文案变「充值 ¥X」，点击打开充值弹窗，支付后回到原上下文 |
| 任务失败 | 红色状态胶囊 + 失败原因 + 已退款提示 + 重试 |
| 会话过期 | 401 → 跳登录并带 `redirect`，登录后回原页 |
| 权限不足 | 403 页面，提供返回控制台 |
| 维护模式 | 全站顶部黄条；管理后台正常 |
| 危险操作 | 二次确认弹窗，需输入对象名称（删除应用/退款） |

---

## 8. 计费规则（v1）

| 项 | 规则 |
|---|---|
| 余额单位 | 人民币元，保留 2 位小数；数据库存「分」（BIGINT）避免浮点误差 |
| 脱壳定价 | App 维度可覆盖；默认 ¥6.00。下单预扣，失败全额退 |
| 成品下载 | App 维度可覆盖；默认 ¥3.00。扣费后生成 15 分钟签名链接 |
| 重复下载 | 已购/已脱壳产物在「我的应用」中 30 天内免费重复下载 |
| 脱壳首下 | 任务成功后 24h 内首次下载免费 |
| 充值套餐 | ¥50（送 0）/ ¥100（送 5）/ ¥300（送 20）/ ¥500（送 50） |
| 并发 | 普通用户同时 2 个任务；管理员可调 |
| 退款 | 仅未消费余额；管理员审核后退回原支付渠道（v1 为余额冲正） |
| 对账 | 所有余额变动必须写 `wallet_transactions`，带 `balance_after` 与业务单号 |

---

## 9. 数据模型草案（MySQL 8）

```sql
users(id, email UNIQUE, password_hash, nickname, avatar_color, role ENUM('USER','ADMIN'),
      status ENUM('ACTIVE','BANNED'), created_at, last_login_at)
wallets(user_id PK, balance_cents BIGINT, total_recharge_cents, total_spend_cents, updated_at)
wallet_transactions(id, user_id, type ENUM('RECHARGE','DECRYPT','DOWNLOAD','REFUND','ADJUST'),
      amount_cents, balance_after_cents, ref_type, ref_id, remark, created_at)
recharge_orders(id, user_id, package_id, amount_cents, bonus_cents, pay_method,
      status ENUM('PENDING','PAID','REFUNDED'), paid_at, created_at)
apps(id, bundle_id UNIQUE, name, developer, category, icon_hue, version, size_bytes,
      min_ios, executable, sha256, price_download_cents, price_decrypt_cents,
      status ENUM('DRAFT','ON_SALE','OFF_SHELF'), downloads, source_task_id, updated_at)
tasks(id, user_id, source ENUM('STORE','UPLOAD'), app_id NULL, target_bundle, target_name,
      version, status ENUM('QUEUED','RESOLVING','DOWNLOADING','DECRYPTING','VERIFYING',
      'SUCCEEDED','FAILED','CANCELED'), price_cents, refunded, progress, stage_text,
      apple_account_id, result_path, result_size, sha256, error_code, error_message,
      created_at, started_at, finished_at)
task_logs(id, task_id, level, message, created_at)
downloads(id, user_id, app_id, task_id NULL, price_cents, ip, created_at)
apple_accounts(id, email, credential_path, priority, credential_ok, session_ok,
      status ENUM('READY','NEED_LOGIN','DISABLED'), last_used_at)
settings(k VARCHAR(64) PK, v TEXT)
audit_logs(id, actor_id, action, target, detail, ip, created_at)
```

索引：`tasks(user_id, created_at)`、`tasks(status, created_at)`、`wallet_transactions(user_id, created_at)`、`apps(status, category)`。

---

## 10. 接口草案（/api/v1）

```
POST   /auth/register            {email,password,captcha}
POST   /auth/login               → {token, user}
GET    /auth/me
POST   /auth/logout

GET    /apps?q=&category=&sort=&page=          # 市场
GET    /apps/{bundleId}
POST   /apps/{bundleId}/download               # 扣费 + 返回签名 URL
POST   /apps/{bundleId}/decrypt                # 发起脱壳任务

GET    /tasks?status=&page=
POST   /tasks            {source, bundleId|uploadId, version?}
GET    /tasks/{id}
POST   /tasks/{id}/retry
POST   /tasks/{id}/cancel
GET    /tasks/{id}/logs?afterId=               # 轮询 / SSE 升级

GET    /wallet
GET    /wallet/transactions?type=&page=
GET    /wallet/packages
POST   /wallet/recharge  {packageId, payMethod} → {orderId, qrCode}
POST   /wallet/recharge/{orderId}/mock-pay      # 仅开发环境

GET    /library
GET    /downloads/{id}/url                      # 校验有效期

# 管理端（ROLE=ADMIN）
GET    /admin/overview
GET    /admin/users?q=&status=
PATCH  /admin/users/{id}         {status?, role?}
POST   /admin/users/{id}/balance {deltaCents, remark}
GET    /admin/tasks?status=&userId=
POST   /admin/tasks/{id}/retry
GET    /admin/apps?status=
POST   /admin/apps               {…}
PATCH  /admin/apps/{id}
POST   /admin/apps/{id}/shelf    {status}
GET    /admin/orders?type=&status=
POST   /admin/orders/{id}/refund
GET    /admin/pricing
PUT    /admin/pricing            {decryptCents, downloadCents, packages[]}
GET    /admin/apple-accounts
POST   /admin/apple-accounts/sync
GET    /admin/logs?action=&page=
GET    /admin/settings
PUT    /admin/settings
```

统一响应：`{ code: 0, data: …, message: "ok" }`；错误 `code` 非 0，`message` 可直接展示。
鉴权：`Authorization: Bearer <JWT>`；管理端接口额外校验角色。

---

## 11. 与能力层的对接（第二轮实现）

```
Spring Boot 任务执行器（@Async / 线程池，并发 = settings.concurrency）
  1. 预扣费 → 写 task(QUEUED)
  2. 调度：调用 fairplay-tools
       cd fairplay-tools && ./appget.sh get <bundle|appid> [--version-id …]
     通过 ProcessBuilder 启动，stdout/stderr 逐行写入 task_logs 并解析进度：
       "下载中"   → DOWNLOADING
       "解密中"   → DECRYPTING
       "校验"     → VERIFYING
       "解密完成" → SUCCEEDED（解析产物路径与大小）
       "✘" / 非零退出 → FAILED + 退款
  3. 成功后：计算 SHA256、登记 apps（若为市场首发）、写 downloads 计费
  4. 超时（默认 30 分钟）→ 杀进程组 + FAILED + 退款
```

注意：`appget.sh` 内部会 `switch` Apple ID 会话，**任务队列必须串行写会话**（同一时刻只有一个任务能切换 Apple ID），下载阶段加全局互斥锁；解密阶段可并行（不依赖会话）。

---

## 12. 响应式与可访问性

- 断点：`≥1200` 四列 / `≥900` 三列 / `≥640` 两列 / `<640` 单列；后台侧栏在 `<900` 折叠为抽屉。
- 焦点环：2px `--blue` + 2px 偏移；所有图标按钮有 `aria-label`。
- 对比度：正文 `#1d1d1f` 对白底 16.9:1；次级 `#6e6e73` 5.6:1，均达 AA。
- 状态不只靠颜色：胶囊同时有文字与图标/圆点。

---

## 13. 本轮交付物

```
appreseal/
├── docs/UI-DESIGN.md          本文档
├── backend/                   Spring Boot 后端（见 §14）
├── frontend/                  Vue 3 + Vite + TS + Pinia + Vue Router（mock 数据）
│   ├── src/views/public/*     首页 / 市场 / 详情 / 价格 / 文档 / 登录 / 注册
│   ├── src/views/console/*    概览 / 新建脱壳 / 任务 / 任务详情 / 我的应用 / 钱包 / 设置
│   ├── src/views/admin/*      概览 / 用户 / 任务 / 应用 / 订单 / 定价 / Apple ID / 系统 / 日志
│   ├── src/components/*       设计系统组件
│   └── src/mock/*             假数据 + 模拟延迟（可一键切真接口）
└── README.md                  启动方式与演示账号
```

演示账号：`demo@appreseal.com / demo1234`（用户）、`admin@appreseal.com / admin1234`（管理员）。

## 14. 后端实现现状

已实现（`backend/`，Spring Boot 3.3.5 + Java 21 + Spring Data JPA，不是 MyBatis-Plus）：

1. JWT（jjwt 0.12.6）+ BCrypt；错误体是 `{message, status, path}`，不是草案里的 `{code, data}`。前端切真接口时直接展示 `message`。
2. 开发库是 H2 文件（`jdbc:h2:file:./data/appreseal`，MySQL 模式，`ddl-auto=update`），不做 Flyway。生产用 `application-mysql.yml`（profile `mysql`）。
3. 钱包行锁扣款、按 `taskId + attempt` 幂等退款、充值订单（`POST /recharges/{id}/pay` 模拟到账）。
4. `TaskDispatcher` 按设置里的并发数调度。`mock` 为默认；`shell` 调 `appget.sh get <bundleId> --force`，全局锁、30 分钟超时，stdout 关键字映射状态。
5. 管理端接口与审计日志已齐。种子数据与 `frontend/src/mock/db.ts` 对齐，只在 users 表为空时写入。

与 §10 草案的路径差异（以代码为准）：

- 市场搜索是 `GET /store/search`，不是把搜索塞进创建任务。
- 充值是 `POST /recharges` + `POST /recharges/{id}/pay`，不是 `/wallet/recharge`。
- 流水是 `GET /transactions`。上下架是 `PATCH /admin/apps/{id}/shelf`。
- 没有 `GET /tasks/{id}/logs` 与签名下载 URL；日志嵌在任务详情里。

前端可用 `VITE_API_MODE=real` 切到本接口（Docker 构建已默认打开）。`docker compose up -d --build` 会拉起 MySQL + 后端 + Nginx 前端，站点在 `http://127.0.0.1:8088`。

还没做：真实支付回调、对象存储上的签名下载（当前产物从后端输出目录直接下载；mock 模式是一份清单）。

---

## 15. 前端实现现状（供后续接手参考）

### 15.1 已完成

- 全部 23 个页面 + 404，路由与守卫见 `frontend/src/router/index.ts`。
- 组件库 17 个，`UiTable` 为泛型组件（`generic="T extends Record<string, any>"`，列类型 `TableColumn` 位于 `src/types.ts`）；新增表格列时若 slot 用到行内字段，需保证 `rows` 的元素类型被推断为具体类型而不是 `Record<string, any>`。
- Mock 层：`src/api/index.ts` 是唯一接口边界；`src/mock/db.ts` 负责种子数据、`localStorage` 持久化、`advanceTasks()` 时间推进。
- 类型检查（`vue-tsc --noEmit`）与生产构建（`vite build`）均通过。

### 15.2 移动端适配结论（已实测）

桌面、平板（768）、手机（390×844）三档全部页面 `document.documentElement.scrollWidth === 视口宽度`，无横向溢出。

关键规则（改动布局时务必保留）：

1. **任何承载表格 / 长内容的 grid 列都要写 `minmax(0, 1fr)`**，不能写 `1fr`。`1fr` 的隐含最小值是 `auto`，表格的 `min-content` 会把整页撑宽（曾导致概览页 454px、后台概览 533px）。
2. `.card` 统一 `min-width: 0`（`src/styles/components.css`），避免卡片作为 grid/flex 子项被内容撑破。
3. 表格本身**不压缩**：`.table-wrap` 已有 `overflow-x: auto`，窄屏时在卡片内横向滑动，而不是挤压列宽。
4. `.card__head a` 为 `inline-flex` + `white-space: nowrap`，否则标题右侧的箭头图标会掉到第二行。
5. 移动端侧栏（`console__aside` / `admin__aside`）默认 `translateX(-100%)`，汉堡按钮切换 `.is-open` 并显示 scrim；展开后 `left = 0`、宽度 236px。

### 15.3 图表注意点

`BarChart` 的柱子高度依赖父容器百分比高度，**不要**给 `.chart__bar` 加 `height` 过渡：在后台标签页中 `requestAnimationFrame` 被节流，过渡不会推进，柱子会停在初始的 `3%`。当前仅保留 `opacity` 过渡。

### 15.4 调试约定

- mock 数据键：`appreseal.db.v1`；会话键：`appreseal.session`（`{ userId, at }`）。
- 需要切换身份调试时，直接写 `localStorage` 的 session 即可，无需走登录表单。
- 生产构建产物在 `frontend/dist`，不要提交调试用的临时 HTML 页面。
