# 出题机 · 详细设计文档

> 版本 v0.2 · 2026-11-02
> 技术栈：Java 21 + Spring Boot 3 + Maven ｜ Vue 3 + Vite + Pinia ｜ MySQL 8
> 部署策略：本地全流程跑通后再上公网（已确认）

---

## 0. 文档状态

| 项 | 状态 |
| --- | --- |
| 已确认决策 | 见第 10 章 |
| 待评审 | 第 3 章核心抽象、第 4 章接口契约、第 6 章六个模块难度轴 |
| 本阶段 | 只交付设计，不产出代码 |

---

## 1. 项目定位与范围

### 1.1 一句话

一个多模块的本地出题小站：程序自己生成题目，用户自选模块和难度，做题、判题、记录进度。

### 1.2 范围边界

**做**

- 六个模块：四则口算、数独、逻辑推理、方程与代数、单位换算、几何图形
- 四种题型渲染：选择、填空、棋盘、图形
- 每个模块声明自己的难度维度，前端动态渲染难度控件
- 服务端判题，答案不下发
- 做题记录、错题本、统计

**不做**（本版本）

- 手写自动识别（用笔迹画布 + 自评对照替代）
- 排行榜、竞赛、实时对战
- 移动端原生 App（只做响应式 Web）
- FSRS 参数训练（用官方默认参数，见 10.3 裁定 2）

### 1.3 关键约束

| 约束 | 原因 |
| --- | --- |
| 答案绝不下发前端 | 公网部署的前提，后期补不上 |
| 前端不硬编码任何模块的难度选项 | 否则新增模块要改前端，架构失效 |
| 不修改系统 JAVA_HOME | 本机 JAVA_HOME 指向 JDK 8，苍穹环境在用 |
| 数据库直接用 MySQL 8 | 已确认，不走 H2 过渡 |

---

## 2. 总体架构

### 2.1 分层

~~~text
Web 层      Controller / 全局异常 / 响应包络
应用层      QuestionService / GradeService / RecordService
模块层      QuestionModule 接口
            └ arithmetic / sudoku / logic / algebra / unit / geometry
基础设施    JPA Repository / Flyway / MySQL
~~~

模块层只依赖 QuestionModule 接口与 DTO，不依赖 Web 层，模块之间也互不依赖。
新增模块 = 新增一个实现类 + 注册为 Spring Bean，其它代码零改动。

### 2.2 目录结构

~~~text
chutiji/
├── backend/
│   ├── pom.xml
│   ├── mvnw / mvnw.cmd / .mvn/
│   └── src/main/java/com/chutiji/
│       ├── ChutijiApplication.java
│       ├── common/         ApiResult / BizException / GlobalExceptionHandler
│       ├── module/
│       │   ├── api/        ★ 模块契约（唯一稳定边界）
│       │   │   QuestionModule / ModuleMeta / DifficultyAxis
│       │   │   AxisKind / QuestionType / GenRequest
│       │   │   GeneratedQuestion / GradeRequest / GradeResult
│       │   ├── registry/   ModuleRegistry
│       │   └── impl/       arithmetic / sudoku / logic / algebra / unit / geometry
│       ├── question/       QuestionService / GradeService / Entity / Repository
│       ├── record/         AnswerRecordEntity / WrongBookEntity / RecordService
│       └── web/            ModuleController / QuestionController / RecordController
│   └── src/main/resources/
│       ├── application.yml
│       └── db/migration/   Flyway: V1__init.sql, V2__record.sql
└── frontend/
    ├── package.json / vite.config.ts
    └── src/  api/ stores/ views/ components/ router/
~~~

---

## 3. 核心抽象

> 这是整个项目最重要的部分。它定死了，后面六个模块只是填空；它定歪了，每加一个模块都要回头改前端。

### 3.1 QuestionModule 接口

~~~java
public interface QuestionModule {

    /** 模块唯一标识，如 "arithmetic"，用于路由与落库 */
    String key();

    /** 模块元信息：名称、题型、难度轴定义 */
    ModuleMeta meta();

    /** 按请求出题。返回对象中不得包含答案 */
    GeneratedQuestion generate(GenRequest request);

    /** 服务端判题。答案只在这里被读取 */
    GradeResult grade(GradeRequest request);
}
~~~

约定：实现类标注 @Component 由 ModuleRegistry 自动收集；generate 无副作用可重复调用；grade 幂等。

### 3.2 ModuleMeta 与 DifficultyAxis

~~~java
public record ModuleMeta(
    String key,
    String name,                 // "四则口算"
    String description,
    QuestionType type,           // CHOICE / FILL / BOARD / FIGURE
    List<DifficultyAxis> axes,   // ★ 难度轴由模块自己声明
    boolean poolBased,           // 是否走预生成题库
    int timeLimitSec
) {}

