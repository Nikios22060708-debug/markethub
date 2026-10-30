package com.markethub.exception;

/** Бизнес-ошибка, транслируется в HTTP 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
