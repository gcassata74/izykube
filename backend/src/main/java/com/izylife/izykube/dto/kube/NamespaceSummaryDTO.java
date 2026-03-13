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
