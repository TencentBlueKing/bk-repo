package com.tencent.bkrepo.docker.context

import com.tencent.bkrepo.common.artifact.api.ArtifactFile

class UploadContext(projectId: String, repoName: String, fullPath: String) {

    // full path
    var projectId: String = ""
    var repoName: String = ""
    var fullPath: String = ""

    var artifactFile: ArtifactFile? = null
    var sha256: String = ""

    init {
        this.projectId = projectId
        this.repoName = repoName
        this.fullPath = fullPath
    }

    fun path(path: String): UploadContext {
        this.fullPath = path
        return this
    }

    fun artifactFile(artifactFile: ArtifactFile): UploadContext {
        this.artifactFile = artifactFile
        return this
    }

    fun sha256(sha256: String): UploadContext {
        this.sha256 = sha256
        return this
    }

    fun projectId(projectId: String): UploadContext {
        this.projectId = projectId
        return this
    }

    fun repoName(repoName: String): UploadContext {
        this.repoName = repoName
        return this
    }
}
