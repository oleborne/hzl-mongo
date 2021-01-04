package com.example.demo.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hazelcast.com.google.common.base.Suppliers;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.EntryStore;
import com.hazelcast.map.IMap;
import com.hazelcast.map.MapLoaderLifecycleSupport;
import com.mongodb.MongoException;
import com.mongodb.client.model.changestream.OperationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.BsonValue;
import org.springframework.data.mongodb.core.ChangeStreamEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.util.backoff.ExponentialBackOff;
import reactor.core.Disposable;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class MongoStore
        implements EntryStore<IdempotencyKey, CachedRequest>, MapLoaderLifecycleSupport {

  private final ObjectMapper objectMapper;

  private final CachedRequestRepository requestRepository;
  private Disposable subcription = () -> {
  };
  private BsonValue resumeToken;
  private HazelcastInstance hazelcastInstance;

  @Override
  public void store(IdempotencyKey key, MetadataAwareValue<CachedRequest> value) {
    log.info("Store document for key {}", key);
    doStore(key, value).block();
  }

  private Mono<CachedRequestDocument> doStore(
          IdempotencyKey key, MetadataAwareValue<CachedRequest> value) {
    try {
      final CachedRequestDocument.CachedRequestDocumentBuilder cachedRequestDocumentBuilder =
              CachedRequestDocument.builder()
                      .key(IdempotencyKeyMongo.from(key))
                      .created(Instant.now())
                      .requestHash(value.getValue().getRequestHash())
                      .creator(this.hazelcastInstance.getCluster().getLocalMember().getUuid().toString());
      Object cachedResponse = value.getValue().getCachedResponse();
      if (cachedResponse != null) {
        cachedRequestDocumentBuilder
            .responseType(value.getValue().getCachedResponse().getClass().getTypeName())
            .cachedResponse(objectMapper.writeValueAsString(value.getValue().getCachedResponse()));
      }
      if (value.getExpirationTime() != Long.MAX_VALUE) {
        cachedRequestDocumentBuilder.expiration(Instant.ofEpochMilli(value.getExpirationTime()));
      }
      final CachedRequestDocument document = cachedRequestDocumentBuilder.build();
      return requestRepository.save(document);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void storeAll(Map<IdempotencyKey, MetadataAwareValue<CachedRequest>> map) {
    log.info("Storing in bulk {} entries", map.size());
    final List<Mono<CachedRequestDocument>> storeOperations =
        map.entrySet().stream()
            .map(e -> doStore(e.getKey(), e.getValue()))
            .collect(Collectors.toList());
    Mono.when(storeOperations).publishOn(Schedulers.boundedElastic()).block();
  }

  @Override
  public void delete(IdempotencyKey key) {
    log.info("Deleting a single entry for key {}", key);
    requestRepository.deleteById(IdempotencyKeyMongo.from(key)).block();
  }

  @Override
  public void deleteAll(Collection<IdempotencyKey> keys) {
    log.info("Deleting in bulk {} docs", keys.size());
    Flux.fromIterable(keys)
            .map(IdempotencyKeyMongo::from)
            .publishOn(Schedulers.boundedElastic())
            .map(requestRepository::deleteById)
            .count()
            .block();
  }

  @Override
  public MetadataAwareValue<CachedRequest> load(IdempotencyKey key) {
    log.info("Loading a single entry for key {}", key);
    return doLoad(key).block();
  }

  private Mono<MetadataAwareValue<CachedRequest>> doLoad(IdempotencyKey key) {
    return requestRepository
            .findById(IdempotencyKeyMongo.from(key))
            .map(this::convertToCachedRequest);
  }

  @Override
  public Map<IdempotencyKey, MetadataAwareValue<CachedRequest>> loadAll(
          Collection<IdempotencyKey> keys) {
    log.info("Loading in bulk {} docs", keys.size());
    return requestRepository
            .findAllById(keys.stream().map(IdempotencyKeyMongo::from).collect(Collectors.toList()))
            .map(doc -> Tuples.of(doc.getKey().toKey(), convertToCachedRequest(doc)))
            .collectMap(Tuple2::getT1, Tuple2::getT2)
            .block();
  }

  @Override
  public Iterable<IdempotencyKey> loadAllKeys() {
    log.info("Priming IMDG with only most recent entries");
    return requestRepository
            .findMostRecentEntries(100)
            .map(IdempotencyKeyMongo::toKey)
            .toIterable();
  }

  @Override
  public void init(HazelcastInstance hazelcastInstance, Properties properties, String mapName) {
    this.hazelcastInstance = hazelcastInstance;
    subscribeToChangeStream(Suppliers.memoize(() -> hazelcastInstance.getMap(mapName)));
  }

  private void subscribeToChangeStream(Supplier<IMap<IdempotencyKey, CachedRequest>> mapSupplier) {
    BackOff backOffChangeStream = new ExponentialBackOff();
    final AtomicReference<BackOffExecution> backOffExecution = new AtomicReference<>();
    final AtomicReference<Instant> nextAttemptWith1sMargin =
            new AtomicReference<>(Instant.now().plus(1, ChronoUnit.SECONDS));
    this.subcription =
            requestRepository
                    .changeStream(this.resumeToken)
                    .filter(event -> OperationType.INSERT.equals(event.getOperationType()))
                    .filter(event -> notGeneratedByCurrentCluster(event.getBody()))
                    .buffer(Duration.of(5, ChronoUnit.SECONDS))
                    .log()
                    .subscribe(
                            events -> {
                              this.resumeToken = events.get(0).getResumeToken();
                              Set<IdempotencyKey> keySet =
                                      events.stream()
                                              .map(ChangeStreamEvent::getBody)
                                              .filter(Objects::nonNull)
                                              .map(CachedRequestDocument::getKey)
                                              .map(IdempotencyKeyMongo::toKey)
                                              .filter(key -> !mapSupplier.get().containsKey(key))
                                              .collect(Collectors.toSet());
                              mapSupplier.get().loadAll(keySet, false);
                              backOffExecution.set(null);
                            },
                            error -> {
                              log.warn("Change stream error", error);
                              if (error instanceof MongoException) {
                                MongoException exception = (MongoException) error;
                                if (exception.getCode() == 40573) {
                                  log.warn(
                                          "MongoDB server not part of a replica-set, will not try to resubscribe");
                                  return;
                                }
                              }
                              // let's wait a bit and resubscribe
                              if (backOffExecution.get() == null
                                      || Instant.now().isAfter(nextAttemptWith1sMargin.get())) {
                                backOffExecution.set(backOffChangeStream.start());
                              }
                              Duration nextBackOff =
                                      Duration.of(backOffExecution.get().nextBackOff(), ChronoUnit.MILLIS);
                              nextAttemptWith1sMargin.set(
                                      Instant.now().plus(nextBackOff).plus(1, ChronoUnit.SECONDS));
                              Mono.delay(nextBackOff).subscribe(aVoid -> subscribeToChangeStream(mapSupplier));
                            });
  }

  @Override
  public void destroy() {
    this.subcription.dispose();
  }

  private MetadataAwareValue<CachedRequest> convertToCachedRequest(CachedRequestDocument doc) {
    try {
      CachedRequest request =
              CachedRequest.builder()
                      .requestHash(doc.requestHash)
                      .cachedResponse(
                              objectMapper.readValue(
                                      doc.getCachedResponse(), Class.forName(doc.getResponseType())))
                      .build();
      long expiration = Long.MAX_VALUE;
      if (doc.getExpiration() != null) {
        expiration = doc.getExpiration().toEpochMilli();
      }
      return new MetadataAwareValue<>(request, expiration);
    } catch (JsonProcessingException | ClassNotFoundException e) {
      throw Exceptions.propagate(e);
    }
  }

  private boolean notGeneratedByCurrentCluster(CachedRequestDocument doc) {
    if (doc == null) {
      return false;
    }
    if (doc.getCreator() == null) {
      return true;
    }
    try {
      UUID uuid = UUID.fromString(doc.getCreator());
      return this.hazelcastInstance.getCluster().getMembers().stream()
              .noneMatch(m -> m.getUuid().equals(uuid));
    } catch (IllegalArgumentException exception) {
      return true;
    }
  }
}
