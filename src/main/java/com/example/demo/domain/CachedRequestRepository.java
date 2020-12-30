package com.example.demo.domain;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CachedRequestRepository
    extends ReactiveMongoRepository<CachedRequestDocument, IdempotencyKeyMongo>,
        CustomCachedRequestRepository {}
