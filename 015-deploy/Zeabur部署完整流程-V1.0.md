# ============================================================
#  Zeabur 免费部署心动空间站 - 完整保姆级流程 V1.0
#  适用：项目重组后目录结构（backend/ + frontend/xindong-web/）
#  部署架构：Zeabur Marketplace MySQL 8（独立托管数据不丢）
#            + SpringBoot合包容器（Dockerfile三阶段构建）
#            + Caffeine本地缓存（跳过Redis，省事零配置）
#  预期耗时：10~15分钟（首次构建镜像慢一点，耐心等）
# ============================================================

============================================================
 0. 前置准备（开始之前必须先做，不然到了一半卡壳）
============================================================

【0.1 代码推送到 Git 仓库】（必须，Zeabur不能直接传本地文件夹）
   · 你要有一个 GitHub 或 Gitee 账号（都免费）
   · 把本地 f:\docker\xindong-station\ 整个项目推到 私有仓库
     （不要用public，避免密钥泄露）
   · 推送到仓库的【关键文件必须有】（其他可以加.gitignore忽略）：
       - Dockerfile            （根目录，三阶段构建我们已经写好）
       - backend/pom.xml       + backend/src/**
       - frontend/xindong-web/package.json + frontend/xindong-web/src/** + frontend/xindong-web/vite.config.ts
   · 忽略的（不用推，浪费时间和空间）：
       - backend/target/       node_modules/
       - .idea/                .vscode/
       - backend/data/         misc/logs/   misc/report_*.html

【0.2 确认根目录 Dockerfile 内容（我们已经写好，下面代码对就行）】
   打开仓库根目录的 Dockerfile，看前几行是否是：
   ```
   FROM node:20-alpine AS frontend-builder
   WORKDIR /build/frontend
   COPY frontend/xindong-web/package*.json ./
   ...
   ```
   对的就没问题，这个是和我们重组后的 backend/ + frontend/xindong-web/ 结构匹配的。

============================================================
 1. 注册 Zeabur + 新建项目（2分钟）
============================================================

1.1 浏览器打开  https://zeabur.com  → 右上角点「立即开始」

1.2 选择「使用 GitHub 登录」（推荐，Gitee也行，后面授权仓库方便）
    → 首次登录会跳 GitHub 授权页面，点「Authorize zeabur」
    （不用绑卡！！开发者计划免费额度直接能用）

1.3 登录成功后 → 左侧「创建项目」
    → 项目名称：随便起（比如 xindong-station）
    → 部署区域：选【香港 Hong Kong】（国内用户延迟最低，秒开）
    → 点「创建」

============================================================
 2. 先建 MySQL（独立托管，数据永不丢）—— 非常重要，先做！
============================================================

【🔴 坑位预警1：必须先建MySQL再部署应用，不然应用启动找不到数据库直接炸】

2.1 在项目首页 → 点中间那个大大的 「+」→ 选【Marketplace（市场）】标签

2.2 搜索框输入「MySQL」→ 选第一个官方的 「MySQL 8.x」（带 official 标的）

2.3 配置页面什么都不用改（默认配置对我们足够用）
    → 直接点右下角「部署 Deploy」
    → 等1~2分钟，MySQL服务状态变成「Running 运行中」= OK

2.4 点进刚刚建好的 MySQL 服务 → 切到顶部 「变量 Variables」 标签
    → 你会看到Zeabur给了6个默认变量名：
       MYSQL_HOST / MYSQL_PORT / MYSQL_DATABASE / MYSQL_USERNAME / MYSQL_PASSWORD
    → 【🔴 坑位预警2：我们SpringBoot读的变量名不一样！！别直接复制这个名字，先抄值，后面要改名】
       找张纸条/记事本把【值】记下来（变量名不用记，后面我们改名叫DB_*）：
       ┌────────────────────────────────────────────────────────┐
       │ 记下的值：（格式举例子，实际以你Zeabur页面为准）       │
       │  主机:  xxx.zeabur.internal  （内网地址，端口不要加） │
       │  端口:  3306                                           │
       │  库名:  mysql_xxxxxxxx                                 │
       │  账号:  root            或   mysql_xxxxxxxx           │
       │  密码:  xxxxxxxxxxxxxxxx   （随机字符串一大坨）        │
       └────────────────────────────────────────────────────────┘

============================================================
 3. 部署 SpringBoot + 前端合包应用（核心步骤，照填环境变量）
============================================================

【3.1 创建应用服务，关联Git仓库】
   · 回到项目首页 → 再次点中间的「+」→ 这次选【源代码 Source Code】标签
   · 第一次用会弹「安装 Zeabur 到 GitHub」→ 选「仅选仓库 Only select repositories」
     → 勾选你刚才推代码的那个 xindong-station 私有仓库 → Install
   · 授权成功后回到Zeabur → 仓库列表里选中你的 xindong-station 仓库 → 下一步
   · 分支：选 main / master（你推代码的哪个分支就选哪个）
   · 根目录：留空（Dockerfile就在仓库根目录）
   · 构建方式：Zeabur会自动检测到根目录的Dockerfile，给你显示「Dockerfile Detected」
     → 如果没检测到：手动点「配置构建」→ 构建方式选「Dockerfile」→ Dockerfile路径填：/Dockerfile → 保存
   · 先别点 Deploy！！先做下一步【3.2 加环境变量】（🔴不加环境变量=必炸）

【3.2 🔴 关键一步：配置环境变量（9个，分3类，照抄名字别写错）】
   点 「变量 Variables」 标签 → 一条条「新增变量」→ 下面的名字和值严格对应：

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 【第一类：数据库配置（把刚才MySQL服务里抄的值填进来！！）】
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 变量名              →  填的值（刚才MySQL抄的）
────────────────────────────────────────────────────────────
 DB_HOST             →  MySQL的MYSQL_HOST值（xxx.zeabur.internal，不要加端口不要加http）
 DB_PORT             →  MySQL的MYSQL_PORT值（一般是3306）
 DB_NAME             →  MySQL的MYSQL_DATABASE值
 DB_USER             →  MySQL的MYSQL_USERNAME值（注意：Zeabur默认名是MYSQL_USERNAME，我们变量叫DB_USER，别搞混）
 DB_PASS             →  MySQL的MYSQL_PASSWORD值（那一大坨随机密码）
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 【第二类：激活dev profile = 跳过Redis，用Caffeine本地缓存（省事！）】
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 SPRING_PROFILES_ACTIVE  →  dev  （就填dev三个字母，不要多填！！）
 解释：激活dev = 自动exclude Redis，自动用本地Caffeine缓存，不用配Redis服务，省一个事儿
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 【第三类：安全密钥（改成你自己的随机值，别用默认的！）】
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 JWT_SECRET           →  瞎打一串32位以上的随机字符，比如：
                         XdLove2026!ChangeMe@Random#Key$32charsOK
 LETTER_AES_KEY       →  再瞎打另一串32位以上的随机字符（和上面不要一样！）
 SMS_SUPER_CODE       →  1234   （万能登录验证码，留着自己用；正式上线后改成复杂的）
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

   填完9个变量后，核对一遍名字有没有打错（尤其 DB_USER / DB_PASS / SPRING_PROFILES_ACTIVE 这三个最容易拼错）。

【3.3 启动部署】
   确认环境变量没问题 → 右下角点「部署 Deploy」
   构建进度条开始走了=成功触发。

============================================================
 4. 构建日志怎么看（正常耗时：第一次3~10分钟，耐心等）
============================================================

【4.1 查看实时日志】
   点进「应用服务」→ 顶部切到「构建 Builds」标签 → 点第一个正在跑的构建ID → 右侧看实时日志
   构建分3个阶段，正常会依次看到：
   阶段1（前端构建）：
     npm install → 一大堆下载 → npm run build → ✓ built in X.XXs  = 前端过了
   阶段2（后端Maven构建）：
     mvn dependency:go-offline → 下载依赖慢（国内5~10分钟，耐心等）
     → mvn package -Pembed-frontend → Building jar → BUILD SUCCESS = 后端过了
   阶段3（推送镜像 + 启动容器）：
     Pushed docker image → Container started → 跳到 运行日志
   然后切到「运行 Logs」标签，看到最后出现：
     「Started XindongStationApplication in X.XXX seconds」
     + 没有红色的 ERROR / Exception 堆栈 = 100% 启动成功！🎉

【4.2 构建常见失败 + 解决方案】
| 失败现象 | 99%的原因 | 解决方法 |
|---------|----------|---------|
| npm install 卡在下载依赖，最后TIMEOUT | Zeabur构建机在海外，npm源慢 | 取消构建 → 重跑一次；或在package.json加publishConfig.registry=https://registry.npmmirror.com/ |
| mvn dependency下载慢/超时 | Maven中央源海外慢 | 取消重跑2-3次一般就过了；或者在backend/pom.xml里加阿里云私服mirror（下次更新代码后生效） |
| 启动日志报 Communications link failure / Access denied for user | DB_HOST/DB_PORT/DB_USER/DB_PASS 填错了一个 | 回到变量标签，对照MySQL服务的值重填一遍，注意不要有空格/换行，填完点「重启 Redeploy」 |
| 启动日志报 FlywayException / Table xxx already exists | 数据库里有旧表（你之前部署过一次删了应用没删MySQL） | MySQL服务→More→Reset重置数据库清空所有表→再重启应用 |
| 构建超过30分钟被Kill | 首次构建npm+mvn依赖都下慢了 | 取消，手动Redeploy一次，第二次有缓存会快很多 |

============================================================
 5. 🔍 4步验证部署100%成功（不要跳过！）
============================================================

5.1 拿访问域名：
   回到应用服务 → 顶部「网络 Network」标签 → 公网访问那块有个免费域名：
   格式是 「xxx.zeabur.app」，复制下来（比如 https://xindong-7nmv.zeabur.app ）

5.2 第1步：后端健康检查（公开接口，不用登录）
   浏览器打开：
   https://你的域名.zeabur.app/api/v1/user/send-sms?phone=13800138000
   ✅ 返回结构化JSON（code=0 或 code=3xxxx业务错误）= 后端应用+数据库联通成功！
   ❌ 502/503 = 应用还在启动，再等2分钟；
   ❌ 白页404 = 域名没绑对，复制错了

5.3 第2步：前端页面加载
   浏览器直接打开：https://你的域名.zeabur.app
   ✅ 能看到心动空间站登录页（Logo+手机号/验证码框）= 前端合包成功！
   ❌ Whitelabel Error Page 404 = SPRING_PROFILES_ACTIVE 没写dev？或者没激活embed-frontend（Dockerfile已经写了激活，检查环境变量拼写）

5.4 第3步：登录/注册成功
   手机号随便填11位 → 验证码填 1234（SMS_SUPER_CODE万能码）→ 点登录/注册
   第一次两个手机号都用1234注册配对成情侣。
   ✅ 跳转到首页 Dashboard = 登录+数据库写数据成功！

5.5 第4步：数据持久化验证（🔴最重要，证明MySQL真的在干活）
   · 登录进去 → 心情页写一条心情（随便写点什么）→ 提交
   · 然后 浏览器 按 Ctrl+F5 强制刷新整页（模拟应用重启场景）
   · 再登录进去 → 心情页能看到刚才写的那条心情 = 100%数据持久化成功🎉
   （如果刷新后心情没了 = 数据库地址填错了，连到了容器本地临时库，检查DB_HOST是不是用的Zeabur内网域名）

============================================================
 6. 绑定自己的域名 + 自动HTTPS（可选，有域名就做）
============================================================

6.1 应用服务 → 「网络」标签 → 「自定义域名 Custom Domains」那块
   → 点「添加域名」
   → 输入你的域名（两种写法选一种，推荐加www）：
       你的域名.com        （比如 love-example.com）
       www.你的域名.com    （比如 www.love-example.com）
   → 下一步

6.2 按提示给你两个记录：
   一般是 CNAME 记录，主机记录 @（或 www），记录值 是 xxx.zeabur.app

6.3 去你买域名的控制台（阿里云万网/腾讯云/Cloudflare）→ DNS解析 → 加记录
   → 等 2~10 分钟（全球DNS生效时间）
   → 回到Zeabur看到「已验证」→ 自动申请HTTPS证书
   → 浏览器开 https://你的域名.com → 地址栏有小锁标=成功！

============================================================
 7. 日常运维 4 条命令（部署完偶尔用）
============================================================

| 你要做什么 | 在Zeabur点哪里 |
|-----------|---------------|
| 更新代码（本地改完push到Git后） | 应用服务 → 「设置 Settings」→ 「手动部署 Deploy Manually」→ 选刚才push的分支 → 开始（3~5分钟自动热更新，数据不丢） |
| 看应用报错日志 | 应用服务 → 「运行 Logs」标签 → 实时滚日志，错误红字标出来了 |
| 重启应用（改完环境变量后必须重启） | 应用服务 → 右上角三个点 → 「重启 Restart」 |
| 备份MySQL数据库（每周一次好习惯） | MySQL服务 → 「More」→ 「备份 Backups」→ 手动点「创建备份」，可以下载.sql文件到本地 |
| 回滚到上一个版本（新版本炸了） | 应用服务 → 「构建 Builds」标签 → 选上一个成功的构建 → 三个点 → 「回滚 Rollback」，几秒钟回到旧版本 |

============================================================
 8. 常见报错速查表（遇到先查这个，90%的问题30秒解决）
============================================================

| 错误信息（日志里看到的） | 原因 | 立刻做什么 |
|------------------------|------|-----------|
| `Communications link failure, The last packet sent successfully...` | 连不上MySQL，DB_HOST/DB_PORT填错了，或者MySQL服务没启动 | ① 先去MySQL服务看是不是Running<br>② 对照变量页把DB_*五个值重填一遍<br>③ 重启应用 |
| `Access denied for user 'xxx'@'%' to database 'xxx'` | DB_USER或DB_PASS密码错了 | 去MySQL Variables页抄MYSQL_PASSWORD的值，别手敲，复制粘贴 |
| `FlywaySqlException: Migration checksum mismatch` | 改过db/migration下的SQL文件（不应该改） | MySQL服务→Reset重置数据库→应用重启（Flyway会重跑建表） |
| `404 Whitelabel Error Page` 打开域名看得到后端接口看不到前端 | 没激活embed-frontend Profile | Dockerfile里已经写了SPRING_PROFILES_ACTIVE默认包含embed-frontend，但你如果在变量里把SPRING_PROFILES_ACTIVE只写了dev=两个激活不了，改写法：dev,embed-frontend（逗号分隔，两个都激活）【🔴 这个是本流程最大的漏写坑！修正！】 |
| `InvalidAlgorithmParameterException: the trustAnchors parameter must be non-empty` | Docker镜像里的JDK证书问题（比较罕见） | 应用服务→设置→添加环境变量：JAVA_OPTS=-Xms256m -Xmx768m → 重启 |
| 打开域名一直502 Bad Gateway转圈圈 | 应用还在冷启动/没启动成功 | 去运行Logs看是不是卡在SpringBoot启动或者报上面几种错 |
| 登录/调接口报 30005 请先登录 | 正常行为=后端正常返回JSON，不是报错（健康检查接口要登录），换 send-sms 接口验证 | 不是bug，直接用1234万能验证码登录就行 |

============================================================
 【🔴 8号之前的最大漏写坑 紧急修正】
 第3.2步的环境变量 SPRING_PROFILES_ACTIVE：
    之前写的填 "dev" 只能跳过Redis，但 WebMvcConfig 的前端路由转发
    只有激活了 embed-frontend Profile 才会生效！
    所以正确值应该是两个一起激活，用英文逗号分隔（不要有空格）：

✅ 正确：SPRING_PROFILES_ACTIVE = dev,embed-frontend

    解释：
    - dev = 跳过Redis + Caffeine缓存 + 调试日志（我们要的）
    - embed-frontend = 把前端dist打进jar + 8080端口访问前端页面
      （WebMvcConfig的addViewControllers才会注册SPA路由）
    两个都激活，才会：前端页面能看 + 不用装Redis + 数据连Zeabur MySQL ✅

============================================================
 DONE. 看到登录页+登录后数据能保存=部署100%成功，恭喜🎉