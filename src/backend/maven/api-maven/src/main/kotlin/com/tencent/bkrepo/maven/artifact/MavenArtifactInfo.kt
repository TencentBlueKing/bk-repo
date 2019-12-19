package com.tencent.bkrepo.maven.artifact

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo

/**
 *
 * @author: carrypan
 * @date: 2019/12/2
 */
class MavenArtifactInfo(
    projectId: String,
    repoName: String,
    artifactUri: String,
    private val groupId: String,
    private val artifactId: String,
    private val version: String
) : ArtifactInfo(projectId, repoName, artifactUri) {
    companion object {
        const val MAVEN_MAPPING_URI = "/{projectId}/{repoName}/**"
    }

    private fun hasGroupId(): Boolean {
        return groupId.isNotBlank() && "NA" != groupId
    }

    private fun hasArtifactId(): Boolean {
        return artifactId.isNotBlank() && "NA" != artifactId
    }

    private fun hasVersion(): Boolean {
        return version.isNotBlank() && "NA" != version
    }

    fun isValid(): Boolean {
        return hasGroupId() && hasArtifactId() && hasVersion()
    }
}
