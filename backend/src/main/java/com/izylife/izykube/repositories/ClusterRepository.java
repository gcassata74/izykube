package com.izylife.izykube.repositories;

import com.izylife.izykube.model.Cluster;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClusterRepository  extends MongoRepository<Cluster, String> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameSpaceIgnoreCase(String nameSpace);

    Optional<Cluster> findByNameSpaceIgnoreCase(String nameSpace);

    default boolean isNamespaceInUse(String namespace, String excludeClusterId) {
        return findByNameSpaceIgnoreCase(namespace)
                .filter(cluster -> excludeClusterId == null || !excludeClusterId.equals(cluster.getId()))
                .isPresent();
    }
}
