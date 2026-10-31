package com.globalpay.lab.common.api;

import org.springframework.http.HttpStatus;

public enum ResultCode {

    SUCCESS(HttpStatus.OK, "Success"),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "Invalid request parameters"),
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "Ledger account not found"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error"),
    INVALID_JOURNAL_STATUS(HttpStatus.CONFLICT, "Invalid journal status"),

    MERCHANT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "Merchant not found"
    );

    private final HttpStatus status;
    private final String message;

    ResultCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public int getCode() {
        return status.value();
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

}
