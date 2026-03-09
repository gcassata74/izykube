package com.izylife.izykube.services;

import com.izylife.izykube.model.operator.OperatorCatalogEntry;
import com.izylife.izykube.repositories.OperatorCatalogRepository;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperatorCatalogServiceTest {

    @Mock
    private OperatorCatalogRepository repository;

    @Mock
    private KubernetesClient kubernetesClient;

    private OperatorCatalogService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new OperatorCatalogService(repository, kubernetesClient);
    }

    @Test
    void sanitizeInstallResourcesRejectsCsv() {
        OperatorCatalogEntry entry = entry("cat-1", "zoperator", "operators");
        GenericKubernetesResource csv = resource("operators.coreos.com/v1alpha1", "ClusterServiceVersion", "zoperator.v0.3.6", "operators");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.sanitizeInstallResources(List.of(csv), entry)
        );

        assertTrue(ex.getMessage().contains("cannot include ClusterServiceVersion"));
    }

    @Test
    void sanitizeInstallResourcesRejectsNonOlmSeedResource() {
        OperatorCatalogEntry entry = entry("cat-1", "zoperator", "operators");
        GenericKubernetesResource deployment = resource("apps/v1", "Deployment", "zoperator", "operators");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.sanitizeInstallResources(List.of(deployment), entry)
        );

        assertTrue(ex.getMessage().contains("only include OLM seed resources"));
    }

    @Test
    void sanitizeInstallResourcesNormalizesNamespaceAndAddsOwnershipLabels() {
        OperatorCatalogEntry entry = entry("cat-1", "zoperator", "operators");
        GenericKubernetesResource subscription = resource(
                "operators.coreos.com/v1alpha1",
                "Subscription",
                "zoperator-sub",
                "wrong-ns"
        );

        List<io.fabric8.kubernetes.api.model.HasMetadata> sanitized =
                service.sanitizeInstallResources(List.of(subscription), entry);

        assertEquals(1, sanitized.size());
        GenericKubernetesResource out = (GenericKubernetesResource) sanitized.get(0);
        assertEquals("operators", out.getMetadata().getNamespace());
        assertEquals("izykube", out.getMetadata().getLabels().get("izykube.io/managed-by"));
        assertEquals("cat-1", out.getMetadata().getLabels().get("izykube.io/operator-catalog-id"));
        assertEquals("zoperator", out.getMetadata().getLabels().get("izykube.io/operator-package"));
    }

    @Test
    void isOwnedResourceMatchesCatalogId() {
        OperatorCatalogEntry entry = entry("cat-1", "zoperator", "operators");
        GenericKubernetesResource subscription = resource("operators.coreos.com/v1alpha1", "Subscription", "zoperator-sub", "operators");
        subscription.getMetadata().setLabels(Map.of(
                "izykube.io/managed-by", "izykube",
                "izykube.io/operator-catalog-id", "cat-1"
        ));

        assertTrue(service.isOwnedResource(subscription, entry));
    }

    @Test
    void isOwnedResourceMatchesNormalizedPackageWhenCatalogIdMissing() {
        OperatorCatalogEntry entry = entry("cat-1", "zoperator", "operators");
        GenericKubernetesResource csv = resource("operators.coreos.com/v1alpha1", "ClusterServiceVersion", "zoperator.v0.3.6", "operators");
        csv.getMetadata().setLabels(Map.of(
                "izykube.io/managed-by", "izykube",
                "izykube.io/operator-package", "zoperator.v0.3.6"
        ));

        assertTrue(service.isOwnedResource(csv, entry));
    }

    @Test
    void isOwnedResourceReturnsFalseForUnmanagedResource() {
        OperatorCatalogEntry entry = entry("cat-1", "zoperator", "operators");
        GenericKubernetesResource csv = resource("operators.coreos.com/v1alpha1", "ClusterServiceVersion", "zoperator.v0.3.6", "operators");
        csv.getMetadata().setLabels(Map.of("app", "other"));

        assertFalse(service.isOwnedResource(csv, entry));
    }

    private OperatorCatalogEntry entry(String id, String packageName, String targetNamespace) {
        OperatorCatalogEntry entry = new OperatorCatalogEntry();
        entry.setId(id);
        entry.setPackageName(packageName);
        entry.setTargetNamespace(targetNamespace);
        return entry;
    }

    private GenericKubernetesResource resource(String apiVersion, String kind, String name, String namespace) {
        GenericKubernetesResource resource = new GenericKubernetesResource();
        resource.setApiVersion(apiVersion);
        resource.setKind(kind);
        ObjectMeta metadata = new ObjectMeta();
        metadata.setName(name);
        metadata.setNamespace(namespace);
        resource.setMetadata(metadata);
        return resource;
    }
}
