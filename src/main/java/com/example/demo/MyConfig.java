package com.example.demo;

import com.example.demo.domain.*;
import com.hazelcast.config.*;
import com.hazelcast.nio.serialization.StreamSerializer;
import com.hazelcast.spring.context.SpringManagedContext;
import org.springframework.boot.autoconfigure.hazelcast.HazelcastProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

import static com.example.demo.controller.MyApi.CACHED_REQUESTS;

@Configuration
public class MyConfig {

  @Bean
  public Config config(
      HazelcastProperties properties, ApplicationContext applicationContext, MongoStore mongoStore)
      throws IOException {
    final Config config = new YamlConfigBuilder(properties.getConfig().getURL()).build();

    MapConfig mapConfig =
        new MapConfig(CACHED_REQUESTS)
            .setMapStoreConfig(new MapStoreConfig().setImplementation(mongoStore));
    config.addMapConfig(mapConfig);

    addSerializer(config, IdempotencyKey.class, IdempotencyKeySerializer.class);
    addSerializer(config, CachedRequest.class, CachedRequestSerializer.class);

    SpringManagedContext managedContext = new SpringManagedContext();
    managedContext.setApplicationContext(applicationContext);
    config.setManagedContext(managedContext);

    return config;
  }

  private <T> void addSerializer(
      Config config, Class<T> typeClass, Class<? extends StreamSerializer<T>> serializerClass) {
    SerializerConfig serializerConfig = new SerializerConfig();
    serializerConfig.setClass(serializerClass);
    serializerConfig.setTypeClass(typeClass);
    config.getSerializationConfig().addSerializerConfig(serializerConfig);
  }
}
