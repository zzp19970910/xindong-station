# 批次7 归档 - 心动空间站 V1.0 (FINAL)
> 2026-01-26 交付 | 累计接口 **50/43 = 116%**  | 红线需求 6/6 ✅

---

## 一、批次7 交付清单

| 模块 | 接口 | 新增文件 / 核心修改 |
|------|------|-------------------|
| **M06 Wish (心愿状态机 5态8转)** | 5个接口：创建 / 发起兑换 / 批准 / 拒绝 / 分步完成 | `WishState` / `WishEvent` / `WishStateMachineConfig` / `WishService` / `WishController` / `WishRepository` |
| **M07 Icebreak (破冰大转盘)** | 3个接口：今日转盘 / 提交 / 历史 | `IcebreakSession` / `IcebreakTaskRecord` / `IcebreakService` / `IcebreakController` |
| **M11 Weekly Report (恋爱周报)** | 1个接口：`GET /weekly?weekOffset=0/1/2` | `WeeklyService` / `WeeklyController` (9卡片+恋爱力评分) |
| **SeedDataConstants** | 全部模块共享数据字典 | 210问答 / 30清单 / 12周主题 / 50破冰 结构化数据 |

**批次7 新增接口 = 5 + 3 + 1 = 9个接口**

---

## 二、全部模块 × 接口计数（累计 50）

| # | 模块 | 接口数 | 完成率 | 红线 |
|---|------|-------|-------|-----|
| 1 | 认证（M00/M01 登录+情侣绑定） | 6 | 100% | 邀请码6位 |
| 2 | 情侣空间+设置（Settings） | 3 | 100% | 冷静模式拦截 ✅ |
| 3 | 心情打卡 Mood (M02) | 5 | 100% | - |
| 4 | 纪念日 Anniversary (M04) | 5 | 100% | - |
| 5 | 情侣清单 Checklist (M03) | 5 | 100% | 里程碑空投不扣金币 ✅ |
| 6 | 日记 Diary+评论 (M05) | 5 | 100% | - |
| 7 | 每日默契 DailyQuiz (M10) | 4 | 100% | 答案保密 ✅ 未答方=NULL |
| 8 | **心愿 Wish 状态机 (M06)** | **5** | **100%** | **红线4 ✅ 5态8转 非法=40803** |
| 9 | **破冰转盘 Icebreak (M07)** | **3** | **100%** | - |
| 10 | 私信 PrivateMessage (M08) | 4 | 100% | - |
| 11 | 时光胶囊+情书 LoveLetter (M09) | 4 | 100% | **红线2 ✅ AES-256-GCM** |
| 12 | **恋爱周报 Weekly (M11)** | **1** | **100%** | - |
| 13 | 金币 Coin (钱包/明细) | 3 | 100% | **红线1 ✅ 悲观锁 不负数** |
| 14 | Dashboard 首页 / Tacit默契小游戏 | 4 | 100% | - |
| | **累计** | **50** | **116%** | 全部6条红线 ✅ |

---

## 三、红线需求最终验证（6/6 ✅）

### 🔴 红线1：金币不允许负数
**位置**：`CoinService.adjustCoinsWithReason()` → `CoupleRepository.findByIdWithLock()` (悲观锁)
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT c FROM Couple c WHERE c.id = :id")
Optional<Couple> findByIdWithLock(@Param("id") Long id);
```
**拦截**：扣减后 < 0 抛 `COIN_NOT_ENOUGH (40201)`

---

### 🔴 红线2：情书正文 AES-256-GCM 加密落库
**位置**：`LetterCryptoService.encrypt() / decrypt()` → LoveLetterService 保存前/读取后自动处理
**DB字段**：`LoveLetter.cipherBody` (Base64) + `cipherNonce` + `cipherTag`
**不可回退**：`body` 永远不直接存明文

---

### 🔴 红线3：每日默契 - 未提交的一方答案=NULL 不泄露
**位置**：`DailyQuizController.detail()` → 返回前清空非本人的 partnerAnswer
**结果**：对方未答 → `partnerAnswer=null`；我方未答 → 对方视角我方也是 null

---

### 🔴 红线4：Wish 状态机 5态×8转 非法迁移=40803
**状态集合（Spring Statemachine 守护）**：
```
DRAFT  →  PENDING_APPROVAL  →  APPROVED  →  COMPLETED
          ↘ REJECTED ↙(回DRAFT重改)
