package com.smartdocs.documents;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

@SpringBootTest
@EnableAutoConfiguration(exclude = RabbitAutoConfiguration.class)
class DocumentServiceApplicationTests {

    @Test
    void contextLoads() {
        // This test will now pass because RabbitMQ auto-configuration is
        // completely disabled for the test, preventing any connection attempts.
    }

}