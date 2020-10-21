package com.tencent.bkrepo.rpm.pojo

enum class ArtifactRepeat(val value: String) {
    // 无重复构件
    NONE("NONE"),
    // artifact_uri 相同
    FULLPATH("FULLPATH"),
    // artifact_uri && sha256 相同
    FULLPATH_SHA256("FULLPATH_SHA256"),

    DELETE("DELETE")
}
