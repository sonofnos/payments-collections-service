package com.sonofnos.payments.service.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Publishes payment lifecycle events onto a Redis Stream. Redis Streams was
 * chosen over real Kafka for this portfolio project because no free managed
 * Kafka broker could be provisioned non-interactively in the time budgeted
 * for it (see README). The event is serialized once and appended with
 * XADD; consumers can read it with a consumer group for at-least-once
 * delivery semantics, same as a Kafka consumer group would.
 */
@Component
public class RedisStreamsEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RedisStreamsEventPublisher.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisStreamsEventPublisher(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(String topic, PaymentEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            MapRecord<String, String, String> record = StreamRecords.newRecord()
                    .in(topic)
                    .ofMap(Map.of(
                            "paymentId", event.paymentId().toString(),
                            "status", event.status().name(),
                            "payload", payload
                    ));
            redisTemplate.opsForStream().add(record);
        } catch (Exception e) {
            // event publishing must never break the payment write path
            log.error("Failed to publish payment event {} to stream {}", event.paymentId(), topic, e);
        }
    }
}
