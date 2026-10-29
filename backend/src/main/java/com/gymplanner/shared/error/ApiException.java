package com.gymplanner.shared.error;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Base class for domain errors translated into RFC 9457 Problem Details by
 * {@link GlobalExceptionHandler}. {@code code} is the stable application error code the
 * frontend relies on; {@code detail} is a human readable English message.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final List<FieldViolation> errors;
    private final Map<String, Object> properties = new LinkedHashMap<>();

    protected ApiException(HttpStatus status, String code, String detail, List<FieldViolation> errors) {
        super(detail);
        this.status = status;
        this.code = code;
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public List<FieldViolation> errors() {
        return errors;
    }

    /**
     * Adds a machine-readable member to the Problem Details (e.g. the conflicting days), so the
     * frontend can write a precise message without parsing {@code detail}. Never put secrets here.
     */
    public ApiException with(String name, Object value) {
        properties.put(name, value);
        return this;
    }

    public Map<String, Object> properties() {
        return java.util.Collections.unmodifiableMap(properties);
    }
}
