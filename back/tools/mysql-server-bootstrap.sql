-- 只需要在一台全新的电脑上，以 MySQL 管理员身份执行一次。
-- 它建立 SetHub 专用账号和数据库；业务表、基础 HR、公司、地点和岗位由后端首次启动自动创建。

SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS person_workbench
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'sethub_app'@'localhost'
    IDENTIFIED BY 'SetHubLocal@123456';

CREATE USER IF NOT EXISTS 'sethub_app'@'127.0.0.1'
    IDENTIFIED BY 'SetHubLocal@123456';

GRANT ALL PRIVILEGES ON person_workbench.* TO 'sethub_app'@'localhost';
GRANT ALL PRIVILEGES ON person_workbench.* TO 'sethub_app'@'127.0.0.1';
FLUSH PRIVILEGES;
