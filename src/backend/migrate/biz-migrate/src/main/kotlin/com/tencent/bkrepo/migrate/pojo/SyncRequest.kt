package com.tencent.bkrepo.migrate.pojo

data class SyncRequest(
    val maven: MavenSyncInfo?,
    val docker: List<DockerImage>?,
    val productList: List<BkProduct>? = mutableListOf()
) {
    fun info(): String {
        return "Request info: ${maven?.info()}, ${docker?.size}"
    }

    fun isNull(): Boolean {
        return (maven == null) && (docker == null)
    }

    fun getThreadNum(): Int {
        var threadNum = 0
        if (maven != null) ++threadNum
        if (docker != null) ++threadNum
        return threadNum
    }
}

data class MavenSyncInfo(
    val repositoryName: String,
    val groupId: String,
    val artifactId: String,
    val version: String,
    val packaging: String,
    val name: String?,
    val artifactList: MutableList<MavenArtifact>
) {
    fun info(): String {
        return "info: $repositoryName/$groupId/$artifactId/$version/$packaging/$name, artifacts: ${artifactList.size}"
    }
}

data class DockerSyncInfo(
    val imageList: List<DockerImage>
)

data class MavenArtifact(
    val groupId: String,
    val artifactId: String,
    val type: String,
    val version: String?
)

data class DockerImage(
    val project: String,
    val name: String,
    val tag: String
)
