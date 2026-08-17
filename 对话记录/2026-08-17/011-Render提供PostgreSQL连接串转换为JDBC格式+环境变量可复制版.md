---
title: "Render提供PostgreSQL连接串转换为JDBC格式+环境变量可复制版"
date: "2026-08-17"
number: "011"
tags: ["Render", "PostgreSQL", "环境变量", "JDBC连接串", "部署细节"]
status: "已归档"
---

# 对话记录 2026-08-17 / 011

## 一、原始问题

用户按010轮的教程做到了第1-3步（PostgreSQL建好，Connections复制了4个值），把4个值全贴过来了，要我把第4步（环境变量）写成可以直接复制粘贴的版本，不用自己拼。

用户贴的4个值：
1. 简写版PostgresURL：`postgresql://xindong_db_user:7DIEkjEvBUYoNsc2RaXMONRh78pHRIrN@dpg-da1f65p5efls73ee4vb0-a/xindong_db`（少了完整域名，是Render内部DNS短名，外部不能用）
2. 单独密码：`7DIEkjEvBUYoNsc2RaXMONRh78pHRIrN`
3. Internal Database URL（完整正确版）：`postgresql://xindong_db_user:7DIEkjEvBUYoNsc2RaXMONRh78pHRIrN@dpg-da1f65p5efls73ee4vb0-a.singapore-postgres.render.com/xindong_db`
4. PSQL命令：`PGPASSWORD=7DIE... psql -h dpg-da1f65p5efls73ee4vb0-a.singapore-postgres.render.com -U xindong_db_user xindong_db`

并说了：「你帮我把第四步的写成可以直接复制的」

## 二、思考过程

### 维度1：用户意图（想解决什么）
用户不想自己拼环境变量，怕拼错，要给现成的Key-Value对，直接复制粘贴就能用。

### 维度2：用户技术水平（对应写法深浅）
可能不知道SpringBoot的PostgreSQL JDBC驱动不认 `postgres://` 协议，必须是 `jdbc:postgresql://` 前缀，并且要加端口号5432、加用户名密码参数、加sslmode=require。所以必须帮他转换好，不能让他自己拼。

### 维度3：需求隐含约束
- DATABASE_URL不能直接用Render的`postgres://`（libpq协议），必须转成JDBC格式：`jdbc:postgresql://host:5432/dbname?user=xxx&password=xxx&sslmode=require`
- Render的PostgreSQL连接**必须加sslmode=require**，不然会报错：`FATAL: no pg_hba.conf entry for host "xxx", user "xxx", database "xxx", no encryption`，这个是Render硬性要求
- 端口必须显式写5432，虽然PG默认是5432，但Render的Internal连接有时候DNS解析会有问题，显式写更稳
- 用户贴的第一个URL是短名`dpg-da1f65p5efls73ee4vb0-a`（Render内部K8s service名），Internal Web Service能解析这个短名，但是为了保险起见，还是用完整域名`dpg-da1f65p5efls73ee4vb0-a.singapore-postgres.render.com`比较稳
- 第7条`SPRING_JPA_HIBERNATE_DDL_AUTO=create`必须在旁边备注「启动成功后改none」，用户忘了改就会每次重启丢数据
- JAVA_OPTS必须设-Xmx512m，Render Free实例只有512MB物理内存，超过会被OOM killer杀掉（SpringBoot默认堆是物理内存1/4，512M机器默认128M？不对：Java17默认MaxRAMPercentage=25.0，所以512M机器默认堆=128M左右，但是SpringBoot + Vue静态资源 + 一堆依赖128M可能不够，设到512M的话会用swap，Render Free实例swap大约1GB，512M能跑）

### 维度4：历史上下文
007轮改的application-render.yml里DATASOURCE_URL的默认值是`jdbc:postgresql://localhost:5432/xindong`，环境变量覆盖，所以格式必须匹配。

### 维度5：边界/歧义点（主动假设/追问）
用户的Render给的是`postgresql://`（最近Render改版，之前是`postgres://`），但不管什么前缀，JDBC格式必须严格是`jdbc:postgresql://`开头，不能错。

另外用户之前贴的第1条是简写URL（`@dpg-xxx-a/xindong_db`，没有.singapore-postgres.render.com后缀），这个会不会是Render新的Internal短名？Internal的话，Web Service和PG在同一个Render项目里的话，应该能解析短名，但是为了万无一失，还是用完整域名（第3条）转换，不会有问题。

## 三、最终回答

把DATABASE_URL转换好了：
```
jdbc:postgresql://dpg-da1f65p5efls73ee4vb0-a.singapore-postgres.render.com:5432/xindong_db?user=xindong_db_user&password=7DIEkjEvBUYoNsc2RaXMONRh78pHRIrN&sslmode=require
```

然后7条环境变量用表格列出来，每条的Key和Value都标了「复制」，用户直接照抄就行。
最后补了其他配置的再确认（Region/Singapore、Instance Type/Free、Branch/main），别再填错。

## 四、命令行执行

无。纯转换+整理。

## 五、补充说明

Render的PostgreSQL Free版几个坑：
1. 90天没有任何活动（没有任何数据库连接）会被自动暂停，需要去控制台手动Resume；Web Service每个月访问几次就不会触发这个
2. 最大连接数是100个，SpringBoot Hikari设的maximum-pool-size=10（application-render.yml里写的），完全够用，不用担心超
3. SSL连接必须是require，不能disable，否则连不上，已经在JDBC URL里加好了