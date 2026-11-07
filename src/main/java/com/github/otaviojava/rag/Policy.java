
package com.github.otaviojava.rag;

import jakarta.nosql.Column;
import jakarta.nosql.Entity;
import jakarta.nosql.Id;
import org.eclipse.jnosql.mapping.vector.Vector;

import java.util.UUID;

@Entity
public record Policy(
        @Id UUID id,
        @Column String title,
        @Column String content,
        @Column String department,
        @Column String version,
        @Column boolean active,
        @Column Vector embedding
) {
}
