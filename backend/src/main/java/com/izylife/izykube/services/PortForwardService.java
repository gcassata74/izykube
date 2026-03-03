package com.izylife.izykube.services;

import com.izylife.izykube.collections.ClusterStatusEnum;
import com.izylife.izykube.model.Cluster;
import com.izylife.izykube.repositories.ClusterRepository;
import com.izylife.izykube.repositories.PortForwardRepository;
import com.izylife.izykube.model.PortForwardEntry;
import com.izylife.izykube.web.request.PortForwardRequest;
import com.izylife.izykube.web.response.PortAvailabilityResponse;
import com.izylife.izykube.web.response.PortForwardResponse;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.LocalPortForward;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.PreDestroy;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.BindException;
import java.nio.channels.ServerSocketChannel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class PortForwardService {

    private static final Logger log = LoggerFactory.getLogger(PortForwardService.class);
    private final KubernetesClient kubernetesClient;
    private final ClusterRepository clusterRepository;
    private final Map<String, ForwardEntry> forwards = new ConcurrentHashMap<>();
    private final PortForwardRepository portForwardRepository;

    @PostConstruct
    public void resetPersistedActiveFlags() {
        portForwardRepository.findAll().forEach(entry -> {
            if (entry.isActive()) {
                entry.setActive(false);
                portForwardRepository.save(entry);
            }
        });
    }

    public PortForwardResponse start(PortForwardRequest request) {
        validateRequest(request);

        if (!isNamespaceForwardAllowed(request.getNamespace())) {
            throw new IllegalStateException("Namespace not deployed: " + request.getNamespace());
        }

        String key = buildKey(request);
        ForwardEntry existing = forwards.get(key);
        if (existing != null && existing.active && existing.forward != null) {
            return buildResponse(request, true, "Port forward already active.");
        }

        io.fabric8.kubernetes.api.model.Service service = kubernetesClient.services()
                .inNamespace(request.getNamespace())
                .withName(request.getServiceName())
                .get();

        if (service == null) {
            throw new IllegalArgumentException("Service not found: " + request.getServiceName());
        }

        LocalPortForward forward;
        try {
            InetAddress loopback = InetAddress.getByName("127.0.0.1");
            forward = kubernetesClient.services()
                    .inNamespace(request.getNamespace())
                    .withName(request.getServiceName())
                    // Fabric8 signature: portForward(remotePort, localAddress, localPort)
                    .portForward(request.getTargetPort(), loopback, request.getLocalPort());
        } catch (Exception ex) {
            log.warn("Port forward failed for service {} in namespace {}: {}",
                    request.getServiceName(), request.getNamespace(), ex.getMessage());
            String detail = resolvePortForwardError(ex);
            throw new IllegalStateException("Unable to port forward: " + detail, ex);
        }

        int actualLocalPort = forward.getLocalPort();
        if (actualLocalPort != request.getLocalPort()) {
            String newKey = buildKey(request.getNamespace(), request.getServiceName(), actualLocalPort, request.getTargetPort());
            forwards.remove(key);
            key = newKey;
        }
        forwards.put(key, new ForwardEntry(forward, request.getNamespace(), request.getServiceName(), actualLocalPort, request.getTargetPort(), true));
        upsertEntry(request.getNamespace(), request.getServiceName(), actualLocalPort, request.getTargetPort(), true);
        log.info("Port forward started for service {} in namespace {}: {} -> {}",
                request.getServiceName(), request.getNamespace(), actualLocalPort, request.getTargetPort());

        return buildResponse(request.getNamespace(), request.getServiceName(), actualLocalPort, request.getTargetPort(), true,
                "Port forward started.");
    }

    public PortForwardResponse stop(PortForwardRequest request) {
        validateRequest(request);
        String key = buildKey(request);
        ForwardEntry entry = forwards.get(key);
        if (entry == null || entry.forward == null || !entry.active) {
            return buildResponse(request, false, "Port forward not active.");
        }
        try {
            entry.forward.close();
        } catch (Exception ex) {
            log.warn("Error closing port forward {}: {}", key, ex.getMessage());
        }
        entry.forward = null;
        entry.active = false;
        upsertEntry(request.getNamespace(), request.getServiceName(), request.getLocalPort(), request.getTargetPort(), false);
        log.info("Port forward stopped for service {} in namespace {}: {} -> {}",
                request.getServiceName(), request.getNamespace(), request.getLocalPort(), request.getTargetPort());
        return buildResponse(request, false, "Port forward stopped.");
    }

    public java.util.List<PortForwardResponse> listForwards() {
        return portForwardRepository.findAll().stream()
                .map(entry -> buildResponse(entry.getNamespace(), entry.getServiceName(), entry.getLocalPort(), entry.getTargetPort(), entry.isActive(), entry.isActive() ? "Active" : "Stopped"))
                .toList();
    }

    public java.util.List<PortForwardResponse> listActive() {
        return forwards.values().stream()
                .filter(handle -> handle.active)
                .map(handle -> buildResponse(handle.namespace, handle.serviceName, handle.localPort, handle.targetPort, true, "Active"))
                .toList();
    }

    public PortForwardResponse getStatus(PortForwardRequest request) {
        validateRequest(request);

        ForwardEntry activeForward = findActiveForward(request.getNamespace(), request.getServiceName(), request.getTargetPort());
        if (activeForward != null) {
            return buildResponse(request.getNamespace(), request.getServiceName(), activeForward.localPort, activeForward.targetPort, true, "Active");
        }

        PortForwardEntry persisted = findPersistedEntry(request.getNamespace(), request.getServiceName(), request.getTargetPort());
        if (persisted != null) {
            return buildResponse(persisted.getNamespace(), persisted.getServiceName(), persisted.getLocalPort(), persisted.getTargetPort(), false, "Inactive");
        }

        return buildResponse(request, false, "Inactive");
    }

    public PortForwardResponse getStatus(String namespace, String serviceName, int targetPort) {
        if (!StringUtils.hasText(namespace)) {
            throw new IllegalArgumentException("Namespace is required.");
        }
        if (!StringUtils.hasText(serviceName)) {
            throw new IllegalArgumentException("Service name is required.");
        }
        if (targetPort < 1 || targetPort > 65535) {
            throw new IllegalArgumentException("Target port must be between 1 and 65535.");
        }

        ForwardEntry activeForward = findActiveForward(namespace, serviceName, targetPort);
        if (activeForward != null) {
            return buildResponse(namespace, serviceName, activeForward.localPort, activeForward.targetPort, true, "Active");
        }

        PortForwardEntry persisted = findPersistedEntry(namespace, serviceName, targetPort);
        if (persisted != null) {
            return buildResponse(persisted.getNamespace(), persisted.getServiceName(), persisted.getLocalPort(), persisted.getTargetPort(), false, "Inactive");
        }

        return buildResponse(namespace, serviceName, 0, targetPort, false, "Inactive");
    }

    public void deleteForward(PortForwardRequest request) {
        validateRequest(request);
        String key = buildKey(request);
        ForwardEntry entry = forwards.remove(key);
        if (entry != null && entry.forward != null) {
            try {
                entry.forward.close();
            } catch (Exception ex) {
                log.warn("Error closing port forward {}: {}", key, ex.getMessage());
            }
        }
        portForwardRepository.deleteById(key);
    }

    public PortAvailabilityResponse checkLocalPort(int port) {
        if (port < 1 || port > 65535) {
            return new PortAvailabilityResponse(port, false, "Port must be between 1 and 65535.");
        }
        String error = tryBind("0.0.0.0", port);
        if (error != null) {
            String message = error.contains("Permission denied")
                    ? "Port is not permitted by the OS (permission denied)."
                    : "Port is already in use or cannot be bound: " + error;
            return new PortAvailabilityResponse(port, false, message);
        }
        return new PortAvailabilityResponse(port, true, "Available");
    }

    @PreDestroy
    public void shutdown() {
        forwards.forEach((key, handle) -> {
            try {
                if (handle != null && handle.forward != null) {
                    handle.forward.close();
                }
            } catch (Exception ex) {
                log.warn("Error closing port forward {} on shutdown: {}", key, ex.getMessage());
            }
        });
        forwards.clear();
        portForwardRepository.findAll().forEach(entry -> {
            if (entry.isActive()) {
                entry.setActive(false);
                portForwardRepository.save(entry);
            }
        });
    }

    private boolean isNamespaceForwardAllowed(String namespace) {
        if (!StringUtils.hasText(namespace)) {
            return false;
        }
        return clusterRepository.findByNameSpaceIgnoreCase(namespace)
                .map(Cluster::getStatus)
                .map(status -> status == ClusterStatusEnum.DEPLOYED)
                .orElse(true);
    }

    private void validateRequest(PortForwardRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Port forward request is required.");
        }
        if (!StringUtils.hasText(request.getNamespace())) {
            throw new IllegalArgumentException("Namespace is required.");
        }
        if (!StringUtils.hasText(request.getServiceName())) {
            throw new IllegalArgumentException("Service name is required.");
        }
        if (request.getLocalPort() < 1 || request.getLocalPort() > 65535) {
            throw new IllegalArgumentException("Local port must be between 1 and 65535.");
        }
        if (request.getTargetPort() < 1 || request.getTargetPort() > 65535) {
            throw new IllegalArgumentException("Target port must be between 1 and 65535.");
        }
    }

    private String buildKey(PortForwardRequest request) {
        return buildKey(request.getNamespace(), request.getServiceName(), request.getLocalPort(), request.getTargetPort());
    }

    private String buildKey(String namespace, String serviceName, int localPort, int targetPort) {
        return namespace.trim().toLowerCase() + ":" +
                serviceName.trim().toLowerCase() + ":" +
                localPort + ":" + targetPort;
    }

    private void upsertEntry(String namespace, String serviceName, int localPort, int targetPort, boolean active) {
        String key = buildKey(namespace, serviceName, localPort, targetPort);
        PortForwardEntry entry = portForwardRepository.findById(key).orElseGet(PortForwardEntry::new);
        entry.setId(key);
        entry.setNamespace(namespace);
        entry.setServiceName(serviceName);
        entry.setLocalPort(localPort);
        entry.setTargetPort(targetPort);
        entry.setActive(active);
        portForwardRepository.save(entry);
    }

    private ForwardEntry findActiveForward(String namespace, String serviceName, int targetPort) {
        return forwards.values().stream()
                .filter(handle -> handle.active)
                .filter(handle -> handle.namespace.equalsIgnoreCase(namespace))
                .filter(handle -> handle.serviceName.equalsIgnoreCase(serviceName))
                .filter(handle -> handle.targetPort == targetPort)
                .findFirst()
                .orElse(null);
    }

    private PortForwardEntry findPersistedEntry(String namespace, String serviceName, int targetPort) {
        return portForwardRepository.findByNamespaceIgnoreCaseAndServiceNameIgnoreCase(namespace, serviceName).stream()
                .filter(entry -> entry.getTargetPort() == targetPort)
                .findFirst()
                .orElse(null);
    }

    private PortForwardResponse buildResponse(PortForwardRequest request, boolean active, String message) {
        return buildResponse(request.getNamespace(), request.getServiceName(), request.getLocalPort(), request.getTargetPort(), active, message);
    }

    private PortForwardResponse buildResponse(String namespace, String serviceName, int localPort, int targetPort, boolean active, String message) {
        return new PortForwardResponse(namespace, serviceName, localPort, targetPort, active, message);
    }

    private String tryBind(String host, int port) {
        try (ServerSocketChannel channel = ServerSocketChannel.open()) {
            channel.bind(new InetSocketAddress(host, port));
            return null;
        } catch (IOException ex) {
            return ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        }
    }

    private String resolvePortForwardError(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        if (root instanceof BindException && root.getMessage() != null && root.getMessage().contains("Permission denied")) {
            return "Permission denied while binding local port. Try a different local port or check OS policies.";
        }
        return root.getMessage() != null ? root.getMessage() : ex.getMessage();
    }

    private static class ForwardEntry {
        private LocalPortForward forward;
        private final String namespace;
        private final String serviceName;
        private final int localPort;
        private final int targetPort;
        private boolean active;

        private ForwardEntry(LocalPortForward forward, String namespace, String serviceName, int localPort, int targetPort, boolean active) {
            this.forward = forward;
            this.namespace = namespace;
            this.serviceName = serviceName;
            this.localPort = localPort;
            this.targetPort = targetPort;
            this.active = active;
        }
    }
}
