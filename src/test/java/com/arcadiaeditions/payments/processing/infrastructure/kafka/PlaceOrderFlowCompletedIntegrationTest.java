package com.arcadiaeditions.payments.processing.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.arcadiaeditions.fulfillment.shipping.events.avro.FulfillmentScheduled;
import com.arcadiaeditions.orders.checkout.events.avro.OrderCreated;
import com.arcadiaeditions.payments.processing.config.DockerComposeInitializer;
import com.arcadiaeditions.payments.processing.events.avro.PaymentAuthorized;
import com.arcadiaeditions.payments.processing.events.avro.PaymentCaptured;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
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

    @Value("${app.kafka.topics.payment-authorized-event-v1.topic}")
    String authorizedTopic;

    @Value("${app.kafka.topics.payment-captured-event-v1.topic}")
    String capturedTopic;

    @Test
    void orderCreatedThenFulfillmentScheduledPublishesAuthorizedThenCaptured() {
        String orderId = "ORD-" + UUID.randomUUID();
        var consumerProps = kafkaProperties.buildConsumerProperties();
        consumerProps.put("group.id", "place-order-flow-it-" + UUID.randomUUID());
        consumerProps.put("auto.offset.reset", "earliest");

        try (var consumer = new KafkaConsumer<Object, Object>(consumerProps);
                var producer = new KafkaProducer<Object, Object>(kafkaProperties.buildProducerProperties())) {
            consumer.subscribe(List.of(authorizedTopic, capturedTopic));
            consumer.poll(Duration.ofSeconds(2));

            producer.send(new ProducerRecord<>(
                    orderCreatedTopic,
                    null,
                    new OrderCreated(orderId, 1, "CUST-099", "c099@example.com", "PM-099", 25.0, "EUR")));
            producer.flush();

            PaymentAuthorized authorized = await(consumer, PaymentAuthorized.class, p -> p.getOrderId(), orderId);
            assertThat(authorized.getAmount()).isEqualTo(25.0);
            assertThat(authorized.getCurrency().toString()).isEqualTo("EUR");

            producer.send(new ProducerRecord<>(
                    fulfillmentScheduledTopic,
                    null,
                    new FulfillmentScheduled("FUL-099", orderId, "CUST-099", "2026-10-14T10:00:00Z")));
            producer.flush();

            PaymentCaptured captured = await(consumer, PaymentCaptured.class, p -> p.getOrderId(), orderId);
            assertThat(captured.getAmount()).isEqualTo(25.0);
        }
    }

    private static <T> T await(
            KafkaConsumer<Object, Object> consumer, Class<T> type, Function<T, CharSequence> orderId, String id) {
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            for (ConsumerRecord<Object, Object> r : consumer.poll(Duration.ofSeconds(1))) {
                if (type.isInstance(r.value())
                        && id.equals(orderId.apply(type.cast(r.value())).toString())) {
                    return type.cast(r.value());
                }
            }
        }
        throw new AssertionError("no " + type.getSimpleName() + " for " + id + " within 60s");
    }
}
