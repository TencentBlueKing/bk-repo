package com.tencent.bkrepo.migrate.pojo.suyan

import com.tencent.bkrepo.migrate.exception.DockerTagInvalidException
import com.tencent.bkrepo.migrate.pojo.BkProduct
import com.tencent.bkrepo.migrate.pojo.DockerImage
import com.tencent.bkrepo.migrate.pojo.MavenArtifact

data class SuyanSyncRequest(
    val repositoryName: String,
    val groupId: String,
    val artifactId: String,
    val version: String,
    val packaging: String,
    val name: String?,
    val artifactList: MutableList<MavenArtifact>,
    val docker: List<SuyanImage>? = listOf(),
    val productList: List<BkProduct>? = mutableListOf()
) {
    fun info(): String {
        return "info: $repositoryName/$groupId/$artifactId/$version/$packaging/$name, artifacts: ${artifactList.size}"
    }
}

data class SuyanImage(
    val tag: String
) {
    fun transfer(): DockerImage {
        try {
            val bakTag = tag.removePrefix("http://").removePrefix("https://")
            val paths = tag.split("/")
            val host = paths.first()
            val project = paths[1]
            val tags = paths.last().split(":")
            require(tags.size == 2)
            val tag = tags.last()
            val name = bakTag.replace("$host/$project/", "")
                .replace(":$tag", "")
            return DockerImage(
                project = project,
                name = name,
                tag = tag
            )
        } catch (e: Exception) {
            throw DockerTagInvalidException(
                "Error parsing reference: \"$tag\" is not a valid repository/tag: invalid reference format"
            )
        }
    }
}
