package com.tencent.bkrepo.docker.artifact

class Artifact(projectId: String, repoName: String, artifactName: String) {

    var projectId: String = ""
    var repoName: String = ""
    var artifactName: String = ""

    var sha256: String? = null
    var length: Long = 0L
    var path: String = ""

    init {
        this.projectId = projectId
        this.repoName = repoName
        this.artifactName = artifactName
    }

    fun sha256(fileSha256: String): Artifact {
        this.sha256 = fileSha256
        return this
    }

    fun length(length: Long): Artifact {
        this.length = length
        return this
    }

    fun artifactName(name: String): Artifact {
        this.artifactName = name
        return this
    }

    fun projectId(projectId: String): Artifact {
        this.projectId = projectId
        return this
    }

    fun repoName(repoName: String): Artifact {
        this.repoName = repoName
        return this
    }

    fun path(path: String): Artifact {
        this.path = path
        return this
    }
}
