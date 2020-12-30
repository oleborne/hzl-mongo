package com.example.demo.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@RequiredArgsConstructor
public class CustomCachedRequestRepositoryImpl implements CustomCachedRequestRepository {
  private final ReactiveMongoTemplate mongoTemplate;

  @Override
  public Flux<IdempotencyKey> findAllKeys() {
    Query query = new Query();
    query.fields().include("_id");
    return mongoTemplate
        .find(query, CachedRequestDocument.class)
        .map(CachedRequestDocument::getKey);
  }
}
