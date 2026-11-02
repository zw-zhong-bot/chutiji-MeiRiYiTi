# 出题机 · 教学课程

> 目标：把 `W:\个人项目\dome` 这个项目的**架构与全部技术栈**讲透。
> 验收标准只有一条：**你能独立新增一个题型模块，从后端 SPI 到前端渲染器端到端跑通。**

## 0. 这份课的约定

| 项 | 约定 |
| --- | --- |
| 开讲时间 | P4 剩余部分（题库跑批、每日一题）交付后。课纲先出，不占开发时间。 |
| 落地位置 | `course/`。`docs/`、`frontend/`、`AGENTS.md`、`README.md` 的禁改约束不变。 |
| 前端范围 | 完整 Vue 体系（响应式、组件通信、路由、Pinia、编译/构建），**不含 HTML/CSS 基础**。 |
| 每章结构 | ① 一张知识地图 ② 一个可运行小节 ③ 配套测试题（我批改） ④ 对照源码的「回去看这几行」。 |
| 章节粒度 | 一章 = 一个能独立跑通的技术主题；知识点一律标到**文件:行号**。 |
| 讲解顺序原则 | 每讲只引入**一类新的失败模式**，而不是按代码行数排。 |

## 1. 知识地图（全景）

```text
                              ┌──────────────────────────┐
                              │  Vue 3 + Vite 8 前端      │
                              │  5173                     │
                              └────────────┬─────────────┘
                                           │ /api（dev 靠 Vite proxy）
                              ┌────────────▼─────────────┐
                              │  Spring Boot 3.5.4        │
                              │  Java 21   8080           │
                              │  ┌────────────────────┐   │
                              │  │ AuthInterceptor    │   │  ← ThreadLocal 身份
                              │  │ JwtService         │   │
                              │  ├────────────────────┤   │
                              │  │ Controller（薄）    │   │
                              │  │ Service            │   │
                              │  │ ModuleRegistry ★   │   │  ← 六个模块的收口点
                              │  │ QuestionModule SPI │   │
                              │  └────────────────────┘   │
                              └────────────┬─────────────┘
                                           │ JPA（ddl-auto=validate）
                              ┌────────────▼─────────────┐
                              │  MySQL 8                 │
                              │  表结构归 Flyway 管       │
                              │  V1__init / V2__review   │
                              └──────────────────────────┘
```

三条主轴，全课围绕它们展开：

1. **模块契约轴** — `QuestionModule` → `ModuleRegistry` → `/api/modules` → `DifficultyPanel` + `QuestionRenderer`。这是「新增模块不改前端」的全部秘密。
2. **数据边界轴** — `payload` 下发 / `answer` 留存 → `GradeService` 判题。前端永远拿不到答案。
3. **表结构治理轴** — Flyway 建表、JPA 只做 `validate` 断言。谁改库谁写迁移。

## 2. 课程章节

### 第一部分 · 地基（后端）

