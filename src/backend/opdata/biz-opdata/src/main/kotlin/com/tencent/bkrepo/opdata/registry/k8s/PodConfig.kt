package com.tencent.bkrepo.opdata.registry.k8s

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * 区分环境用的label
 */
@Component
@ConfigurationProperties("release.name")
class PodConfig (
    var labelName: String ="app.kubernetes.io/instance",
    var labelValue: String = "",
    var nameSpace: String = ""
)