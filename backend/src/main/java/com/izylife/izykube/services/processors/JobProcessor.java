package com.izylife.izykube.services.processors;

import com.izylife.izykube.dto.cluster.ConfigMapDTO;
import com.izylife.izykube.dto.cluster.ContainerDTO;
import com.izylife.izykube.dto.cluster.JobDTO;
import com.izylife.izykube.dto.cluster.LinkDTO;
import com.izylife.izykube.dto.cluster.NodeDTO;
import com.izylife.izykube.dto.cluster.ServiceDTO;
import com.izylife.izykube.dto.cluster.ServiceAccountDTO;
import com.izylife.izykube.model.Asset;
import com.izylife.izykube.repositories.AssetRepository;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

@Processor(JobDTO.class)
@Service
@AllArgsConstructor
public class JobProcessor implements TemplateProcessor<JobDTO> {


    private final AssetRepository assetRepository;
    private final ContainerProcessor containerProcessor;
    private final ConfigMapProcessor configMapProcessor;

    @Override
    public String createTemplate(JobDTO dto) {
        String namespace = resolveNamespace(dto);
        Asset asset = assetRepository.findById(dto.getAssetId())
                .orElseThrow(() -> new NoSuchElementException("Asset not found: " + dto.getAssetId()));

        ServiceDTO sourceService = dto.getSourceNodes().stream()
                .filter(node -> node instanceof ServiceDTO)
                .map(node -> (ServiceDTO) node)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Job must be connected to a target service"));

        StringBuilder yaml = new StringBuilder();

        // Create script ConfigMap using ConfigMapProcessor
        ConfigMapDTO scriptConfigMap = createScriptConfigMapDTO(dto, asset, namespace);
        yaml.append(configMapProcessor.createTemplate(scriptConfigMap));
        yaml.append("---\n");

        // Create Job with init container
        yaml.append(createJobTemplate(dto, scriptConfigMap.getName(), sourceService, asset, namespace));

        return yaml.toString();
    }

    private ConfigMapDTO createScriptConfigMapDTO(JobDTO dto, Asset asset, String namespace) {
        String configMapName = dto.getName() + "-script";

        // Create yaml content directly
        StringBuilder yamlBuilder = new StringBuilder();
        // Removed the extra 'data:' nesting
        yamlBuilder.append("script.sh: |\n");
        // Indent each line of the script with 2 spaces to maintain YAML format
        String[] lines = asset.getScript().split("\n");
        for (String line : lines) {
            yamlBuilder.append("  ").append(line).append("\n");
        }

        ConfigMapDTO configMapDTO = new ConfigMapDTO(
                dto.getName() + "-script",
                configMapName,
                yamlBuilder.toString()
        );
        configMapDTO.setNamespace(namespace);
        return configMapDTO;
    }

    private String createJobTemplate(JobDTO dto, String configMapName, ServiceDTO targetService, Asset asset, String namespace) {
        List<EnvVar> envVars = createEnvironmentVariables(targetService, namespace);

        // Create main container using ContainerProcessor
        ContainerDTO mainContainerDto = new ContainerDTO(
                dto.getName(),
                dto.getName(),
                asset.getId(),
                targetService.getPort()
        );

        Container mainContainer = containerProcessor.processContainer(mainContainerDto, List.of(
                new VolumeMountBuilder()
                        .withName("script-volume")
                        .withMountPath("/scripts")
                        .build()
        ));
        mainContainer.setCommand(List.of("/bin/sh", "/scripts/script.sh"));
        mainContainer.setEnv(envVars);

        Job job = new JobBuilder()
                .withNewMetadata()
                .withName(dto.getName())
                .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                .withTtlSecondsAfterFinished(100)
                .withBackoffLimit(4)
                .withNewTemplate()
                .withNewSpec()
                .withContainers(mainContainer)
                .addNewVolume()
                .withName("script-volume")
                .withNewConfigMap()
                .withName(configMapName)
                .withDefaultMode(0755)
                .endConfigMap()
                .endVolume()
                .withRestartPolicy("Never")
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();

        String serviceAccountName = resolveServiceAccountName(dto, namespace);
        if (StringUtils.hasText(serviceAccountName)
                && job.getSpec() != null
                && job.getSpec().getTemplate() != null
                && job.getSpec().getTemplate().getSpec() != null) {
            job.getSpec().getTemplate().getSpec().setServiceAccountName(serviceAccountName);
        }

        return Serialization.asYaml(job);
    }

