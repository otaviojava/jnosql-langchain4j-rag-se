package com.github.otaviojava.rag;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class OpenEndedDateConverterTest {

    private final OpenEndedDateConverter converter = new OpenEndedDateConverter();

    @Test
    void shouldRoundTripDate() {
        var date = LocalDate.of(2026, 7, 1);

        String stored = converter.convertToDatabaseColumn(date);

        assertThat(stored).isEqualTo("2026-07-01");
        assertThat(converter.convertToEntityAttribute(stored)).isEqualTo(date);
    }

    @Test
    void shouldStoreOpenEndedAsSentinelAndRestoreNull() {
        String stored = converter.convertToDatabaseColumn(null);

        assertThat(stored).isEqualTo("9999-12-31");
        assertThat(converter.convertToEntityAttribute(stored)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
