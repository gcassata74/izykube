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

package com.izylife.izykube.enums;

public enum TemplatableNodes {

    DEPLOYMENT("deployment"),
    SERVICE("service"),
    CONFIG_MAP("configmap"),
    SECRET("secret"),
    INGRESS("ingress"),
    ISTIO("istio"),
    PERSISTENT_VOLUME_CLAIM("persistentvolumeclaim"),
    STATEFUL_SET("statefulset"),
    DAEMON_SET("daemonset"),
    JOB("job"),
    CRON_JOB("cronjob"),
    SERVICE_ACCOUNT("serviceaccount"),
    CUSTOM_RESOURCE("cr");

    private final String kind;

    TemplatableNodes(String kind) {
        this.kind = kind;
    }

    public String getKind() {
        return kind;
    }

}
