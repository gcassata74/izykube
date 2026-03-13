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

import com.izylife.izykube.collections.ClusterStatusEnum;
import com.izylife.izykube.model.Cluster;
import com.izylife.izykube.repositories.ClusterRepository;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ContainerResource;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.dsl.PodResource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.OutputStream;

@Service
@RequiredArgsConstructor
public class PodShellGateway {

    private final KubernetesClient kubernetesClient;
    private final ClusterRepository clusterRepository;

    public ExecWatch openShell(String namespace,
                               String podName,
                               String containerName,
                               OutputStream output,
                               OutputStream error,
                               ExecListener listener) {

        if (!isNamespaceShellAllowed(namespace)) {
            throw new IllegalStateException("Namespace must be deployed before starting a shell session.");
        }
        if (!StringUtils.hasText(podName)) {
            throw new IllegalArgumentException("Pod name is required.");
        }

        PodResource podResource = kubernetesClient.pods().inNamespace(namespace).withName(podName);
        if (podResource == null || podResource.get() == null) {
            throw new IllegalArgumentException("Pod not found: " + podName);
        }

        ContainerResource execable = StringUtils.hasText(containerName) ? podResource.inContainer(containerName) : podResource;

        return execable
                .redirectingInput()
                .writingOutput(output)
                .writingError(error)
                .writingErrorChannel(error)
                .withTTY()
                .usingListener(listener)
                .exec("/bin/sh");
    }

    public boolean isNamespaceShellAllowed(String namespace) {
        if (!StringUtils.hasText(namespace)) {
            return false;
        }
        return clusterRepository.findByNameSpaceIgnoreCase(namespace)
                .map(Cluster::getStatus)
                .filter(status -> status == ClusterStatusEnum.DEPLOYED)
                .isPresent();
    }
}
