package com.example.demo;

import com.example.demo.domain.*;
import com.hazelcast.config.*;
import com.hazelcast.nio.serialization.DataSerializable;
import com.hazelcast.nio.serialization.StreamSerializer;
import com.hazelcast.spring.context.SpringManagedContext;
import org.springframework.boot.autoconfigure.hazelcast.HazelcastProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.List;

import static com.example.demo.controller.MyApi.CACHED_REQUESTS;

@Configuration
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

  private <T> void addSerializer(Config config, StreamSerializer<?> serializer) {
    SerializerConfig serializerConfig = new SerializerConfig();
    serializerConfig.setImplementation(serializer);
    serializerConfig.setTypeClass(extractTargetClass(serializer));
    config.getSerializationConfig().addSerializerConfig(serializerConfig);
  }

  private Class<?> extractTargetClass(StreamSerializer<?> serializer) {
    Class<?> serializerClass = serializer.getClass();
    return Arrays.stream(serializerClass.getGenericInterfaces())
            .filter(type -> type instanceof ParameterizedType)
            .map(type -> (ParameterizedType)type)
            .filter(parameterizedType -> StreamSerializer.class.equals(parameterizedType.getRawType()))
            .map(parameterizedType -> parameterizedType.getActualTypeArguments()[0])
            .filter(type -> type instanceof Class)
            .map(type -> (Class<?>)type)
            .findFirst()
            .orElseThrow(() -> new UnsupportedOperationException("Invalid Serializer definition: " + serializerClass));
  }
}
