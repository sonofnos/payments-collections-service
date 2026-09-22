package com.sonofnos.payments.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 4.1 switched its own auto-configured ObjectMapper to the new
 * tools.jackson (Jackson 3) engine, but springdoc-openapi and jjwt-jackson
 * still pull in classic com.fasterxml.jackson:jackson-databind (2.x) - this
 * bean supplies that classic ObjectMapper explicitly for the places in this
 * service (webhook parsing, Redis Streams event serialization) that use it.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
