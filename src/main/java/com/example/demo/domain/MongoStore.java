package com.example.demo.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hazelcast.map.EntryStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MongoStore implements EntryStore<IdempotencyKey, CachedRequest> {

  final ObjectMapper objectMapper;

  final CachedRequestRepository requestRepository;

  @Override
  public void store(IdempotencyKey key, MetadataAwareValue<CachedRequest> value) {
    doStore(key, value).block();
  }

  private Mono<CachedRequestDocument> doStore(
      IdempotencyKey key, MetadataAwareValue<CachedRequest> value) {
    try {
      final CachedRequestDocument.CachedRequestDocumentBuilder cachedRequestDocumentBuilder =
          CachedRequestDocument.builder()
              .key(key)
              .created(Instant.now())
              .requestHash(value.getValue().getRequestHash());
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
    final List<Mono<CachedRequestDocument>> storeOperations =
        map.entrySet().stream()
            .map(e -> doStore(e.getKey(), e.getValue()))
            .collect(Collectors.toList());
    Mono.when(storeOperations).publishOn(Schedulers.boundedElastic()).block();
  }

  @Override
  public void delete(IdempotencyKey key) {
    requestRepository.deleteById(key).block();
  }

  @Override
  public void deleteAll(Collection<IdempotencyKey> keys) {
    Mono.when(keys.stream().map(requestRepository::deleteById).collect(Collectors.toList()))
        .publishOn(Schedulers.boundedElastic())
        .block();
  }

  @Override
  public MetadataAwareValue<CachedRequest> load(IdempotencyKey key) {
    return doLoad(key).block();
  }

  private Mono<MetadataAwareValue<CachedRequest>> doLoad(IdempotencyKey key) {
    return requestRepository
        .findById(key)
        .map(
            doc -> {
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
                throw new RuntimeException(e);
              }
            });
  }

  @Override
  public Map<IdempotencyKey, MetadataAwareValue<CachedRequest>> loadAll(
      Collection<IdempotencyKey> keys) {
    Map<IdempotencyKey, MetadataAwareValue<CachedRequest>> result = new HashMap<>(keys.size());
    final List<Mono<MetadataAwareValue<CachedRequest>>> loadOperations =
        keys.stream()
            .map(k -> this.doLoad(k).doOnNext(v -> result.put(k, v)))
            .collect(Collectors.toList());
    Mono.when(loadOperations).publishOn(Schedulers.boundedElastic()).block();
    return result;
  }

  @Override
  public Iterable<IdempotencyKey> loadAllKeys() {
    return requestRepository.findAllKeys().toIterable();
  }
}
