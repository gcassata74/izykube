/*
 * IzyKube
 * Copyright (c) 2026-present Izylife Solutions s.r.l.
 * Author: Giuseppe Cassata
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.izylife.izykube.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.izylife.izykube.dto.kube.RouteSummaryDTO;
import com.izylife.izykube.model.Cluster;
import com.izylife.izykube.repositories.ClusterRepository;
import com.izylife.izykube.web.request.RouteCreateRequest;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RouteService {
    private static final String LABEL_MANAGED_BY = "izykube.io/managed-by";
    private static final String LABEL_ROUTE_NAMESPACE = "izykube.io/route-namespace";
    private static final String LABEL_ROUTE_NAME = "izykube.io/route-name";
    private static final String MANAGED_BY_VALUE = "izykube";

    private static final String ISTIO_API_VERSION = "networking.istio.io/v1beta1";
    private static final String ISTIO_GATEWAY_KIND = "Gateway";
    private static final String ISTIO_VIRTUALSERVICE_KIND = "VirtualService";
    private static final String ISTIO_GATEWAY_SELECTOR_KEY = "istio";
    private static final String ISTIO_GATEWAY_SELECTOR_VALUE = "ingressgateway";
    private static final String SHARED_GATEWAY_NAME = "izykube-gateway";
    private static final String SHARED_GATEWAY_NAMESPACE = "istio-system";
    private static final String SHARED_GATEWAY_REF = SHARED_GATEWAY_NAMESPACE + "/" + SHARED_GATEWAY_NAME;
    private static final String CERT_MANAGER_API_VERSION = "cert-manager.io/v1";
    private static final String CERTIFICATE_KIND = "Certificate";
    private static final String CERTIFICATE_ISSUER_NAME = "izykube-ca-issuer";
    private static final String CERTIFICATE_ISSUER_KIND = "ClusterIssuer";
    private static final Set<Integer> DEFAULT_HTTP_PORTS = new HashSet<>(Set.of(
            80, 81, 443, 8080, 8081, 8443, 3000, 4200, 5000, 5173, 8000, 8888, 9000
    ));
    private static final String KIND_VIRTUALSERVICE = "virtualservice";

    private static final ResourceDefinitionContext GATEWAY_CONTEXT = new ResourceDefinitionContext.Builder()
            .withGroup("networking.istio.io")
            .withVersion("v1beta1")
            .withKind("Gateway")
            .withPlural("gateways")
            .withNamespaced(true)
            .build();

    private static final ResourceDefinitionContext VIRTUALSERVICE_CONTEXT = new ResourceDefinitionContext.Builder()
            .withGroup("networking.istio.io")
            .withVersion("v1beta1")
            .withKind("VirtualService")
            .withPlural("virtualservices")
            .withNamespaced(true)
            .build();

    private static final ResourceDefinitionContext GATEWAY_CONTEXT_V1ALPHA3 = new ResourceDefinitionContext.Builder()
            .withGroup("networking.istio.io")
            .withVersion("v1alpha3")
            .withKind("Gateway")
            .withPlural("gateways")
            .withNamespaced(true)
            .build();

    private static final ResourceDefinitionContext VIRTUALSERVICE_CONTEXT_V1ALPHA3 = new ResourceDefinitionContext.Builder()
            .withGroup("networking.istio.io")
            .withVersion("v1alpha3")
            .withKind("VirtualService")
            .withPlural("virtualservices")
            .withNamespaced(true)
            .build();

    private static final ResourceDefinitionContext CERTIFICATE_CONTEXT = new ResourceDefinitionContext.Builder()
            .withGroup("cert-manager.io")
            .withVersion("v1")
            .withKind("Certificate")
            .withPlural("certificates")
            .withNamespaced(true)
            .build();

    private final KubernetesClient kubernetesClient;
    private final ClusterRepository clusterRepository;
    private final ObjectMapper objectMapper;

    public RouteSummaryDTO create(RouteCreateRequest request) {
        validate(request);

        String namespace = request.getNamespace().trim();
        String name = request.getName().trim();
        String tlsSecret = resolveTlsSecret(request, namespace, name);

        ServicePort matchedServicePort = validateServiceTarget(namespace, request.getServiceName().trim(), request.getServicePort());

        if (StringUtils.hasText(tlsSecret)) {
            GenericKubernetesResource certificate = buildCertificate(request, SHARED_GATEWAY_NAMESPACE, buildCertificateName(namespace, name), tlsSecret);
            createOrReplaceCertificate(SHARED_GATEWAY_NAMESPACE, certificate);
            updateSharedGatewayTls(request, tlsSecret);
        } else {
            updateSharedGatewayTls(request, null);
        }

        GenericKubernetesResource virtualService = buildVirtualService(request, namespace, name, matchedServicePort.getPort());
        createOrReplaceWithFallback(VIRTUALSERVICE_CONTEXT, VIRTUALSERVICE_CONTEXT_V1ALPHA3, namespace, virtualService);

        GenericKubernetesResource created = getWithFallback(VIRTUALSERVICE_CONTEXT, VIRTUALSERVICE_CONTEXT_V1ALPHA3, namespace, name);

        if (created == null) {
            throw new IllegalStateException("Route created but could not be retrieved.");
        }

        return mapVirtualService(created);
    }

    public void delete(String namespace, String name) {
        if (!StringUtils.hasText(namespace) || !StringUtils.hasText(name)) {
            throw new IllegalArgumentException("Namespace and name are required.");
        }
        String trimmedNamespace = namespace.trim();
        String trimmedName = name.trim();

        GenericKubernetesResource existing = getWithFallback(VIRTUALSERVICE_CONTEXT, VIRTUALSERVICE_CONTEXT_V1ALPHA3, trimmedNamespace, trimmedName);

        if (existing != null) {
            var deletion = deleteWithFallback(VIRTUALSERVICE_CONTEXT, VIRTUALSERVICE_CONTEXT_V1ALPHA3, trimmedNamespace, trimmedName);
            boolean deleted = deletion != null && !deletion.isEmpty();
            if (!deleted) {
                GenericKubernetesResource recheck = getWithFallback(VIRTUALSERVICE_CONTEXT, VIRTUALSERVICE_CONTEXT_V1ALPHA3, trimmedNamespace, trimmedName);
                if (recheck != null) {
                    throw new IllegalStateException("Route not found or could not be deleted.");
                }
            }
        }

        deleteCertificateResources(trimmedNamespace, trimmedName);
        removeSharedGatewayTls(trimmedNamespace, trimmedName);
        deletePersistedRoute(trimmedNamespace, trimmedName);
    }

    public RouteSummaryDTO update(String namespace, String name, RouteCreateRequest request) {
        if (!StringUtils.hasText(namespace) || !StringUtils.hasText(name)) {
            throw new IllegalArgumentException("Namespace and name are required.");
        }
        if (request == null) {
            throw new IllegalArgumentException("Route request is required.");
        }
        request.setNamespace(namespace);
        request.setName(name);
        validate(request);

        String trimmedNamespace = namespace.trim();
        String trimmedName = name.trim();

        ServicePort matchedServicePort = validateServiceTarget(trimmedNamespace, request.getServiceName().trim(), request.getServicePort());

        String tlsSecret = resolveTlsSecret(request, trimmedNamespace, trimmedName);
        if (StringUtils.hasText(tlsSecret)) {
            GenericKubernetesResource certificate = buildCertificate(request, SHARED_GATEWAY_NAMESPACE, buildCertificateName(trimmedNamespace, trimmedName), tlsSecret);
            createOrReplaceCertificate(SHARED_GATEWAY_NAMESPACE, certificate);
            updateSharedGatewayTls(request, tlsSecret);
        } else {
            deleteCertificateResources(trimmedNamespace, trimmedName);
            removeSharedGatewayTls(trimmedNamespace, trimmedName);
        }

        GenericKubernetesResource virtualService = buildVirtualService(request, trimmedNamespace, trimmedName, matchedServicePort.getPort());

        GenericKubernetesResource result = createOrReplaceWithFallback(VIRTUALSERVICE_CONTEXT, VIRTUALSERVICE_CONTEXT_V1ALPHA3, trimmedNamespace, virtualService);

        if (result == null) {
            throw new IllegalStateException("Route update failed.");
        }

        return mapVirtualService(result);
    }

    private GenericKubernetesResource buildGateway(RouteCreateRequest request, String namespace, String gatewayName) {
        String host = StringUtils.hasText(request.getHost()) ? request.getHost().trim() : "*";
        String tlsSecret = StringUtils.hasText(request.getTlsSecret()) ? request.getTlsSecret().trim() : null;

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", gatewayName);
        metadata.put("namespace", namespace);
        metadata.put("labels", buildManagedLabels(namespace, request.getName()));

        List<Map<String, Object>> servers = new ArrayList<>();
        Map<String, Object> httpServer = new LinkedHashMap<>();
        httpServer.put("port", Map.of(
                "number", 80,
                "name", "http",
                "protocol", "HTTP"
        ));
        httpServer.put("hosts", List.of(host));
        servers.add(httpServer);

        if (StringUtils.hasText(tlsSecret)) {
            Map<String, Object> httpsServer = new LinkedHashMap<>();
            httpsServer.put("port", Map.of(
                    "number", 443,
                    "name", "https",
                    "protocol", "HTTPS"
            ));
            httpsServer.put("hosts", List.of(host));
            httpsServer.put("tls", Map.of(
                    "mode", "SIMPLE",
                    "credentialName", tlsSecret
            ));
            servers.add(httpsServer);
        }

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("selector", Map.of(ISTIO_GATEWAY_SELECTOR_KEY, ISTIO_GATEWAY_SELECTOR_VALUE));
        spec.put("servers", servers);

        GenericKubernetesResource gateway = new GenericKubernetesResource();
        gateway.setApiVersion(ISTIO_API_VERSION);
        gateway.setKind(ISTIO_GATEWAY_KIND);
        gateway.setMetadata(buildObjectMeta(metadata));
        gateway.setAdditionalProperty("spec", spec);
        return gateway;
    }

    private GenericKubernetesResource buildVirtualService(RouteCreateRequest request, String namespace, String name, int resolvedServicePort) {
        String host = StringUtils.hasText(request.getHost()) ? request.getHost().trim() : "*";
        String path = StringUtils.hasText(request.getPath()) ? request.getPath().trim() : "/";
        String serviceName = request.getServiceName().trim();
        int servicePort = resolvedServicePort;

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", name);
        metadata.put("namespace", namespace);
        metadata.put("labels", buildManagedLabels(namespace, name));

        Map<String, Object> destination = new LinkedHashMap<>();
        destination.put("host", serviceName);
        destination.put("port", Map.of("number", servicePort));

        Map<String, Object> route = new LinkedHashMap<>();
        route.put("destination", destination);

        Map<String, Object> match = new LinkedHashMap<>();
        match.put("uri", Map.of("prefix", path));

        Map<String, Object> http = new LinkedHashMap<>();
        http.put("match", List.of(match));
        http.put("route", List.of(route));

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("hosts", List.of(host));
        spec.put("gateways", List.of(SHARED_GATEWAY_REF));
        spec.put("http", List.of(http));

        GenericKubernetesResource virtualService = new GenericKubernetesResource();
        virtualService.setApiVersion(ISTIO_API_VERSION);
        virtualService.setKind(ISTIO_VIRTUALSERVICE_KIND);
        virtualService.setMetadata(buildObjectMeta(metadata));
        virtualService.setAdditionalProperty("spec", spec);
        return virtualService;
    }

    private void updateSharedGatewayTls(RouteCreateRequest request, String tlsSecret) {
        List<String> hosts = parseHosts(request.getHost());
        if (hosts.isEmpty()) {
            return;
        }
        GenericKubernetesResource gateway = getWithFallback(GATEWAY_CONTEXT, GATEWAY_CONTEXT_V1ALPHA3, SHARED_GATEWAY_NAMESPACE, SHARED_GATEWAY_NAME);
        if (gateway == null) {
            throw new IllegalStateException("Shared gateway not found. Run terraform -chdir=terraform/addons apply.");
        }
        Map<String, Object> spec = getSpecMap(gateway);
        List<Map<String, Object>> servers = new ArrayList<>(getMapList(spec.get("servers")));

        List<Map<String, Object>> filtered = servers.stream()
                .filter(server -> !isHttpsServerForHosts(server, hosts))
                .collect(Collectors.toList());

        if (StringUtils.hasText(tlsSecret)) {
            Map<String, Object> httpsServer = new LinkedHashMap<>();
            httpsServer.put("port", Map.of(
                    "number", 443,
                    "name", "https-" + normalizeName(hosts.get(0)),
                    "protocol", "HTTPS"
            ));
            httpsServer.put("hosts", hosts);
            httpsServer.put("tls", Map.of(
                    "mode", "SIMPLE",
                    "credentialName", tlsSecret
            ));
            filtered.add(httpsServer);
        }

        spec.put("servers", filtered);
        gateway.setAdditionalProperty("spec", spec);
        createOrReplaceWithFallback(GATEWAY_CONTEXT, GATEWAY_CONTEXT_V1ALPHA3, SHARED_GATEWAY_NAMESPACE, gateway);
    }

    private void removeSharedGatewayTls(String namespace, String routeName) {
        String secretName = buildTlsSecretName(namespace, routeName);
        GenericKubernetesResource gateway = getWithFallback(GATEWAY_CONTEXT, GATEWAY_CONTEXT_V1ALPHA3, SHARED_GATEWAY_NAMESPACE, SHARED_GATEWAY_NAME);
        if (gateway == null) {
            return;
        }
        Map<String, Object> spec = getSpecMap(gateway);
        List<Map<String, Object>> servers = getMapList(spec.get("servers"));
        List<Map<String, Object>> filtered = servers.stream()
                .filter(server -> !secretName.equals(getMap(server.get("tls")).get("credentialName")))
                .collect(Collectors.toList());
        if (filtered.size() == servers.size()) {
            return;
        }
        spec.put("servers", filtered);
        gateway.setAdditionalProperty("spec", spec);
        createOrReplaceWithFallback(GATEWAY_CONTEXT, GATEWAY_CONTEXT_V1ALPHA3, SHARED_GATEWAY_NAMESPACE, gateway);
    }

    private boolean isHttpsServerForHosts(Map<String, Object> server, List<String> hosts) {
        Map<String, Object> port = getMap(server.get("port"));
        Object number = port.get("number");
        if (number == null || !"443".equals(number.toString())) {
            return false;
        }
        List<String> serverHosts = getStringList(server.get("hosts"));
        if (serverHosts.isEmpty()) {
            return false;
        }
        for (String host : hosts) {
            if (serverHosts.contains(host)) {
                return true;
            }
        }
        return false;
    }

    private GenericKubernetesResource buildCertificate(RouteCreateRequest request,
                                                       String namespace,
                                                       String certName,
                                                       String tlsSecretName) {
        List<String> hosts = parseHosts(request.getHost());

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", certName);
        metadata.put("namespace", namespace);
        metadata.put("labels", buildManagedLabels(namespace, request.getName()));

        Map<String, Object> issuerRef = new LinkedHashMap<>();
        issuerRef.put("name", CERTIFICATE_ISSUER_NAME);
        issuerRef.put("kind", CERTIFICATE_ISSUER_KIND);
        issuerRef.put("group", "cert-manager.io");

        Map<String, Object> secretTemplate = new LinkedHashMap<>();
        secretTemplate.put("labels", buildManagedLabels(namespace, request.getName()));

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("secretName", tlsSecretName);
        spec.put("commonName", hosts.get(0));
        spec.put("dnsNames", hosts);
        spec.put("issuerRef", issuerRef);
        spec.put("secretTemplate", secretTemplate);

        GenericKubernetesResource certificate = new GenericKubernetesResource();
        certificate.setApiVersion(CERT_MANAGER_API_VERSION);
        certificate.setKind(CERTIFICATE_KIND);
        certificate.setMetadata(buildObjectMeta(metadata));
        certificate.setAdditionalProperty("spec", spec);
        return certificate;
    }

    private ObjectMeta buildObjectMeta(Map<String, Object> metadata) {
        ObjectMeta meta = new ObjectMeta();
        meta.setName((String) metadata.get("name"));
        meta.setNamespace((String) metadata.get("namespace"));
        @SuppressWarnings("unchecked")
        Map<String, String> labels = (Map<String, String>) metadata.get("labels");
        meta.setLabels(labels);
        return meta;
    }

    private void createOrReplaceCertificate(String namespace, GenericKubernetesResource certificate) {
        kubernetesClient.genericKubernetesResources(CERTIFICATE_CONTEXT)
                .inNamespace(namespace)
                .resource(certificate)
                .createOrReplace();
    }

    private void deleteCertificateResources(String namespace, String routeName) {
        String certName = buildCertificateName(namespace, routeName);
        kubernetesClient.genericKubernetesResources(CERTIFICATE_CONTEXT)
                .inNamespace(SHARED_GATEWAY_NAMESPACE)
                .withName(certName)
                .delete();

        String secretName = buildTlsSecretName(namespace, routeName);
        kubernetesClient.secrets()
                .inNamespace(SHARED_GATEWAY_NAMESPACE)
                .withName(secretName)
                .delete();
    }

    private Map<String, String> buildManagedLabels(String namespace, String routeName) {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(LABEL_MANAGED_BY, MANAGED_BY_VALUE);
        labels.put(LABEL_ROUTE_NAMESPACE, namespace);
        if (StringUtils.hasText(routeName)) {
            labels.put(LABEL_ROUTE_NAME, routeName);
        }
        return labels;
    }

    private String buildGatewayName(String routeName) {
        return routeName + "-gateway";
    }

    private String buildCertificateName(String namespace, String routeName) {
        return normalizeName(namespace + "-" + routeName + "-cert");
    }

    private String buildTlsSecretName(String namespace, String routeName) {
        return normalizeName(namespace + "-" + routeName + "-tls");
    }

    private String resolveTlsSecret(RouteCreateRequest request, String namespace, String routeName) {
        boolean httpsEnabled = Boolean.TRUE.equals(request.getHttpsEnabled());
        if (!httpsEnabled) {
            request.setTlsSecret(null);
            return null;
        }
        String tlsSecret = buildTlsSecretName(namespace, routeName);
        request.setTlsSecret(tlsSecret);
        return tlsSecret;
    }

    private String normalizeName(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
    }

    private List<String> parseHosts(String hostValue) {
        if (!StringUtils.hasText(hostValue)) {
            return List.of();
        }
        return List.of(hostValue.split(",")).stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
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
        if (Boolean.TRUE.equals(request.getHttpsEnabled())) {
            List<String> hosts = parseHosts(request.getHost());
            if (hosts.isEmpty() || (hosts.size() == 1 && "*".equals(hosts.get(0)))) {
                throw new IllegalArgumentException("HTTPS routes require a concrete host.");
            }
        }
    }

    private ServicePort validateServiceTarget(String namespace, String serviceName, int servicePort) {
        io.fabric8.kubernetes.api.model.Service service = kubernetesClient.services()
                .inNamespace(namespace)
                .withName(serviceName)
                .get();
        if (service == null) {
            throw new IllegalArgumentException("Service not found: " + serviceName);
        }
        List<ServicePort> ports = Optional.ofNullable(service.getSpec())
                .map(spec -> spec.getPorts())
                .orElse(List.of())
                .stream()
                .filter(Objects::nonNull)
                .toList();
        ServicePort matchedPort = ports.stream()
                .filter(port -> port.getPort() == servicePort)
                .findFirst()
                .orElseGet(() -> ports.stream()
                        .filter(port -> {
                            IntOrString target = port.getTargetPort();
                            Integer intVal = target != null ? target.getIntVal() : null;
                            return intVal != null && intVal == servicePort;
                        })
                        .findFirst()
                        .orElse(null));
        if (matchedPort == null) {
            throw new IllegalArgumentException("Service port " + servicePort + " not found on service " + serviceName);
        }
        if (!isHttpLikePort(matchedPort)) {
            throw new IllegalArgumentException("HTTP/HTTPS routes are allowed only for web service ports. Selected port: " + servicePort);
        }
        return matchedPort;
    }

    private boolean isHttpLikePort(ServicePort port) {
        if (port == null) {
            return false;
        }
        String name = Optional.ofNullable(port.getName()).map(value -> value.toLowerCase(Locale.ROOT)).orElse("");
        if (name.startsWith("http") || name.startsWith("web")) {
            return true;
        }
        return DEFAULT_HTTP_PORTS.contains(port.getPort());
    }

    private RouteSummaryDTO mapVirtualService(GenericKubernetesResource virtualService) {
        String name = Optional.ofNullable(virtualService.getMetadata())
                .map(meta -> StringUtils.hasText(meta.getName()) ? meta.getName() : "")
                .orElse("");
        String namespace = Optional.ofNullable(virtualService.getMetadata())
                .map(meta -> StringUtils.hasText(meta.getNamespace()) ? meta.getNamespace() : "")
                .orElse("");

        Map<String, Object> spec = getSpecMap(virtualService);
        List<String> hosts = getStringList(spec.get("hosts"));
        String hostsValue = normalizeHosts(hosts);
        String gatewayName = getStringList(spec.get("gateways")).stream().findFirst().orElse("");

        Map<String, Object> httpEntry = getFirstMap(spec.get("http"));
        String path = "/";
        if (!httpEntry.isEmpty()) {
            Map<String, Object> match = getFirstMap(httpEntry.get("match"));
            Map<String, Object> uri = getMap(match.get("uri"));
            path = Optional.ofNullable(uri.get("prefix"))
                    .map(Object::toString)
                    .filter(StringUtils::hasText)
                    .orElse(path);
        }

        Map<String, Object> routeEntry = getFirstMap(httpEntry.get("route"));
        Map<String, Object> destination = getMap(routeEntry.get("destination"));
        String serviceName = Optional.ofNullable(destination.get("host")).map(Object::toString).orElse("");
        Integer servicePort = Optional.ofNullable(getMap(destination.get("port")).get("number"))
                .filter(Objects::nonNull)
                .map(value -> Integer.parseInt(value.toString()))
                .orElse(null);
        String serviceTarget = StringUtils.hasText(serviceName)
                ? serviceName + (servicePort != null ? ":" + servicePort : "")
                : "";

        String tls = resolveRouteTls(namespace, name);
        String age = "just now";

        return new RouteSummaryDTO(name, namespace, hostsValue, serviceTarget, gatewayName, path, tls, age, "DEPLOYED");
    }

    private String resolveRouteTls(String namespace, String routeName) {
        String secretName = buildTlsSecretName(namespace, routeName);
        boolean exists = kubernetesClient.secrets()
                .inNamespace(SHARED_GATEWAY_NAMESPACE)
                .withName(secretName)
                .get() != null;
        return exists ? secretName : "";
    }

    private Map<String, Object> getSpecMap(GenericKubernetesResource resource) {
        Object spec = resource.getAdditionalProperties().get("spec");
        return getMap(spec);
    }

    private Map<String, Object> getMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, val) -> {
                if (key != null) {
                    result.put(key.toString(), val);
                }
            });
            return result;
        }
        return new LinkedHashMap<>();
    }

    private Map<String, Object> getFirstMap(Object value) {
        List<Map<String, Object>> list = getMapList(value);
        return list.isEmpty() ? new LinkedHashMap<>() : list.get(0);
    }

    private List<Map<String, Object>> getMapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object entry : list) {
            if (entry instanceof Map<?, ?>) {
                result.add(getMap(entry));
            }
        }
        return result;
    }

    private List<String> getStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .collect(Collectors.toList());
    }

    private String normalizeHosts(List<String> hosts) {
        if (hosts == null || hosts.isEmpty()) {
            return "<all hosts>";
        }
        if (hosts.stream().anyMatch(host -> host.equals("*"))) {
            return "<all hosts>";
        }
        return hosts.stream().filter(StringUtils::hasText).collect(Collectors.joining(", "));
    }

    private ResourceDefinitionContext resolveGatewayContext(String namespace) {
        return resolveContext(GATEWAY_CONTEXT, GATEWAY_CONTEXT_V1ALPHA3, namespace);
    }

    private ResourceDefinitionContext resolveVirtualServiceContext(String namespace) {
        return resolveContext(VIRTUALSERVICE_CONTEXT, VIRTUALSERVICE_CONTEXT_V1ALPHA3, namespace);
    }

    private ResourceDefinitionContext resolveContext(ResourceDefinitionContext primary,
                                                     ResourceDefinitionContext fallback,
                                                     String namespace) {
        try {
            kubernetesClient.genericKubernetesResources(primary).inNamespace(namespace).list();
            return primary;
        } catch (Exception ex) {
            try {
                kubernetesClient.genericKubernetesResources(fallback).inNamespace(namespace).list();
                return fallback;
            } catch (Exception ignored) {
                if (ex instanceof RuntimeException) {
                    throw (RuntimeException) ex;
                }
                throw new IllegalStateException("Istio CRDs not available for gateways/virtualservices.");
            }
        }
    }

    private GenericKubernetesResource createOrReplaceWithFallback(ResourceDefinitionContext primary,
                                                                  ResourceDefinitionContext fallback,
                                                                  String namespace,
                                                                  GenericKubernetesResource resource) {
        try {
            return kubernetesClient.genericKubernetesResources(primary)
                    .inNamespace(namespace)
                    .resource(resource)
                    .createOrReplace();
        } catch (KubernetesClientException ex) {
            if (!isNotFound(ex)) {
                throw ex;
            }
            return kubernetesClient.genericKubernetesResources(fallback)
                    .inNamespace(namespace)
                    .resource(resource)
                    .createOrReplace();
        }
    }

    private GenericKubernetesResource getWithFallback(ResourceDefinitionContext primary,
                                                      ResourceDefinitionContext fallback,
                                                      String namespace,
                                                      String name) {
        try {
            return kubernetesClient.genericKubernetesResources(primary)
                    .inNamespace(namespace)
                    .withName(name)
                    .get();
        } catch (KubernetesClientException ex) {
            if (!isNotFound(ex)) {
                throw ex;
            }
            return kubernetesClient.genericKubernetesResources(fallback)
                    .inNamespace(namespace)
                    .withName(name)
                    .get();
        }
    }

    private List<io.fabric8.kubernetes.api.model.StatusDetails> deleteWithFallback(ResourceDefinitionContext primary,
                                                                                   ResourceDefinitionContext fallback,
                                                                                   String namespace,
                                                                                   String name) {
        try {
            return kubernetesClient.genericKubernetesResources(primary)
                    .inNamespace(namespace)
                    .withName(name)
                    .delete();
        } catch (KubernetesClientException ex) {
            if (!isNotFound(ex)) {
                throw ex;
            }
            return kubernetesClient.genericKubernetesResources(fallback)
                    .inNamespace(namespace)
                    .withName(name)
                    .delete();
        }
    }

    private boolean isNotFound(KubernetesClientException ex) {
        return ex != null && ex.getCode() == 404;
    }

    private void deletePersistedRoute(String namespace, String routeName) {
        List<Cluster> clusters = clusterRepository.findAll();
        if (clusters.isEmpty()) {
            return;
        }
        for (Cluster cluster : clusters) {
            if (cluster == null || !StringUtils.hasText(cluster.getDiagram())) {
                continue;
            }
            try {
                JsonNode root = objectMapper.readTree(cluster.getDiagram());
                if (!(root instanceof ObjectNode objectNode)) {
                    continue;
                }
                JsonNode raw = objectNode.path("rawManifests");
                if (!(raw instanceof ArrayNode rawManifests)) {
                    continue;
                }

                ArrayNode filtered = objectMapper.createArrayNode();
                boolean removed = false;
                for (JsonNode entry : rawManifests) {
                    if (isMatchingPersistedVirtualService(entry, namespace, routeName, cluster.getNameSpace())) {
                        removed = true;
                        continue;
                    }
                    filtered.add(entry);
                }
                if (!removed) {
                    continue;
                }
                objectNode.set("rawManifests", filtered);
                cluster.setDiagram(objectMapper.writeValueAsString(objectNode));
                clusterRepository.save(cluster);
            } catch (Exception ex) {
                // Route deletion should not fail because one persisted snapshot could not be updated.
            }
        }
    }

    private boolean isMatchingPersistedVirtualService(JsonNode entry,
                                                      String namespace,
                                                      String routeName,
                                                      String fallbackNamespace) {
        if (entry == null || entry.isNull()) {
            return false;
        }
        JsonNode manifestNode = entry.path("manifest");

        String kind = textOrEmpty(entry.path("kind"));
        if (!StringUtils.hasText(kind)) {
            kind = textOrEmpty(manifestNode.path("kind"));
        }
        if (!KIND_VIRTUALSERVICE.equalsIgnoreCase(kind)) {
            return false;
        }

        String name = textOrEmpty(entry.path("name"));
        if (!StringUtils.hasText(name)) {
            name = textOrEmpty(manifestNode.path("metadata").path("name"));
        }
        if (!routeName.equalsIgnoreCase(name)) {
            return false;
        }

        String manifestNamespace = textOrEmpty(manifestNode.path("metadata").path("namespace"));
        if (!StringUtils.hasText(manifestNamespace)) {
            manifestNamespace = fallbackNamespace;
        }
        return StringUtils.hasText(manifestNamespace) && namespace.equalsIgnoreCase(manifestNamespace);
    }

    private String textOrEmpty(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        String value = node.asText("");
        return value == null ? "" : value.trim();
    }
}
