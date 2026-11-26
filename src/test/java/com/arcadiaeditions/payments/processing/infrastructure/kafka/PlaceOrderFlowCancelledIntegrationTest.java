package com.arcadiaeditions.payments.processing.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.arcadiaeditions.orders.checkout.events.avro.OrderCreated;
import com.arcadiaeditions.payments.processing.config.DockerComposeInitializer;
import com.arcadiaeditions.payments.processing.events.avro.PaymentDeclined;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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

/** Payments side of the cancelled paths of place-order-flow. Real Kafka and schema registry (docker compose). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@DockerComposeInitializer.EnableDockerCompose
class PlaceOrderFlowCancelledIntegrationTest {

    @Autowired
    KafkaProperties kafkaProperties;

    @Value("${app.kafka.topics.order-created-event-v1.topic}")
    String orderCreatedTopic;

    @Value("${app.kafka.topics.payment-authorized-event-v1.topic}")
    String authorizedTopic;

    @Value("${app.kafka.topics.payment-declined-event-v1.topic}")
    String declinedTopic;

    @Value("${app.kafka.topics.payment-failed-event-v1.topic}")
    String failedTopic;

    @Test
    void declined() {
        String orderId = "ORD-" + UUID.randomUUID();
        var consumerProps = kafkaProperties.buildConsumerProperties();
        consumerProps.put("group.id", "place-order-flow-cancelled-it-" + UUID.randomUUID());
        consumerProps.put("auto.offset.reset", "earliest");

        try (var consumer = new KafkaConsumer<Object, Object>(consumerProps);
                var producer = new KafkaProducer<Object, Object>(kafkaProperties.buildProducerProperties())) {
            consumer.subscribe(List.of(authorizedTopic, declinedTopic, failedTopic));
            consumer.poll(Duration.ofSeconds(2));

            producer.send(new ProducerRecord<>(
                    orderCreatedTopic,
                    null,
                    new OrderCreated(orderId, 1, "CUST-098", "c098@example.com", "pm_decline", 25.0, "EUR")));
            producer.flush();

            List<Object> seen = new ArrayList<>();
            long deadline = System.currentTimeMillis() + 60_000;
            boolean declined = false;
            while (System.currentTimeMillis() < deadline && !declined) {
                for (ConsumerRecord<Object, Object> r : consumer.poll(Duration.ofSeconds(1))) {
                    if (r.value() instanceof PaymentDeclined d
                            && orderId.equals(d.getOrderId().toString())) {
                        declined = true;
                        seen.add(d);
                    }
                }
            }
            assertThat(declined).as("PaymentDeclined for " + orderId).isTrue();

            // nothing else for this order in a short window
            long quiet = System.currentTimeMillis() + 5_000;
            while (System.currentTimeMillis() < quiet) {
                for (ConsumerRecord<Object, Object> r : consumer.poll(Duration.ofSeconds(1))) {
                    if (!(r.value() instanceof PaymentDeclined)
                            && r.value().toString().contains(orderId)) {
                        seen.add(r.value());
                    }
                }
            }
            assertThat(seen).hasSize(1);
        }
    }
}
