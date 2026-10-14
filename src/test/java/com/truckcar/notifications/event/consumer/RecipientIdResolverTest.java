package com.truckcar.notifications.event.consumer;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RecipientIdResolverTest {

    @Test
    void keepsUuidRecipientsUnchanged() {
        UUID userId = UUID.randomUUID();

        assertThat(RecipientIdResolver.resolve(userId.toString())).isEqualTo(userId);
    }

    @Test
    void mapsDomainDriverIdsToStableUuid() {
        UUID first = RecipientIdResolver.resolve("driver-42");

        assertThat(first).isNotNull();
        assertThat(RecipientIdResolver.resolve("driver-42")).isEqualTo(first);
        assertThat(RecipientIdResolver.resolve("driver-7")).isNotEqualTo(first);
    }

    @Test
    void returnsNullForMissingRecipient() {
        assertThat(RecipientIdResolver.resolve(null)).isNull();
        assertThat(RecipientIdResolver.resolve("  ")).isNull();
    }
}
