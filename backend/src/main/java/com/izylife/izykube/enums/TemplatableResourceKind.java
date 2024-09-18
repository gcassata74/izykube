package com.izylife.izykube.enums;

public enum TemplatableResourceKind {

    DEPLOYMENT("deployment"),
    SERVICE("service"),
    CONFIG_MAP("configmap"),
    SECRET("secret"),
    INGRESS("ingress"),
    PERSISTENT_VOLUME_CLAIM("persistentvolumeclaim"),
    STATEFUL_SET("statefulset"),
    DAEMON_SET("daemonset"),
    JOB("job"),
    CRON_JOB("cronjob");

    private final String kind;

    TemplatableResourceKind(String kind) {
        this.kind = kind;
    }

    public String getKind() {
        return kind;
    }

}
