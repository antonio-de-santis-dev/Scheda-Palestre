package com.gymplanner.shared.error;

import java.util.List;
import org.springframework.http.HttpStatus;

/** 422: the request is well formed but incompatible with the current domain state. */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String code, String detail) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, code, detail, List.of());
    }

    public BusinessRuleException(String code, String detail, List<FieldViolation> errors) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, code, detail, errors);
    }
}