| 章 | 主题 | 核心知识点 | 源码锚点 |
| --- | --- | --- | --- |
| 1 | 工程与构建 | Maven 父子、`maven.compiler.release` 才是真正的 JDK 开关、mvnw 进程内锁 JDK 21 | `backend/pom.xml:10,21-22`、`backend/mvnw.cmd:15-22` |
| 2 | Spring Boot 启动与装配 | `@SpringBootApplication`、`@EnableScheduling`、构造器注入、单例 Bean 与 `ThreadLocal<Random>` | `ChutijiApplication.java:6-8`、`SudokuModule.java:53` |
| 3 | 统一响应与错误模型 | `ApiResult` record、`BizException`、业务失败走 200+code / 兜底才 500 | `ApiResult.java:6`、`GlobalExceptionHandler.java:19-22,37-42` ⚠ |
| 4 | 用户系统与 JWT | record DTO、BCrypt、jjwt 三件套的 runtime 作用域陷阱 | `AuthDtos.java:14-41`、`pom.xml:61-76` ⚠ |
| 5 | 鉴权链路 | `HandlerInterceptor` vs Filter、白名单、ThreadLocal 写入与清理、手写 401 包络 | `AuthInterceptor.java:27-54` ⚠、`WebConfig.java:26-33` |
| 6 | JPA 与实体设计 | `validate` 语义、`insertable=false` 与 DB 生成时间戳、`@JsonIgnore` vs 手工白名单 DTO | `UserEntity.java:38-42`、`AuthService.java:82-84` |
| 7 | Flyway 与表结构治理 | 版本化迁移、已发布脚本不改、`baseline-on-migrate` 静默跳过 V1 的坑 | `V1__init.sql`、`V2__review.sql`、`application.yml:29-32` ⚠ |
| 8 | 配置逐项解读 | 连接串五参数、`preferred_boolean_jdbc_type`（bit vs tinyint）、两层时区、`non_null` | `application.yml:13-39` ⚠ |
| 9 | 定时任务与批处理 | 六段式 cron、分批 + 上限防死循环、事务边界放在「一批」上 | `QuestionCleanupJob.java:22-56`、`ExpiredQuestionCleaner.java:8-26` |
| 10 | 测试策略 | 为什么不用 H2、`@Transactional` 回滚、`flush+clear` 读库生成值、手写替身优于 Mockito | `RecordControllerTest.java:33,62-77` |

### 第二部分 · 模块契约（全课脊柱）

| 章 | 主题 | 核心知识点 | 源码锚点 |
| --- | --- | --- | --- |
| 11 | 模块 SPI | 四个方法的契约含义；`GeneratedQuestion` 用类型形状把 post 答案与 payload 分开 | `QuestionModule.java:6-17`、`GeneratedQuestion.java:8` |
| 12 | 注册表与收集 | 构造器注入 `List<QuestionModule>`、key 冲突启动期即炸、`metas()` 支撑 `/api/modules` | `ModuleRegistry.java:20-41` |
| 13 | 难度轴系统 | 三 factories 压成一行、`AxisKind` 是渲染指令、**轴声明必须与实现对齐** | `DifficultyAxis.java:19-27`、`LogicModule.java:32` vs `LogicGenerator.java:55` ⚠ |
| 14 | 判题链路 | `payload` / `answer` 双列、`load()` 只在 `GradeService` 被调、判完才给 correctAnswer | `QuestionEntity.java:31-37`、`GradeService.java:42-46` |

### 第三部分 · 六个题型算法（由易到难）

排课依据：每章引入一类新的失败模式 —— unit 会算错、geometry 会画错、arithmetic 会溢出、algebra 会判错、logic 会漏解、sudoku 会超时。

| 章 | 模块 | 引入的新问题 | 核心知识点 | 源码锚点 |
| --- | --- | --- | --- | --- |
| 15 | **unit** 单位换算 | 数值精度 | `UnitDef` 一个 record 建完整体系、BigDecimal 换算、`isNice` 定义、有向对与等值单位筛除 | `UnitFamily.java:6-43`、`UnitModule.java:162-191` ⚠ |
| 16 | **geometry** 几何 | 呈现与答案的一致性 | 参数即真相源、`Formula` 十条路径、`SvgCanvas` 包围盒反推 viewBox、「求什么画什么」纪律、`esc()` 与 v-html 信任边界 | `SvgCanvas.java:22-166`、`GeometryGenerator.java:382-396`、`GeometryModule.java:136` ⚠ |
| 17 | **arithmetic** 四则 | 溢出与解析 | `sealed interface` 表达式树、Fraction 三条不变量、`multiplyExact` 防溢出、带分数解析顺序坑、括号渲染优先级 | `Expr.java:4-57`、`Fraction.java:22-44`、`AnswerParser.java:32-97` ⚠ |
| 18 | **algebra** 代数 | 判对错的策略 | 化归成多项式、代回验证（残差 + 相对容差）、先定根再反推系数、`SolutionParser` 的 find vs matches | `Polynomial.java:12-93`、`AlgebraModule.java:237-251`、`AlgebraGenerator.java:31` ⚠ |
| 19 | **logic** 逻辑 | 搜索与剪枝正确性 | 六种 Condition 纯函数、`stillPossible` 剪枝的正确性条件、countUpTo 限额剪枝、先定答案再凑条件 | `Condition.java:7-84`、`PermutationSolver.java:34-99` ⚠ |
| 20 | **sudoku** 数独 | 综合收口 | 三组 9 位掩码、MRV、count-to-limit、`& -mask` 取最低位、技巧阶梯评级、时间预算与静默降级 | `FullGridGenerator.java:33-62`、`SolutionCounter.java:50-79`、`TechniqueSolver.java:22-165`、`SudokuGenerator.java:22-96` ⚠ |

