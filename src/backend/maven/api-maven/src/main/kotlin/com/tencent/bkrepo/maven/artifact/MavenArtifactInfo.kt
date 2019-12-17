package com.tencent.bkrepo.maven.artifact

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import org.apache.commons.lang.StringUtils

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

    private fun hasGroupId(): Boolean{
        return StringUtils.isNotBlank(groupId) && "NA" != groupId
    }

    private fun hasArtifactId(): Boolean{
        return StringUtils.isNotBlank(artifactId) && "NA" != artifactId
    }

    private fun hasVersion(): Boolean{
        return StringUtils.isNotBlank(version) && "NA" != version
    }

    fun isValid(): Boolean {
        return hasGroupId() && hasArtifactId() && hasVersion()
    }
}
