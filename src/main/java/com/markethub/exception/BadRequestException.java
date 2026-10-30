package com.markethub.exception;

/** Бизнес-ошибка, транслируется в HTTP 400. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
