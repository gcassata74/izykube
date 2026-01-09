package com.izylife.izykube.repositories;

import com.izylife.izykube.model.CrdDefinition;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CrdDefinitionRepository extends MongoRepository<CrdDefinition, String> {
}

