# 批次8 归档 - 心动空间站 V1.0 (FINAL)
> 2026-08-17 交付 | 业务对账用例 **12个**  | 启动模式 **分离 / 合包 双模式自由切换**
> 核心铁律：AI 不得自行执行 PowerShell / CMD 命令，必须贴代码块由用户手动执行

---

## 一、批次8 交付清单

| 模块 | 交付内容 | 核心修改文件 |
|------|---------|-------------|
| **J类静默Bug对账脚本（12个核心场景全覆）** | 从J002~J013共12个业务断言用例，专门抓"接口code=0但结果不符合预期"的假成功Bug | `scripts/biz_assert_only.py` |
| **J008 金币负数钳位 修复** | overview不返回coupleId导致internal-add 30001→NoResultException → 新增`fetch_coupleid()`三级兜底优先从`/couple/info data.id`拿 | `scripts/biz_assert_only.py` (fetch_coupleid + safe_coin_amount) |
| **J004 金币流水字段误判 修复** | 后端logs主字段名=`delta`（CoinService.java 475行）不是`amount` → safe_coin_amount()优先读delta兼容amount/coinDelta等6个别名 | `scripts/biz_assert_only.py` (safe_coin_amount) |
| **J010 恋爱清单勾选 修复** | 后端toggle方法是`@PutMapping`(ChecklistController.java:68)不是POST → 三档兜底：PUT body → PUT ?done=true → POST /mark-done别名；双路径/checklist(s)都试 | `scripts/biz_assert_only.py` (j010 + ApiClient.put新增) |
| **心愿步骤勾选状态提取 修复** | 之前仅读completedSteps数字，改为优先读`steps[].done`数组逐位索引，精确到具体步骤下标 | `scripts/biz_assert_only.py` (collect_done_idx) |
| **心情打卡精确回读 修复** | 之前按moodType模糊匹配，改为**按POST返回的主键id精确命中GET列表**，杜绝假成功 | `scripts/biz_assert_only.py` (j003 + find_mood_by_id) |
| **纪念日JSON/Form双兼容 修复** | POST创建时先试RequestBody JSON → 失败自动fallback到`/anniversaries/q` Form编码方式，兼容两种后端签名 | `scripts/biz_assert_only.py` (j009) |
| **默契题题干字段五兼容 修复** | 死磕title字段导致误判缺字段 → 题干兼容q/stem/text/question/content/title六选一；选项兼容options/choices二选一 | `scripts/biz_assert_only.py` (j011) |
| **前后端分离 双模式架构改造** | 默认=分离模式（后端只提供/api/v1接口不承载页面）；合包时加-Pembed-frontend+spring.profiles.active=embed-frontend即可回到单jar模式 | `pom.xml` (Profile) + `WebMvcConfig.java` (Profile判断) + `web/src/api/request.ts` (VITE_API_BASE) + `web/.env.production`（新建） |

---

## 二、12个静默Bug业务对账用例（J类）覆盖全景

> 运行入口：`python scripts/biz_assert_only.py`，纯接口不打开浏览器，专门抓假Toast假成功

| 用例编号 | 名称 | 核心断言论点 |
|---------|------|------------|
| J002 | 破冰大转盘抽奖次数扣减校验 | spin成功后B2 = B1 - 1（真扣减，不是Toast假成功） |
| J003 | 心情打卡真保存校验（按ID精确回读） | POST返回id → GET按id精确命中且moodValue一致；重复打卡=20301幂等 |
| J004 | 金币中心前后台余额对账 | overview余额不空≥0无乱码；logs首条delta(非amount)是数字+reason不空 |
| J005 | 心愿步骤勾选真存入校验（读steps[].done） | 只勾选APPROVED/IN_PROGRESS状态；steps数组target_step位回读为true |
| J006 | 金币余额流水对账（overview≈Σlogs） | logs分页全量拉取；按时间倒序；overview余额与logsΣ差值在合理范围 |
| J007 | 破冰次数耗尽真拦截（连抽必触发扣到0） | 抽成功次数 ≤ 初始left+1；left=0时第一抽必被拦截 |
| J008 | 金币负数钳位兜底（写负数读≥0） | **从/couple/info拿cid**（不再依赖overview可能没有的字段）；写-12345后overview≥0（DB钳位） |
| J009 | 纪念日CRUD真存真删 | POST创建code=0 + 自增id>0 → GET列表命中且title一致 → DELETE后列表没了=真删 |
| J010 | 恋爱清单勾选真持久（GET回读done=true） | **toggle用PUT不是POST**（后端@PutMapping）；toggle返回done=true → GET回读done=true（DB真写） |
| J011 | 默契questions字段齐全不空 | 题干六兼容(q/stem/text/question/content/title)；选项二兼容(options/choices)；每题≥1选项不空 |
| J012 | 每日默契submit重复幂等（不双送币） | 第2次submit返回20301；两次submit余额差≤1份奖励（不双送） |
| J013 | 日记CRUD真存真删 | POST+自增id → GET detail命中/title一致 → GET列表命中 → DELETE清理 |