public record DifficultyAxis(
    String key,                  // "maxOperand"
    String label,                // "数值范围"
    AxisKind kind,               // INT_RANGE / ENUM / BOOLEAN
    Integer min, Integer max, Integer step,   // INT_RANGE 专用
    Object defaultValue,
    List<AxisOption> options     // ENUM 专用
) {}

public record AxisOption(String value, String label) {}
~~~

前端拿到 axes 后按 kind 分别渲染为滑杆 / 下拉 / 开关。前端代码里不允许出现任何模块名或难度字段名的字面量。

### 3.3 题目生命周期（答案不下发的实现方式）

~~~text
客户端                        服务端
  │ POST /api/questions        │
  │ {moduleKey, difficulty}    │
  │ ──────────────────────────►│ 1. module.generate()
  │                            │ 2. 得到 prompt + payload + answer
  │                            │ 3. answer 存 t_question，payload 下发
  │ {questionId, prompt,       │
  │  payload}                  │ ← 响应中不含 answer
  │ ◄──────────────────────────│
  │                            │
  │ 用户作答                    │
  │ POST /api/questions/{id}/grade
  │ {answer: 用户提交}          │
  │ ──────────────────────────►│ 4. 按 id 取出 answer
  │                            │ 5. module.grade()
  │                            │ 6. 写 t_answer_record
  │ {correct, correctAnswer,   │
  │  explanation}              │ ← 判完才给正确答案
  │ ◄──────────────────────────│
~~~

**为什么用服务端存题而不是签名令牌**：令牌方案无状态但题目长度受限，且错题本需要题目原文；
存库顺带解决错题复习、预生成题库、做题统计。代价是要做 TTL 清理，可接受。

**题目有效期**：实时生成的题 24 小时过期；池题长期有效。

---

## 4. 接口契约

### 4.1 响应包络

~~~json
{ "code": 0, "message": "ok", "data": {} }
~~~

错误码：0 成功，1xxx 参数错误，2xxx 业务错误，5xxx 系统错误。

### 4.2 端点表

| 方法 | 路径 | 用途 | Phase |
| --- | --- | --- | --- |
| GET | /api/modules | 模块列表 + meta（含难度轴） | 0 |
| POST | /api/questions | 出题 | 0 |
| POST | /api/questions/{id}/grade | 判题 | 0 |
| POST | /api/questions/{id}/hint | 取提示 | 2 |
| GET | /api/records | 做题记录分页 | 2 |
| GET | /api/review/due | 到期待复习卡片列表 | 2 |
| POST | /api/review/{cardId}/rate | 手动评分（可选，默认自动映射） | 2 |
| GET | /api/review/stats | 复习统计、复习日历 | 4 |
| POST | /api/auth/register 与 /api/auth/login | 注册登录 | 5 |

### 4.3 示例报文

**GET /api/modules** → 四则口算的 meta

~~~json
{
  "code": 0,
  "data": [{
    "key": "arithmetic",
    "name": "四则口算",
    "description": "加减乘除混合运算",
    "type": "FILL",
    "poolBased": false,
    "timeLimitSec": 120,
    "axes": [
      {"key":"maxOperand","label":"数值范围","kind":"INT_RANGE","min":10,"max":1000,"step":10,"defaultValue":100},
      {"key":"operatorCount","label":"运算符个数","kind":"INT_RANGE","min":1,"max":4,"step":1,"defaultValue":2},
      {"key":"allowParentheses","label":"允许括号","kind":"BOOLEAN","defaultValue":false},
      {"key":"allowDivision","label":"允许除法","kind":"BOOLEAN","defaultValue":true}
    ]
  }]
}
~~~

**POST /api/questions** 请求：

~~~json
{"moduleKey":"arithmetic","difficulty":{"maxOperand":100,"operatorCount":2,"allowParentheses":false,"allowDivision":true}}
~~~

响应（注意：没有 answer）：

~~~json
{
  "code": 0,
  "data": {
    "questionId": "q_8f3a1c2e",
    "moduleKey": "arithmetic",
    "type": "FILL",
    "prompt": "计算：84 ÷ 7 + 19 = ?",
    "payload": {"answerFormat":"NUMBER","hint":"结果保留整数"},
    "timeLimitSec": 120,
    "expiresAt": "2026-11-03T10:00:00Z"
  }
}
~~~

**POST /api/questions/{id}/grade** 请求：{"answer":"31"}，响应：

~~~json
{ "code": 0, "data": {
  "correct": true,
  "correctAnswer": "31",
  "explanation": "84 ÷ 7 = 12，12 + 19 = 31"
} }
~~~

