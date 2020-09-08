package com.tencent.bkrepo.docker.artifact

import com.tencent.bkrepo.common.api.constant.StringPool.EMPTY

/**
 * docker artifact describe class
 */
class DockerArtifact(projectId: String, repoName: String, artifactName: String) {

    var projectId: String = EMPTY
    var repoName: String = EMPTY
    var artifactName: String = EMPTY

    var sha256: String? = null
    var length: Long = 0L
    var fullPath: String = EMPTY

    init {
        this.projectId = projectId
        this.repoName = repoName
        this.artifactName = artifactName
    }

    fun sha256(fileSha256: String): DockerArtifact {
        this.sha256 = fileSha256
        return this
    }

    fun length(length: Long): DockerArtifact {
        this.length = length
        return this
    }

    fun fullPath(fullPath: String): DockerArtifact {
        this.fullPath = fullPath
        return this
    }
}