### 第四部分 · 复习引擎

| 章 | 主题 | 核心知识点 | 源码锚点 |
| --- | --- | --- | --- |
| 21 | FSRS-5 调度 | 卡片粒度是「知识点」不是「题」、`knowledge_key` 的键序无关性、S/D 状态机、间隔护栏（1~365 天） | `ReviewCardEntity.java:12,28-38`、`FsrsScheduler.java`、`KnowledgeKey.java` |
| 22 | 复习统计与口径 | 统计口径怎么定、按用户过滤靠 join、`t_review_log` 刻意不存 user_id | `ReviewLogRepository.java:14-25`、`ReviewService.java` |

### 第五部分 · 前端（完整 Vue 体系，不含 HTML/CSS）

| 章 | 主题 | 核心知识点 | 源码锚点 |
| --- | --- | --- | --- |
| 23 | Vue 响应式基础 | `ref`/`computed`/`watch`/`reactive`/`shallowRef`；为什么大数组用 shallowRef、为什么计时器不用 ref | `InkCanvas.vue:29,35-37`、`PracticeView.vue:21` |
| 24 | 组件通信三件套 | `defineProps`/`defineEmits`/`defineExpose`；门面模式 `collectAnswer`/`collectInk` | `QuestionRenderer.vue:13-35`、`NavBar.vue` vs `ModulePicker.vue` |
| 25 | 动态难度面板 | 三 kind 驱动三控件、`reactive` 字典与 `delete`、`v-model` 手工展开 | `DifficultyPanel.vue:15-54`、`AxisRange.vue:31` ⚠ |
| 26 | 路由与守卫 | `createWebHistory` 的生产代价、`meta.public` 白名单、守卫真相源是 localStorage、`pathMatch` 兜底 | `router/index.ts:5-37` ⚠ |
| 27 | Pinia 状态 | Setup Store、必须 return 才对外、凭证持久但身份不持久、两个真相源的接缝 | `stores/auth.ts:8-40` ⚠ |
| 28 | 网络层与错误模型 | 自写 90 行 fetch 替掉 axios、`ApiError` 继承、错误码空间 = 后端 ∪ {-1}、包络 `code===0`、类型断言不是校验 | `client.ts:3-93` ⚠ |
| 29 | TypeScript 类型手法 | 字面量联合替 enum（`erasableSyntaxOnly` 强制）、可辨识联合、`unknown` 收窄、`InstanceType<typeof X>` vs 鸭子类型 | `types.ts:3-66`、`tsconfig.app.json:9-11` |
| 30 | 构建与工程配置 | `vue-tsc -b && vite build` 两段式、project references 拆 app/node 的真正理由、`verbatimModuleSyntax` | `package.json:8`、`tsconfig.node.json:6-12` |
| 31 | Canvas 与 PointerEvent | DPR 缩放、坐标取 CSS 像素、pointer capture、`getCoalescedEvents`、`touch-action` 掉了画不出线 | `InkCanvas.vue:42-171,271-272` ⚠ |
| 32 | 最重的一页：ReviewView | 一页两相位、并发拉三个接口、`finally` 里 await 的前提、不引图表库的自制柱状图 | `ReviewView.vue:16-134,278-291` |

