package com.example.demo.controller;

import com.example.demo.domain.XrefUserEntry;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.query.Predicates;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuples;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@RestController
@Slf4j
@RequestMapping("/x-ref")
public class XrefApi {
  public static final String USER_XREF = "userxref";
  public static final int SEED_COUNT = 2000000;
  private final IMap<String, XrefUserEntry> userXrefMap;
  private final HazelcastInstance hzInstance;

  public XrefApi(HazelcastInstance hzInstance) {
    this.hzInstance = hzInstance;
    this.userXrefMap = hzInstance.getMap(USER_XREF);
  }

  @PostMapping("/seed-users")
  public Mono<Integer> seedUsers() {
    if (!userXrefMap.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }
    return Flux.range(0, SEED_COUNT)
            .map(
                    id ->
                            Tuples.of(
                                    String.format("goog%08d", id),
                                    XrefUserEntry.builder()
                                            .phoenixId(String.format("phx%08d", id))
                                            .uOpenId(String.format("uOpen%08d", id))
                                            .edcId(String.format("edc%08d", id))
                                            .build()))
            .buffer(100)
            .map(
                    tuples -> {
                      Map map = new HashMap<>(tuples.size());
                      tuples.forEach(t -> map.put(t.getT1(), t.getT2()));
                      return map;
                    })
            .flatMap(
                    m ->
                            ((Mono<Void>) Mono.fromCompletionStage(userXrefMap.putAllAsync(m)))
                                    .thenReturn(m.size()))
            .reduce(Integer::sum);
  }

  @PostMapping("/create-user/{googleUserId}")
  public void createUser(@PathVariable("googleUserId") String googleUserId) {
  }

  @GetMapping("/get-user/phx/{userId}")
  public Mono<XrefUserEntry> getUserByPhxId(@PathVariable("userId") String phxUserId) {
    // userXrefMap.entrySet(Predicates.equal("phoenixId", phxUserId))
    return Mono.fromCallable(
            () ->
                    hzInstance
                            .getSql()
                            .execute("SELECT this FROM userxref WHERE phoenixId = ?", phxUserId))
            .subscribeOn(Schedulers.boundedElastic())
            .map(Iterable::iterator)
            .filter(Iterator::hasNext)
            .map(Iterator::next)
            .map(row -> row.<XrefUserEntry>getObject(0))
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));
  }

  @GetMapping("/get-user2/phx/{userId}")
  public Mono<XrefUserEntry> getUserByPhxId2(@PathVariable("userId") String phxUserId) {
    return Mono.fromCallable(() -> userXrefMap.entrySet(Predicates.equal("phoenixId", phxUserId)))
            .subscribeOn(Schedulers.boundedElastic())
            .map(Iterable::iterator)
            .filter(Iterator::hasNext)
            .map(Iterator::next)
            .map(Map.Entry::getValue)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));
  }

  @GetMapping("/get-user/edc/{userId}")
  public Mono<XrefUserEntry> getUserByEdcId(@PathVariable("userId") String edcUserId) {
    return Mono.fromCallable(() -> userXrefMap.entrySet(Predicates.equal("edcId", edcUserId)))
            .subscribeOn(Schedulers.boundedElastic())
            .map(result -> result.iterator())
            .filter(Iterator::hasNext)
            .map(Iterator::next)
            .map(Map.Entry::getValue)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));
  }

  @GetMapping("/get-user/goog/{userId}")
  public Mono<XrefUserEntry> getUserByGoogleId(@PathVariable("userId") String googleUserId) {
    return Mono.fromCompletionStage(userXrefMap.getAsync(googleUserId))
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));
  }
}
