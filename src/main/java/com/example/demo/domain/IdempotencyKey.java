package com.example.demo.domain;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class IdempotencyKey implements Serializable {
    String paymentIntegratorAccountId;
    String requestId;
}
