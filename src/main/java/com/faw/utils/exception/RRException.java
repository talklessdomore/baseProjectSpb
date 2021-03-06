package com.faw.utils.exception;

/**
 * 自定义异常
 *
 * @author liushengbin
 * @email liushengbin7@gmail.com
 * @date 2016年10月27日 下午10:11:27
 */
public class RRException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private String msg;
    private int code = 500;

    public RRException(Throwable e) {
        super(e.getMessage());
        this.msg = e.getMessage();
    }

    public RRException(int code, Throwable e) {
        super(e.getMessage());
        this.code = code;
        this.msg = e.getMessage();
    }

    public RRException(String msg) {
        super(msg);
        this.msg = msg;
    }

    public RRException(String msg, Throwable e) {
        super(msg, e);
        this.msg = msg;
    }

    public RRException(String msg, int code) {
        super(msg);
        this.msg = msg;
        this.code = code;
    }

    public RRException(String msg, int code, Throwable e) {
        super(msg, e);
        this.msg = msg;
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }


}
