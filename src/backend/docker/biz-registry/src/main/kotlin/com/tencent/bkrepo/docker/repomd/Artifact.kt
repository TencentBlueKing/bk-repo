package com.tencent.bkrepo.docker.repomd

class Artifact(projectId: String, repoName: String, name: String) {

    var sha256: String? = null
    var contentLength: Long = 0
    var name: String = ""
    var path: String = ""
    var projectId: String = ""
    var repoName: String = ""

    init {
        this.projectId = projectId
        this.repoName = repoName
        this.name = name
    }

    fun sha256(fielSha256: String): Artifact {
        this.sha256 = fielSha256
        return this
    }

    fun contentLength(contentLength: Long): Artifact {
        this.contentLength = contentLength
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

//     fun getSha256(): String? {
//         return this.fielSha256
//     }

    fun getLength(): Long {
        return this.contentLength
    }

    fun getArtifactPath(): String {
        return this.path
    }

    fun getRepoId(): String {
        return ""
    }
}
