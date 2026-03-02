package com.izylife.izykube.services;

import com.izylife.izykube.dto.kube.IngressSummaryDTO;
import com.izylife.izykube.web.request.RouteCreateRequest;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPathBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressRuleValueBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBackendBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressClass;
import io.fabric8.kubernetes.api.model.networking.v1.IngressClassBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressRule;
import io.fabric8.kubernetes.api.model.networking.v1.IngressRuleBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressServiceBackendBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressTLSBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.ServiceBackendPortBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RouteService {
    private static final String LABEL_MANAGED_BY = "izykube.io/managed-by";
    private static final String LABEL_ROUTE_NAMESPACE = "izykube.io/route-namespace";
    private static final String MANAGED_BY_VALUE = "izykube";
    private static final String DEFAULT_INGRESS_CONTROLLER = "k8s.io/ingress-nginx";
    private static final String DEFAULT_INGRESS_CLASS = "izykube-class";

    private final KubernetesClient kubernetesClient;

    public IngressSummaryDTO create(RouteCreateRequest request) {
        validate(request);

        String namespace = request.getNamespace().trim();
        String name = request.getName().trim();
        Ingress ingress = buildIngress(null, request, namespace, name);

        kubernetesClient.network().v1().ingresses()
                .inNamespace(namespace)
                .resource(ingress)
                .createOrReplace();

        Ingress created = kubernetesClient.network().v1().ingresses()
                .inNamespace(namespace)
                .withName(name)
                .get();

        if (created == null) {
            throw new IllegalStateException("Route created but could not be retrieved.");
        }

        return mapIngress(created);
    }

    public void delete(String namespace, String name) {
        if (!StringUtils.hasText(namespace) || !StringUtils.hasText(name)) {
            throw new IllegalArgumentException("Namespace and name are required.");
        }
        String trimmedNamespace = namespace.trim();
        String trimmedName = name.trim();
        Ingress existing = kubernetesClient.network().v1().ingresses()
                .inNamespace(trimmedNamespace)
                .withName(trimmedName)
                .get();
        if (existing == null) {
            throw new IllegalStateException("Route not found or could not be deleted.");
        }
        String ingressClassName = Optional.ofNullable(existing.getSpec())
                .map(spec -> spec.getIngressClassName())
                .orElse(null);
        var deletion = kubernetesClient.network().v1().ingresses()
                .inNamespace(trimmedNamespace)
                .withName(trimmedName)
                .delete();
        boolean deleted = deletion != null && !deletion.isEmpty();
        if (!deleted) {
            throw new IllegalStateException("Route not found or could not be deleted.");
        }
        // IngressClass lifecycle is managed externally; do not delete on route removal.
    }

    public IngressSummaryDTO update(String namespace, String name, RouteCreateRequest request) {
        if (!StringUtils.hasText(namespace) || !StringUtils.hasText(name)) {
            throw new IllegalArgumentException("Namespace and name are required.");
        }
        if (request == null) {
            throw new IllegalArgumentException("Route request is required.");
        }
        request.setNamespace(namespace);
        request.setName(name);
        validate(request);

        Ingress existing = kubernetesClient.network().v1().ingresses()
                .inNamespace(namespace.trim())
                .withName(name.trim())
                .get();
        if (existing == null) {
            throw new IllegalStateException("Route not found.");
        }

        String previousIngressClass = Optional.ofNullable(existing.getSpec())
                .map(spec -> spec.getIngressClassName())
                .orElse(null);
        Ingress updated = buildIngress(existing, request, namespace.trim(), name.trim());
        Ingress result = kubernetesClient.network().v1().ingresses()
                .inNamespace(namespace.trim())
                .resource(updated)
                .update();
        if (result == null) {
            throw new IllegalStateException("Route update failed.");
        }
        // Do not delete ingress classes on update.
        return mapIngress(result);
    }

    private Ingress buildIngress(Ingress existing, RouteCreateRequest request, String namespace, String name) {
        String host = StringUtils.hasText(request.getHost()) ? request.getHost().trim() : null;
        String path = StringUtils.hasText(request.getPath()) ? request.getPath().trim() : "/";
        String ingressClassName = resolveIngressClassName(request.getIngressClassName(), namespace);
        String serviceName = request.getServiceName().trim();
        int servicePort = request.getServicePort();

        validateServiceTarget(namespace, serviceName, servicePort);
        ensureIngressClass(ingressClassName, namespace);

        IngressRule rule = new IngressRuleBuilder()
                .withHost(host)
                .withHttp(new HTTPIngressRuleValueBuilder()
                        .withPaths(new HTTPIngressPathBuilder()
                                .withPath(path)
                                .withPathType("Prefix")
                                .withBackend(new IngressBackendBuilder()
                                        .withService(new IngressServiceBackendBuilder()
                                                .withName(serviceName)
                                                .withPort(new ServiceBackendPortBuilder().withNumber(servicePort).build())
                                                .build())
                                        .build())
                                .build())
                        .build())
                .build();

        IngressBuilder builder = existing == null ? new IngressBuilder() : new IngressBuilder(existing);
        Map<String, String> labels = new LinkedHashMap<>(Optional.ofNullable(existing)
                .map(Ingress::getMetadata)
                .map(meta -> meta.getLabels())
                .orElse(Map.of()));
        labels.put(LABEL_MANAGED_BY, MANAGED_BY_VALUE);
        labels.put(LABEL_ROUTE_NAMESPACE, namespace);
        builder.editOrNewMetadata()
                .withName(name)
                .withNamespace(namespace)
                .withLabels(labels)
                .endMetadata()
                .editOrNewSpec()
                .withRules(rule)
                .withIngressClassName(ingressClassName)
                .endSpec();

        if (StringUtils.hasText(request.getTlsSecret())) {
            builder.editSpec()
                    .withTls(new IngressTLSBuilder()
                            .withSecretName(request.getTlsSecret().trim())
                            .withHosts(host != null ? List.of(host) : List.of())
                            .build())
                    .endSpec();
        } else {
            builder.editSpec().withTls(List.of()).endSpec();
        }

        return builder.build();
    }

    private void validate(RouteCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Route request is required.");
        }
        if (!StringUtils.hasText(request.getNamespace())) {
            throw new IllegalArgumentException("Namespace is required.");
        }
        if (!StringUtils.hasText(request.getName())) {
            throw new IllegalArgumentException("Name is required.");
        }
        if (!StringUtils.hasText(request.getServiceName())) {
            throw new IllegalArgumentException("Service name is required.");
        }
        if (request.getServicePort() == null || request.getServicePort() < 1) {
            throw new IllegalArgumentException("Service port is required.");
        }
        if (StringUtils.hasText(request.getPath()) && !request.getPath().trim().startsWith("/")) {
            throw new IllegalArgumentException("Path must start with '/'.");
        }
    }

    private void validateServiceTarget(String namespace, String serviceName, int servicePort) {
        io.fabric8.kubernetes.api.model.Service service = kubernetesClient.services()
                .inNamespace(namespace)
                .withName(serviceName)
                .get();
        if (service == null) {
            throw new IllegalArgumentException("Service not found: " + serviceName);
        }
        boolean portMatch = Optional.ofNullable(service.getSpec())
                .map(spec -> spec.getPorts())
                .orElse(List.of())
                .stream()
                .anyMatch(port -> port != null && port.getPort() == servicePort);
        if (!portMatch) {
            throw new IllegalArgumentException("Service port " + servicePort + " not found on service " + serviceName);
        }
    }

    private IngressSummaryDTO mapIngress(Ingress ingress) {
        String name = Optional.ofNullable(ingress.getMetadata()).map(meta -> StringUtils.hasText(meta.getName()) ? meta.getName() : "").orElse("");
        String namespace = Optional.ofNullable(ingress.getMetadata()).map(meta -> StringUtils.hasText(meta.getNamespace()) ? meta.getNamespace() : "").orElse("");
        String ingressClassName = Optional.ofNullable(ingress.getSpec())
                .map(spec -> StringUtils.hasText(spec.getIngressClassName()) ? spec.getIngressClassName() : "")
                .orElse("");
        String path = Optional.ofNullable(ingress.getSpec())
                .map(spec -> spec.getRules())
                .orElse(List.of())
                .stream()
                .map(IngressRule::getHttp)
                .filter(Objects::nonNull)
                .flatMap(http -> Optional.ofNullable(http.getPaths()).orElse(List.of()).stream())
                .map(pathSpec -> pathSpec.getPath())
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("/");
        String hosts = Optional.ofNullable(ingress.getSpec())
                .map(spec -> spec.getRules())
                .orElse(List.of())
                .stream()
                .map(rule -> StringUtils.hasText(rule.getHost()) ? rule.getHost() : "<all hosts>")
                .collect(Collectors.joining(", "));

        String services = Optional.ofNullable(ingress.getSpec())
                .map(spec -> spec.getRules())
                .orElse(List.of())
                .stream()
                .map(IngressRule::getHttp)
                .filter(Objects::nonNull)
                .flatMap(http -> Optional.ofNullable(http.getPaths()).orElse(List.of()).stream())
                .map(pathSpec -> pathSpec.getBackend())
                .filter(Objects::nonNull)
                .map(backend -> Optional.ofNullable(backend.getService()).map(svc -> svc.getName()).orElse(""))
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.joining(", "));

        String tls = Optional.ofNullable(ingress.getSpec())
                .map(spec -> spec.getTls())
                .orElse(List.of())
                .stream()
                .map(entry -> Optional.ofNullable(entry.getSecretName()).orElse(""))
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(", "));

        String age = "just now";

        return new IngressSummaryDTO(name, namespace, hosts, services, ingressClassName, path, tls, age);
    }

    private void ensureIngressClass(String ingressClassName, String namespace) {
        if (!StringUtils.hasText(ingressClassName)) {
            return;
        }
        IngressClass existing = kubernetesClient.network().v1().ingressClasses()
                .withName(ingressClassName)
                .get();
        if (existing != null) {
            return;
        }
        String controller = resolveIngressController();
        IngressClass ingressClass = new IngressClassBuilder()
                .withNewMetadata()
                .withName(ingressClassName)
                .withLabels(buildManagedLabels(namespace))
                .endMetadata()
                .withNewSpec()
                .withController(controller)
                .endSpec()
                .build();
        kubernetesClient.network().v1().ingressClasses()
                .resource(ingressClass)
                .createOrReplace();
    }

    private Map<String, String> buildManagedLabels(String namespace) {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(LABEL_MANAGED_BY, MANAGED_BY_VALUE);
        labels.put(LABEL_ROUTE_NAMESPACE, namespace);
        return labels;
    }

    private String resolveIngressClassName(String ingressClassName, String namespace) {
        if (StringUtils.hasText(ingressClassName)) {
            return ingressClassName.trim();
        }
        return DEFAULT_INGRESS_CLASS;
    }

    private String resolveIngressController() {
        return kubernetesClient.network().v1().ingressClasses()
                .list()
                .getItems()
                .stream()
                .map(item -> Optional.ofNullable(item.getSpec()).map(spec -> spec.getController()).orElse(null))
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(DEFAULT_INGRESS_CONTROLLER);
    }
}