### 第六部分 · 收束与验收

| 章 | 主题 | 交付物 |
| --- | --- | --- |
| 33 | 「新增模块不改前端」全链路复盘 | 反证：`frontend/src/` 里模块 key 的带引号字面量 **0 命中**（实测；搜裸词 `unit` 会命中 2 处 `payload.unit` 这个字段名，课堂现像演示「证明要精确」）；说清「零改动接入**新模块**」≠「零改动接入**新题型**」 |
| 34 | **大作业：独立新增一个题型模块** | 后端实现 `QuestionModule` + 新题型 + 前端加一个渲染器；我会按你的验收标准批改 |

## 3. 必须讲透的「坑」清单（课程最强部分）

这些是核实的真实偏差，不是理论风险。每一条都会在对应章节现场演示。

| # | 坑 | 证据 |
| --- | --- | --- |
| 1 | `2007` 错误码一处两义：登录接口是「用户不存在」，出题是「生成失败」，前端按码分支会误判 | `AuthService.java:79` vs `LogicGenerator.java:74` |
| 2 | `poolBased` 是死字段：`ModuleMeta` 声明、前端类型也声明，全仓**零读取方** | `ModuleMeta.java:12`（全仓唯一出现） |
| 3 | UI 允许选 `conditionCount=2`，生成器强制抬到 3，用户选了也白选 | `LogicModule.java:32` vs `LogicGenerator.java:55` |
| 4 | `degree=QUADRATIC` + 2 未知数时 `degree` 被忽略，得到的是二元一次方程组 | `AlgebraGenerator.java:31` |
| 5 | `LogicGenerator` 类注释说「必要时继续加条件」，方法注释说「绝不加、整组重来」，两处互相矛盾 | `LogicGenerator.java:13-17` vs `:49-53` |
| 6 | `SvgCanvas.esc()` 注释声称转义控制字符，实现里没有该分支 | `SvgCanvas.java:28` vs `:36-43` |
| 7 | `ddl-auto=validate` **不校验索引与唯一约束**，`unique=true` 在实体上是装饰品 | `UserEntity.java:20` vs `V1__init.sql:14` |
| 8 | `baseline-on-migrate=true` 对「手工建过表」的库会**静默跳过 V1**，错误推迟到 validate 才炸 | `application.yml:31` |
| 9 | 401 响应体是手写 JSON 字符串，绕过 `ApiResult` 与 Jackson，改包络就得手工同步两处 | `AuthInterceptor.java:48-54` |
| 10 | `afterCompletion` 只在 `preHandle` 返回 true 后被调；漏清 ThreadLocal 在 Tomcat 线程复用下等于越权 | `AuthInterceptor.java:39,44-46` |
| 11 | jjwt 的 `impl`/`jackson` 是 runtime 作用域，编译永远通过，删掉是**运行时**才炸 | `pom.xml:61-76` |
| 12 | `meta.public` 没有 `RouteMeta` 类型增强，拼错成 `meta.pubilc` 照样编译通过，守卫静默失效 | `router/index.ts:12,30` |
| 13 | 401 链路只清 localStorage 不调 `logout()`，`auth.isLoggedIn` 仍为真，导航栏会留在登录页（据代码推演，待实跑验证） | `client.ts:41` vs `stores/auth.ts:34-38` |

## 4. 每章的固定交付格式

```text
NN-主题名/
  README.md      知识地图 + 本节要解决的问题 + 回去看这几行（文件:行号）
  demo.md        可运行小节：命令、操作步骤、预期输出、出错怎么查
  exercises.md   3~5 道测试题（含 1 道「找出并解释这个坑」）
  answers.md     我先不给，你交作业后我批改并回填
```

## 5. 待确认

- P4 剩余部分（题库跑批、每日一题）交付后会新增代码，第 2、12、20 章需要复核后再定稿。
- 第 33 章的 grep 反证已预跑（2026-11-02）：带引号模块 key 0 命中，裸词 `unit` 命中 2 处。前端冻结后需复核一次。
