package com.tencent.bkrepo.rpm.pojo

enum class ArtifactRepeat {
    // 无重复构件
    NONE,
    // artifact_uri 相同
    FULLPATH,
    // artifact_uri && sha256 相同
    FULLPATH_SHA256,

    DELETE
}
