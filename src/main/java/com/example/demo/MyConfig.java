package com.example.demo;

import com.example.demo.domain.IdempotencyKey;
import com.example.demo.domain.IdempotencyKeySerializer;
import com.hazelcast.config.Config;
import com.hazelcast.config.SerializationConfig;
import com.hazelcast.config.SerializerConfig;
import com.hazelcast.config.YamlConfigBuilder;
import com.hazelcast.spring.context.SpringManagedContext;
import org.springframework.boot.autoconfigure.hazelcast.HazelcastProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class MyConfig /*extends AbstractReactiveMongoConfiguration */ {
    /*@Bean
    public MongoClient mongoClient() {
        return MongoClients.create();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Override
    protected String getDatabaseName() {
        return "testdb";
    }*/


    @Bean
    public Config config(HazelcastProperties properties, ApplicationContext applicationContext) throws IOException {
        final Config config = new YamlConfigBuilder(properties.getConfig().getURL()).build();
        //Config config = new Config();

        SpringManagedContext managedContext = new SpringManagedContext();
        managedContext.setApplicationContext(applicationContext);
        config.setManagedContext(managedContext);

        return config;
    }

    private void addSerializer(SerializationConfig serializationConfig, Class<IdempotencyKey> typeClass, Class<IdempotencyKeySerializer> serializerClass) {
        SerializerConfig serializerConfig = new SerializerConfig();
        serializerConfig.setClass(serializerClass);
        serializerConfig.setTypeClass(typeClass);
        serializationConfig.addSerializerConfig(serializerConfig);
    }
}
