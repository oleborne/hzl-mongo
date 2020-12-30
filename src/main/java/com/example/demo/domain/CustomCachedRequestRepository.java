package com.example.demo.domain;

import org.springframework.data.mongodb.core.ChangeStreamEvent;
import reactor.core.publisher.Flux;

public interface CustomCachedRequestRepository {
    Flux<IdempotencyKeyMongo> findMostRecentEntries(int i);

    Flux<ChangeStreamEvent<CachedRequestDocument>> changeStream();

    Flux<ChangeStreamEvent<CachedRequestDocument>> changeStream(Object resumeToken);
}
