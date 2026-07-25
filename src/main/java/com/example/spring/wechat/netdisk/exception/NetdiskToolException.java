package com.example.spring.wechat.netdisk.exception;

/**
 * 网盘工具统一业务异常。
 *
 * <p>对外部 OAuth、MCP、文件上传等能力做封装时，底层异常通常比较技术化。
 * 这里统一转换成业务异常，方便工具层给微信用户返回更清楚的失败原因。</p>
 */
public class NetdiskToolException extends RuntimeException {

    public NetdiskToolException(String message) {
        super(message);
    }

    public NetdiskToolException(String message, Throwable cause) {
        super(message, cause);
    }
}
