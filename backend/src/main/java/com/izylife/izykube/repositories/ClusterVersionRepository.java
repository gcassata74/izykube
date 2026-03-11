package com.izylife.izykube.repositories;

import com.izylife.izykube.model.ClusterVersion;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClusterVersionRepository extends MongoRepository<ClusterVersion, String> {
    List<ClusterVersion> findByNamespaceIgnoreCaseOrderByVersionNumberDesc(String namespace);

    Optional<ClusterVersion> findByNamespaceIgnoreCaseAndVersionNumber(String namespace, int versionNumber);

    Optional<ClusterVersion> findFirstByNamespaceIgnoreCaseOrderByVersionNumberDesc(String namespace);

    void deleteByClusterId(String clusterId);
}
