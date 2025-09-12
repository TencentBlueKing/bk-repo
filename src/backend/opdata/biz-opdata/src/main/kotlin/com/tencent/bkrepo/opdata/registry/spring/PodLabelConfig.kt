package com.tencent.bkrepo.opdata.registry.spring

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * 区分环境用的label
 */
@Component
@ConfigurationProperties("release.name")
class PodLabelConfig (
    val labelName: String ="app.kubernetes.io/instance",
    val labelValue: String = ""
)