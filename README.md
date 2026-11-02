# 出题机

多模块的本地出题小站：程序自己生成题目，用户自选模块和难度，做题、判题、记录进度、按知识点间隔重复复习。

## 技术栈

| 层 | 选型 |
| --- | --- |
| 后端 | Java 21 + Spring Boot 3 + Maven |
| 数据库 | MySQL 8 + Flyway |
| 鉴权 | Spring Security 风格拦截器 + JWT (HS256) |
| 前端 | Vue 3 + Vite + Pinia |

## 目录

~~~text
docs/design.md      详细设计文档（唯一权威，改设计先改这里）
backend/            Spring Boot 后端
frontend/           Vue 3 前端
~~~

## 本地启动

### 前置

- JDK 21（本项目锁定 21，不依赖系统 JAVA_HOME）
- MySQL 8，库 `chutiji` 已建（utf8mb4 / utf8mb4_0900_ai_ci）
- Node 18+ 与 pnpm

### 后端

~~~powershell
cd backend
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\mvnw.cmd spring-boot:run
~~~

数据库连接见 `backend/src/main/resources/application.yml`，密码默认读环境变量 `DB_PASSWORD`，缺省为 root。

### 前端

~~~powershell
cd frontend
pnpm install
pnpm dev
~~~

## 核心约束

1. **答案绝不下发**：出题响应里没有 answer 字段，判题只在服务端。
2. **前端不硬编码难度选项**：难度控件由 `/api/modules` 返回的 `axes` 动态渲染。
3. **新增模块不改前端**：后端实现 `QuestionModule` 接口即可，前端按 `type` 分发渲染器。
4. **表结构由 Flyway 管**：`ddl-auto` 必须是 `validate`。

## 进度

| 阶段 | 内容 | 状态 |
| --- | --- | --- |
| P0 | 骨架 + 用户系统 + 模块抽象 + 四则口算 | 进行中 |
| P1 | 数独 + BoardRenderer | 未开始 |
| P2 | 逻辑推理 / 方程 / 单位换算 + FSRS 复习 | 未开始 |
| P3 | 几何 + 手写笔迹层 | 未开始 |
| P4 | 题库跑批 + 复习统计 | 未开始 |
| P5 | 公网化：Docker + Nginx | 未开始 |

完整设计见 [docs/design.md](docs/design.md)。
