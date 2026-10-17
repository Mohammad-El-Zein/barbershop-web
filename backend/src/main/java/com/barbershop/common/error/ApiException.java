package com.barbershop.common.error;

import org.springframework.http.HttpStatus;

/**
 * Business error with an HTTP status and a stable message code. The code is
 * returned to clients as {@code code} and resolved to a localized message
 * (messages*.properties) for {@code detail}.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final transient Object[] args;

    public ApiException(HttpStatus status, String code, Object... args) {
        super(code);
        this.status = status;
        this.code = code;
        this.args = args;
    }

    public static ApiException notFound(String code, Object... args) {
        return new ApiException(HttpStatus.NOT_FOUND, code, args);
    }

    public static ApiException conflict(String code, Object... args) {
        return new ApiException(HttpStatus.CONFLICT, code, args);
    }

    public static ApiException unprocessable(String code, Object... args) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, args);
    }

    public static ApiException badRequest(String code, Object... args) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, args);
    }

    public static ApiException unauthorized(String code, Object... args) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, args);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Object[] args() {
        return args;
    }
}
