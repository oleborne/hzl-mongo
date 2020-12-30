package com.example.demo.controller;

import com.example.demo.domain.CachedRequest;
import com.example.demo.domain.IdempotencyKey;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.hazelcast.com.google.common.collect.ImmutableList;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import static java.time.Instant.now;

@RestController
@Slf4j
@RequestMapping("/api/{tenantId}")
public class MyApi {
  public static final String MAP_NAME = "MY_MAP";
  public static final String CACHED_REQUESTS = "cached-requests";
  public static final List<String> IGNORED_PROPERTIES = ImmutableList.of("timeHeader");
  private final IMap<IdempotencyKey, CachedRequest> idempotencyCache;
  private final HazelcastInstance hzInstance;
  private final ObjectMapper objectMapper;

  public MyApi(HazelcastInstance hzInstance, ObjectMapper objectMapper) {
    this.hzInstance = hzInstance;
    this.idempotencyCache = hzInstance.<IdempotencyKey, CachedRequest>getMap(CACHED_REQUESTS);
    this.objectMapper =
        objectMapper
            .copy()
            .setSerializerFactory(
                objectMapper
                    .getSerializerFactory()
                    .withSerializerModifier(
                        new BeanSerializerModifier() {
                          @Override
                          public List<BeanPropertyWriter> changeProperties(
                              SerializationConfig config,
                              BeanDescription beanDesc,
                              List<BeanPropertyWriter> beanProperties) {
                            beanProperties.removeIf(p -> IGNORED_PROPERTIES.contains(p.getName()));
                            return super.changeProperties(config, beanDesc, beanProperties);
                          }
                        }));
  }

  @GetMapping("/hello")
  Mono<String> sayHello(String name) {
    return Mono.just("Hello,  " + name);
  }

  @PostMapping("/map/{keyId}")
  Mono<String> putValue(@PathVariable("keyId") String keyId, @RequestBody String value) {
    final CompletionStage<String> result =
        hzInstance.<String, String>getMap(MAP_NAME).putAsync(keyId, value);
    return Mono.fromCompletionStage(result);
  }

  @GetMapping("/map/{keyId}")
  Mono<String> getValue(@PathVariable("keyId") String keyId) {
    final CompletionStage<String> value =
        hzInstance.<String, String>getMap(MAP_NAME).getAsync(keyId);
    return Mono.fromCompletionStage(value);
  }

  @PostMapping("/doStuff")
  Mono<ResponseObject> putValue2(
      @RequestBody Mono<RequestObject> requestMono, ServerWebExchange exchange) {
    return requestMono.flatMap(
        request -> {
          log.info("Received request {} sent at {}", request.getKeyId(), request.getTimeHeader());
          IdempotencyKey idempotencyKey = extractIdempotencyKey(request, exchange);
          CompletionStage<ResponseObject> response =
              idempotencyCache
                  .getAsync(idempotencyKey)
                  .thenApply(
                      cachedRequest ->
                          getCachedResponse(request, cachedRequest)
                              .orElseGet(
                                  () -> {
                                    CachedRequest newCachedRequest = CachedRequest.builder().requestHash(computeHash(request)).build();
                                    idempotencyCache.put(idempotencyKey, newCachedRequest);

                                    ResponseObject responseObject = doGenerateResponse(request);

                                    newCachedRequest.setCachedResponse(responseObject);
                                    idempotencyCache.put(idempotencyKey, newCachedRequest);
                                    return responseObject;
                                  }));

          return Mono.fromCompletionStage(response);
        });
  }


  private IdempotencyKey extractIdempotencyKey(RequestObject request, ServerWebExchange exchange) {
    String piaid = exchange.getRequest().getPath().pathWithinApplication().subPath(3, 4).value();
    return IdempotencyKey.builder()
        .paymentIntegratorAccountId(piaid)
        .requestId(request.getKeyId())
        .build();
  }

  private ResponseObject doGenerateResponse(RequestObject request) {
    try {
      log.info("Faking slow process for 10s");
      Thread.sleep(10000); // simulate slow process
      log.info("Done sleeping");
      return ResponseObject.builder()
          .created(now())
          .fullMessage("Hello " + request.getName())
          .build();
    } catch (InterruptedException e) {
      throw Exceptions.propagate(e);
    }
  }

  private Optional<ResponseObject> getCachedResponse(
      RequestObject request, CachedRequest cachedRequest) {
    log.info("Found " + cachedRequest);
    if (cachedRequest != null) {
      if (cachedRequest.getCachedResponse() != null) {
        if (cachedRequest.getRequestHash().equals(computeHash(request))) {
          ResponseObject cachedResponse = (ResponseObject) cachedRequest.getCachedResponse();
          cachedResponse.setCreated(Instant.now());
          return Optional.of(cachedResponse);
        }
        throw new InvalidRequestException();
      }
      throw new TryAgainLaterException();
    }
    return Optional.empty();
  }

  private String computeHash(RequestObject requestObject) {
    try {
      String serializedRequest = objectMapper.writeValueAsString(requestObject);
      return DigestUtils.md5DigestAsHex(serializedRequest.getBytes(StandardCharsets.UTF_8));
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
