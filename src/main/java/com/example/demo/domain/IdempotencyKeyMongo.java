package com.example.demo.domain;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class IdempotencyKeyMongo implements Serializable {
    String paymentIntegratorAccountId;
    String requestId;

    public static IdempotencyKeyMongo from(IdempotencyKey idempotencyKey) {
        return IdempotencyKeyMongo.builder()
                .paymentIntegratorAccountId(idempotencyKey.getPaymentIntegratorAccountId())
                .requestId(idempotencyKey.getRequestId())
                .build();
    }

    public IdempotencyKey toKey() {
        return IdempotencyKey.builder()
                .paymentIntegratorAccountId(this.getPaymentIntegratorAccountId())
                .requestId(this.getRequestId())
                .build();
    }
}
