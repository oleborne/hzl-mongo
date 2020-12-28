package com.example.demo.controller;

import com.example.demo.domain.CachedRequest;
import com.example.demo.domain.IdempotencyKey;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletionStage;

import static java.time.Instant.now;

@RestController
@Slf4j
public class MyApi {
    public static final String MAP_NAME = "MY_MAP";
    public static final String MAP_NAME2 = "MY_MAP2";

    @Autowired
    HazelcastInstance hzInstance;

    @GetMapping("/hello")
    Mono<String> sayHello(String name) {
        return Mono.just("Hello,  " + name);
    }

    @PostMapping("/map/{keyId}")
    Mono<String> putValue(@PathVariable("keyId") String keyId, @RequestBody String value) {
        final CompletionStage<String> result = hzInstance.<String, String>getMap(MAP_NAME).putAsync(keyId, value);
        return Mono.fromCompletionStage(result);
    }

    @GetMapping("/map/{keyId}")
    Mono<String> getValue(@PathVariable("keyId") String keyId) {
        final CompletionStage<String> value = hzInstance.<String, String>getMap(MAP_NAME).getAsync(keyId);
        return Mono.fromCompletionStage(value);
    }

    @PostMapping("/doStuff")
    Mono<ResponseObject> putValue2(@RequestBody RequestObject request) {
        return Mono.create(sink -> {
            final IdempotencyKey idempotencyKey = IdempotencyKey.builder()
                    .paymentIntegratorAccountId("12345")
                    .requestId(request.getKeyId())
                    .build();

            final IMap<IdempotencyKey, CachedRequest> idempotencyCache = hzInstance.<IdempotencyKey, CachedRequest>getMap(MAP_NAME2);

            idempotencyCache.getAsync(idempotencyKey)
                    .thenApply(cachedRequest -> {
                        try {
                            log.info("Found " + cachedRequest);
                            if (cachedRequest != null && cachedRequest.getCachedResponse() != null) {
                                return (ResponseObject) cachedRequest.getCachedResponse();
                            }
                            Thread.sleep(1000);
                            final ResponseObject responseObject = ResponseObject.builder()
                                    .created(now())
                                    .fullMessage("Hello " + request.getName())
                                    .build();
                            final CachedRequest newCachedRequest = CachedRequest.builder()
                                    .requestHash("ABC")
                                    .cachedResponse(responseObject)
                                    .build();
                            idempotencyCache.put(idempotencyKey, newCachedRequest);
                            return responseObject;
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .thenAccept(sink::success)
                    .exceptionally(t -> {
                        sink.error(t);
                        return null;
                    });
        });
    }

}
