package com.izylife.izykube.repositories;

import com.izylife.izykube.model.Namespace;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NamespaceRepository extends MongoRepository<Namespace, String> {

    Optional<Namespace> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    default List<Namespace> findAllSorted() {
        return findAll(Sort.by(Sort.Direction.ASC, "name"));
    }
}
