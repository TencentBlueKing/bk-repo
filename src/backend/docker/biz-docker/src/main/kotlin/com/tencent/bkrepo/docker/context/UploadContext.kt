package com.tencent.bkrepo.docker.context

import com.tencent.bkrepo.common.artifact.api.ArtifactFile

/**
 * docker registry upload context
 * @author: owenlxu
 * @date: 2019-12-01
 */
data class UploadContext(var projectId: String, var repoName: String, var fullPath: String) {

    var artifactFile: ArtifactFile? = null
    var sha256: String = ""
    var metadata: Map<String, String> = emptyMap()

    fun artifactFile(artifactFile: ArtifactFile): UploadContext {
        this.artifactFile = artifactFile
        return this
    }

    fun sha256(sha256: String): UploadContext {
        this.sha256 = sha256
        return this
    }

    fun metadata(metadata: Map<String, String>): UploadContext {
        this.metadata = metadata
        return this
    }
}
