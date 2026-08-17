package com.xindong.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@Profile({"prod"})
@ConditionalOnBean(StringRedisTemplate.class)
@RequiredArgsConstructor
public class RedisSmsCodeStore implements SmsCodeStore {

    private final StringRedisTemplate redis;

    @Override
    public Boolean setIfAbsent(String key, String value, long timeout, TimeUnit unit) {
        return redis.opsForValue().setIfAbsent(key, value, timeout, unit);
    }

    @Override
    public void set(String key, String value, long timeout, TimeUnit unit) {
        redis.opsForValue().set(key, value, timeout, unit);
    }

    @Override
    public String get(String key) {
        return redis.opsForValue().get(key);
    }

    @Override
    public void delete(String key) {
        redis.delete(key);
    }
}
