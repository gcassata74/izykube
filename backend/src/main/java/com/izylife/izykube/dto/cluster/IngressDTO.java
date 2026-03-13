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

package com.izylife.izykube.dto.cluster;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class IngressDTO extends NodeDTO {
    private String host;
    private String path;
    private String serviceName;
    private int servicePort;
    private String tls;
    private Map<String, String> annotations = new LinkedHashMap<>();

    public IngressDTO(String id, String name, String host, String path, String serviceName, int servicePort) {
        this(id, name, host, path, serviceName, servicePort, null, new LinkedHashMap<>());
    }

    public IngressDTO(String id,
                      String name,
                      String host,
                      String path,
                      String serviceName,
                      int servicePort,
                      String tls,
                      Map<String, String> annotations) {
        super(id, name, "ingress");
        this.host = host;
        this.path = path;
        this.serviceName = serviceName;
        this.servicePort = servicePort;
        this.tls = tls;
        this.annotations = annotations != null ? new LinkedHashMap<>(annotations) : new LinkedHashMap<>();
    }
}
