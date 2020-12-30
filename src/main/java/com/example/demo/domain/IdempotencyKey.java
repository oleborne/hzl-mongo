package com.example.demo.domain;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class IdempotencyKey {
    String paymentIntegratorAccountId;
    String requestId;
}