### 4.4 用户系统接口

**鉴权方式**：JWT 放 `Authorization: Bearer <token>` 头。无状态，服务端不存 session。

| 方法 | 路径 | 鉴权 | 用途 |
| --- | --- | --- | --- |
| POST | /api/auth/register | 否 | 注册 |
| POST | /api/auth/login | 否 | 登录，返回 token |
| GET | /api/auth/me | 是 | 取当前用户信息 |
| PUT | /api/auth/me | 是 | 改昵称、改密码 |

**POST /api/auth/register**

~~~json
请求：{ "username": "zhangwei", "password": "明文密码", "nickname": "ZW" }
响应：{ "code": 0, "data": { "userId": 1, "username": "zhangwei", "nickname": "ZW" } }
~~~

**POST /api/auth/login**

~~~json
请求：{ "username": "zhangwei", "password": "明文密码" }
响应：{ "code": 0, "data": {
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "expiresAt": "2026-11-09T10:00:00Z",
  "user": { "userId": 1, "username": "zhangwei", "nickname": "ZW" }
} }
~~~

**GET /api/auth/me** → `{ "code":0, "data": { "userId":1, "username":"...", "nickname":"...", "createdAt":"..." } }`

**鉴权约定**：

| 项 | 值 |
| --- | --- |
| 算法 | HS256 |
| 有效期 | 7 天（本地开发够用，公网阶段再加 refresh token） |
| 载荷 | userId、username，不放敏感信息 |
| 密钥 | 从 `app.jwt.secret` 读，默认值仅用于本地开发 |
| 密码存储 | BCrypt，cost 10 |

**未登录访问受保护接口**：返回 HTTP 401，body 为 `{"code":2001,"message":"未登录或登录已过期"}`。

**Phase 0 的保护范围**：`/api/questions/**`、`/api/records/**`、`/api/review/**`、`/api/auth/me` 需登录；
`/api/modules`、`/api/auth/register`、`/api/auth/login` 开放。

---

## 5. 数据模型（MySQL 8）

### 5.1 表清单

| 表 | 用途 | Phase |
| --- | --- | --- |
| t_question | 题目（实时生成 + 预生成池） | 0 |
| t_answer_record | 每次作答记录 | 0 |
| t_review_card | 复习卡：按知识点调度的 FSRS 状态 | 4 |
| t_review_log | 复习历史：每次复习前后状态快照 | 4 |
| t_user | 用户（含 BCrypt 密码、角色、启用状态） | 0 |

**注**：原设计的 `t_wrong_book` 已取消，由 `t_review_card` + `t_review_log` 替代，原因见 10.3 裁定 2。

### 5.2 DDL（Flyway V1__init.sql）

