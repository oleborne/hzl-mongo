package com.example.demo.domain;

import reactor.core.publisher.Flux;

public interface CustomCachedRequestRepository {
    Flux<IdempotencyKey> findAllKeys();
}
