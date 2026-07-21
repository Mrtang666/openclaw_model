-- OpenClaw 本地开发数据库初始化脚本
-- 使用方式：
-- 1. 先登录 MySQL，例如：mysql -u root -p
-- 2. 再执行本文件，例如：source docs/sql/create_database.sql
-- 3. 只需要创建空数据库，具体业务表由 Flyway 在项目启动时自动创建和升级。

CREATE DATABASE IF NOT EXISTS openclaw
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

-- 可选：如果合作者需要单独的测试库，可以同时创建 openclaw_test。
-- 项目的测试配置会连接 openclaw_test，并通过 Flyway 自动创建测试表。
CREATE DATABASE IF NOT EXISTS openclaw_test
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
