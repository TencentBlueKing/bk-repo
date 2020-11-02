package com.tencent.bkrepo.pypi.pojo

data class PypiArtifactVersionData(
    val basic: Basic,
    val metadata: Map<String, Any>
)
