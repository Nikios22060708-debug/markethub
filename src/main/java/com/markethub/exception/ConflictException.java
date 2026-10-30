package com.markethub.exception;

/** Бизнес-ошибка, транслируется в HTTP 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
