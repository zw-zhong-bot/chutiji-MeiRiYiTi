# 出题机 · IDEA 启动与调试文档

> 面向：用 IntelliJ IDEA 打开本项目、断点调试前后端。
> 版本事实：2026-11-02 在本机实测。IDEA 装在 `D:\App\IntelliJ IDEA 2025.3.4\bin\idea64.exe`（目录名与版本号不一致，是 Toolbox 原地升级留下的，不影响使用）。

---

## 0. 开工前必须知道的三个前提

这三条不满足，后面每一步都会卡住，而且报错信息都不指向真正的原因。

| # | 前提 | 为什么 |
| --- | --- | --- |
| 1 | **JDK 21 用于本项目** | 系统 `JAVA_HOME` 指向 `E:\Java\java1.8`（你的苍穹环境在用），IDEA 很可能会默认塞一个 JDK 8 进来。Spring Boot 3 在 JDK 8 上编译直接失败。**绝对不要去改系统 JAVA_HOME**，只在 IDEA 里为这个项目指定 SDK。 |
| 2 | **MySQL 的 `chutiji` 库已存在** | 后端是 `ddl-auto: validate` + Flyway。库不存在时应用起不来，但报错长得像 JPA 映射问题，容易误判。 |
| 3 | **3306 端口可连** | 本机 MySQL80 服务默认随开机启动。如果 3306 连不上，先查服务，别去改代码。 |

自查命令（PowerShell，可在任意目录跑）：

```powershell
# JDK 是否就位
& 'C:\Program Files\Java\jdk-21\bin\java.exe' -version

# MySQL 与库是否就位
& 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' `
  --host=127.0.0.1 --port=3306 --user=root --password=root `
  --execute="SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='chutiji';"
```

> 注意 mysql.exe 必须用 `--host=` 这种等号风格。写成 `-h127.0.0.1` 在 PowerShell 里会报 `ERROR 2005 Unknown MySQL server host '127'`。

---

## 1. 打开项目

**根目录 `W:\个人项目\dome` 没有 pom.xml**，它是个双模块仓库：

```text
dome/
├── backend/     ← Maven 工程，pom.xml 在这里
├── frontend/    ← Vue 3 + Vite，package.json 在这里
├── docs/
└── course/
```

所以：

1. **File → Open**，选 `W:\个人项目\dome`（根目录），点 OK。
2. 打开后右键 `backend\pom.xml` → **Add as Maven Project**。
3. 等右下角 Maven 索引跑完。

这一步之后 IDEA 左侧 Project 面板里 `backend` 会带上蓝色的 Maven 标记，`src/main/java` 变成深蓝色源码根目录。

---

## 2. 把 JDK 换成 21（最容易漏的一步）

**Project Structure**（`Ctrl+Alt+Shift+S`）：

| 位置 | 设置成 |
| --- | --- |
| Project → SDK | `21`（路径指向 `C:\Program Files\Java\jdk-21`） |
| Project → Language level | `21` |
| Modules → chutiji-backend → Dependencies → Module SDK | 同上 `21` |

如果下拉里没有 21：**Add SDK → JDK → 选 `C:\Program Files\Java\jdk-21`**。

接着 **Settings**（`Ctrl+Alt+S`）→ **Build, Execution, Deployment → Build Tools → Maven**：

| 位置 | 设置成 |
| --- | --- |
| Maven home path | `D:\App\IntelliJ IDEA 2025.3.4\plugins\maven\lib\maven3`（IDEA 自带的那个） |
| User settings file | **Override** 勾上，指到 `W:\个人项目\dome\backend\.mvn\settings.xml` |
| Local repository | **Override** 勾上，指到 `W:\个人项目\dome\backend\.m2repo` |
| Importing → JDK for importer | `21` |
| Runner → JRE | `21` |

> **为什么要 Override 本地仓库**：本机 `~/.m2` 在命令行构建时曾被拒绝访问，项目已经把本地仓库放到 `backend/.m2repo` 并配好了 `.mvn/settings.xml`。让 IDEA 也用同一份，能和你命令行 `mvnw` 的缓存共用，省一次全量下载。
>
> **注意 IDEA 不会自动读 `.mvn/settings.xml`**。Maven 只自动认 `.mvn` 下的 `maven.config` 和 `jvm.config`，`settings.xml` 必须在这里手工指。

