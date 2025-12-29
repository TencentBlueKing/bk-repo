package com.tencent.bkrepo.media.job.k8s

import org.springframework.boot.context.properties.ConfigurationProperties


@ConfigurationProperties(prefix = "k8s")
data class K8sProperties(
    var namespace: String = "default",
    var apiServer: String? = null,
    var token: String? = null,
    var limit: ResourceLimitProperties = ResourceLimitProperties(),
)
