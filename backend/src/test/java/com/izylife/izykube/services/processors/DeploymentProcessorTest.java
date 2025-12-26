package com.izylife.izykube.services.processors;

import com.izylife.izykube.dto.cluster.ContainerDTO;
import com.izylife.izykube.dto.cluster.ContainerRole;
import com.izylife.izykube.dto.cluster.DeploymentDTO;
import com.izylife.izykube.dto.cluster.DeploymentWorkloadType;
import com.izylife.izykube.dto.cluster.LinkDTO;
import com.izylife.izykube.dto.cluster.NodeDTO;
import com.izylife.izykube.dto.cluster.ServiceAccountDTO;
import com.izylife.izykube.model.Asset;
import com.izylife.izykube.repositories.AssetRepository;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.apps.DaemonSet;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class DeploymentProcessorTest {

    private AssetRepository assetRepository;
    private DeploymentProcessor deploymentProcessor;

    @BeforeEach
    void setUp() {
        assetRepository = Mockito.mock(AssetRepository.class);
        when(assetRepository.findById(anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            Asset asset = new Asset();
            asset.setId(id);
            asset.setImage("repo/" + id + ":1.0.0");
            return Optional.of(asset);
        });
        deploymentProcessor = new DeploymentProcessor(new ContainerProcessor(assetRepository));
    }

    @Test
    void initContainerPlacedUnderInitContainers() {
        DeploymentDTO deployment = buildDeployment("dep-1", "web-app", "main-asset");
        ContainerDTO initContainer = new ContainerDTO("c-init", "init-task", "init-asset", 8080, ContainerRole.INIT);
        deployment.setTargetNodes(List.of(initContainer));
        deployment.setOutgoingLinks(List.of(link(deployment.getId(), initContainer.getId(), ContainerRole.INIT, "link-1")));

        Deployment manifest = renderDeployment(deployment);
        PodSpec spec = manifest.getSpec().getTemplate().getSpec();

        assertNotNull(spec.getInitContainers());
        assertEquals(1, spec.getInitContainers().size());
        assertEquals("init-task", spec.getInitContainers().get(0).getName());
        assertEquals(1, spec.getContainers().size());
    }

    @Test
    void initContainerLinkedInboundIsHonored() {
        DeploymentDTO deployment = buildDeployment("dep-1b", "web-app", "main-asset");
        ContainerDTO initContainer = new ContainerDTO("c-init-in", "init-in", "init-asset", 8080, ContainerRole.INIT);
        deployment.setSourceNodes(List.of(initContainer));
        deployment.setIncomingLinks(List.of(link(initContainer.getId(), deployment.getId(), ContainerRole.INIT, "link-1b")));

        Deployment manifest = renderDeployment(deployment);
        PodSpec spec = manifest.getSpec().getTemplate().getSpec();

        assertNotNull(spec.getInitContainers());
        assertEquals(1, spec.getInitContainers().size());
        assertEquals("init-in", spec.getInitContainers().get(0).getName());
    }

    @Test
    void sidecarAppendedAfterMainContainers() {
        DeploymentDTO deployment = buildDeployment("dep-2", "web-app", "main-asset");
        ContainerDTO sidecar = new ContainerDTO("c-side", "logger", "side-asset", 8081, ContainerRole.SIDECAR);
        deployment.setTargetNodes(List.of(sidecar));
        deployment.setOutgoingLinks(List.of(link(deployment.getId(), sidecar.getId(), ContainerRole.SIDECAR, "link-2")));

        Deployment manifest = renderDeployment(deployment);
        List<String> names = manifest.getSpec().getTemplate().getSpec().getContainers().stream()
                .map(c -> c.getName())
                .toList();

        assertEquals(List.of("web-app", "logger"), names);
    }

    @Test
    void initContainersOmittedWhenEmpty() {
        DeploymentDTO deployment = buildDeployment("dep-3", "web-app", "main-asset");

        Deployment manifest = renderDeployment(deployment);
        assertTrue(manifest.getSpec().getTemplate().getSpec().getInitContainers() == null
                || manifest.getSpec().getTemplate().getSpec().getInitContainers().isEmpty());
    }

    @Test
    void containersAreOrderedDeterministically() {
        DeploymentDTO deployment = buildDeployment("dep-4", "web-app", "main-asset");

        ContainerDTO initB = new ContainerDTO("c-init-b", "b-init", "init-b", 8080, null);
        ContainerDTO initA = new ContainerDTO("c-init-a", "a-init", "init-a", 8080, ContainerRole.INIT);

        ContainerDTO sidecarB = new ContainerDTO("c-side-b", "beta", "side-b", 9090, null);
        ContainerDTO sidecarA = new ContainerDTO("c-side-a", "alpha", "side-a", 9090, ContainerRole.SIDECAR);

        deployment.setTargetNodes(List.of(initB, initA, sidecarB, sidecarA));
        List<LinkDTO> links = new ArrayList<>();
        links.add(link(deployment.getId(), initB.getId(), ContainerRole.INIT, "link-init-b"));
        links.add(link(deployment.getId(), initA.getId(), ContainerRole.INIT, "link-init-a"));
        links.add(link(deployment.getId(), sidecarB.getId(), ContainerRole.SIDECAR, "link-side-b"));
        links.add(link(deployment.getId(), sidecarA.getId(), ContainerRole.SIDECAR, "link-side-a"));
        deployment.setOutgoingLinks(links);

        Deployment manifest = renderDeployment(deployment);
        PodSpec spec = manifest.getSpec().getTemplate().getSpec();

        assertEquals(List.of("a-init", "b-init"), spec.getInitContainers().stream().map(c -> c.getName()).toList());
        assertEquals(List.of("web-app", "alpha", "beta"), spec.getContainers().stream().map(c -> c.getName()).toList());
    }

    @Test
    void duplicateContainerNamesAreRejected() {
        DeploymentDTO deployment = buildDeployment("dep-5", "web-app", "main-asset");
        ContainerDTO sidecar = new ContainerDTO("c-dup", "web-app", "side-asset", 8080, ContainerRole.SIDECAR);
        deployment.setTargetNodes(List.of(sidecar));
        deployment.setOutgoingLinks(List.of(link(deployment.getId(), sidecar.getId(), ContainerRole.SIDECAR, "link-dup")));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> renderDeployment(deployment));
        assertTrue(ex.getMessage().contains("dep-5"));
        assertTrue(ex.getMessage().contains("web-app"));
        assertTrue(ex.getMessage().contains("link-dup"));
    }

    @Test
    void missingRoleDefaultsToSidecar() {
        DeploymentDTO deployment = buildDeployment("dep-6", "web-app", "main-asset");
        ContainerDTO sidecar = new ContainerDTO("c-no-role", "helper", "helper-asset", 8080);
        deployment.setTargetNodes(List.of(sidecar));
        deployment.setOutgoingLinks(List.of(link(deployment.getId(), sidecar.getId(), null, "link-helper")));

        Deployment manifest = renderDeployment(deployment);
        List<String> names = manifest.getSpec().getTemplate().getSpec().getContainers().stream()
                .map(c -> c.getName())
                .toList();

        assertEquals(List.of("web-app", "helper"), names);
    }

    @Test
    void containerRoleOverridesLinkRole() {
        DeploymentDTO deployment = buildDeployment("dep-7", "web-app", "main-asset");
        ContainerDTO container = new ContainerDTO("c-override", "prep", "prep-asset", 8080, ContainerRole.INIT);
        deployment.setTargetNodes(List.of(container));
        deployment.setOutgoingLinks(List.of(link(deployment.getId(), container.getId(), ContainerRole.SIDECAR, "link-override")));

        Deployment manifest = renderDeployment(deployment);
        PodSpec spec = manifest.getSpec().getTemplate().getSpec();

        assertEquals(List.of("prep"), spec.getInitContainers().stream().map(c -> c.getName()).toList());
        assertEquals(List.of("web-app"), spec.getContainers().stream().map(c -> c.getName()).toList());
    }

    @Test
    void initContainerRoleFromNodeWorksWithExposeLinkAndNullLinkRole() {
        DeploymentDTO deployment = buildDeployment("dep-8", "web-app", "main-asset");
        ContainerDTO initContainer = new ContainerDTO("c-init-expose", "init-task", "init-asset", 8080, ContainerRole.INIT);
        deployment.setTargetNodes(List.of(initContainer));
        deployment.setOutgoingLinks(List.of(link(deployment.getId(), initContainer.getId(), null, "Expose", "link-expose")));

        Deployment manifest = renderDeployment(deployment);
        PodSpec spec = manifest.getSpec().getTemplate().getSpec();

        assertNotNull(spec.getInitContainers());
        assertEquals(List.of("init-task"), spec.getInitContainers().stream().map(c -> c.getName()).toList());
    }

    @Test
    void initAndSidecarPlacementWorksForStatefulSets() {
        DeploymentDTO deployment = buildDeployment("sts-1", "my-statefulset", "main-asset", DeploymentWorkloadType.STATEFULSET);
        ContainerDTO init = new ContainerDTO("c-init", "init-container-1", "init-asset", 8080, ContainerRole.INIT);
        ContainerDTO sidecar = new ContainerDTO("c-side", "sidecar-container-1", "side-asset", 8081, ContainerRole.SIDECAR);
        deployment.setTargetNodes(List.of(init, sidecar));
        deployment.setOutgoingLinks(List.of(
                link(deployment.getId(), init.getId(), null, "Expose", "link-sts-init"),
                link(deployment.getId(), sidecar.getId(), null, "Expose", "link-sts-side")
        ));

        StatefulSet manifest = Serialization.unmarshal(deploymentProcessor.createTemplate(deployment), StatefulSet.class);
        PodSpec spec = manifest.getSpec().getTemplate().getSpec();
        assertEquals(List.of("init-container-1"), spec.getInitContainers().stream().map(c -> c.getName()).toList());
        assertEquals(List.of("my-statefulset", "sidecar-container-1"), spec.getContainers().stream().map(c -> c.getName()).toList());
        assertNotNull(manifest.getSpec().getServiceName());
        assertFalse(manifest.getSpec().getServiceName().isBlank());
    }

    @Test
    void initAndSidecarPlacementWorksForDaemonSets() {
        DeploymentDTO deployment = buildDeployment("ds-1", "my-daemonset", "main-asset", DeploymentWorkloadType.DAEMONSET);
        ContainerDTO init = new ContainerDTO("c-init", "init-container-1", "init-asset", 8080, ContainerRole.INIT);
        ContainerDTO sidecar = new ContainerDTO("c-side", "sidecar-container-1", "side-asset", 8081, ContainerRole.SIDECAR);
        deployment.setTargetNodes(List.of(init, sidecar));
        deployment.setOutgoingLinks(List.of(
                link(deployment.getId(), init.getId(), ContainerRole.INIT, "link-ds-init"),
                link(deployment.getId(), sidecar.getId(), ContainerRole.SIDECAR, "link-ds-side")
        ));

        DaemonSet manifest = Serialization.unmarshal(deploymentProcessor.createTemplate(deployment), DaemonSet.class);
        PodSpec spec = manifest.getSpec().getTemplate().getSpec();
        assertEquals(List.of("init-container-1"), spec.getInitContainers().stream().map(c -> c.getName()).toList());
        assertEquals(List.of("my-daemonset", "sidecar-container-1"), spec.getContainers().stream().map(c -> c.getName()).toList());
    }

    @Test
    void injectsServiceAccountNameWhenServiceAccountRefPresent() {
        DeploymentDTO deployment = buildDeployment("dep-sa", "web-app", "main-asset");
        deployment.setNamespace("demo");
        deployment.setServiceAccountRef("sa-1");

        ServiceAccountDTO sa = new ServiceAccountDTO("sa-1", "example-sa");
        sa.setNamespace("demo");
        deployment.setNodeIndex(Map.of(sa.getId(), (NodeDTO) sa));

        Deployment manifest = renderDeployment(deployment);
        PodSpec spec = manifest.getSpec().getTemplate().getSpec();
        assertEquals("example-sa", spec.getServiceAccountName());
    }

    @Test
    void rejectsCrossNamespaceServiceAccountUsage() {
        DeploymentDTO deployment = buildDeployment("dep-sa-ns", "web-app", "main-asset");
        deployment.setNamespace("demo");
        deployment.setServiceAccountRef("sa-1");

        ServiceAccountDTO sa = new ServiceAccountDTO("sa-1", "example-sa");
        sa.setNamespace("other");
        deployment.setNodeIndex(Map.of(sa.getId(), (NodeDTO) sa));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> deploymentProcessor.createTemplate(deployment));
        assertTrue(ex.getMessage().contains("Workload namespace must match ServiceAccount namespace"));
    }

    private DeploymentDTO buildDeployment(String id, String name, String assetId) {
        DeploymentDTO dto = new DeploymentDTO(id, name, 1, "RollingUpdate", assetId, 8080);
        dto.setTargetNodes(new ArrayList<>());
        dto.setOutgoingLinks(new ArrayList<>());
        return dto;
    }

    private DeploymentDTO buildDeployment(String id, String name, String assetId, DeploymentWorkloadType workloadType) {
        DeploymentDTO dto = new DeploymentDTO(id, name, 1, "RollingUpdate", assetId, 8080, workloadType);
        dto.setTargetNodes(new ArrayList<>());
        dto.setOutgoingLinks(new ArrayList<>());
        return dto;
    }

    private LinkDTO link(String source, String target, ContainerRole role, String id) {
        return link(source, target, role, "Container", id);
    }

    private LinkDTO link(String source, String target, ContainerRole role, String type, String id) {
        LinkDTO link = new LinkDTO();
        link.setId(id);
        link.setSource(source);
        link.setTarget(target);
        link.setType(type);
        link.setContainerRole(role);
        return link;
    }

    private Deployment renderDeployment(DeploymentDTO dto) {
        String yaml = deploymentProcessor.createTemplate(dto);
        return Serialization.unmarshal(yaml, Deployment.class);
    }
}
