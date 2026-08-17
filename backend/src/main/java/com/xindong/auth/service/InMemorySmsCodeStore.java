package com.xindong.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@Profile({"dev","render"})
public class InMemorySmsCodeStore implements SmsCodeStore {

    private final Map<String, ValueWithExpiry> store = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sms-code-cleaner");
        t.setDaemon(true);
        return t;
    });

    public InMemorySmsCodeStore() {
        cleaner.scheduleAtFixedRate(this::cleanExpired, 5, 5, TimeUnit.SECONDS);
        log.warn("[DEV][SMS] 使用内存版验证码存储（非生产，重启清空），请不要用于生产环境！");
    }

    @Override
    public synchronized Boolean setIfAbsent(String key, String value, long timeout, TimeUnit unit) {
        ValueWithExpiry existing = store.get(key);
        if (existing != null && !existing.isExpired()) return Boolean.FALSE;
        store.put(key, new ValueWithExpiry(value, System.currentTimeMillis() + unit.toMillis(timeout)));
        return Boolean.TRUE;
    }

    @Override
    public synchronized void set(String key, String value, long timeout, TimeUnit unit) {
        store.put(key, new ValueWithExpiry(value, System.currentTimeMillis() + unit.toMillis(timeout)));
    }

    @Override
    public synchronized String get(String key) {
        ValueWithExpiry v = store.get(key);
        if (v == null || v.isExpired()) {
            store.remove(key);
            return null;
        }
        return v.value;
    }

    @Override
    public synchronized void delete(String key) {
        store.remove(key);
    }

    private void cleanExpired() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(e -> e.getValue().expireAt <= now);
    }

    private record ValueWithExpiry(String value, long expireAt) {
        boolean isExpired() { return System.currentTimeMillis() > expireAt; }
    }
}