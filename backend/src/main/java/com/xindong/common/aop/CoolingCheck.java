package com.xindong.common.aop;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CoolingCheck {
    /**
     * WRITE：冷静模式拦截新建/编辑 抛21202
     * SETTING：冷静模式拦截设置项 抛21203
     */
    String value() default "WRITE";
}