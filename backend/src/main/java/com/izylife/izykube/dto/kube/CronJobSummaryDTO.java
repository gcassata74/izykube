package com.izylife.izykube.dto.kube;

public record CronJobSummaryDTO(
        String name,
        String namespace,
        String schedule,
        boolean suspended,
        String lastScheduleTime,
        int activeJobs,
        String age
) {
}
