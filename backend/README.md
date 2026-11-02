# 出题机后端（chutiji-backend）

Spring Boot 3.5.4 / Java 21 / MySQL 8 / Flyway / JPA(validate)

## 本机构建约束

本机系统 `JAVA_HOME` 指向 JDK 8（苍穹环境在用），**不要改它**。
`mvnw` / `mvnw.cmd` 已在脚本内部把 `JAVA_HOME` 指向 JDK 21，只作用于该次进程。
如需换 JDK 路径，设 `CHUTIJI_JDK` 环境变量覆盖。

用户目录 `~/.m2` 在本机不可写，因此本地仓库放在工程内 `.m2repo`，
Maven 发行版解压在 `.mvn/maven-home`（首次由 IntelliJ 自带 Maven 拷入）。

## 常用命令

```powershell
cd backend
mvnw.cmd clean package     # 编译 + 测试 + 打包（产物 target/chutiji-backend.jar）
mvnw.cmd test              # 只跑测试
java -jar target/chutiji-backend.jar
```

## 数据库

库名 `chutiji`，连接参数见 `src/main/resources/application.yml`。
表结构由 Flyway 独占管理（`src/main/resources/db/migration`），JPA `ddl-auto=validate` 只校验不建表。

注意：DDL 里布尔列是 `TINYINT`，`application.yml` 中用
`hibernate.type.preferred_boolean_jdbc_type: TINYINT` 把 Hibernate 的期望类型对齐，
否则 `validate` 会报 `bit vs tinyint`。

## 端点

| 方法 | 路径 | 鉴权 |
| --- | --- | --- |
| POST | /api/auth/register | 否 |
| POST | /api/auth/login | 否 |
| GET | /api/auth/me | 是 |
| PUT | /api/auth/me | 是 |
| GET | /api/modules | 否 |
| POST | /api/questions | 是 |
| POST | /api/questions/{id}/grade | 是 |

所有响应统一包络 `{ code, message, data }`；未登录返回 HTTP 401 + `{"code":2001,...}`。
出题响应**不含任何答案字段**，答案只存 `t_question.answer`，判题在服务端完成。
