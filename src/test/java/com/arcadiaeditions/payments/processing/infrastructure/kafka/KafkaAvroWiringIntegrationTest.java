package com.arcadiaeditions.payments.processing.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.arcadiaeditions.payments.processing.asyncapi.provider.PaymentsProcessingEventsProducer;
import com.arcadiaeditions.payments.processing.config.DockerComposeInitializer;
import com.arcadiaeditions.payments.processing.events.avro.PaymentAuthorized;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Avro wiring: a PaymentAuthorized sent by the generated producer is read back from the topic as Avro.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@DockerComposeInitializer.EnableDockerCompose
class KafkaAvroWiringIntegrationTest {

    @Autowired
    PaymentsProcessingEventsProducer producer;

    @Autowired
    KafkaProperties kafkaProperties;

    @Value("${app.kafka.topics.payment-authorized-event-v1.topic}")
    String topic;

    @Test
    void paymentAuthorizedRoundTripsAsAvro() {
        String orderId = "order-" + UUID.randomUUID();
        var props = kafkaProperties.buildConsumerProperties();
        props.put("group.id", "avro-wiring-it-" + UUID.randomUUID());
        props.put("auto.offset.reset", "earliest");

        try (var consumer = new KafkaConsumer<Object, Object>(props)) {
            consumer.subscribe(List.of(topic));
            consumer.poll(Duration.ofSeconds(2));

            var sent =
                    new PaymentAuthorized(orderId, "customer-1", 10.5, "EUR", "provider-ref-1", "2026-11-26T10:00:00Z");
            assertThat(producer.onPaymentAuthorized(sent)).isTrue();

            PaymentAuthorized received = null;
            long deadline = System.currentTimeMillis() + 30_000;
            while (received == null && System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<Object, Object> r : consumer.poll(Duration.ofSeconds(1))) {
                    if (r.value() instanceof PaymentAuthorized p
                            && orderId.equals(p.getOrderId().toString())) {
                        received = p;
                    }
                }
            }
            assertThat(received).isNotNull();
            assertThat(received.getAmount()).isEqualTo(10.5);
            assertThat(received.getCurrency().toString()).isEqualTo("EUR");
        }
    }
}
