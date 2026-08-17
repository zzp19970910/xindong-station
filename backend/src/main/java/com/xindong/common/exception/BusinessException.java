package com.xindong.common.exception;

import com.xindong.common.enums.ErrorCode;
import lombok.Getter;

import java.util.Map;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, Object> data;

    public BusinessException(ErrorCode code) {
        super(code.getMsg());
        this.code = code;
        this.data = null;
    }

    public BusinessException(ErrorCode code, String customMsg) {
        super(customMsg);
        this.code = code;
        this.data = null;
    }

    /** 🔴红线专用：错误响应附带结构化数据(如B6余额差额current/need/short) */
    public BusinessException(ErrorCode code, String customMsg, Map<String, Object> extraData) {
        super(customMsg);
        this.code = code;
        this.data = extraData;
    }

    public String getCodeValue() {
        return code.getCode();
    }

    public String getMsg() {
        return super.getMessage() == null ? code.getMsg() : super.getMessage();
    }
}