package com.skillbook.payment;

public class PaymentSignatureException extends RuntimeException {

    public PaymentSignatureException(String message, Throwable cause) {
        super(message, cause);
    }
}