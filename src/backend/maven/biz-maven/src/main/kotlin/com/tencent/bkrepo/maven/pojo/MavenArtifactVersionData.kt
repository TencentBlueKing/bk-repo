package com.tencent.bkrepo.maven.pojo

data class MavenArtifactVersionData(
    val basic: Basic,
    val metadata: Map<String, Any>
)
