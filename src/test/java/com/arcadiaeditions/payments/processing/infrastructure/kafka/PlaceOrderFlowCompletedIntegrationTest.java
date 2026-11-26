package com.arcadiaeditions.payments.processing.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.arcadiaeditions.fulfillment.shipping.events.avro.FulfillmentScheduled;
import com.arcadiaeditions.orders.checkout.events.avro.OrderCreated;
import com.arcadiaeditions.payments.processing.config.DockerComposeInitializer;
import com.arcadiaeditions.payments.processing.events.avro.PaymentAuthorized;
import com.arcadiaeditions.payments.processing.events.avro.PaymentCaptured;
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

/**
 * Payments side of place-order-flow: OrderCreated in, PaymentAuthorized out; FulfillmentScheduled in, PaymentCaptured
 * out. Real Kafka and schema registry (docker compose).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@DockerComposeInitializer.EnableDockerCompose
class PlaceOrderFlowCompletedIntegrationTest {

    @Autowired
    KafkaProperties kafkaProperties;

    @Value("${app.kafka.topics.order-created-event-v1.topic}")
    String orderCreatedTopic;

    @Value("${app.kafka.topics.fulfillment-scheduled-event-v1.topic}")
    String fulfillmentScheduledTopic;

    @Test
    void orderCreatedThenFulfillmentScheduledPublishesAuthorizedThenCaptured() {
        String orderId = "ORD-" + UUID.randomUUID();
        try (var events = new FlowEvents(kafkaProperties, orderId);
                var producer = new KafkaProducer<Object, Object>(kafkaProperties.buildProducerProperties())) {
            producer.send(new ProducerRecord<>(
                    orderCreatedTopic,
                    null,
                    new OrderCreated(orderId, 1, "CUST-099", "c099@example.com", "PM-099", 25.0, "EUR")));
            producer.flush();

            events.await(PaymentAuthorized.class);
            events.quiet(Duration.ofSeconds(3));
            assertThat(events.types()).containsExactly(PaymentAuthorized.class);
            PaymentAuthorized authorized = events.first(PaymentAuthorized.class);
            assertThat(authorized.getAmount()).isEqualTo(25.0);
            assertThat(authorized.getCurrency().toString()).isEqualTo("EUR");

            producer.send(new ProducerRecord<>(
                    fulfillmentScheduledTopic,
                    null,
                    new FulfillmentScheduled("FUL-099", orderId, "CUST-099", "2026-10-14T10:00:00Z")));
            producer.flush();

            events.await(PaymentCaptured.class);
            events.quiet(Duration.ofSeconds(3));
            assertThat(events.types()).containsExactly(PaymentAuthorized.class, PaymentCaptured.class);
            assertThat(events.first(PaymentCaptured.class).getAmount()).isEqualTo(25.0);
        }
    }
}
