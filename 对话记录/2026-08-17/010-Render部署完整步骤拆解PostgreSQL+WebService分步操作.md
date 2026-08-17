---
title: "Render部署完整步骤拆解PostgreSQL+WebService分步操作"
date: "2026-08-17"
number: "010"
tags: ["Render", "部署教程", "PostgreSQL", "免费部署", "公网"]
status: "已归档"
---

# 对话记录 2026-08-17 / 010

## 一、原始问题

用户问：「怎么部署到render」，要完整的、一步步能跟着做的Render部署教程。
前面已经改好了PostgreSQL兼容代码（007轮），现在用户要具体操作步骤。

## 二、思考过程

### 维度1：用户意图（想解决什么）
用户是第一次用Render，要从零到一能跟着点出来，不能有任何模糊的地方（比如Region选哪个、环境变量具体填什么、Internal和External选哪个），必须是按图级、严格按顺序、每个值都有明确答案。

### 维度2：用户技术水平（对应写法深浅）
之前用户在Zeabur反复点到「购买服务器」页面，说明对PaaS控制台入口不熟悉，所以步骤必须写死，Region、Instance Type、Tab选哪个全部明确，不能让用户自己选。

### 维度3：需求隐含约束
- 必须严格走免费路线：PostgreSQL Instance Type选Free，Web Service Instance Type选Free，不能有任何付费选项
- Region必须Singapore：国内用户延迟最低，Render的PG Internal连接只有同区才能用，Web Service和PG必须同区
- DATABASE_URL的坑：Render的PostgreSQL Connections有4个Tab（Internal/External/PSQL/Connection），很多人选External，部署后连不上或用了外部流量超了，必须明确写Internal Tab
- ddl-auto=create的坑：之前007轮思考过程里特别提到的——第一次启动用create建表，成功后必须改成none，不然每次重启删表重建丢数据！这个必须在第7步重点强调
- SMS_SUPER_CODE=1234：免费部署没有短信服务商，用户登录就靠万能验证码，环境变量必须加
- JAVA_OPTS=-Xmx512m：Render Free实例只有512MB内存（实际可用约1.5GB swap），JVM堆内存不能设太大，不然OOM被系统杀掉

### 维度4：历史上下文
前面用户踩过的坑：
1. Zeabur改版后必须买服务器，放弃
2. 本地8080被Docker占了，改8081
3. 归档漏掉被用户骂过两次（所以这轮结束必须自动归档010）

### 维度5：边界/歧义点（主动假设/追问）
1. 代码推了吗？用户可能还没把改好的5处兼容代码push到Git，所以开头必须提醒先git commit & push，不然Render拉的是旧代码，MySQL的SET @TRG_ALLOW_COIN_UPDATE在PG上直接报错启动失败
2. 分支名是main还是master？GitHub新仓库默认main，老仓库可能是master，步骤里要写「是master就改master」
3. 启动成功后怎么验证？不能光看Started日志，要让用户打开域名实际能访问到首页、能用1234验证码登录、测试创建情侣、存数据不丢

## 三、最终回答

分7大步，每一步都有明确的值：
1. 【前置】git push代码（PostgreSQL兼容改动）
2. 新建PostgreSQL（Free / Singapore / PG16）
3. 复制 Connections → Internal Tab → Internal Database URL（存好）
4. 新建Web Service（Connect GitHub / Docker Runtime / Free Instance / Singapore）
5. 加7个环境变量（SPRING_PROFILES_ACTIVE=render,embed-frontend / DATABASE_URL / JWT_SECRET / LETTER_AES_KEY / SMS_SUPER_CODE=1234 / JAVA_OPTS=-Xmx512m / SPRING_JPA_HIBERNATE_DDL_AUTO=create）
6. 等构建（5-15分钟）+ 启动（看Started日志）
7. 【最重要】启动成功后立刻把 SPRING_JPA_HIBERNATE_DDL_AUTO 从 create 改成 none → 保存重启，防丢数据

最后加了常见问题表（构建超时、DB连不上、冷启动慢等），降低用户卡壳时的挫败感。

## 四、命令行执行

无。纯部署教程说明。

## 五、补充说明

Render的Web Service免费额度是750小时/月，正好是31天×24小时=744小时，所以一个月一直开着也超不了（Render免费账号只能有1个Web Service实例，不会开多个爆额度）。PostgreSQL Free版有1GB存储限制，心动小站纯文字+JSON，1GB能存几十万条记录，个人用完全够。唯一缺点是冷启动，15分钟没访问会休眠，唤醒要30-60秒，适合情侣两人自用（每天都会访问几次，基本不会冷）。