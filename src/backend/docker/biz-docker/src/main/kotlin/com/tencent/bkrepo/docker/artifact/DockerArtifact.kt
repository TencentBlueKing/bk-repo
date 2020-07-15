package com.tencent.bkrepo.docker.artifact

import com.tencent.bkrepo.docker.constant.EMPTYSTR

/**
 * docker artifact describe class
 * @author: owenlxu
 * @date: 2020-03-12
 */
class DockerArtifact(projectId: String, repoName: String, artifactName: String) {

    var projectId: String = EMPTYSTR
    var repoName: String = EMPTYSTR
    var artifactName: String = EMPTYSTR

    var sha256: String? = null
    var length: Long = 0L
    var fullPath: String = EMPTYSTR

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
