package com.izylife.izykube.services;

import com.izylife.izykube.model.ClusterTemplate;
import com.izylife.izykube.dto.cluster.ClusterDTO;
import com.izylife.izykube.dto.cluster.LinkDTO;
import com.izylife.izykube.dto.cluster.NodeDTO;
import com.izylife.izykube.model.Cluster;
import com.izylife.izykube.repositories.ClusterRepository;
import com.izylife.izykube.repositories.ClusterTemplateRepository;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import javassist.tools.rmi.ObjectNotFoundException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
@Service
@Slf4j
public class ClusterService {

    private final KubernetesClient client;
    private final ClusterRepository clusterRepository;
    private final ClusterTemplateRepository clusterTemplateRepository;


    public ClusterDTO createCluster(ClusterDTO clusterDTO) {

        try {
            Cluster cluster = new Cluster();
            cluster.setName(clusterDTO.getName());
            cluster.setNodes(clusterDTO.getNodes());
            cluster.setLinks(clusterDTO.getLinks());
            cluster.setDiagram(clusterDTO.getDiagram());
            Cluster savedCluster = clusterRepository.save(cluster);

            ClusterDTO savedClusterDTO = new ClusterDTO();
            savedClusterDTO.setId(savedCluster.getId());
            savedClusterDTO.setName(savedCluster.getName());
            savedClusterDTO.setNodes(savedCluster.getNodes());
            savedClusterDTO.setLinks(savedCluster.getLinks());
            savedClusterDTO.setDiagram(savedCluster.getDiagram());

            return savedClusterDTO;

        } catch (Exception e) {
            log.error("Error saving cluster: " + e.getMessage());
            return null;
        }
    }

    public void deployCluster(ClusterDTO clusterDTO) throws KubernetesClientException {

    }



    public ClusterDTO updateCluster(ClusterDTO clusterDTO) throws Exception {
        try {
            Cluster cluster = clusterRepository.findById(clusterDTO.getId()).orElseThrow(() -> new ObjectNotFoundException("Cluster not found"));
            cluster.setId(clusterDTO.getId());
            cluster.setName(clusterDTO.getName());
            cluster.setNameSpace(clusterDTO.getNameSpace());
            cluster.setNodes(clusterDTO.getNodes());
            cluster.setLinks(clusterDTO.getLinks());
            cluster.setDiagram(clusterDTO.getDiagram());
            Cluster updatedCluster = clusterRepository.save(cluster);

            ClusterDTO updatedClusterDTO = new ClusterDTO();
            updatedClusterDTO.setId(updatedCluster.getId());
            updatedClusterDTO.setName(updatedCluster.getName());
            updatedClusterDTO.setNameSpace(updatedCluster.getNameSpace());
            updatedClusterDTO.setNodes(updatedCluster.getNodes());
            updatedClusterDTO.setLinks(updatedCluster.getLinks());
            updatedClusterDTO.setDiagram(updatedCluster.getDiagram());

            return updatedClusterDTO;

        } catch (Exception e) {
            log.error("Error updating cluster: " + e.getMessage());
            return null;
        }
    }

    public Object getAllClusters() throws Exception {
        try {
            return clusterRepository.findAll();
        } catch (Exception e) {
            log.error("Error getting all clusters: " + e.getMessage());
            return null;
        }
    }

    public void deleteCluster(String id) {
        try {
            clusterRepository.deleteById(id);
        } catch (Exception e) {
            log.error("Error deleting cluster: " + e.getMessage());
        }
    }

    public Object getClusterById(String id) {
        try {
            return clusterRepository.findById(id).orElseThrow(() -> new ObjectNotFoundException("Cluster not found"));
        } catch (Exception e) {
            log.error("Error getting cluster with ID " + id + ": " + e.getMessage());
            return null;
        }
    }

    public void createTemplate(String id) throws ObjectNotFoundException {
        Cluster cluster = clusterRepository.findById(id).orElseThrow(() -> new ObjectNotFoundException("Cluster not found"));
        List<NodeDTO> nodes = cluster.getNodes();
        List<LinkDTO> links = cluster.getLinks();
        List<String> yamlList = new ArrayList<>();

        for (NodeDTO node : nodes) {
            List<NodeDTO> linkedNodes = cluster.findSourceNodesOf(node.getId());
            node.setLinkedNodes(linkedNodes);
            yamlList.add(node.create(client));
        }

        ClusterTemplate clusterTemplate = new ClusterTemplate();
        clusterTemplate.setClusterId(id);
        clusterTemplate.setYamlList(yamlList);
        clusterTemplateRepository.save(clusterTemplate);

    }

    public void deploy(String id) throws ObjectNotFoundException {
        Cluster cluster = clusterRepository.findById(id).orElseThrow(() -> new ObjectNotFoundException("Cluster not found"));
    }
}
