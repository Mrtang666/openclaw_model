# OpenClaw 数据库初始化说明

本文档用于帮助合作者在本机创建 OpenClaw 所需的 MySQL 数据库。

当前项目使用 MySQL 保存微信端上下文记忆、工具调用日志、用户偏好、文件解析记录和生成文档记录。项目启动时会通过 Flyway 自动执行 `src/main/resources/db/migration` 目录下的版本化 SQL 脚本，因此合作者通常只需要提前创建空数据库。

## 1. 前置条件

- 本机已安装 MySQL，建议 MySQL 8.x。
- 已创建可以访问本机 MySQL 的账号，例如 `root`。
- 项目可以正常读取根目录下的 `.env` 配置文件。

## 2. 创建数据库

在项目根目录执行：

```bash
mysql -u root -p < docs/sql/create_database.sql
```

或者登录 MySQL 后手动执行：

```sql
CREATE DATABASE IF NOT EXISTS openclaw
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS openclaw_test
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

说明：

- `openclaw` 是项目运行时使用的开发数据库。
- `openclaw_test` 是测试环境数据库，避免测试过程污染正式开发数据。
- 这里只创建数据库，不手动创建业务表。

## 3. 配置 `.env`

复制 `.env.example` 为 `.env`：

```bash
copy .env.example .env
```

然后按自己的本机 MySQL 修改：

```properties
MYSQL_URL=jdbc:mysql://127.0.0.1:3306/openclaw?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
MYSQL_USERNAME=root
MYSQL_PASSWORD=你的MySQL密码
FLYWAY_ENABLED=true
```

如果你想让 JDBC 在数据库不存在时尝试自动创建数据库，可以把连接改成：

```properties
MYSQL_URL=jdbc:mysql://127.0.0.1:3306/openclaw?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&createDatabaseIfNotExist=true
```

不过更推荐先执行 `docs/sql/create_database.sql`，这样数据库初始化过程更清晰。

## 4. Flyway 自动建表

项目启动时，Flyway 会自动执行以下迁移脚本：

- `V1__create_wechat_memory_tables.sql`：创建微信用户、会话、消息、会话状态、用户偏好、摘要和工具日志表。
- `V2__create_wechat_document_tables.sql`：创建微信文件、文件分块和生成文档记录表。

不要手动反复执行这些 `V1`、`V2` 迁移脚本。它们应该交给 Flyway 管理，Flyway 会用 `flyway_schema_history` 表记录哪些版本已经执行过。

## 5. 当前表结构覆盖范围

当前 SQL 已覆盖这些功能：

- 微信用户身份：`users`
- 微信会话：`conversations`
- 最近消息和上下文：`conversation_messages`
- 会话临时状态 JSON：`conversation_states`
- 用户长期偏好：`user_preferences`
- 会话摘要：`conversation_summaries`
- 工具调用日志：`tool_execution_logs`
- 用户上传文件记录：`wechat_documents`
- 文件内容分块：`wechat_document_chunks`
- 生成文档记录：`wechat_generated_documents`

## 6. 常见问题

### 连接失败：Unknown database 'openclaw'

原因：本机还没有创建 `openclaw` 数据库。

解决：执行：

```bash
mysql -u root -p < docs/sql/create_database.sql
```

### 表不存在

原因：Flyway 没有执行，或者 `FLYWAY_ENABLED=false`。

解决：确认 `.env` 中：

```properties
FLYWAY_ENABLED=true
```

然后重新启动项目。

### 数据没有写入数据库

优先检查：

- `MYSQL_URL` 是否连接到正确数据库。
- `MYSQL_USERNAME` 和 `MYSQL_PASSWORD` 是否正确。
- 控制台是否有 MySQL 或 Flyway 报错。
- 是否误连到了 `openclaw_test`。

### 不想影响已有数据

不要删除 `openclaw` 数据库。测试时使用 `openclaw_test`，或者单独配置自己的测试数据库。
