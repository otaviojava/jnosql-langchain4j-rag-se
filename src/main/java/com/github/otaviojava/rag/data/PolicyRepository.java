package com.github.otaviojava.rag.data;

import com.github.otaviojava.rag.model.Policy;
import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Jakarta Data repository over the same Qdrant {@code policies} collection that
 * LangChain4j searches. Eclipse JNoSQL implements it on top of its vector template.
 */
@Repository
public interface PolicyRepository extends CrudRepository<Policy, UUID> {

    boolean existsById(UUID id);

    List<Policy> findByIdIn(Collection<UUID> ids);
}
