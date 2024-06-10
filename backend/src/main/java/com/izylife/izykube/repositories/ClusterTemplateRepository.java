package com.izylife.izykube.repositories;

import com.izylife.izykube.collections.ClusterTemplate;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ClusterTemplateRepository extends MongoRepository<ClusterTemplate, String> {
}
