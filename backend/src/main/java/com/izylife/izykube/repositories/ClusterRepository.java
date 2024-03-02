package com.izylife.izykube.repositories;

import com.izylife.izykube.model.Cluster;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClusterRepository  extends MongoRepository<Cluster, String> {
}