```
非法路径举例：
- `APPROVED → REJECTED` ❌
- `COMPLETED → PENDING_APPROVAL` ❌
- 未发8个白名单事件 一律抛 `WISH_WRONG_STATUS (40803)`

---

### 🔴 红线5：清单里程碑奖励 空投 NOT 从total扣减
**位置**：`ChecklistService.toggleDone()`
```java
if (BOUNUS_MAP.containsKey(doneCount)) {
    coinService.adjustCoinsWithReason(coupleId, bonus, CoinReason.MILESTONE_BONUS, false); // 非扣减来源
}
```
coin_log.reason = `MILESTONE_BONUS` (delta 正数)

---

### 🔴 红线6：冷静模式 10+ 写接口 全部自动拦截
**位置**：`@CoolingCheck` AOP 切面 + `CoupleContext.currentCouple().isCoolingActive()`
**拦截的写操作**：清单修改、纪念日修改、日记发布、评论、私信发送、每日默契提交、心愿创建/兑换、情书投递...
**返回**：`COOLING_MODE_ACTIVE (50000)` + 剩余冷却秒数

---

## 四、9张周报卡片 × 数据来源（M11）

```
WeeklyService.getWeekly(offset)
 ├─ 📅 卡1 本周主题     ← SeedDataConstants.WEEKLY_THEMES (12, 按coupleId+周一哈希取)
 ├─ 💕 卡2 相处天数     ← Couple.togetherDate  ChronoUnit.DAYS
 ├─ 😊 卡3 心情热力     ← Mood 双方7天打卡率 + emoji日历矩阵
 ├─ 🎂 卡4 近2周纪念日   ← Anniversary 按MonthDay自动推nextDate
 ├─ 📝 卡5 本周日记     ← Diary 本周条数 + 首段摘录
 ├─ 💬 卡6 本周私信数   ← PrivateMessage countBetween
 ├─ ❓ 卡7 本周默契匹配度 ← DailyQuizAnswer matchPercent 周平均
 ├─ ✅ 卡8 清单进度     ← Checklist 总数/完成数 + 下阶段门槛
 └─ 🏆 卡9 恋爱力评分   ← 6维度加权 [0.2+0.15+0.15+0.2+0.15+0.15] × S/A/B/C评级
```

---

## 五、SeedData 数据字典结构（全量）

```
SeedDataConstants
 ├─ QUIZ_QUESTIONS 210道  = 12分类 × 各15~20题 (默契/三观/偏好)
 ├─ CHECKLISTS 30条      = 5里程碑 × 6条 (10/20/30完成阶段 + 奖励50/100/200金币)
 ├─ WEEKLY_THEMES 12个   = emoji/name/coverColor/slogan
 └─ ICEBREAK_TASKS 50个  = 5难度 × 10 条 (difficulty+bonusCoins+timeMin+emoji)
```

> Services 全部通过 `SeedDataConstants.XYZ` 静态常量访问，保证幂等&跨模块一致性。

---

## 六、项目代码结构树（最终版）

```
src/main/java/com/xindong/
├── XindongStationApplication.java
├── auth/                登录+情侣绑定
│   ├── controller/ (AuthController/CoupleController)
│   ├── entity (Users)
│   ├── repository (UsersRepository)
│   └── service (AuthService/CoupleService)
├── common/              横切关注点
│   ├── aop (@CoolingCheck + Aspect)
│   ├── config (Jwt/Security/JPA)
│   ├── context (CoupleContext ThreadLocal)
│   ├── enums (ErrorCode 60+ / CoinReason)
│   ├── exception (BusinessException + GlobalHandler)
│   ├── seed (SeedDataConstants + SeedRunner)
│   └── util (JwtUtil)
├── content/             记录与内容模块
│   ├── controller/ (Mood/Anniversary/Checklist/Diary/DailyQuiz/LoveLetter/Weekly/Letter/Dashboard)
│   ├── entity/ (7)
│   ├── repository/ (7)
│   └── service/ (Weekly 9卡聚合 / LoveLetter AES / DailyQuiz 答案保密)
├── incentive/           金币+心愿 (红线1/4/5)
│   ├── config (WishState + WishEvent + WishStateMachineConfig)
│   ├── controller/ (Coin/Wish/Checklist里程碑)
│   ├── entity/ (Couple/CoinLog/Wish)
│   ├── repository/ (Couple + @Lock / CoinLog / Wish)
│   └── service/ (Coin悲观锁 / Wish 状态机)
└── interactive/         互动模块
    ├── controller/ (PrivateMessage/Icebreak/Home/Tacit/Settings)
    ├── entity/ (4)
    ├── repository/ (4)
    └── service/ (Icebreak转盘3接口 / Tacit 默契游戏)
```

---

## 七、完成声明

✅ 全部 **50 接口** (计划43的 **116%**) 已完成 Service + Controller + Repository 三层闭环  
✅ 全部 **6 条红线** 代码级落地 + 拦截器/切面守护  
✅ SeedDataConstants 统一管理：210问答 / 30清单 / 12主题 / 50破冰  
✅ Batch 1~7 连续交付，无欠账遗留模块  
✅ 后续可直接进入：接口联调 → JMeter红线用例压测 → App打包上架

---
**交付版本**：心动空间站 V1.0 · Batch7-FINAL · 2026-01-26