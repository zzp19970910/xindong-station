package com.xindong.common.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.xindong.common.enums.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;

@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "统一响应信封")
public class Result<T> {

    @Schema(description = "业务错误码 0xxxx=成功 1xxxx=提示 2xxxx=冲突 3xxxx=参数/权限 4xxxx=不存在 5xxxx=服务端", example = "00000")
    private String code;

    @Schema(description = "提示文案", example = "操作成功")
    private String msg;

    @Schema(description = "返回数据，无数据为null(非省略)")
    private T data;

    @Schema(description = "服务器时间戳秒级", example = "1744560000")
    private Long ts;

    public Result() {
        this.ts = Instant.now().getEpochSecond();
    }

    public static <T> Result<T> success() {
        Result<T> r = new Result<>();
        r.code = ErrorCode.SUCCESS.getCode();
        r.msg = ErrorCode.SUCCESS.getMsg();
        return r;
    }

    public static <T> Result<T> success(T data) {
        Result<T> r = success();
        r.data = data;
        return r;
    }

    public static <T> Result<T> error(ErrorCode code) {
        Result<T> r = new Result<>();
        r.code = code.getCode();
        r.msg = code.getMsg();
        return r;
    }

    public static <T> Result<T> error(ErrorCode code, String customMsg) {
        Result<T> r = new Result<>();
        r.code = code.getCode();
        r.msg = (customMsg == null || customMsg.isEmpty()) ? code.getMsg() : customMsg;
        return r;
    }

    /** 🔴红线专用：错误响应同时带结构化数据(如B6余额差额) */
    @SuppressWarnings("unchecked")
    public static <T> Result<T> error(ErrorCode code, String customMsg, Object data) {
        Result<T> r = new Result<>();
        r.code = code.getCode();
        r.msg = (customMsg == null || customMsg.isEmpty()) ? code.getMsg() : customMsg;
        r.data = (T) data;
        return r;
    }

    /** 🔴红线专用：原生错误码+消息+附加数据 */
    @SuppressWarnings("unchecked")
    public static <T> Result<T> error(String rawCode, String rawMsg, Object data) {
        Result<T> r = new Result<>();
        r.code = rawCode;
        r.msg = rawMsg;
        r.data = (T) data;
        return r;
    }

    public static <T> Result<T> error(String rawCode, String rawMsg) {
        Result<T> r = new Result<>();
        r.code = rawCode;
        r.msg = rawMsg;
        return r;
    }

    public boolean isOK() {
        return code != null && code.charAt(0) == '0';
    }
}