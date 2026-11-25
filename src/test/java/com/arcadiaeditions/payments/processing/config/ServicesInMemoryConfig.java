package com.arcadiaeditions.payments.processing.config;

import com.arcadiaeditions.payments.processing.asyncapi.provider.*;
import com.arcadiaeditions.payments.processing.core.application.*;
import com.arcadiaeditions.payments.processing.core.domain.*;
import com.arcadiaeditions.payments.processing.core.inbound.*;
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

    protected final PaymentsProcessingServiceImpl paymentsProcessingService = new PaymentsProcessingServiceImpl(
            paymentRepository(), eventsProducerInMemoryContext.paymentsProcessingEventsProducer(), TestClock.FIXED);

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
