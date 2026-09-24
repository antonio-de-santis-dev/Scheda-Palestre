package com.gymplanner.shared.error;

import java.util.List;
import org.springframework.http.HttpStatus;

/** 401: missing/expired session or rejected credentials (always generic). */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String code, String detail) {
        super(HttpStatus.UNAUTHORIZED, code, detail, List.of());
    }
}
