package com.izylife.izykube.repositories;


import com.izylife.izykube.model.Asset;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AssetRepository extends MongoRepository<Asset, String> {
    // Define custom query methods if needed
}
