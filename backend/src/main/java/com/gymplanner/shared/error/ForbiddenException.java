package com.gymplanner.shared.error;

import java.util.List;
import org.springframework.http.HttpStatus;

/** 403 with an application code, e.g. {@code PASSWORD_CHANGE_REQUIRED}. */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String code, String detail) {
        super(HttpStatus.FORBIDDEN, code, detail, List.of());
    }
}
