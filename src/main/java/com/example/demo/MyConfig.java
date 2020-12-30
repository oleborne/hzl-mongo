package com.example.demo;

import com.example.demo.domain.MongoStore;
import com.hazelcast.config.*;
import com.hazelcast.nio.serialization.StreamSerializer;
import com.hazelcast.spring.context.SpringManagedContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.hazelcast.HazelcastProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static com.example.demo.controller.MyApi.CACHED_REQUESTS;

@Configuration
@Slf4j
public class MyConfig {

  @Bean
  public Config config(
      HazelcastProperties properties,
      ApplicationContext applicationContext,
      MongoStore mongoStore,
      List<StreamSerializer<?>> serializers)
      throws IOException {
    final Config config = new YamlConfigBuilder(properties.getConfig().getURL()).build();

    MapConfig mapConfig =
        new MapConfig(CACHED_REQUESTS)
            .setMapStoreConfig(new MapStoreConfig().setImplementation(mongoStore));
    config.addMapConfig(mapConfig);

    serializers.forEach(s -> addSerializer(config, s));

    SpringManagedContext managedContext = new SpringManagedContext();
    managedContext.setApplicationContext(applicationContext);
    config.setManagedContext(managedContext);

    return config;
  }

  private void addSerializer(Config config, StreamSerializer<?> serializer) {
    SerializationConfig serializationConfig = config.getSerializationConfig();

    Stream<Class<?>> targetClasses = extractTargetClass(serializer);
    long count =
            targetClasses
                    .map(
                            targetClass -> {
                              SerializerConfig serializerConfig =
                                      new SerializerConfig()
                                              .setImplementation(serializer)
                                              .setTypeClass(targetClass);
                              serializationConfig.addSerializerConfig(serializerConfig);
                              log.info("Adding serializer {} for target class {}", serializer, targetClass);
                              return targetClass;
                            })
                    .count();

    if (count == 0) {
      log.warn("No target class detected for serializer {}", serializer);
    }
  }

  private Stream<Class<?>> extractTargetClass(StreamSerializer<?> serializer) {
    Class<?> serializerClass = serializer.getClass();
    return Arrays.stream(serializerClass.getGenericInterfaces())
            .filter(type -> type instanceof ParameterizedType)
            .map(type -> (ParameterizedType) type)
            .filter(parameterizedType -> StreamSerializer.class.equals(parameterizedType.getRawType()))
            .map(parameterizedType -> parameterizedType.getActualTypeArguments()[0])
            .filter(type -> type instanceof Class)
            .map(type -> (Class<?>) type);
  }
}
