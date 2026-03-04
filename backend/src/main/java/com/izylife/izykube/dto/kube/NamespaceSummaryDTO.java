package com.izylife.izykube.dto.kube;

import java.util.List;

public record NamespaceSummaryDTO(
        String namespace,
        List<PodSummaryDTO> pods,
        List<DeploymentSummaryDTO> deployments,
        List<ServiceSummaryDTO> services,
        List<RouteSummaryDTO> routes,
        List<ConfigMapSummaryDTO> configMaps,
        List<SecretSummaryDTO> secrets,
        List<JobSummaryDTO> jobs,
        List<CronJobSummaryDTO> cronJobs,
        List<DaemonSetSummaryDTO> daemonSets,
        List<StatefulSetSummaryDTO> statefulSets
) {
}
