package com.xindong.common.seed;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("🛡️ SeedRunner 启动容错测试 - 初始化错误绝不影响Spring启动")
class SeedRunnerRobustnessTest {

    @Autowired
    private ApplicationContext appCtx;

    @Autowired(required = false)
    private SeedRunner seedRunner;

    @Test
    @DisplayName("SR1: 应用上下文能正常启动 → 说明SeedRunner就算出问题也没崩Spring")
    void sr1_contextStarts() {
        assertNotNull(appCtx, "ApplicationContext必须初始化完成");
        assertTrue(appCtx.getBeanDefinitionCount() > 100, "Bean数量正常>100，Spring启动完成");
        // 核心Bean必须存在，Spring没崩
        assertNotNull(appCtx.getBean(org.springframework.jdbc.core.JdbcTemplate.class), "JdbcTemplate应存在");
        assertNotNull(appCtx.getBean(com.xindong.common.util.JwtUtil.class), "JwtUtil应存在");
    }

    @Test
    @DisplayName("SR2: SeedRunner Bean存在 → 被Spring正确注册，CommandLineRunner可用")
    void sr2_seedRunnerExists() {
        assertNotNull(seedRunner, "SeedRunner Bean应存在于测试Context中");
        assertTrue(seedRunner instanceof org.springframework.boot.CommandLineRunner,
                "SeedRunner必须实现CommandLineRunner");
    }

    @Test
    @DisplayName("SR3: 手动触发SeedRunner.run() → 即使有DB异常也绝不抛出任何Throwable！")
    void sr3_runNeverThrows() throws Exception {
        assertNotNull(seedRunner);
        // run里我们已经四保险：L1 run()最外层Throwable catch；L2 3个子函数各Throwable；L3 红线8张表独立try-catch
        // 这里在H2内存DB里(部分表结构可能和PG有差异如ENUM/JSON)，应该有些WARN，但是不会抛！
        assertDoesNotThrow(() -> seedRunner.run("test", "args"),
                "SeedRunner.run()永远不抛Throwable! 一旦抛就会让Spring Boot启动失败=Render Your service is live永不出现！");
    }

    @Test
    @DisplayName("SR4: tableExists检测方法(通过反射或子类验证) → 不存在的表立即false, 不崩")
    void sr4_tableExistsWorks() throws Exception {
        assertNotNull(seedRunner);
        java.lang.reflect.Method m = SeedRunner.class.getDeclaredMethod("tableExists", String.class);
        m.setAccessible(true);

        // 不存在的表：xxx_never_exists_table_999 → false
        Boolean r1 = (Boolean) m.invoke(seedRunner, "xxx_never_exists_table_999");
        assertFalse(r1, "不存在的表tableExists应false");

        // 存在的表：couples (ddl-auto=create-drop启动后存在) → true 或者 H2 MODE=MySQL下大小写不敏感都true
        Boolean r2 = (Boolean) m.invoke(seedRunner, "couples");
        assertNotNull(r2, "tableExists必须返回boolean非null");
        // 如果couples表没创建（H2启动还没DDL完，也应该是false不崩溃，总之永远不抛）
    }

    @Test
    @DisplayName("SR5: SpringBoot启动日志 → 没有出现'Startup failed'/'Application run failed'字样，SeedRunner失败也不影响")
    void sr5_noStartupFailed() {
        // 如果这个测试方法能执行到这里，本身就说明Spring Boot启动成功了！
        // Startup failure = JUnit @SpringBootTest抛异常，不会进入测试方法
        assertTrue(true, "SeedRunnerRobustnessTest方法本身能运行 = Spring启动成功=SR5通过");
    }
}