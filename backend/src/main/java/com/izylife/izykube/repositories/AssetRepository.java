package com.izylife.izykube.repositories;
import com.izylife.izykube.enums.AssetType;
import com.izylife.izykube.model.Asset;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssetRepository extends MongoRepository<Asset, String> {
    List<Asset> findByType(AssetType type);

    boolean existsByTypeAndImageIgnoreCase(AssetType type, String image);

    Optional<Asset> findByTypeAndImageIgnoreCase(AssetType type, String image);

    Optional<Asset> findFirstByTypeAndNameIgnoreCaseAndVersionIgnoreCase(AssetType type, String name, String version);
}
