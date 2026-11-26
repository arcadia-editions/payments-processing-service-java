package com.arcadiaeditions.payments.processing.config;

import com.arcadiaeditions.payments.processing.asyncapi.provider.*;
import com.arcadiaeditions.payments.processing.core.application.*;
import com.arcadiaeditions.payments.processing.core.domain.*;
import com.arcadiaeditions.payments.processing.core.inbound.*;
import com.arcadiaeditions.payments.processing.core.outbound.processor.PaymentProcessor;
import com.arcadiaeditions.payments.processing.infrastructure.processor.StandInPaymentProcessor;
import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Services InMemory Config. It can be used standalone or with @SpringBootTest.
 */
@Configuration
@Profile("in-memory")
public class ServicesInMemoryConfig extends RepositoriesInMemoryConfig {

    protected final EventsProducerInMemoryContext eventsProducerInMemoryContext = new EventsProducerInMemoryContext();

    /** Stand-in processor (ADR 0002) with a short timeout. */
    protected final PaymentProcessor paymentProcessor = new StandInPaymentProcessor(Duration.ofMillis(500));

    protected final PaymentsProcessingServiceImpl paymentsProcessingService = new PaymentsProcessingServiceImpl(
            paymentRepository(),
            eventsProducerInMemoryContext.paymentsProcessingEventsProducer(),
            TestClock.FIXED,
            paymentProcessor);

    @Bean
    public <T extends PaymentsProcessingService> T paymentsProcessingService() {
        return (T) paymentsProcessingService;
    }

    static List<Payment> _payments;

    public void reloadTestData() {

        var testDataLoader = new TestDataLoader(List.of(Payment.class));
        var payments = _payments != null ? _payments : testDataLoader.loadCollectionTestDataAsObjects(Payment.class);
        paymentRepository().deleteAll();
        paymentRepository().saveAll(payments);
    }

    public EventsProducerInMemoryContext getEventsProducerInMemoryContext() {
        return eventsProducerInMemoryContext;
    }
}
