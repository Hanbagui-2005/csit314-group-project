package com.csit314.platform.service;

// business rule failure; the message is safe to show to the user
public class ServiceException extends RuntimeException {

    public ServiceException(String message) {
        super(message);
    }
}
