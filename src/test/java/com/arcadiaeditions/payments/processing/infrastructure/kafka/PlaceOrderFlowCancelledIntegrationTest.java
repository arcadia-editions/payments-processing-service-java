package com.arcadiaeditions.payments.processing.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.arcadiaeditions.orders.checkout.events.avro.OrderCreated;
import com.arcadiaeditions.payments.processing.config.DockerComposeInitializer;
import com.arcadiaeditions.payments.processing.events.avro.PaymentDeclined;
import com.arcadiaeditions.payments.processing.events.avro.PaymentFailed;
import com.arcadiaeditions.payments.processing.events.avro.PaymentRetried;
import com.arcadiaeditions.payments.processing.events.avro.PaymentRetryExhausted;
import java.time.Duration;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Payments side of the cancelled paths of place-order-flow. Real Kafka and schema registry (docker compose). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@DockerComposeInitializer.EnableDockerCompose
class PlaceOrderFlowCancelledIntegrationTest {

    @Autowired
    KafkaProperties kafkaProperties;

    @Value("${app.kafka.topics.order-created-event-v1.topic}")
    String orderCreatedTopic;

    @Test
    void declined() {
        String orderId = "ORD-" + UUID.randomUUID();
        try (var events = new FlowEvents(kafkaProperties, orderId);
                var producer = new KafkaProducer<Object, Object>(kafkaProperties.buildProducerProperties())) {
            producer.send(new ProducerRecord<>(
                    orderCreatedTopic,
                    null,
                    new OrderCreated(orderId, 1, "CUST-098", "c098@example.com", "pm_decline", 25.0, "EUR")));
            producer.flush();

            events.await(PaymentDeclined.class);
            events.quiet(Duration.ofSeconds(5));

            // exactly one event for the order, of any type: PaymentDeclined and nothing else
            assertThat(events.types()).containsExactly(PaymentDeclined.class);
        }
    }

    @Test
    void retriesExhausted() {
        String orderId = "ORD-" + UUID.randomUUID();
        try (var events = new FlowEvents(kafkaProperties, orderId);
                var producer = new KafkaProducer<Object, Object>(kafkaProperties.buildProducerProperties())) {
            producer.send(new ProducerRecord<>(
                    orderCreatedTopic,
                    null,
                    new OrderCreated(orderId, 1, "CUST-098", "c098@example.com", "pm_fail_always", 25.0, "EUR")));
            producer.flush();

            events.await(PaymentRetryExhausted.class);
            events.quiet(Duration.ofSeconds(5));

            // topics are polled together, so compare per type and attemptCount, not arrival order across topics
            assertThat(events.types())
                    .containsExactlyInAnyOrder(
                            PaymentFailed.class,
                            PaymentRetried.class,
                            PaymentFailed.class,
                            PaymentRetried.class,
                            PaymentFailed.class,
                            PaymentRetryExhausted.class);
            assertThat(events.all().stream()
                            .filter(PaymentFailed.class::isInstance)
                            .map(e -> ((PaymentFailed) e).getAttemptCount()))
                    .containsExactlyInAnyOrder(1, 2, 3);
            assertThat(events.all().stream()
                            .filter(PaymentRetried.class::isInstance)
                            .map(e -> ((PaymentRetried) e).getAttemptCount()))
                    .containsExactlyInAnyOrder(1, 2);
        }
    }
}
