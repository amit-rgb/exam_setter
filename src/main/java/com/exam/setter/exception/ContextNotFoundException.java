package com.exam.setter.exception;

public class ContextNotFoundException extends RuntimeException {
    public ContextNotFoundException(String message) {
        super(message);
    }
}