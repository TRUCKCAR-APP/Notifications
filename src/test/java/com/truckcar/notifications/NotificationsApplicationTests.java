package com.truckcar.notifications;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.polly.PollyClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.sns.SnsClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {
        "truckcar.route.alerts",
        "truckcar.route.closures",
        "truckcar.loads.reassigned",
        "truckcar.trips.status",
        "truckcar.dispatch.messages"
})
class NotificationsApplicationTests {

    @MockBean
    private SnsClient snsClient;

    @MockBean
    private PollyClient pollyClient;

    @MockBean
    private S3Client s3Client;

    @MockBean
    private S3Presigner s3Presigner;

    @Test
    @DisplayName("Context loads successfully with embedded Kafka and mock AWS SDK")
    void contextLoads() {
        assertThat(snsClient).isNotNull();
        assertThat(pollyClient).isNotNull();
    }
}
