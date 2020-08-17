package com.tencent.bkrepo.common.artifact.repository.context

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactFileMap
import com.tencent.bkrepo.common.artifact.constant.OCTET_STREAM

/**
 * 构件上传context
 */
class ArtifactUploadContext : ArtifactTransferContext {

    val artifactFileMap: ArtifactFileMap

    constructor(artifactFile: ArtifactFile) {
        val artifactFileMap = ArtifactFileMap()
        artifactFileMap[OCTET_STREAM] = artifactFile
        this.artifactFileMap = artifactFileMap
    }

    constructor(artifactFileMap: ArtifactFileMap) {
        this.artifactFileMap = artifactFileMap
    }

    /**
     * 默认获取二进制流文件
     */
    fun getArtifactFile(): ArtifactFile {
        return artifactFileMap[OCTET_STREAM]!!
    }

    /**
     * 根据field name获取multipart file
     */
    fun getArtifactFile(name: String): ArtifactFile? {
        return artifactFileMap[name]
    }
}
