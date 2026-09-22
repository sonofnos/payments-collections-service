package com.sonofnos.payments.service.event;

/**
 * Kafka-shaped publisher interface for payment lifecycle events. The current
 * implementation ({@link RedisStreamsEventPublisher}) backs this with a Redis
 * Stream, not Kafka - see README for why. Swapping in a real Kafka producer
 * later is an adapter + config change behind this same interface, not a
 * redesign of anything that calls it.
 */
public interface EventPublisher {
    void publish(String topic, PaymentEvent event);
}