    private String resolveServiceAccountName(JobDTO dto, String workloadNamespace) {
        if (dto == null) {
            return null;
        }
        List<LinkDTO> incomingBindings = safeStream(dto.getIncomingLinks()).stream()
                .filter(link -> link != null && "serviceAccountBinding".equalsIgnoreCase(link.getType()))
                .toList();
        if (incomingBindings.size() > 1) {
            throw new IllegalArgumentException("Job " + dto.getName() + " references multiple ServiceAccounts; only one is allowed");
        }

        String ref = dto.getServiceAccountRef();
        ServiceAccountDTO serviceAccount = null;
        if (StringUtils.hasText(ref)) {
            serviceAccount = resolveServiceAccountById(dto, ref);
        } else {
            if (incomingBindings.size() == 1) {
                String sourceId = incomingBindings.get(0).getSource();
                if (StringUtils.hasText(sourceId)) {
                    dto.setServiceAccountRef(sourceId);
                    serviceAccount = resolveServiceAccountById(dto, sourceId);
                }
            }
        }

        if (serviceAccount != null && incomingBindings.size() == 1 && incomingBindings.get(0).getSource() != null) {
            String linkedId = incomingBindings.get(0).getSource();
            if (StringUtils.hasText(linkedId) && StringUtils.hasText(ref) && !linkedId.equals(ref)) {
                throw new IllegalArgumentException("Job " + dto.getName() + " ServiceAccount reference does not match its diagram binding");
            }
        }

        if (serviceAccount == null) {
            return null;
        }

        String saNamespace = serviceAccount.getNamespace();
        String effectiveSaNamespace = saNamespace == null || saNamespace.isBlank() ? workloadNamespace : saNamespace;
        if (!Objects.equals(workloadNamespace, effectiveSaNamespace)) {
            throw new IllegalArgumentException("Workload namespace must match ServiceAccount namespace. Kubernetes does not allow using a ServiceAccount across namespaces.");
        }

        String name = normalizeName(serviceAccount.getName());
        if (name.isEmpty()) {
            throw new IllegalArgumentException("ServiceAccount name is required");
        }
        validateDns1123Subdomain(name);
        return name;
    }

    private ServiceAccountDTO resolveServiceAccountById(JobDTO dto, String serviceAccountId) {
        if (dto == null || !StringUtils.hasText(serviceAccountId)) {
            return null;
        }
        Map<String, NodeDTO> nodeIndex = dto.getNodeIndex();
        if (nodeIndex == null) {
            throw new IllegalArgumentException("Job " + dto.getName() + " cannot resolve ServiceAccount reference (node index missing)");
        }
        NodeDTO resolved = nodeIndex.get(serviceAccountId);
        if (resolved == null) {
            throw new IllegalArgumentException("Job " + dto.getName() + " references missing ServiceAccount: " + serviceAccountId);
        }
        if (!(resolved instanceof ServiceAccountDTO serviceAccount)) {
            throw new IllegalArgumentException("Job " + dto.getName() + " references non-ServiceAccount node: " + serviceAccountId);
        }
        return serviceAccount;
    }

    private List<LinkDTO> safeStream(List<LinkDTO> links) {
        return links == null ? List.of() : links;
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.trim();
    }

    private void validateDns1123Subdomain(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("ServiceAccount name is required");
        }
        if (name.length() > 253) {
            throw new IllegalArgumentException("ServiceAccount name must be <= 253 characters");
        }
        if (!name.matches("^[a-z0-9]([a-z0-9-.]*[a-z0-9])?$")) {
            throw new IllegalArgumentException("ServiceAccount name must be a valid DNS-1123 subdomain (lowercase alphanumeric, '-', '.', start/end alphanumeric)");
        }
    }

    private List<EnvVar> createEnvironmentVariables(ServiceDTO targetService, String namespace) {
        List<EnvVar> envVars = new ArrayList<>();
        String serviceName = targetService.getName().toUpperCase().replace("-", "_");
        String resolvedNamespace = namespace == null || namespace.isBlank() ? "default" : namespace;

        envVars.add(new EnvVar(
                serviceName + "_SERVICE_HOST",
                targetService.getName() + "." + resolvedNamespace + ".svc.cluster.local",
                null
        ));
        envVars.add(new EnvVar(
                serviceName + "_SERVICE_PORT",
                String.valueOf(targetService.getPort()),
                null
        ));
        envVars.add(new EnvVar(
                "TARGET_SERVICE",
                targetService.getName(),
                null
        ));

        return envVars;
    }

    private String resolveNamespace(JobDTO dto) {
        return dto.getNamespace() == null || dto.getNamespace().isBlank() ? "default" : dto.getNamespace();
    }
}
