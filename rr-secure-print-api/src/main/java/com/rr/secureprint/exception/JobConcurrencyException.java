package com.rr.secureprint.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class JobConcurrencyException extends RuntimeException {
    public JobConcurrencyException(String message) {
        super(message);
    }
}
