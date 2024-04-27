package com.izylife.izykube.services;

import com.izylife.izykube.dto.cluster.*;
import com.izylife.izykube.model.Cluster;
import com.izylife.izykube.repositories.AssetRepository;
import com.izylife.izykube.repositories.ClusterRepository;
import com.izylife.izykube.services.k8s.K8sDeploymentService;
import com.izylife.izykube.services.k8s.K8sPodService;
import com.izylife.izykube.services.k8s.K8sServiceService;
import io.fabric8.kubernetes.client.KubernetesClientException;
import javassist.tools.rmi.ObjectNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
@Slf4j
public class ClusterService {

    @Autowired
    private K8sPodService k8sPodService;

    @Autowired
    private K8sServiceService k8sServiceService;

    @Autowired
    private K8sDeploymentService k8sDeploymentService;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private ClusterRepository clusterRepository;


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
        // Iterate over each node in the cluster
        clusterDTO.getNodes().stream().filter(node -> node.getKind().equals("pod") || node.getKind().equals("deployment")).forEach(node -> {
            if (node.getKind().equals("pod")) {
                handlePod(clusterDTO, (PodDTO) node);
            } else {
                handleDeployment(clusterDTO, (DeploymentDTO) node);
            }
        });
    }

    private void handlePod(ClusterDTO clusterDTO, PodDTO pod) {
        k8sPodService.createPod(pod);
        // Handle connections from this Pod to other resources
    }

    private void handleDeployment(ClusterDTO clusterDTO, DeploymentDTO deployment) throws KubernetesClientException {

        // Find all config maps or other resources connected to this deployment
        List<NodeDTO> sourceNodes = clusterDTO.findSourceNodesOf(deployment.getId());
        for (NodeDTO sourceNode : sourceNodes) {
            // Process source nodes, e.g., ConfigMaps

        }
        k8sDeploymentService.createDeployment(deployment);
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
        for (NodeDTO node : nodes) {
            if (node instanceof DeploymentDTO) {
                DeploymentDTO deployment = (DeploymentDTO) node;
                k8sDeploymentService.createDeployment(deployment);
            } else if (node instanceof PodDTO) {
                PodDTO pod = (PodDTO) node;
                k8sPodService.createPod(pod);
            } else if (node instanceof Service) {
                Service service = (Service) node;
                //detect the deployment or pod that this service is connected to


              //  k8sServiceService.createService(service);
            }
        }

    }

    public void deploy(String id) throws ObjectNotFoundException {
        Cluster cluster = clusterRepository.findById(id).orElseThrow(() -> new ObjectNotFoundException("Cluster not found"));
    }
}
