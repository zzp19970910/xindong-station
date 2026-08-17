package com.xindong.common.exception;

import com.xindong.common.enums.ErrorCode;
import com.xindong.common.result.Result;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MeterRegistry meterRegistry;
    private Counter bizErrCounter;
    private Counter sysErrCounter;

    @PostConstruct
    void init() {
        bizErrCounter = Counter.builder("xindong.exceptions.biz.total")
                .description("业务异常计数(2/3/4段位)").register(meterRegistry);
        sysErrCounter = Counter.builder("xindong.exceptions.sys.total")
                .description("系统异常计数(5段位)").register(meterRegistry);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Object>> onBiz(BusinessException e) {
        String code = e.getCodeValue();
        String first = code.substring(0, 1);
        if ("2".equals(first) || "3".equals(first) || "4".equals(first)) {
            bizErrCounter.increment();
            log.warn("[BizEx] code={} msg={}", code, e.getMsg());
        } else if ("5".equals(first)) {
            sysErrCounter.increment();
            log.error("[SysEx] code={} msg={}", code, e.getMsg(), e);
        }
        HttpStatus status = switch (code) {
            case "20701", "20801", "20301" -> HttpStatus.CONFLICT;
            case "30004" -> HttpStatus.NOT_FOUND;
            case "4003" -> HttpStatus.FORBIDDEN;
            case "50301", "30001" -> HttpStatus.BAD_REQUEST;
            case "50703" -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.OK;
        };
        // 🔴红线B6/B8等：异常附加数据(差额/校验明细)透传到响应体data字段
        if (e.getData() != null) {
            return ResponseEntity.status(status).body(Result.error(e.getCode(), e.getMsg(), e.getData()));
        }
        return ResponseEntity.status(status).body(Result.error(e.getCode(), e.getMsg()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<Result<Object>> onValid(Exception e) {
        bizErrCounter.increment();
        String msg;
        if (e instanceof MethodArgumentNotValidException ex) {
            msg = ex.getBindingResult().getFieldErrors().stream()
                    .map(fe -> fe.getField() + ":" + fe.getDefaultMessage())
                    .collect(Collectors.joining("; "));
        } else {
            BindException be = (BindException) e;
            msg = be.getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage).collect(Collectors.joining("; "));
        }
        log.warn("[ValidEx] {}", msg);
        return ResponseEntity.ok(Result.error(ErrorCode.PARAM_ERROR, msg));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Object>> onConstraint(ConstraintViolationException e) {
        bizErrCounter.increment();
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage).collect(Collectors.joining("; "));
        return ResponseEntity.ok(Result.error(ErrorCode.PARAM_ERROR, msg));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<Result<Object>> onParam(Exception e) {
        bizErrCounter.increment();
        return ResponseEntity.ok(Result.error(ErrorCode.PARAM_ERROR, e.getMessage()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Result<Object>> onAuth(AuthenticationException e) {
        bizErrCounter.increment();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Result.error(ErrorCode.AUTH_REQUIRED));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Object>> onForbidden(AccessDeniedException e) {
        bizErrCounter.increment();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Result.error(ErrorCode.COUPLE_DATA_FORBIDDEN));
    }

    @ExceptionHandler({DataIntegrityViolationException.class,
            SQLIntegrityConstraintViolationException.class})
    public ResponseEntity<Result<Object>> onUniqueConflict(Exception e) {
        sysErrCounter.increment();
        log.warn("[DB冲突]", e);
        return ResponseEntity.ok(Result.error(ErrorCode.DB_UNIQUE_CONFLICT));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Result<Object>> on404(NoHandlerFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error("40000", "接口不存在"));
    }

    @ExceptionHandler({HttpRequestMethodNotSupportedException.class,
            HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<Result<Object>> onMethod(Exception e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Result.error("40001", "请求方法/类型不支持"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Object>> onIllegalArg(IllegalArgumentException e) {
        bizErrCounter.increment();
        log.warn("[IllegalArgEx] {}", e.getMessage());
        return ResponseEntity.ok(Result.error(ErrorCode.PARAM_ERROR, e.getMessage()));
    }

    @ExceptionHandler({NumberFormatException.class, NullPointerException.class, ClassCastException.class})
    public ResponseEntity<Result<Object>> onParamRuntime(Exception e) {
        bizErrCounter.increment();
        log.warn("[ParamRuntimeEx] type={} msg={}", e.getClass().getSimpleName(), e.getMessage());
        return ResponseEntity.ok(Result.error(ErrorCode.PARAM_ERROR, "参数格式错误: " + e.getMessage()));
    }

    @ExceptionHandler(JpaSystemException.class)
    public ResponseEntity<Result<Object>> onJpaSystem(JpaSystemException e) {
        sysErrCounter.increment();
        String msg = e.getMessage() == null ? "" : e.getMessage();
        // 🔴B5红线：Hibernate 把 DB trigger 的 50703 SIGNAL 包装成 JpaSystemException 了，这里解包还原
        if (msg.contains("50703") || msg.contains("BLOCK_ILLEGAL_COIN_UPDATE") || msg.contains("trg_block_illegal_coin_update")) {
            log.error("[DB触发器拦截 50703] {}", msg.split("\n")[0]);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error(ErrorCode.COIN_DB_TRIGGER_BLOCKED));
        }
        log.error("[JpaSystemEx]", e);
        return ResponseEntity.status(HttpStatus.OK).body(Result.error(ErrorCode.SYSTEM_BUSY, e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Object>> onCatchAll(Exception e) {
        sysErrCounter.increment();
        log.error("[CatchAllEx]", e);
        // QA调试：50002时把异常根因180字符透出到msg（生产再关）
        String root = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        Throwable cause = e.getCause();
        if (cause != null && cause.getMessage() != null) root += " | cause: " + cause.getMessage();
        if (root.length() > 180) root = root.substring(0, 180);
        return ResponseEntity.status(HttpStatus.OK).body(Result.error(ErrorCode.SYSTEM_BUSY, "系统繁忙(DEBUG): " + root));
    }
}