package com.xindong.auth.service;

import java.util.concurrent.TimeUnit;

public interface SmsCodeStore {

    Boolean setIfAbsent(String key, String value, long timeout, TimeUnit unit);

    void set(String key, String value, long timeout, TimeUnit unit);

    String get(String key);

    void delete(String key);
}