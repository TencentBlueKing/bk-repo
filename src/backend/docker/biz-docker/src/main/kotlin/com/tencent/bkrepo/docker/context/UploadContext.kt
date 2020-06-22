package com.tencent.bkrepo.docker.context

import com.tencent.bkrepo.common.artifact.api.ArtifactFile

data class UploadContext(var projectId: String, var repoName: String, var fullPath: String) {

    var artifactFile: ArtifactFile? = null
    var sha256: String = ""

    fun artifactFile(artifactFile: ArtifactFile): UploadContext {
        this.artifactFile = artifactFile
        return this
    }

    fun sha256(sha256: String): UploadContext {
        this.sha256 = sha256
        return this
    }
}
