package com.example.demo.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ChangeStreamEvent;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@RequiredArgsConstructor
public class CustomCachedRequestRepositoryImpl implements CustomCachedRequestRepository {
  private final ReactiveMongoTemplate mongoTemplate;

  @Override
  public Flux<IdempotencyKeyMongo> findMostRecentEntries(int limit) {
    Query query = new Query().limit(limit).with(Sort.by(Sort.Direction.DESC, "created"));
    query.fields().include("_id");

    return mongoTemplate
            .find(query, CachedRequestDocument.class)
            .map(CachedRequestDocument::getKey);
  }

  @Override
  public Flux<ChangeStreamEvent<CachedRequestDocument>> changeStream() {
    return mongoTemplate.changeStream(CachedRequestDocument.class).listen();
  }

  @Override
  public Flux<ChangeStreamEvent<CachedRequestDocument>> changeStream(Object resumeToken) {
    return mongoTemplate.changeStream(CachedRequestDocument.class).resumeAt(resumeToken).listen();
  }
}