~~~sql
CREATE TABLE t_question (
    id            VARCHAR(32)  NOT NULL PRIMARY KEY,
    module_key    VARCHAR(32)  NOT NULL,
    question_type VARCHAR(16)  NOT NULL,
    difficulty    JSON         NOT NULL,   -- 本次出题使用的难度轴取值
    prompt        TEXT         NOT NULL,
    payload       JSON         NOT NULL,   -- 下发给前端，无答案
    answer        JSON         NOT NULL,   -- 服务端留存，绝不下发
    explanation   TEXT,
    source        VARCHAR(16)  NOT NULL,   -- GENERATED / POOL
    user_id       BIGINT,
    expires_at    DATETIME,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_question_module (module_key, source),
    INDEX idx_question_expire (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE t_answer_record (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT,
    question_id VARCHAR(32) NOT NULL,
    module_key  VARCHAR(32) NOT NULL,
    submitted   JSON        NOT NULL,
    correct     TINYINT     NOT NULL,
    duration_ms INT,
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_record_user (user_id, created_at),
    INDEX idx_record_question (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
~~~

**注意**：payload 与 answer 用 MySQL 原生 JSON 类型，可直接用 JSON 函数做统计查询；
utf8mb4_0900_ai_ci 是 MySQL 8 默认排序规则，中文与 emoji 都安全。

### 5.3 连接配置（已实测）

**环境实测值**：MySQL 8.0.45 ｜ 字符集 utf8mb4 ｜ 排序规则 utf8mb4_0900_ai_ci ｜ 时区 SYSTEM ｜ 3306 可连。

**已建库**（2026-11-02 实测创建成功）：

~~~sql
CREATE DATABASE IF NOT EXISTS chutiji
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;
~~~

**application.yml**：

~~~yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/chutiji?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false&rewriteBatchedStatements=true
    username: root
    password: ${DB_PASSWORD:root}
  jpa:
    hibernate:
      ddl-auto: validate      # 表结构由 Flyway 管，JPA 只校验
    open-in-view: false
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration
~~~

**几个必须写对的点**：

| 参数 | 值 | 为什么 |
| --- | --- | --- |
| serverTimezone | Asia/Shanghai | 服务器时区是 SYSTEM，不显式指定会和 JVM 时区打架，时间差 8 小时 |
| allowPublicKeyRetrieval | true | MySQL 8 默认 caching_sha2_password，不开启会报 Public Key Retrieval is not allowed |
| useSSL | false | 本地开发关掉，公网阶段再开 |
| rewriteBatchedStatements | true | 批量插入提速，题库跑批时会用到 |
| ddl-auto | validate | 必须是 validate 不能是 update，否则 JPA 绕过 Flyway 改表 |
| open-in-view | false | 避免懒加载在视图层触发，养成显式查询的习惯 |

**密码处理**：`${DB_PASSWORD:root}` 表示优先读环境变量 `DB_PASSWORD`，没有则回退到 root。
本地开发零配置，公网部署时改成环境变量注入，密码不进代码库。

### 5.4 用户表 DDL（Flyway V1 一并创建）

~~~sql
CREATE TABLE t_user (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,   -- BCrypt，60 字符，留余量
    nickname      VARCHAR(64)  NOT NULL,
    email         VARCHAR(128),
    role          VARCHAR(16)  NOT NULL DEFAULT 'USER',
    enabled       TINYINT      NOT NULL DEFAULT 1,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
~~~

**字段说明**：

| 字段 | 说明 |
| --- | --- |
| password_hash | BCrypt 输出固定 60 字符，VARCHAR(100) 留出换算法的余量 |
| role | 目前只有 USER，预留 ADMIN 给后台管理 |
| enabled | 禁用账号用，不物理删除用户（否则做题记录会断链） |

**与既有表的关系**：`t_question.user_id` 与 `t_answer_record.user_id` 关联 t_user.id。
Phase 0 起所有出题和作答都必须带 user_id，不再允许匿名作答。

---

## 6. 六个模块设计

### 6.1 四则口算 · arithmetic · FILL

| 难度轴 | 类型 | 范围 | 默认 |
| --- | --- | --- | --- |
| maxOperand | INT_RANGE | 10–1000，步长 10 | 100 |
| operatorCount | INT_RANGE | 1–4 | 2 |
| allowParentheses | BOOLEAN | — | false |
| allowDivision | BOOLEAN | — | true |

**生成**：递归构建表达式树。所有中间结果用 Fraction（分子分母 long）表示，避免浮点误差；
allowDivision=false 时保证整除；最后统一化简。

**判题**：用户输入解析为 Fraction 与原值精确比较；支持 1/3、0.333（容差 1e-6）、带分数三种格式。

**陷阱**：除不尽时答案必须是分数而非小数，否则用户永远答不对。

### 6.2 数独 · sudoku · BOARD

| 难度轴 | 类型 | 取值 |
| --- | --- | --- |
| techniqueLevel | ENUM | EASY / MEDIUM / HARD / EXPERT |
| symmetry | BOOLEAN | 是否对称挖洞 |

**生成三步**：

1. 填终盘：随机化回溯填满 9×9，用位掩码加速候选判断
2. 挖洞：按对称策略逐格挖空，每挖一次跑唯一解验证，不唯一就回填
3. 评级：用技巧求解器逐步求解，记录用到的最高级技巧

**技巧等级表**

| 等级 | 求解所需技巧 |
| --- | --- |
| EASY | 唯一候选 Naked Single、隐性唯一 Hidden Single |
| MEDIUM | 数对 Naked/Hidden Pair |
| HARD | 三数组、区块删减 Pointing/Claiming |
| EXPERT | X-Wing、XY-Wing、链 |

**性能目标**：终盘 < 50ms；完整生成（含挖洞验证）< 300ms；超过 1s 视为不合格。

**判题**：逐格比对，全对才算对；返回第一处错误格位置用于提示。

### 6.3 逻辑推理 · logic · CHOICE

| 难度轴 | 类型 | 范围 |
| --- | --- | --- |
| conditionCount | INT_RANGE | 2–6 |
| stepDepth | INT_RANGE | 1–4 |

**生成**：模板化排列题——N 个人、N 个位置、N 个物品，随机生成条件（相邻、在左、不在两端等），
穷举所有排列验证唯一解，不唯一就加条件重试。

**判题**：选项比对。四个选项由正确答案 + 三个干扰项（交换两人位置）构成。

**为什么走题库**：唯一解验证有重试成本，实时生成可能超时，因此预生成池按难度分档。

### 6.4 方程与代数 · algebra · FILL

| 难度轴 | 类型 | 取值 |
| --- | --- | --- |
| degree | ENUM | LINEAR / QUADRATIC |
| variables | INT_RANGE | 1–2 |
| integerRoot | BOOLEAN | 根是否限定整数 |

**生成**：先定根（整数或分数）→ 反推系数 → 展开成标准式。二次方程若 integerRoot=true 则保证判别式为完全平方。

**判题**：把用户答案代入原方程验证（而非比对字符串），支持 x=3、3、x=3或-2 多种写法。

### 6.5 单位换算 · unit · FILL

| 难度轴 | 类型 | 取值 |
| --- | --- | --- |
| unitFamily | ENUM | LENGTH / WEIGHT / VOLUME / TIME / AREA |
| crossMagnitude | BOOLEAN | 是否跨 10³ 以上量级 |

**生成**：内置单位表（单位 → 基准单位换算因子），随机取同族两个单位生成换算题。

**判题**：数值比较，按题目有效数字给容差（默认相对误差 1e-3）。

### 6.6 几何图形 · geometry · FIGURE

| 难度轴 | 类型 | 取值 |
| --- | --- | --- |
| shapeComplexity | ENUM | BASIC / COMPOSITE |
| needAuxiliary | BOOLEAN | 是否需要辅助线 |

**生成**：参数化几何题（三角形/矩形/圆的周长面积、勾股定理、相似三角形）。SVG 由参数实时生成，随 payload 下发。

~~~json
{ "svg": "<svg viewBox='0 0 300 200'>...</svg>", "answerFormat": "NUMBER", "unit": "cm" }
~~~

**判题**：数值比较，容差 1e-2。

**说明**：这是唯一需要 figure 渲染器的模块，手写笔迹层主要服务它（用户可以在图上画辅助线）。

---

## 7. 前端设计

### 7.1 组件树

~~~text
App.vue
├── HomeView.vue                首页：模块卡片墙
├── PracticeView.vue            核心练习页
│   ├── ModulePicker.vue        选模块（数据来自 /api/modules）
│   ├── DifficultyPanel.vue     ★ 动态难度控件
│   │   ├── AxisRange.vue       kind=INT_RANGE
│   │   ├── AxisEnum.vue        kind=ENUM
│   │   └── AxisBool.vue        kind=BOOLEAN
│   ├── QuestionRenderer.vue    ★ 按 type 分发
│   │   ├── ChoiceRenderer.vue
│   │   ├── FillRenderer.vue
│   │   ├── BoardRenderer.vue
│   │   └── FigureRenderer.vue
│   ├── InkCanvas.vue           手写笔迹层（叠在 Fill/Figure 上）
│   └── ResultPanel.vue         判题结果 + 正确答案 + 解析
├── RecordsView.vue             记录与错题明细
└── ReviewView.vue              复习队列：到期卡片 + 开始复习
~~~

### 7.2 动态难度控件

~~~text
for axis in module.axes:
    if axis.kind == INT_RANGE:  渲染 AxisRange
    if axis.kind == ENUM:       渲染 AxisEnum
    if axis.kind == BOOLEAN:    渲染 AxisBool
difficulty = { axis.key: 控件当前值 }
~~~

**硬性约束**：全项目搜索不到 sudoku、maxOperand 这类字面量（模块 key 除外）。

### 7.3 四个渲染器

| 渲染器 | 输入 payload | 输出 | 覆盖模块 |
| --- | --- | --- | --- |
| ChoiceRenderer | options[] | 单选项 → 提交选项值 | 逻辑推理 |
| FillRenderer | answerFormat | 输入框（可叠笔迹层）→ 提交字符串 | 口算、方程、单位 |
| BoardRenderer | grid 9×9 + fixed | 9×9 网格，键盘/点击/候选数标记 | 数独 |
| FigureRenderer | svg + answerFormat | SVG 渲染 + 笔迹层 + 输入框 | 几何 |

**统一契约**：每个渲染器对外只暴露 submit()，返回该题型约定的答案结构，由 PracticeView 统一提交。
渲染器不认识 HTTP，也不认识模块。

### 7.4 手写笔迹层 InkCanvas

- 基于 Canvas 2D + PointerEvent（同时支持鼠标、触控笔、手指）
- 笔迹以点数组存为 JSON：[{x,y,p,t}]，随作答一起提交
- 不做识别：提交后与标准答案并排显示，用户自评对照
- 支持撤销、清空、调整线宽

---

## 8. 构建与运行

### 8.1 环境（实测）

| 项 | 实测值 | 处理方式 |
| --- | --- | --- |
| Java | JDK 21 在 C:\Program Files\Java\jdk-21；JAVA_HOME 指向 JDK 8 | 不改 JAVA_HOME，用项目内 mvnw 锁定 |
| Maven | 未装独立 Maven | 生成 Maven Wrapper mvnw |
| Node | v24.20.0 / pnpm 11.22.0 | 直接用 |
| MySQL | MySQL80 服务运行中 | Phase 0 即接入 |

### 8.2 启动命令

~~~text
cd backend  && mvnw spring-boot:run
cd frontend && pnpm install && pnpm dev
~~~

### 8.3 编译目标锁定

~~~xml
<properties>
    <java.version>21</java.version>
    <maven.compiler.release>21</maven.compiler.release>
</properties>
~~~

---

## 9. 分期计划与验收判据

| 阶段 | 交付物 | 验收判据 |
| --- | --- | --- |
| P0 骨架闭环 | 工程结构、MySQL + Flyway、**用户系统（注册/登录/JWT）**、模块抽象、四则口算、Vue 骨架 + FillRenderer | ① 能注册、登录拿到 token；② 未登录访问 /api/questions 返回 401；③ 登录后能：选口算 → 调难度 → 出题 → 作答 → 看到对错和解析；④ 抓包确认响应无 answer 字段；⑤ 库里 t_user / t_question / t_answer_record 都有数据且 user_id 正确 |
| P1 数独 | 生成器、唯一解验证、技巧评级、BoardRenderer | 生成 100 题全部唯一解；EASY 题 300ms 内出；浏览器能键盘填格并判题 |
| P2 三个模块 + FSRS | 逻辑推理、方程与代数、单位换算、FSRS 复习引擎 | 复用已有渲染器，前端零改动；三模块均能完整答题判题；FSRS 验收判据见 10.3 裁定 2.6 |
| P3 几何 + 手写 | FigureRenderer、InkCanvas | 几何题 SVG 正确渲染；笔迹可撤销清空并随作答保存 |
| P4 题库与运营 | 预生成跑批、每日一题、复习统计与日历 | 池题可被取用；复习统计有数据；复习日历按到期日着色正确 |
| P5 公网化 | 注册登录 JWT、Docker Compose、Nginx | 未登录访问被拦截；一条命令起整套 |

顺序原则：P0 验证架构，P1 验证算法，P2 验证扩展性（前端零改动是硬指标），P3 验证交互，P4 补功能，P5 上公网。

---

## 10. 决策记录与风险

### 10.1 已确认决策

| # | 决策 | 理由 |
| --- | --- | --- |
| D1 | 按题型渲染，不按模块建页面 | 新增模块不改前端 |
| D2 | 每个模块自己声明难度轴 | 需求要求；通过 meta 下发兼顾扩展性 |
| D3 | 答案服务端留存，判题在服务端 | 公网部署前提 |
| D4 | 手写只做笔迹 + 自评，不做识别 | 数学手写识别无廉价开源方案，会吃掉一半工期 |
| D5 | 简单题实时生成，难题走题库池 | 兼顾延迟与算法成本 |
| D6 | 公网上线放最后 | 先在本地验证全部模块 |
| D7 | 数据库直接用 MySQL 8，不走 H2 | 已确认；Flyway 管理表结构，JPA ddl-auto=validate |
| D8 | 题目有效期：实时题 24h，池题永久 | 见 10.3 裁定 1 |
| D9 | 错题本改为 FSRS-5 间隔重复，按知识点调度 | 见 10.3 裁定 2（2026-11-02 修订，原定「只做计数」被否决） |
| D10 | 几何 SVG 服务端生成 | 见 10.3 裁定 3 |
| D11 | 用户系统从 P5 提前到 P0 | 主内容依赖 user_id，先有账号才能有做题记录 |
| D12 | 不物理删除用户，用 enabled 禁用 | 删用户会让做题记录和复习卡断链 |

### 10.2 风险清单

| 风险 | 影响 | 应对 |
| --- | --- | --- |
| 数独 EXPERT 生成超时 | 体验差 | 难题走预生成池；生成加超时熔断，超时降级一档 |
| 分数输入格式多样导致误判 | 答对判错 | 统一解析为 Fraction，支持三种格式；单测覆盖 |
| 难度轴设计不当，后期加模块要改前端 | 架构失效 | P2 强制检查：三个新模块接入时前端零改动 |
| 手写笔迹在移动端 Safari 兼容问题 | P3 阻塞 | PointerEvent + touch-action 双保险，早测 |
| JAVA_HOME 冲突 | 编译失败或污染苍穹环境 | mvnw 内锁定 JDK 21，不碰系统变量 |
| MySQL 时区与字符集 | 时间错乱或中文乱码 | 连接串固定 serverTimezone=Asia/Shanghai，表用 utf8mb4 |
| knowledge_key 因 JSON 键序不同而分裂 | 同一知识点多张卡，间隔调度混乱 | 生成 key 前先按键排序再哈希；单测覆盖键序无关性 |
| 复习时生成的题难度漂移 | 复习失去针对性 | generate 必须原样使用卡片存的 difficulty，不允许模块内部再随机 |
| FSRS 参数异常导致间隔爆炸 | 排出几年后复习 | 间隔护栏：最短 1 天、最长 365 天 |

### 10.3 三项设计裁定（已定稿）

以下三条原为待确认项，现按「先能跑、后能改」的原则定稿。

#### 裁定 1 · 题目有效期：**24 小时，且按来源区分**

| 题目来源 | 有效期 | 理由 |
| --- | --- | --- |
| GENERATED（实时生成） | 24 小时 | 题目只服务于「本次作答」，过期即清；不清理会让表无限膨胀 |
| POOL（预生成池） | 永久 | 池题是资产，同一道题可能被反复抽取，不能过期 |

**实现方式**：`expires_at` 对池题写 NULL，对实时题写 now()+24h。清理用 Spring `@Scheduled` 每天凌晨跑一次：
`DELETE FROM t_question WHERE source='GENERATED' AND expires_at < NOW() LIMIT 5000`（分批删，避免长事务锁表）。

**为什么不是更短**：用户可能出题后去接杯水、回来再做。15 分钟太短，会频繁遇到「题目已过期」。
**为什么不是更长**：超过一天，表里堆积的都是没人做的废题，清理成本反而更高。

#### 裁定 2 · 错题本：**FSRS-5 间隔重复，按知识点调度**（2026-11-02 修订）

> 本节最初裁定为「只做计数、不做间隔重复」，用户否决，改为 FSRS。以下为新方案。

##### 2.1 调度单元：知识点，不是原题

**复习卡（ReviewCard）的粒度 = 模块 + 难度配置**，不是某一道具体的题。

例：用户在「四则口算 / 数值范围 100 / 2 个运算符」这一档做错 → 生成一张复习卡。
到期复习时，**用同一份难度配置重新生成一道新题**，而不是重做原题。

**为什么这样定**：

1. **防背答案**：同一道题做第二遍，答案是记下来的，不是算出来的。重新生成才能验证真实掌握度。
2. **正好用上出题机的核心能力**：出题机最擅长的就是「同规格无限出题」，这是它相对通用错题本工具的结构性优势。
3. **卡片数量可控**：六个模块 × 每个模块几档难度，总卡片数是有限的（几十张），不会像原题粒度那样无限增长。

**原题仍可查看**：错题明细保存在 t_answer_record，点开复习卡能看到「这一档错过哪几道原题」。

##### 2.2 算法：FSRS-5，使用官方默认参数

FSRS 的核心状态（每张卡三个数）：

| 状态 | 含义 | 范围 |
| --- | --- | --- |
| stability (S) | 记忆稳定度，可理解为「遗忘到 90% 所需天数」 | > 0 |
| difficulty (D) | 该知识点对你的难度 | 1–10 |
| reps / lapses | 累计复习次数 / 遗忘次数 | >= 0 |

**评分四档**（沿用 Anki 惯例）：

| 评分 | 含义 | 触发方式 |
| --- | --- | --- |
| Again | 完全不会 / 答错 | 答错自动记 Again |
| Hard | 答对但很吃力 | 答对且用时 > 该卡历史 P75 |
| Good | 正常答对 | 答对且用时正常 |
| Easy | 秒答 | 答对且用时 < 该卡历史 P25 |

**关键实现点**：

1. 用 FSRS-5 官方默认的 19 个参数（`w[0..18]`），不做训练。理由：训练需要每人上千条复习记录，冷启动阶段无从谈起；默认参数已被验证优于 SM-2。
2. 时间单位统一用**天**，内部用 `double`，展示时四舍五入。
3. **desired retention 默认 0.9**，即「目标记住概率 90%」。可做成用户设置，但默认值不动。
4. 计算出的间隔加两道护栏：最短 1 天、最长 365 天。防止参数异常时排出「3 年后复习」这种荒谬结果。

**为什么是 FSRS 而不是 SM-2**：SM-2 的间隔完全由「难度因子 × 上次间隔」线性推导，无法表达「这次答对了，但其实是蒙的」这类情况。FSRS 用 stability + difficulty 双变量建模，且对「按知识点重新生成新题」这种场景更稳——因为每次题目难度有波动，FSRS 的 D 值能吸收这种波动，SM-2 不能。

##### 2.3 数据模型

~~~sql
CREATE TABLE t_review_card (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT       NOT NULL,
    module_key     VARCHAR(32)  NOT NULL,
    difficulty     JSON         NOT NULL,   -- 该知识点对应的难度配置
    knowledge_key  VARCHAR(128) NOT NULL,   -- module_key + 难度配置的稳定指纹
    stability      DOUBLE       NOT NULL DEFAULT 0,
    difficulty_fsrs DOUBLE      NOT NULL DEFAULT 5,
    reps           INT          NOT NULL DEFAULT 0,
    lapses         INT          NOT NULL DEFAULT 0,
    state          VARCHAR(16)  NOT NULL,   -- NEW / LEARNING / REVIEW / RELEARNING
    due_at         DATETIME     NOT NULL,
    last_review_at DATETIME,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_card_user_knowledge (user_id, knowledge_key),
    INDEX idx_card_due (user_id, due_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE t_review_log (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    card_id       BIGINT      NOT NULL,
    rating        TINYINT     NOT NULL,   -- 1=Again 2=Hard 3=Good 4=Easy
    s_before      DOUBLE      NOT NULL,
    d_before      DOUBLE      NOT NULL,
    s_after       DOUBLE      NOT NULL,
    d_after       DOUBLE      NOT NULL,
    interval_days DOUBLE      NOT NULL,
    reviewed_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_log_card (card_id, reviewed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
~~~

**knowledge_key 生成规则**：`module_key + ":" + sha256(难度配置 JSON 按键排序后)`。
排序是为了让 `{a:1,b:2}` 和 `{b:2,a:1}` 得到同一个 key，否则同一档难度会分裂成多张卡。

**t_review_log 的作用**：记录每次复习前后 S/D 的变化。这是日后想训练个性化 FSRS 参数时的唯一数据来源，现在不训练，但必须先存。

##### 2.4 与出题链路的衔接

~~~text
用户作答
   ↓
判题（服务端）
   ↓
答错 ──► 找到或创建 t_review_card（按 knowledge_key）
         → 按 FSRS 计算新状态，rating = Again
         → 写 t_review_log
   ↓
答对 ──► 如果该知识点已有卡片，按用时映射 Hard/Good/Easy 更新
         如果没有卡片，不创建（没做错过就不进复习队列）

到期复习
   ↓
GET /api/review/due  → 返回到期卡片列表
   ↓
用户点某张卡 → 用卡里的难度配置调 generate → 生成新题 → 正常作答判题
   ↓
按结果再次更新 FSRS 状态
~~~

**关键决策**：只有「答错」才创建卡片。答对的题不进复习队列——否则队列会被大量已掌握的题淹没。

##### 2.5 分期影响

复习功能从 P4 提前到 **P2**，理由：FSRS 的核心逻辑（状态机 + 间隔计算）必须尽早验证，等模块全做完再回来加，会和判题链路纠缠。

| 阶段 | 复习相关内容 |
| --- | --- |
| P0 | 不涉及 |
| P1 | 不涉及 |
| P2 | FSRS 算法 + t_review_card/t_review_log + 答错建卡 + 到期列表 |
| P3 | 不涉及 |
| P4 | 复习统计、复习日历、参数设置 |

##### 2.6 验收判据

1. 同一知识点连续答对 3 次，间隔应递增（1 天 → 3 天 → 7 天量级）。
2. 答错后，该卡 stability 应下降、due_at 应回到次日。
3. 相同难度配置、键顺序不同的 JSON，生成同一个 knowledge_key。
4. 复习时生成的题目，难度配置必须与卡片一致（抓包比对）。
5. FSRS 单元测试覆盖：新卡首次评分、复习卡四档评分、lapses 累加、间隔上下限护栏。

#### 裁定 3 · 几何 SVG：**服务端生成**

**理由**：

1. **判题一致性**：图形参数（边长、角度）必须和答案严格对应。服务端生成意味着「参数 → 图形」和「参数 → 答案」出自同一份代码，不会出现图画的是 3cm 而答案按 4cm 算。
2. **可复现**：同一道题重新打开，图形必须完全一样。服务端把 SVG 存进 payload，天然可复现；前端按参数画则依赖前端渲染逻辑不变。
3. **前端零改动原则**：FigureRenderer 只负责「把字符串塞进 v-html」，不关心几何。以后加别的图形模块（统计图、坐标系），只要服务端吐 SVG，前端一行不用改。

**代价与应对**：SVG 字符串会让 payload 变大（每题约 2–5 KB）。应对：几何题走池题（复用同一份 SVG），实时生成的题不存 SVG 只存参数，判题时再渲染。
