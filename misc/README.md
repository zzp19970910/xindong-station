# Xindong Station 心动空间站 V1.0

> 情侣恋爱网站 · Java 17 + Spring Boot 3.3 + Vue 3 + Vant 4
> 架构方案：模块化单体（未来Phase 3平滑微服务化拆分）

## 目录结构
```
xindong-station/
├── pom.xml                                  # Maven 父POM（Spring Boot 3.3.2）
├── README.md
├── .gitignore
├── data/                                    # SQLite开发库文件路径(Git忽略)
├── logs/                                    # 生产日志输出路径(Git忽略)
├── web/                                     # 前端 Vue 3 + Vite + Vant 4
│   ├── src/
│   │   ├── api/           request.ts 统一拦截器 + auth.api/coupleApi
│   │   ├── router/        5底部Tab + 导航守卫(未登录跳/auth)
│   │   ├── stores/        Pinia auth.store(token/user/couple本地持久化)
│   │   ├── styles/        global.css CSS变量(主题色/金币色)
│   │   └── views/         AuthView + MainLayout(5Tab) + 5页面骨架
│   ├── index.html
│   ├── vite.config.ts     375px px转vw + /api代理到8080
│   └── package.json
└── src/main/
    ├── java/com/xindong/
    │   ├── XindongStationApplication.java  @SpringBootApplication + 3启动注解
    │   ├── common/                          【公共层】未来微服务公共依赖
    │   │   ├── enums/        ErrorCode(52个) + CoinReason(12种)
    │   │   ├── result/       Result统一响应信封(code/msg/data/ts)
    │   │   ├── exception/    BusinessException + GlobalExceptionHandler
    │   │   ├── context/      CoupleContext ThreadLocal(UserId/CoupleId/PartnerIdx)
    │   │   ├── config/       SecurityConfig + JwtAuthFilter + CoupleGuard + WebMvcConfig
    │   │   ├── aop/          @CoolingCheck + CoolingModeAspect (🔴红线6)
    │   │   └── util/         JwtUtil JJWT0.12.5
    │   ├── auth/                            【未来用户服务】
    │   │   ├── entity/       Users (JPA + MP双注解)
    │   │   ├── repository/   UsersRepository JPA
    │   │   └── controller/   AuthController(G1-G4 登录注册) + CoupleController(M01 绑定)
    │   ├── content/                         【未来内容服务】
    │   │   ├── service/      🔴LetterCryptoService AES-256-GCM (红线3)
    │   │   └── controller/   Mood + Anniversary + Diary + Letter 4个Controller
    │   ├── incentive/                       【未来激励服务】
    │   │   ├── entity/       Couple(金币钱包真源) + CoinLog(12种流水)
    │   │   ├── repository/   CoupleRepository + CoinLogRepository
    │   │   ├── service/      🔴CoinService addCoins() JPA悲观行锁 (红线1+5)
    │   │   ├── config/       WishStateMachine Spring Statemachine (🔴红线4)
    │   │   └── controller/   Coin + Wish(5状态机) + Checklist 3个Controller
    │   └── interactive/                     【未来互动服务】
    │       └── controller/   Home(首屏) + Quiz(默契) + Icebreak(大转盘) + Settings(周报/冷静)
    └── resources/
        ├── application.yml                  # 公共配置(降级开关/JWT/AES/短信/缓存TTL)
        ├── application-dev.yml              # SQLite + Redis localhost + show-sql
        ├── application-prod.yml             # MySQL 8.0 + 生产连接池参数 + JSON日志落盘
        └── db/migration/
            └── V1__init_11_core_tables.sql  # Flyway 11张核心表 + 4张辅助表 + 索引
```

## 启动方式（开发环境，零配置SQLite）

```bash
# ======= 1. 启动后端 (默认application-dev.yml，零配置) =======
cd xindong-station
mvn spring-boot:run -Dspring-boot.run.profiles=dev
# Swagger UI:  http://localhost:8080/api/v1/swagger-ui.html
# 健康检查:    http://localhost:8080/api/v1/actuator/health
# Prometheus:  http://localhost:8080/api/v1/actuator/prometheus

# ======= 2. 启动前端 =======
cd xindong-station/web
npm install
npm run dev
# http://localhost:5173  Vite自动代理/api到后端8080
```

## 🔴 6条红线 100% 代码落点

| 红线 | 文件 | 实现 |
|------|------|------|
| 1.金币永不负数 | `incentive/service/CoinService.java` | JPA `@Lock(PESSIMISTIC_WRITE)` + `REPEATABLE_READ` 隔离 |
| 2.默契答案不泄露 | 待Service层实现 | `partnerAnswerContent = null` + QA grep断言 |
| 3.情书AES+未到期屏蔽 | `content/service/LetterCryptoService.java` | `Encryptors.stronger()` AES-256-GCM + 到期判断******** |
| 4.愿望状态机合法跳转 | `incentive/config/WishStateMachineConfig.java` | Spring Statemachine 4.0 6状态/13跳转配置式声明 |
| 5.里程碑空投零扣费 | `incentive/service/CoinService.java` addCoins()前 | reason前缀`milestone_9_`强制`from_partner==null && delta>0`拦截 |
| 6.冷静模式全拦截 | `common/aop/CoolingModeAspect.java` + `@CoolingCheck` | AOP切面拦截所有写接口，21202/21203错误码 |

## 52错误码段位速查
| 段位 | code前缀 | 前端行为 | 例 |
|------|---------|---------|----|
| 成功 | 0xxxx | 正常 | 00000 |
| 提示 | 1xxxx | Toast | 10201邀请码复制 |
| 冲突 | 2xxxx | Dialog | 20701金币不足 / 21202冷静拦截 |
| 参数/权限 | 3xxxx | Toast+高亮 | 30004跨情侣访问 / 30011限流 |
| 不存在 | 4xxxx | 404页 | 404xx 资源不存在 |
| 服务端 | 5xxxx | 重试页 | 50701扣币死锁 / 50601AES解密失败 |

## 43接口7批次交付（Controller已创建空壳）
```
批次1(M0 D1-2): AuthController 4接口(G1-G4) + CoupleController M01 4接口
批次2(M1 D3-4): CoinController(M07) 3接口 + CoinService.addCoins() 1000并发单测
批次3(M1 D5-6): MoodController(M03)+AnniversaryController(M04) 共6接口
批次4(M2 D7-8): DiaryController(M05) 6接口
批次5(M2 D9-10): LetterController(M06) 6接口 + ChecklistController(M09) 4接口 + AES 6case单测
批次6(M3 D11-12): WishController(M08) 7接口 + QuizController(M10) 3接口 + 状态机8非法跳转单测
批次7(M4 D13-14): HomeController(M02 9表) + IcebreakController(M11) 4接口 + SettingsController(M12 9接口)
```

## 三阶段演进（架构预留）
| 阶段 | 时机 | 动作 |
|------|------|------|
| Phase1 (当前) | <1万对情侣 | 本仓库模块化单体（4包=4个未来微服务） |
| Phase2 (3个月后) | Duo-WAD≥4天/周 | Java21虚拟线程升级+MySQL读写分离+OSS真实图片+富文本+微信 |
| Phase3 (6个月后) | 日活≥1万对 | auth/content/incentive/interactive 四个包分别copy独立部署=Nacos+Gateway微服务 |