package com.arcadiaeditions.payments.processing.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.arcadiaeditions.orders.checkout.events.avro.OrderCreated;
import com.arcadiaeditions.payments.processing.config.DockerComposeInitializer;
import com.arcadiaeditions.payments.processing.core.outbound.jpa.PaymentRepository;
import com.arcadiaeditions.payments.processing.events.avro.PaymentAuthorized;
import com.arcadiaeditions.payments.processing.events.avro.PaymentFailed;
import com.arcadiaeditions.payments.processing.events.avro.PaymentRetried;
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

/** Payments side of the retry loop of place-order-flow. Real Kafka and schema registry (docker compose). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@DockerComposeInitializer.EnableDockerCompose
class PlaceOrderFlowRetryIntegrationTest {

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    KafkaProperties kafkaProperties;

    @Value("${app.kafka.topics.order-created-event-v1.topic}")
    String orderCreatedTopic;

    @Test
    void failsOnceThenAuthorized() {
        String orderId = "ORD-" + UUID.randomUUID();
        try (var events = new FlowEvents(kafkaProperties, orderId);
                var producer = new KafkaProducer<Object, Object>(kafkaProperties.buildProducerProperties())) {
            producer.send(new ProducerRecord<>(
                    orderCreatedTopic,
                    null,
                    new OrderCreated(orderId, 1, "CUST-098", "c098@example.com", "pm_fail_once", 25.0, "EUR")));
            producer.flush();

            events.await(PaymentAuthorized.class);
            events.quiet(Duration.ofSeconds(5));

            assertThat(events.types())
                    .containsExactlyInAnyOrder(PaymentFailed.class, PaymentRetried.class, PaymentAuthorized.class);
            // PaymentAuthorized carries no attemptCount: read it from the Payment
            assertThat(paymentRepository.findByOrderId(orderId).orElseThrow().getAttemptCount())
                    .isEqualTo(2);
        }
    }
}
