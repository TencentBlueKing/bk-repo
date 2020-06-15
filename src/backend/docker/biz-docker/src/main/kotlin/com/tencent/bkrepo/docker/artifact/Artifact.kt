package com.tencent.bkrepo.docker.artifact

class Artifact(projectId: String, repoName: String, name: String) {

    var projectId: String = ""
    var repoName: String = ""
    var name: String = ""

    var sha256: String? = null
    var length: Long = 0L
    var path: String = ""

    init {
        this.projectId = projectId
        this.repoName = repoName
        this.name = name
    }

    fun sha256(fileSha256: String): Artifact {
        this.sha256 = fileSha256
        return this
    }

    fun length(length: Long): Artifact {
        this.length = length
        return this
    }

    fun name(name: String): Artifact {
        this.name = name
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
