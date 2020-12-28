package com.example.demo.domain;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("idempotency")
@Data
@Builder
public class CachedRequestDocument {
    @Id
    IdempotencyKey key;
    Instant created;
    Instant expiration;
    String requestHash;
    String responseType;
    String cachedResponse;
}
