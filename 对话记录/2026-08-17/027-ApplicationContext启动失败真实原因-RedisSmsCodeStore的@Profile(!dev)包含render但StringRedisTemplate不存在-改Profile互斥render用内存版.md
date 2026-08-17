---
title: "ApplicationContext启动失败真实原因-RedisSmsCodeStore的@Profile(!dev)包含render但StringRedisTemplate不存在-改Profile互斥render用内存版"
date: "2026-08-17"
number: "027"
tags: ["UnsatisfiedDependencyException", "StringRedisTemplate缺失", "SmsCodeStore Profile冲突", "RedisSmsCodeStore @Profile(!dev)"]
status: "已归档"
---

# 对话记录 2026-08-17 / 027

## 一、原始问题

026轮让用户加了6个DEBUG/ddl-auto=none环境变量后，Render日志里终于打出了完整的错误堆栈（而不是只有Error starting ApplicationContext），核心是：

```
UnsatisfiedDependencyException:
  Error creating bean with name 'authController'
    -> AuthService constructor param4
      -> Error creating bean with name 'redisSmsCodeStore'
        -> No qualifying bean of type 'org.springframework.data.redis.core.StringRedisTemplate' available
```

后面跟着的就是Port scan timeout（因为ApplicationContext启动失败，Tomcat没启动，$PORT没监听）。

## 二、思考过程

### 维度1：根因定位
application-render.yml里已经写了：
```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
      - org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration
```
所以RedisAutoConfiguration不加载，StringRedisTemplate这个Bean就不存在于ApplicationContext里。

但Grep两个SmsCodeStore实现：
1. RedisSmsCodeStore.java L11：`@Profile("!dev")` → 只要Spring激活的Profile不是"dev"，就会创建这个Bean。Render环境激活的Profile是「render,embed-frontend」，显然不是dev，所以RedisSmsCodeStore被创建，构造函数需要StringRedisTemplate → 找不到 → UnsatisfiedDependencyException
2. InMemorySmsCodeStore.java L15：`@Profile("dev")` → 只有dev Profile才创建，Render环境不加载 → 没有可用的SmsCodeStore兜底

### 维度2：两种可能的解法
解法A：不改代码，给RedisSmsCodeStore加`@ConditionalOnBean(StringRedisTemplate.class)`，让它在没有RedisTemplate时自动不创建——但InMemorySmsCodeStore还是@Profile("dev")，Render里不创建，还是会没SmsCodeStore。
解法B：改两个@Profile，让render和dev都用内存版（InMemorySmsCodeStore），prod用Redis版（RedisSmsCodeStore）——最干净，零配置。
选择解法B，因为Render的实例是Free Web Service，免费实例本身会冷启动、可能重启，内存版验证码存储虽然重启后清空，但Render没Redis（免费版没有内建Redis），只能用内存版，否则要自己搭Redis外部服务。生产环境再自己连正式Redis。

### 维度3：Profile互斥设计
最终Profile：
| 实现类 | @Profile | 激活条件 |
|--------|----------|----------|
| RedisSmsCodeStore | {"prod"} + @ConditionalOnBean(StringRedisTemplate) | 只有prod环境，并且RedisAutoConfiguration真的加载了StringRedisTemplate才创建，双保险 |
| InMemorySmsCodeStore | {"dev","render"} | dev本地开发 或 Render部署Free层时创建 |

Render激活的Profile是「render,embed-frontend」，所以只会创建InMemorySmsCodeStore（内存版，不需要任何外部服务），不会创建RedisSmsCodeStore（因为profile不是prod），完美避开StringRedisTemplate缺失问题。

### 维度4：历史上下文
006轮用户问「部署Redis吗？」，当时决定不用Redis免费层（Render Redis要付费），所以排除了RedisAutoConfiguration，但忘了SmsCodeStore的Profile问题——直到Render环境真的启动时才爆出来。这是典型的「本地dev走一套，线上render走另一套，两个配置不交叉测试」的问题。

## 三、最终回答
直接告诉用户找到真实错误了（Redis Bean缺失，不是端口问题），给Profile修改的结果，然后告诉git add哪9个文件push。
代码修改用PowerShell逐行改（因为多行Replace CR/LF不匹配会失败），改完验证：
- RedisSmsCodeStore L5: 加了import ConditionalOnBean
- RedisSmsCodeStore L12: @Profile({"prod"})
- RedisSmsCodeStore L13: @ConditionalOnBean(StringRedisTemplate.class)
- InMemorySmsCodeStore L15: @Profile({"dev","render"})

## 四、命令行执行
1. Grep找SmsCodeStore接口和两个实现的Profile注解和StringRedisTemplate依赖
2. Glob确认路径（3个Java文件都在com.xindong.auth.service包）
3. 第一次PowerShell修改：用Replace整段（因为Windows CR/LF问题，RedisSmsCodeStore替换失败，InMemory成功）
4. 第二次PowerShell精准修复：用ReadAllLines逐行修改——4行插入ConditionalOnBean import，11行改@Profile({"prod"})，12行插入@ConditionalOnBean——三修改全成功

## 五、补充说明
为什么端口超时不是真的端口问题？
Spring Boot的启动流程：
1. 创建ApplicationContext → 2. 扫描Bean定义 → 3. 实例化Bean + 依赖注入（这一步UnsatisfiedDependencyException就炸了）→ 4. 触发BeanPostProcessor → 5. refresh上下文完成 → 6. 才启动WebServer（Tomcat/Jetty/Netty）绑定到server.port
所以只要第3步Bean创建失败，永远不会执行到第6步，Render的PORT扫描永远找不到LISTEN socket，最后必然报Port scan timeout。
看到Port scan timeout时第一步应该是日志往上翻找Bean创建相关的异常，而不是真的去检查server.port绑定。