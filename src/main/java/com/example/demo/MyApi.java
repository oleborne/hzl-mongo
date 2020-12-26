package com.example.demo;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletionStage;

@RestController
public class MyApi {
    public static final String MAP_NAME = "MY_MAP";

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
}
