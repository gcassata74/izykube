package com.izylife.izykube.repositories;

import com.izylife.izykube.model.operator.OperatorCatalogEntry;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OperatorCatalogRepository extends MongoRepository<OperatorCatalogEntry, String> {
}
