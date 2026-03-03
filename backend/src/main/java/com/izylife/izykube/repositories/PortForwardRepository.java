package com.izylife.izykube.repositories;

import com.izylife.izykube.model.PortForwardEntry;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PortForwardRepository extends MongoRepository<PortForwardEntry, String> {
    List<PortForwardEntry> findByNamespaceIgnoreCaseAndServiceNameIgnoreCase(String namespace, String serviceName);
}
