package com.tencent.bkrepo.docker.context

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import java.io.InputStream

class UploadContext(projectId: String, repoName: String, path: String) {

    // full path
    var path: String = ""
    var content: InputStream? = null
    var sha256: String = ""
    var projectId: String = ""
    var repoName: String = ""
    var artifactFile: ArtifactFile? = null

    init {
        this.projectId = projectId
        this.repoName = repoName
        this.path = path
    }

    fun path(path: String): UploadContext {
        this.path = path
        return this
    }

    fun artifactFile(artifactFile: ArtifactFile): UploadContext {
        this.artifactFile = artifactFile
        return this
    }

    fun content(content: InputStream): UploadContext {
        this.content = content
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