---

## 三、前后端分离 & 不分离 双模式启动方案（★本批次核心交付）

### 🔵 方案A：前后端分离（推荐日常开发 + 正式部署）
> **本质**：后端=纯接口服务（8080只响应/api/v1/**），前端=独立静态资源（Nginx或Vite Dev Server），二者通过HTTP通信。CORS在WebMvcConfig已全局放开。

#### 开发阶段（您本地联调，零配置）
```powershell
# 终端1：启动后端（不要加任何Profile，默认就是分离模式）
cd f:\docker\xindong-station
mvn spring-boot:run
# 验证：http://localhost:8080/api/v1/coins/overview  → 返回JSON（接口正常）
# 验证：http://localhost:8080/                         → 返回404（后端不承载页面=分离成功✅）

# 终端2：启动前端Vite开发服务器（5173端口，vite.config.ts里已写好proxy /api -> 8080）
cd f:\docker\xindong-station\web
npm install    # 第一次用才执行，后面不用每次跑
npm run dev
# 打开浏览器：http://localhost:5173  →  页面+数据全OK（走代理天然无跨域）
```

#### 生产部署（前端放 Nginx，后端独立 jar）
```powershell
# ===== 第一步：打包后端（不要加任何Profile）=====
cd f:\docker\xindong-station
mvn clean package
# 产物：target/xindong-station-*.jar
# 服务器启动：java -jar xindong-station-*.jar  --server.port=8080

# ===== 第二步：打包前端（改web/.env.production里的VITE_API_BASE为您真实后端地址）=====
# 例：VITE_API_BASE=https://api.yourdomain.com/api/v1   （一定要写到/api/v1）
cd f:\docker\xindong-station\web
npm install
npm run build
# 产物：web/dist/*  →  上传到 Nginx /usr/share/nginx/html/ 目录
# Nginx配置里加一条 History Fallback（否则刷新子路由404）：
#   location / { try_files $uri $uri/ /index.html; }
```

---

### 🟠 方案B：前后端不分离（合包单jar，不用装Nginx，演示/内网首选）
> **本质**：前端打包产物被塞进 jar 的 classpath:/static/，后端同时承载页面 + 接口。只开一个端口=能访问全栈。

#### 打包 & 启动（只比分离多2个参数）
```powershell
# 顺序1：必须先build前端（生成 web/dist/ 目录）
cd f:\docker\xindong-station\web
npm install   # 首次才需要
npm run build

# 顺序2：后端打包时加 -Pembed-frontend （激活合包Profile，把dist拷进static/）
cd f:\docker\xindong-station
mvn clean package -Pembed-frontend

# 顺序3：启动jar时加 spring.profiles.active=embed-frontend （激活WebMvcConfig的History Fallback）
java -jar -Dspring.profiles.active=embed-frontend target\xindong-station-*.jar
# 或IDEA里VM Options填：-Dspring.profiles.active=embed-frontend

# 打开浏览器：http://localhost:8080     →  页面+接口都在同一个端口✅
# 验证：直接访问 http://localhost:8080/interactive/icebreak  子路由不会404✅
```

---

### 📊 双模式快速对照表
| 对比项 | 方案A 分离（默认） | 方案B 合包单jar |
|-------|------------------|----------------|
| 打包后端命令 | `mvn clean package` | `mvn clean package -Pembed-frontend` |
| 启动后端参数 | 无需 | `-Dspring.profiles.active=embed-frontend` |
| 后端是否承载页面 | ❌ 不承载 | ✅ 承载 |
| 前端部署方式 | 独立Nginx / Vite Dev Server（5173） | 随jar内置（不用额外部署） |
| 推荐场景 | 正式上线、团队开发、前后端团队独立迭代 | 单机演示、内网部署、没有Nginx/运维能力 |
| pom.xml是否复制web/dist到static | 不复制 | 复制（仅Profile激活时） |
| WebMvcConfig是否forward路由 | 不forward | forward（仅Profile激活时） |
| 前端baseURL | VITE_API_BASE环境变量指定或代理 `/api/v1` | 默认 `/api/v1` 同源（零配置） |

---

## 四、本次修改文件清单（拷贝到其他目录时必带scripts+docs）

| 路径 | 变更类型 | 说明 |
|------|---------|------|
| `scripts/biz_assert_only.py` | **重写** | 12个J类用例全量覆盖；字段兼容/HTTP方法兼容/CID三级兜底等修复 |
| `pom.xml` | 修改 | 把前端复制插件移到`<profiles><profile id="embed-frontend">`内，默认不复制=分离 |
| `src/main/java/com/xindong/common/config/WebMvcConfig.java` | 修改 | 注入Environment；只有`embed-frontend` Profile激活才注册 History forward |
| `web/src/api/request.ts` | 修改 | baseURL优先读`import.meta.env.VITE_API_BASE`，兼容合包同源默认 `/api/v1` |
| `web/.env.production` | **新建** | 分离部署专用，填VITE_API_BASE=http://后端地址:端口/api/v1 |
| `docs/Batch8-批次8归档-V1.0-FINAL.md` | **新建** | 本文件（归档/启动方案/铁律） |

---

## 五、AI 不得自行执行命令铁律（用户批评过多次，本批次固定写入记忆库）

> 规则号 **I-9**（承接历史问题记忆库避坑指南「I 规范执行类」）
>
> **严禁**：AI 直接调用 RunCommand / PowerShell / CMD / Bash / mvn / python 等任何本地执行工具。
>
> **必须执行的流程**：
> 1. AI 把要执行的命令以 **代码块** 形式贴在聊天里（前面标「👉 您执行：」）
> 2. 用户 **手动复制粘贴** 到自己的 PowerShell / CMD / IDEA 终端运行
> 3. 用户把运行结果（文字/截图/红标FAIL段）贴回聊天
> 4. AI 基于结果继续分析/改代码
>
> **为什么**：之前 AI 自行执行命令导致「命令缓冲区报错」「多字节字符乱码」「当前目录错误」「用户没授权权限」等 5+ 次被批评，已写入记忆库永久约束。

---

## 六、12个J类业务对账运行结果模板（用户跑完贴回来时对照）

```
======================================================================
🧪 心动空间站 · 业务逻辑对账（J002~J013静默Bug挖掘机 · 不启动浏览器）
======================================================================
【✅ PASS】 J002-破冰大转盘抽奖次数扣减校验
【✅ PASS】 J003-心情打卡真保存校验（按ID精确回读）
【✅ PASS】 J004-金币中心前后台余额对账
【✅ PASS】 J005-心愿步骤勾选真存入校验（读steps[].done）
【✅ PASS】 J006-金币余额流水对账（overview≈Σlogs + logs倒序）
【✅ PASS】 J007-破冰次数耗尽真拦截（连抽必触发扣到0）
【✅ PASS】 J008-金币负数钳位兜底（写负数读≥0）
【✅ PASS】 J009-纪念日CRUD真存真删
【✅ PASS】 J010-恋爱清单勾选真持久（GET回读done=true）
【✅ PASS】 J011-默契questions字段齐全不空
【✅ PASS】 J012-每日默契submit重复幂等（不双送币）
【✅ PASS】 J013-日记CRUD真存真删
======================================================================
📊 结果：PASS 12 / FAIL 0   共12个用例
🎉 全绿！当前覆盖的12个业务对账场景，0静默Bug！
======================================================================
```

一旦有 `❌ FAIL` 的红色行，请**整段（含 — 后面的msg/code）** 贴给AI，对应修后端业务代码，不要让AI自己跑。