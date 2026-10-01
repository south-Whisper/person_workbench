package com.example.demo;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MySqlOnlyConfigurationTests {
    @Test
    void backendConfigurationUsesMySqlOnly() throws IOException {
        String dependencies = Files.readString(Path.of("pom.xml"), StandardCharsets.UTF_8);
        String application = Files.readString(Path.of("src", "main", "resources", "application.yml"));
        String local = Files.readString(Path.of("src", "main", "resources", "application-local.yml"));
        String mysql = Files.readString(Path.of("src", "main", "resources", "application-mysql.yml"));
        assertTrue(dependencies.contains("mysql-connector-j"), "后端必须包含 MySQL 驱动");
        assertTrue(application.contains("jdbc:mysql:"), "默认数据库必须是 MySQL");
        assertTrue(application.contains("createDatabaseIfNotExist=true"), "数据库不存在时必须允许自动创建");
        assertTrue(local.contains("createDatabaseIfNotExist=true"), "本地配置必须允许自动创建数据库");
        assertTrue(mysql.contains("createDatabaseIfNotExist=true"), "MySQL 配置必须允许自动创建数据库");
        assertTrue(application.contains("com.mysql.cj.jdbc.Driver"), "默认驱动必须是 MySQL 驱动");
    }

    @Test
    void newComputerBootstrapCreatesDatabaseAndDedicatedAccount() throws IOException {
        String script = Files.readString(Path.of("tools", "mysql-server-bootstrap.sql"), StandardCharsets.UTF_8);
        assertTrue(script.contains("CREATE DATABASE IF NOT EXISTS person_workbench"), "新电脑脚本必须创建数据库");
        assertTrue(script.contains("CREATE USER IF NOT EXISTS 'sethub_app'"), "新电脑脚本必须创建专用账号");
        assertTrue(script.contains("GRANT ALL PRIVILEGES ON person_workbench.*"), "专用账号必须拥有 person_workbench 数据库权限");
    }

    @Test
    void initialDataScriptProvidesMinimumBusinessData() throws IOException {
        String script = Files.readString(Path.of("src", "main", "resources", "db", "initial-data.sql"), StandardCharsets.UTF_8);
        assertTrue(script.contains("INSERT INTO company("), "首次启动脚本必须创建公司");
        assertTrue(script.contains("INSERT INTO company_location("), "首次启动脚本必须创建办公地点");
        assertTrue(script.contains("INSERT INTO `position`("), "首次启动脚本必须创建岗位");
        assertTrue(script.contains("NOT EXISTS"), "首次启动脚本必须防止重复插入");
    }
}
