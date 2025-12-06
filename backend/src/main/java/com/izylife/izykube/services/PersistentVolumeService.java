package com.izylife.izykube.services;

import com.izylife.izykube.dto.storage.PersistentVolumeDTO;
import io.fabric8.kubernetes.api.model.HostPathVolumeSource;
import io.fabric8.kubernetes.api.model.PersistentVolume;
import io.fabric8.kubernetes.api.model.PersistentVolumeBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeSpec;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.StatusDetails;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PersistentVolumeService {

    private static final String DEFAULT_CAPACITY = "10Gi";
    private static final String DEFAULT_RECLAIM_POLICY = "Retain";
    private static final String DEFAULT_VOLUME_MODE = "Filesystem";
    private static final List<String> DEFAULT_ACCESS_MODES = List.of("ReadWriteOnce");

    private final KubernetesClient kubernetesClient;

    public List<PersistentVolumeDTO> listPersistentVolumes() {
        return kubernetesClient.persistentVolumes().list().getItems().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public PersistentVolumeDTO getPersistentVolume(String name) {
        PersistentVolume pv = kubernetesClient.persistentVolumes().withName(name).get();
        return pv == null ? null : toDto(pv);
    }

    public PersistentVolumeDTO createOrUpdate(PersistentVolumeDTO dto) {
        if (!StringUtils.hasText(dto.getName())) {
            throw new IllegalArgumentException("PersistentVolume name is required");
        }
        PersistentVolume resource = buildPersistentVolume(dto);
        PersistentVolume persisted = kubernetesClient.persistentVolumes().resource(resource).createOrReplace();
        return toDto(persisted);
    }

    public boolean deletePersistentVolume(String name) {
        if (!StringUtils.hasText(name)) {
            return false;
        }
        List<StatusDetails> result = kubernetesClient.persistentVolumes().withName(name).delete();
        return result != null && !result.isEmpty();
    }

    private PersistentVolume buildPersistentVolume(PersistentVolumeDTO dto) {
        String reclaimPolicy = StringUtils.hasText(dto.getReclaimPolicy()) ? dto.getReclaimPolicy() : DEFAULT_RECLAIM_POLICY;
        String volumeMode = StringUtils.hasText(dto.getVolumeMode()) ? dto.getVolumeMode() : DEFAULT_VOLUME_MODE;
        List<String> accessModes = dto.getAccessModes().isEmpty() ? DEFAULT_ACCESS_MODES : dto.getAccessModes();
        String capacity = dto.getCapacityOrDefault(DEFAULT_CAPACITY);
        String hostPath = StringUtils.hasText(dto.getPath()) ? dto.getPath() : "/data/" + dto.getName();

        return new PersistentVolumeBuilder()
                .withNewMetadata()
                .withName(dto.getName())
                .endMetadata()
                .withNewSpec()
                .withAccessModes(accessModes)
                .withCapacity(Collections.singletonMap("storage", new Quantity(capacity)))
                .withPersistentVolumeReclaimPolicy(reclaimPolicy)
                .withVolumeMode(volumeMode)
                .withStorageClassName(dto.getStorageClassName())
                .withHostPath(new HostPathVolumeSource(hostPath, null))
                .endSpec()
                .build();
    }

    private PersistentVolumeDTO toDto(PersistentVolume persistentVolume) {
        if (persistentVolume == null) {
            return null;
        }
        PersistentVolumeSpec spec = persistentVolume.getSpec();
        Map<String, Quantity> capacityMap = spec != null ? spec.getCapacity() : null;
        String capacity = Optional.ofNullable(capacityMap)
                .map(map -> map.get("storage"))
                .map(Quantity::toString)
                .orElse(null);
        HostPathVolumeSource hostPath = spec != null ? spec.getHostPath() : null;

        return PersistentVolumeDTO.builder()
                .name(persistentVolume.getMetadata() != null ? persistentVolume.getMetadata().getName() : null)
                .storageClassName(spec != null ? spec.getStorageClassName() : null)
                .accessModes(spec != null ? spec.getAccessModes() : Collections.emptyList())
                .capacity(capacity)
                .reclaimPolicy(spec != null ? spec.getPersistentVolumeReclaimPolicy() : null)
                .volumeMode(spec != null ? spec.getVolumeMode() : null)
                .path(hostPath != null ? hostPath.getPath() : null)
                .build();
    }
}
