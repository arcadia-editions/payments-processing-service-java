package com.arcadiaeditions.payments.processing.adapters.web;

import com.arcadiaeditions.payments.processing.config.DockerComposeInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@DockerComposeInitializer.EnableDockerCompose
@org.springframework.transaction.annotation.Transactional
public abstract class BaseWebTestClientTest {

    @Autowired
    protected WebApplicationContext context;

    protected WebTestClient webTestClient;

    @BeforeEach
    public void setup() {
        // application.yml spring.security.user. Security runs before bean validation, so an
        // unauthenticated call is 401 and the 400 case never runs.
        this.webTestClient = MockMvcWebTestClient.bindToApplicationContext(this.context)
                .build()
                .mutate()
                .defaultHeaders(headers -> headers.setBasicAuth("admin", "password"))
                .build();
    }
}
