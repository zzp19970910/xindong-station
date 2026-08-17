package com.xindong.common.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLog;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class RenderDatabaseUrlPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final DeferredLog log = new DeferredLog();

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String rawUrl = getRawDatabaseUrl(environment);
        if (rawUrl == null || rawUrl.isBlank()) {
            return;
        }

        try {
            ParsedDbUrl parsed = parseAndNormalize(rawUrl);
            Map<String, Object> overrides = new HashMap<>();
            overrides.put("spring.datasource.url", parsed.jdbcUrl());
            overrides.put("DB_USER", parsed.username());
            overrides.put("DB_PASS", parsed.password());
            overrides.put("spring.datasource.username", parsed.username());
            overrides.put("spring.datasource.password", parsed.password());

            PropertySource<Map<String, Object>> ps = new MapPropertySource("render-db-overrides", overrides);
            environment.getPropertySources().addFirst(ps);

            log.info("Render DATABASE_URL normalized: " + parsed.jdbcUrl() + " (user=" + parsed.username() + ")");
            log.replayTo(RenderDatabaseUrlPostProcessor.class);
        } catch (Exception e) {
            log.warn("Failed to parse DATABASE_URL, using raw value: " + e.getMessage());
            log.replayTo(RenderDatabaseUrlPostProcessor.class);
        }
    }

    private static String getRawDatabaseUrl(ConfigurableEnvironment env) {
        String url = env.getProperty("DATABASE_URL");
        if (url == null) url = env.getProperty("spring.datasource.url");
        if (url == null) {
            Properties sysProps = System.getProperties();
            if (sysProps.containsKey("DATABASE_URL")) url = sysProps.getProperty("DATABASE_URL");
        }
        if (url == null) url = System.getenv("DATABASE_URL");
        return url;
    }

    private static ParsedDbUrl parseAndNormalize(String raw) throws Exception {
        String schemeLess = raw;
        boolean isJdbc = raw.startsWith("jdbc:");
        boolean isLibpq = raw.startsWith("postgres://") || raw.startsWith("postgresql://");

        if (!isJdbc && !isLibpq) {
            if (raw.contains("://")) {
                throw new IllegalArgumentException("Unsupported scheme: " + raw.substring(0, raw.indexOf("://")));
            }
        }

        String pureUri;
        if (isJdbc) {
            pureUri = raw.substring(5);
        } else {
            pureUri = raw;
        }

        URI uri = URI.create(pureUri);
        String host = uri.getHost();
        int port = uri.getPort() == -1 ? 5432 : uri.getPort();
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (path.startsWith("/")) path = path.substring(1);
        String dbName = path;

        String username = null;
        String password = null;
        String userInfo = uri.getUserInfo();
        if (userInfo != null && !userInfo.isBlank()) {
            int colon = userInfo.indexOf(':');
            if (colon >= 0) {
                username = userInfo.substring(0, colon);
                password = userInfo.substring(colon + 1);
            } else {
                username = userInfo;
            }
        }

        Map<String, String> params = new HashMap<>();
        String query = uri.getQuery();
        if (query != null && !query.isBlank()) {
            for (String kv : query.split("&")) {
                int eq = kv.indexOf('=');
                if (eq > 0) {
                    String k = kv.substring(0, eq);
                    String v = kv.substring(eq + 1);
                    params.put(k, v);
                    if ("user".equalsIgnoreCase(k) && username == null) username = v;
                    if ("password".equalsIgnoreCase(k) && password == null) password = v;
                }
            }
        }
        params.putIfAbsent("sslmode", "require");

        StringBuilder jdbc = new StringBuilder();
        jdbc.append("jdbc:postgresql://").append(host).append(':').append(port).append('/').append(dbName);
        if (!params.isEmpty()) {
            jdbc.append('?');
            boolean first = true;
            for (Map.Entry<String, String> e : params.entrySet()) {
                if (!first) jdbc.append('&');
                jdbc.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
                first = false;
            }
        }

        return new ParsedDbUrl(jdbc.toString(),
            username == null ? "" : username,
            password == null ? "" : password);
    }

    private record ParsedDbUrl(String jdbcUrl, String username, String password) {}
}