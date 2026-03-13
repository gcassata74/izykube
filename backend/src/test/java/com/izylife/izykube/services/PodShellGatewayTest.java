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
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.api.model.PodList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PodShellGatewayTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private KubernetesClient kubernetesClient;

    @Mock
    private ClusterRepository clusterRepository;

    @Mock
    private ExecListener execListener;

    private PodShellGateway podShellGateway;

    @BeforeEach
    void setUp() {
        podShellGateway = new PodShellGateway(kubernetesClient, clusterRepository);
    }

    @Test
    void shouldRejectShellRequestsWhenNamespaceNotDeployed() {
        Cluster cluster = new Cluster();
        cluster.setStatus(ClusterStatusEnum.CREATED);
        when(clusterRepository.findByNameSpaceIgnoreCase("demo")).thenReturn(Optional.of(cluster));

        assertThrows(IllegalStateException.class, () ->
                podShellGateway.openShell(
                        "demo",
                        "pod-1",
                        null,
                        new ByteArrayOutputStream(),
                        new ByteArrayOutputStream(),
                        execListener)
        );
    }

    @Test
    void shouldCreateExecWatchWhenNamespaceIsDeployed() {
        Cluster cluster = new Cluster();
        cluster.setStatus(ClusterStatusEnum.DEPLOYED);
        when(clusterRepository.findByNameSpaceIgnoreCase("demo")).thenReturn(Optional.of(cluster));

        MixedOperation<Pod, PodList, PodResource> podsOperation = mock(MixedOperation.class);
        NonNamespaceOperation<Pod, PodList, PodResource> namespacedPods = mock(NonNamespaceOperation.class);
        when(kubernetesClient.pods()).thenReturn(podsOperation);
        when(podsOperation.inNamespace("demo")).thenReturn(namespacedPods);

        PodResource podResource = mock(PodResource.class, Answers.RETURNS_SELF);
        when(namespacedPods.withName("pod-1")).thenReturn(podResource);
        when(podResource.get()).thenReturn(new Pod());

        ExecWatch execWatch = mock(ExecWatch.class);
        when(podResource
                .redirectingInput()
                .writingOutput(any())
                .writingError(any())
                .writingErrorChannel(any())
                .withTTY()
                .usingListener(any())
                .exec("/bin/sh")).thenReturn(execWatch);

        podShellGateway.openShell(
                "demo",
                "pod-1",
                null,
                new ByteArrayOutputStream(),
                new ByteArrayOutputStream(),
                execListener
        );

        verify(podResource).exec("/bin/sh");
    }
}
