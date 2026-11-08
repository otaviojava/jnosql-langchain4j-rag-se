package com.github.otaviojava.rag;

import jakarta.nosql.AttributeConverter;

import java.time.LocalDate;

/**
 * Persists an open-ended (null) {@link LocalDate} as the {@link #OPEN_ENDED}
 * sentinel and restores it as null when reading.
 * <p>
 * JNoSQL does not call converters for null values and writes a null payload
 * entry, which LangChain4j's Qdrant store cannot read back (it fails with a
 * NullPointerException). Both libraries share the collection, so null must
 * never be persisted; see {@link Policy#forStorage()}.
 */
public class OpenEndedDateConverter implements AttributeConverter<LocalDate, String> {

    public static final LocalDate OPEN_ENDED = LocalDate.of(9999, 12, 31);

    @Override
    public String convertToDatabaseColumn(LocalDate attribute) {
        return (attribute == null ? OPEN_ENDED : attribute).toString();
    }

    @Override
    public LocalDate convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        LocalDate date = LocalDate.parse(dbData);
        return OPEN_ENDED.equals(date) ? null : date;
    }
}
