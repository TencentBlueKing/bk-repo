package com.tencent.bkrepo.docker.artifact

/**
 *  docker artifact des class
 * @author: owenlxu
 * @date: 2020-03-12
 */
class DockerArtifact(projectId: String, repoName: String, artifactName: String) {

    var projectId: String = ""
    var repoName: String = ""
    var artifactName: String = ""

    var sha256: String? = null
    var length: Long = 0L
    var fullPath: String = ""

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