---

## 3. 排掉两个会让搜索变吵的目录

右键这两个目录 → **Mark Directory as → Excluded**（灰色）：

- `backend\.m2repo` —— 4110 个文件，全是 jar。不排掉，`Ctrl+Shift+F` 全局搜索会翻出一堆依赖类名。
- `backend\.mvn\maven-home` —— 整个 Maven 发行版。

---

## 4. 启动后端并断点调试

### 4.1 准备数据库连接（可选但强烈建议）

IDEA 右侧 **Database** 面板 → `+` → **Data Source → MySQL**：

| 项 | 值 |
| --- | --- |
| Host / Port | `127.0.0.1` / `3306` |
| User / Password | `root` / `root` |
| Database | `chutiji` |
| URL 追加参数 | `?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false` |

连上以后可以直接在 IDEA 里看 `t_question`、`t_answer_record`、`t_review_card` 这几张表，调试时不用切 Navicat/DataGrip。

> 项目的 `.idea/dataSources.xml` 已经存在，说明你之前配过数据源，打开 Database 面板应该能直接看到。

### 4.2 建运行配置

打开 `backend\src\main\java\com\chutiji\ChutijiApplication.java`，点行号旁的绿色三角 → **Modify Run Configuration**：

| 项 | 值 |
| --- | --- |
| Name | `ChutijiApplication` |
| Main class | `com.chutiji.ChutijiApplication` |
| **Use classpath of module** | `chutiji-backend`（选错会报找不到主类） |
| **JRE** | `21`（这一步漏了就会 `UnsupportedClassVersionError`） |
| Working directory | `$PROJECT_DIR$/backend` |

**Environment variables**（可选，不填就用默认值）：

```text
DB_USERNAME=root
DB_PASSWORD=root
```

> `application.yml` 里写的是 `${DB_USERNAME:root}` 和 `${DB_PASSWORD:root}`，冒号后是缺省值。本地开发不配这两个变量也能直接跑，密码没有硬编码进代码库。

### 4.3 跑起来

点绿色三角或 `Shift+F10`。启动成功的标志：

```text
Tomcat started on port 8080 (http)
Started ChutijiApplication in X.XXX seconds
```

浏览器访问 `http://127.0.0.1:8080/api/modules`，应返回 6 个模块的 JSON（不需要登录）。

### 4.4 断点建议

调试时最值得下断点的几个位置：

| 想看什么 | 断在哪 |
| --- | --- |
| 题目怎么生成的 | `QuestionService.create()` 里 `module.generate(...)` 那一行 |
| 判题怎么比对的 | `GradeService.grade()` 里调 `module.grade(...)` 那一行 |
| 为什么答案没下发 | `QuestionDtos.QuestionView` 这个 record —— 它**结构上就没有 answer 字段**，断点看不出来，要看构造它的地方 |
| 复习卡怎么算间隔 | `FsrsScheduler.schedule()` |
| 池题 vs 实时题走了哪条 | `QuestionService.create()` 开头取池题的分支 |

---

## 5. 启动前端并调试

IDEA 是旗舰版的话可以直接用内置终端跑：

```powershell
cd W:\个人项目\dome\frontend
pnpm install   # 第一次才需要
pnpm dev
```

终端会打印：

```text
VITE v8.3.5  ready in XXX ms
➜  Local:   http://localhost:5173/
```

**注意实际监听的是 `localhost:5173`（IPv6 `::1`）**，不是 `127.0.0.1:5173`。用 `127.0.0.1` 访问会连不上，这是 Vite 的默认 host 行为，不是 bug。

### 5.1 前后端怎么连的

`frontend/vite.config.ts` 里配了 proxy：

```ts
proxy: { '/api': { target: 'http://127.0.0.1:8080', changeOrigin: true } }
```

