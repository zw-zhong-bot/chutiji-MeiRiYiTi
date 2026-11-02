package com.chutiji.common;

/** 业务异常，携带错误码，由全局异常处理器转成统一包络。 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
