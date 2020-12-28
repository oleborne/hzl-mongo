package com.example.demo.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hazelcast.config.Config;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.ManagedContext;
import com.hazelcast.map.EntryStore;
import com.hazelcast.map.MapLoaderLifecycleSupport;
import com.hazelcast.spring.context.SpringAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@SpringAware
@Component
public class MongoStore implements EntryStore<IdempotencyKey, CachedRequest>, MapLoaderLifecycleSupport {

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    CachedRequestRepository requestRepository;

    @Override
    public void store(IdempotencyKey key, MetadataAwareValue<CachedRequest> value) {
        doStore(key, value).block();
    }

    private Mono<CachedRequestDocument> doStore(IdempotencyKey key, MetadataAwareValue<CachedRequest> value) {
        try {
            final CachedRequestDocument.CachedRequestDocumentBuilder cachedRequestDocumentBuilder = CachedRequestDocument.builder().key(key).created(Instant.now())
                    .requestHash(value.getValue().getRequestHash())
                    .responseType(value.getValue().getCachedResponse().getClass().getTypeName())
                    .cachedResponse(objectMapper.writeValueAsString(value.getValue().getCachedResponse()));
            if (value.getExpirationTime() != Long.MAX_VALUE) {
                cachedRequestDocumentBuilder.expiration(Instant.ofEpochMilli(value.getExpirationTime()));
            }
            final CachedRequestDocument document = cachedRequestDocumentBuilder
                    .build();
            return requestRepository.save(document);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void storeAll(Map<IdempotencyKey, MetadataAwareValue<CachedRequest>> map) {
        final List<Mono<CachedRequestDocument>> storeOperations = map.entrySet().stream()
                .map(e -> doStore(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
        Mono.when(storeOperations)
                .publishOn(Schedulers.boundedElastic())
                .block();
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
        return requestRepository.findById(key)
                .map(doc -> {
                    try {
                        CachedRequest request = CachedRequest.builder()
                                .requestHash(doc.requestHash)
                                .cachedResponse(objectMapper.readValue(doc.getCachedResponse(), Class.forName(doc.getResponseType())))
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
    public Map<IdempotencyKey, MetadataAwareValue<CachedRequest>> loadAll(Collection<IdempotencyKey> keys) {
        Map<IdempotencyKey, MetadataAwareValue<CachedRequest>> result = new HashMap<>(keys.size());
        final List<Mono<MetadataAwareValue<CachedRequest>>> loadOperations = keys.stream()
                .map(k -> this.doLoad(k)
                        .doOnNext(v -> result.put(k, v)))
                .collect(Collectors.toList());
        Mono.when(loadOperations)
                .publishOn(Schedulers.boundedElastic())
                .block();
        return result;
    }

    @Override
    public Iterable<IdempotencyKey> loadAllKeys() {
        return requestRepository.findAll()
                .map(CachedRequestDocument::getKey)
                .reduce(new LinkedList<IdempotencyKey>(), (list, key) -> {
                    list.add(key);
                    return list;
                })
                .block();
    }

    @Override
    public void init(HazelcastInstance hazelcastInstance, Properties properties, String mapName) {
        final Config config = hazelcastInstance.getConfig();
        final ManagedContext managedContext = config.getManagedContext();
        managedContext.initialize(this);
    }

    @Override
    public void destroy() {

    }
}
