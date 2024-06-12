package com.izylife.izykube.repositories;

import com.izylife.izykube.model.ClusterTemplate;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ClusterTemplateRepository extends MongoRepository<ClusterTemplate, String> {
}
