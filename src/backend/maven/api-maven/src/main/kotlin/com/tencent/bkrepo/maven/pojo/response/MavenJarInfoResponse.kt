package com.tencent.bkrepo.maven.pojo.response

data class MavenJarInfoResponse(
    val jarMap: Map<String, List<JarInfo>>
) {
    data class JarInfo(
        val projectId: String,
        val repoName: String,
        var fullPath: String,
        val groupId: String,
        val artifactId: String,
        val version: String,
        var createdDate: String,
        var lastModifiedDate: String,
        var sha256: String? = null,
        var md5: String? = null,
    )
}