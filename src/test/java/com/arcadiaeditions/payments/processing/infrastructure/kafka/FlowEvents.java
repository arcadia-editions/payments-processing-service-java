package com.arcadiaeditions.payments.processing.infrastructure.kafka;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;

/**
 * Collects every record of any payments-processing event topic whose payload carries the orderId, in arrival order.
 * Shared by the place-order-flow integration tests to prove "and nothing else".
 */
final class FlowEvents implements AutoCloseable {

    static final List<String> TOPICS = List.of(
                    "payment-authorized",
                    "payment-declined",
                    "payment-failed",
                    "payment-retried",
                    "payment-retry-exhausted",
                    "payment-captured",
                    "payment-capture-failed",
                    "payment-voided",
                    "payment-void-failed")
            .stream()
            .map(n -> "payments-processing." + n + ".event.avro.v1")
            .toList();

    private final KafkaConsumer<Object, Object> consumer;
    private final String orderId;
    private final List<Object> seen = new ArrayList<>();

    FlowEvents(KafkaProperties kafkaProperties, String orderId) {
        this.orderId = orderId;
        var props = kafkaProperties.buildConsumerProperties();
        props.put("group.id", "flow-events-it-" + UUID.randomUUID());
        props.put("auto.offset.reset", "earliest");
        this.consumer = new KafkaConsumer<>(props);
        consumer.subscribe(TOPICS);
        consumer.poll(Duration.ofSeconds(2));
    }

    private void pollOnce() {
        for (ConsumerRecord<Object, Object> r : consumer.poll(Duration.ofSeconds(1))) {
            if (r.value() != null && r.value().toString().contains(orderId)) {
                seen.add(r.value());
            }
        }
    }

    /** Polls until an event of the type has been collected (any other types are kept too), up to 60s. */
    void await(Class<?> type) {
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            if (seen.stream().anyMatch(type::isInstance)) {
                return;
            }
            pollOnce();
        }
        if (seen.stream().noneMatch(type::isInstance)) {
            throw new AssertionError("no " + type.getSimpleName() + " for " + orderId + " within 60s, seen " + seen);
        }
    }

    /** Keeps collecting for the window, so late or duplicate events show up. */
    void quiet(Duration window) {
        long end = System.currentTimeMillis() + window.toMillis();
        while (System.currentTimeMillis() < end) {
            pollOnce();
        }
    }

    <T> T first(Class<T> type) {
        return seen.stream()
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst()
                .orElseThrow();
    }

    /** Snapshot of everything collected so far, in arrival order. */
    List<Object> all() {
        return List.copyOf(seen);
    }

    List<Class<?>> types() {
        return seen.stream().<Class<?>>map(Object::getClass).toList();
    }

    @Override
    public void close() {
        consumer.close();
    }
}
