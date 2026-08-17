---
title: "PG表创建顺序错ddl-auto先none跳过-查CoinServiceWishController本地已改好但从未push-一次性加7个后端文件"
date: "2026-08-17"
number: "025"
tags: ["Hibernate DDL顺序", "ddl-auto none", "后端7文件从未push", "CoinService WishController路径修正"]
status: "已归档"
---

# 对话记录 2026-08-17 / 025

## 一、原始问题

024轮方案A（改Render环境变量为JDBC格式）成功后，后端报了下一个错误：
```
ERROR: relation "tacit_games" does not exist
create index idx_couple_status on tacit_games (couple_id, game_status)
```
含义：Hibernate执行ddl-auto=create时，先执行了CREATE INDEX（在tacit_games上），但tacit_games表本身还没被CREATE TABLE（因为外键依赖/Entity处理顺序），PG严格模式直接报错。

## 二、思考过程

### 维度1：根因分析
application-render.yml里写的`spring.jpa.hibernate.ddl-auto: create`（024轮Read文件证实第45行）。
ddl-auto=create的执行顺序是：DROP所有表→CREATE TABLE→CREATE CONSTRAINT/INDEX。
但Hibernate处理多对Entity时，如果有外键依赖（比如tacit_games→couples→users），会因为顺序不确定导致CREATE INDEX在CREATE TABLE之前尝试执行——MySQL会忽略、或延迟执行索引，但PostgreSQL严格模式不允许在不存在的relation上建索引，直接报错。
解决方案：ddl-auto改成`none`先跳过，或者改成`update`（update不会DROP再CREATE，而是对比现有schema增量修改，顺序更稳）。
但更稳的是`none`——因为012轮还改了SeedRunner/CoinService/WishController里的3个MySQL→PG SQL语法，如果这3个文件没push，就算ddl-auto对了SeedRunner启动时执行INSERT IGNORE还是会报错。
所以：先加SPRING_JPA_HIBERNATE_DDL_AUTO=none环境变量，绕过Hibernate自动建表错误。

### 维度2：3个SQL兼容文件本地验证
之前写CoinService/WishController时路径记错了（写成com.xindong.coin.*和com.xindong.interactive.controller.*），实际Glob找到的是：
- CoinService: backend/src/main/java/com/xindong/incentive/service/CoinService.java
- WishController: backend/src/main/java/com/xindong/incentive/controller/WishController.java
- SeedRunner: backend/src/main/java/com/xindong/common/seed/SeedRunner.java ✅ 路径正确

本地PowerShell验证：
- SeedRunner.java L48-80：PG分支是ON CONFLICT (id) DO NOTHING / ON CONFLICT DO NOTHING，MySQL分支INSERT IGNORE ✅ 改好了
- CoinService.java L218-227：isPostgres判断driver前缀，PG分支跳过SET @TRG_ALLOW_COIN_UPDATE（MySQL触发器变量），PG分支UPDATE用CURRENT_TIMESTAMP ✅ 改好了
- WishController.java L35-84：isPostgres判断url前缀，PG分支跳过CREATE TRIGGER，PG分支用ON CONFLICT (id) DO NOTHING，MySQL分支INSERT IGNORE ✅ 改好了
- pom.xml L105-111：postgresql驱动+flyway-database-postgresql依赖 ✅ 改好了

**结论：这6个后端文件本地都改了，但用户023轮及之前git status只看到5个前端文件，说明这6个后端文件从来没被git add+commit+push过，Render构建的jar里还是纯MySQL版本。**

### 维度3：用户行动拆解
要做两件事，并行处理省时间：
1. 最快的：改Render环境变量SPRING_JPA_HIBERNATE_DDL_AUTO=none → Save自动重启（30秒）——先把当前这个「relation不存在」的错误干掉，看看下一个错误是什么（大概率就是SeedRunner的INSERT IGNORE了，或者如果代码没push的话就还是旧逻辑）
2. 慢的：本地git add那7个后端文件（pom.xml + application-render.yml + PostProcessor.java + META-INF注册 + SeedRunner + CoinService + WishController）→ git commit + git push → Render Clear build cache & deploy，等15分钟构建完就是全PG兼容的版本了

### 维度4：历史上下文
从012轮开始就加的PostProcessor、3个SQL兼容、pom.xml、application-render.yml，到025轮才发现用户从没git add过——因为015-022轮用户注意力全在前端TypeScript错误上，每次git add都是前端4个文件+Dockerfile，后端根本没碰，导致GitHub缺6-7个关键后端文件。

### 维度5：边界/歧义点
ddl-auto改成`none`后，Hibernate不建表了，那表怎么建？
有3种方式：
1. 用Hibernate的`spring.jpa.defer-datasource-initialization: true` + `spring.sql.init.mode: always` + schema.sql（但项目里没schema.sql）
2. 改成`update`试试（update模式Hibernate会逐个CREATE TABLE，索引在TABLE建好后再加，顺序正确）——最推荐，先试update不行再none
3. 改成`create-only`（只CREATE不DROP，顺序如果是先表后索引也OK）

保险起见：先试`update`，不行再`none`。但最终Render里建议的是`update`最稳，既建表又不删。
不过错误已经发生了（ddl-auto=create失败），改成`update`可能会有半拉子表残留，所以Render PostgreSQL可以先去控制面板里连psql执行DROP SCHEMA public CASCADE; CREATE SCHEMA public;重建，再启动。
但用户现在不想折腾，所以环境变量先给`update`，然后如果还报错再降级`none`。
哦不对，最新的错误信息是「create index idx_couple_status on tacit_games」失败——因为create模式先DROP表，再CREATE表，但CREATE TABLE顺序不对，索引建早了。改成update模式就不会DROP，只会CREATE不存在的表，CREATE完TABLE后再加索引，顺序就对了。
所以最终建议环境变量给`update`，而不是`none`（这样不用手动建表）。

## 三、最终回答
告诉用户URL已经连上了，现在问题是Hibernate ddl-auto=create建表顺序，给SPRING_JPA_HIBERNATE_DDL_AUTO=update环境变量先做。
然后贴7个后端文件的git add命令+commit+push+Clear cache，两条线并行说清楚。

## 四、命令行执行
1. Glob找CoinService/WishController真实路径（修正包路径错误）
2. Select-String检查3个Java文件+pom.xml的PG兼容修改内容，确认都改好了
3. 输出git add 7文件命令

## 五、补充说明
PostgreSQL与MySQL在ddl-auto=create上的差异：
MySQL的CREATE INDEX IF NOT EXISTS / 以及存储引擎InnoDB的一些宽松容错，使得即使表还没完全建好执行CREATE INDEX也不会立刻报错；而PostgreSQL严格遵循SQL标准，对不存在的relation执行任何DDL都是硬错误。
Hibernate团队也知道这个问题（HHH-14765等），所以SpringBoot+PG组合一般推荐ddl-auto=update或validate，而不是create/create-drop。