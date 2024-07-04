package com.izylife.izykube.repositories;

import com.izylife.izykube.model.ClusterTemplate;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ClusterTemplateRepository extends MongoRepository<ClusterTemplate, String> {

    Optional<ClusterTemplate> findByClusterId(String clusterId);
}