意思是：前端所有 `/api` 请求由 Vite 转发到后端 8080。**所以后端必须跑在 8080**，改端口要同步改这里。

### 5.2 前端断点

在 IDEA 里对 `frontend/src` 下的 `.vue` / `.ts` 断点，需要：

1. 装 **JavaScript and TypeScript** 插件（旗舰版自带）；
2. Run → **Edit Configurations** → `+` → **JavaScript Debug**，URL 填 `http://localhost:5173`。

跑起来后 IDEA 的 Debugger 面板能看到 Vue 组件层级和响应式变量。

---

## 6. 跑测试

Maven 面板 → `chutiji-backend` → **Lifecycle** → 双击 `test`。

或者内置终端：

```powershell
cd W:\个人项目\dome\backend
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'   # 只影响当前这个终端窗口
.\mvnw.cmd test
```

**当前基线：170 个测试全绿。** 如果在你机器上不是这个数字，先看是不是少加了某个模块，别急着改断言。

> 测试会连真实 MySQL（`chutiji` 库），靠 `@Transactional` 回滚不落库。所以**测试期间 MySQL 必须能连**，否则会一路红。

---

## 7. 常见问题速查

| 现象 | 真实原因 | 怎么修 |
| --- | --- | --- |
| `UnsupportedClassVersionError: ... class file version 65.0` | 运行用的 JDK 是 8 | Run Configuration 的 JRE 改成 21 |
| `java: 错误: 不支持发行版本 21` | 编译用的 SDK 是 8 | Project Structure 里 SDK 和 Language level 都改成 21 |
| 启动报 `Table 'chutiji.t_xxx' doesn't exist` | Flyway 没跑或库不对 | 确认连的是 `chutiji` 库，且 `spring.flyway.enabled=true` |
| 启动报 `bit vs tinyint` 类型不匹配 | `preferred_boolean_jdbc_type` 没生效 | 这个已经在 `application.yml` 配好了；如果你改过配置文件，把它加回去 |
| `ERROR 2005 (HY000): Unknown MySQL server host '127'` | PowerShell 拆了 mysql.exe 的参数 | 用 `--host=127.0.0.1` 等号风格 |
| 前端页面一直转圈 / 接口全 404 | 后端没起，或没跑在 8080 | 先确认 `http://127.0.0.1:8080/api/modules` 能返回 |
| `127.0.0.1:5173` 打不开 | Vite 只听 `localhost`（IPv6） | 用 `http://localhost:5173` |
| `mvnw clean` 报 BUILD FAILURE 且信息含糊 | `target/*.jar` 被运行中的进程占用 | **先停掉正在跑的后端**，再 clean |
| `ReplaceFileW EIO` 写入 `.vue` 失败 | Vite dev server 正在监视该文件 | 停掉 `pnpm dev` 再改 |

---

## 8. 加一个新模块要动哪些地方

这是本项目最值得记住的一条：**加模块只需要动后端，前端一行不用改。**

```text
1. 在 backend/.../module/impl/ 下新建一个包
2. 写一个类实现 QuestionModule 接口（key / meta / generate / grade 四个方法）
3. 类名上加 @Component
```

就这三步。前端会自动多出一个模块卡片、自动按 `meta.axes` 渲染难度控件、自动按 `type` 选渲染器 —— 因为 `DifficultyPanel` 和 `QuestionRenderer` 完全由后端下发的 meta 驱动，代码里没有任何模块名或难度字段名的字面量。

唯一的例外：如果新模块的 `type` 是四种之一（FILL / CHOICE / BOARD / FIGURE），前端零改动；如果要加**第五种题型**，才需要写一个新的渲染器组件并在 `QuestionRenderer.vue` 里加一个分支。

---

## 9. 尚未完成的部分

| 项 | 状态 |
| --- | --- |
| P5 公网化（Docker Compose / Nginx 反代 / 生产配置注入） | **未做**，因暂无公网环境 |
| 端到端验收脚本 | 在 `.tmp/p4-e2e.mjs`，243 项检查，按约定未入库，仅本地保留 |

本地开发不受影响，但哪天要部署，这两块需要单独开一轮。
