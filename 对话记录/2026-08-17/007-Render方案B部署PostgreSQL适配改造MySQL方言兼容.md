---
title: "Render方案B部署PostgreSQL适配改造MySQL方言兼容"
date: "2026-08-17"
number: "007"
tags: ["Render", "PostgreSQL", "部署", "免费", "数据库兼容"]
status: "已归档"
---

# 对话记录 2026-08-17 / 007

## 一、原始问题

用户选择了方案B（Render + PostgreSQL数据持久化），需要将原本只支持MySQL/SQLite的项目改造为支持PostgreSQL，并给出Render上的完整部署步骤。

前置背景：
- Zeabur改版后不再有免费PaaS，必须买服务器，放弃
- 用户明确要求免费方案，选Render
- 方案A：SQLite（重启丢数据）不选；方案B：PostgreSQL托管（数据永久）选了

## 二、思考过程

### 维度1：用户意图（想解决什么）
把代码改造成能在Render上跑起来（PostgreSQL免费数据库），然后能一步步跟着部署到公网，完全免费，数据不丢。

### 维度2：用户技术水平（对应写法深浅）
SpringBoot/Vue全栈项目作者，懂Maven/Docker，能改代码，但对PostgreSQL和MySQL方言差异不熟悉，需要我把所有改造点找全、改好，不能让他自己排查SQL语法错误。

### 维度3：需求隐含约束
- 改造必须**向后兼容**：原来的MySQL/SQLite本地开发不能坏，要加分支判断，不能一刀切
- 改动越少越好：别大改架构，只改数据库方言相关的点
- Flyway的MySQL迁移脚本（V1-V11）全是MySQL语法（AUTO_INCREMENT/TINYINT/ENGINE=InnoDB/INSERT IGNORE/NOW()/DELIMITER $$触发器），不能直接在PG上跑，所以application-render.yml里必须关Flyway，用Hibernate ddl-auto=create直接建表（第一次启动全新库没问题）
- SeedRunner.java里写死了 `INSERT IGNORE`、`NOW()`，这俩PG不认，必须按数据库类型分支
- CoinService.java里写了 `SET @TRG_ALLOW_COIN_UPDATE = 1`（MySQL会话变量），PG里没有这个语法，会直接报错，必须跳过
- WishController.java /admin/reset-b7 接口里写了MySQL触发器 CREATE TRIGGER ... BEGIN ... END 语法，PG也不认，必须跳过

### 维度4：历史上下文
之前项目经过了很多轮红线修复（B2/B3/B5/B7），很多SQL语法是写死在Java代码里的，不是通过Flyway统一管理，所以必须全局扫 MySQL专属语法：`@变量`、`NOW()`、`INSERT IGNORE`、`TINYINT`、`AUTO_INCREMENT`、`DATE_FORMAT`、`CURDATE`、`DATEDIFF`、`ON UPDATE CURRENT_TIMESTAMP`、`DELIMITER`、`SIGNAL SQLSTATE '45000'`

### 维度5：边界/歧义点（主动假设/追问）
Hibernate的ddl-auto=create会不会丢数据？Render的PostgreSQL是免费托管的持久化存储，只要不删库，数据一直在。ddl-auto=create是**第一次启动时**建表，后面如果重启服务且库表已存在的话，create实际上会先drop再create？不对，SpringBoot + JPA的 ddl-auto=create 是每次启动都删表重建！哦，这个坑必须注意！

等等，用户是第一次部署，库是空的，第一次启动create没问题，但后面如果服务重启（Render冷启动重启实例），ddl-auto=create会把所有表删掉重建，数据就丢了！这太严重了！

哦不对，查一下：Hibernate的 ddl-auto 有4个值：
- none：不做任何操作（生产推荐）
- create-only：只建表，不删
- drop：只删表
- create：启动时删表→建表，关闭时删表（危险！）
- create-drop：启动时删表→建表，关闭时删表（测试用）
- validate：只校验表结构，不改动
- update：增量更新表结构（生产也不推荐，但比create好）

刚才写的application-render.yml里我写的是 `ddl-auto: create`，这会导致每次重启丢数据！必须改成 `none` 或者第一次启动用 `create` 成功后改成 `none`。或者更安全：第一次部署用 `create`（因为是空库），部署成功后立刻改成 `none` 重新部署。或者直接写 `update`？但update跟Hibernate的PG方言+实体类列定义可能有冲突。

最安全方案：第一次部署环境变量手动设 `SPRING_JPA_HIBERNATE_DDL_AUTO=create`，等启动成功、表建好了、Seed数据进去了，再去Render改环境变量设成 `SPRING_JPA_HIBERNATE_DDL_AUTO=none`，重启。这样就不会丢数据了。

这个注意事项必须写在部署步骤里，不能漏！

## 三、最终回答

### 代码改造清单（改了5处）
1. pom.xml加了 `postgresql` 驱动和 `flyway-database-postgresql` 依赖
2. 新建 `application-render.yml`：PostgreSQL数据源（用Render的DATABASE_URL），ddl-auto=create（第一次启动建表用，后面要改none），关Flyway，关Redis
3. `CoinService.java` 加了 isPostgres 判断：PG下跳过SET @TRG_ALLOW_COIN_UPDATE（PG没有MySQL触发器），NOW()改成CURRENT_TIMESTAMP
4. `WishController.java` /admin/reset-b7 加了 isPostgres 判断：PG下不建MySQL触发器，INSERT IGNORE改成ON CONFLICT DO NOTHING，NOW()改成CURRENT_TIMESTAMP
5. `SeedRunner.java` 加了 isPostgres() 辅助方法：所有INSERT IGNORE改成PG的ON CONFLICT (id) DO NOTHING，NOW()改成CURRENT_TIMESTAMP，红线数据初始化SQL全部分支

### Render部署步骤（3大步）
1. 先建PostgreSQL（Free，Singapore区），复制Internal Database URL
2. 再建Web Service（Docker，Free，Singapore区），加6个环境变量（DATABASE_URL、SPRING_PROFILES_ACTIVE=render,embed-frontend、JWT_SECRET、LETTER_AES_KEY、SMS_SUPER_CODE、JAVA_OPTS=-Xmx512m）
3. 等第一次启动成功→确认数据都在→立刻去改环境变量加 `SPRING_JPA_HIBERNATE_DDL_AUTO=none`→重启，防止后面重启丢数据

### Render免费实例注意事项
冷启动15分钟休眠（第一次30-60秒）；750小时/月足够一直开；PostgreSQL免费永久。

## 四、命令行执行

本地没有执行命令，纯代码改动：
1. Edit pom.xml 加PostgreSQL依赖（+5行）
2. Write application-render.yml（新建）
3. Edit CoinService.java：加isPostgres分支（SET @变量+NOW()）
4. Edit WishController.java：/admin/reset-b7 全改PG兼容
5. Edit SeedRunner.java：加isPostgres()方法，seedRedlineData和seedChecklist全分支适配

## 五、补充说明

SpringBoot多profile激活技巧：Render环境变量设 SPRING_PROFILES_ACTIVE=render,embed-frontend，等于同时激活两个profile：
- render：接管数据源/JPA配置（PostgreSQL）
- embed-frontend：接管WebMvc静态资源（Vue3 dist/），保证前端能访问
Spring会自动合并两个profile的配置，不用写重复项，这点很重要。