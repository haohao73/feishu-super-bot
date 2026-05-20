package com.bluemountain.bot.common;

import lombok.Getter;

/**
 * 业务异常，所有模块的异常都抛这个或其子类
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(String message) {
        super(message);
        this.code = 500;
    }
}
