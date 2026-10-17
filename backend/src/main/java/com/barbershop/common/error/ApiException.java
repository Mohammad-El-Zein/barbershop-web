package com.barbershop.common.error;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Business error with an HTTP status and a stable message code. The code is
 * returned to clients as {@code code} and resolved to a localized message
 * (messages*.properties) for {@code detail}. Extra properties (e.g. the current
 * booking status for a used email link) are added to the problem detail.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final transient Object[] args;
    private final transient Map<String, Object> properties = new LinkedHashMap<>();
    private final transient Map<String, String> fieldErrors = new LinkedHashMap<>();

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

    public static ApiException forbidden(String code, Object... args) {
        return new ApiException(HttpStatus.FORBIDDEN, code, args);
    }

    /** 410: an email link that was already used or has expired. */
    public static ApiException gone(String code, Object... args) {
        return new ApiException(HttpStatus.GONE, code, args);
    }

    /**
     * 400 validation.failed with per-field message codes (localized by the handler into
     * {@code errors}, the same shape as Bean Validation errors).
     */
    public static ApiException validation(Map<String, String> fieldCodes) {
        ApiException e = new ApiException(HttpStatus.BAD_REQUEST, "validation.failed");
        e.fieldErrors.putAll(fieldCodes);
        return e;
    }

    public Map<String, String> fieldErrors() {
        return fieldErrors;
    }

    public ApiException with(String key, Object value) {
        if (value != null) properties.put(key, value);
        return this;
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

    public Map<String, Object> properties() {
        return properties;
    }
}
