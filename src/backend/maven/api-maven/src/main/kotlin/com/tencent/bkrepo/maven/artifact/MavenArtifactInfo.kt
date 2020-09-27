package com.tencent.bkrepo.maven.artifact

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo

class MavenArtifactInfo(
    projectId: String,
    repoName: String,
    artifactUri: String
) : ArtifactInfo(projectId, repoName, artifactUri) {

    var groupId: String? = null
    var artifactId: String? = null
    var versionId: String? = null

    companion object {
        const val MAVEN_MAPPING_URI = "/{projectId}/{repoName}/**"
        const val MAVEN_EXT_DETAIL = "/version/detail/{projectId}/{repoName}"
        const val MAVEN_EXT_PACKAGE_DELETE = "/package/delete/{projectId}/{repoName}"
        const val MAVEN_EXT_VERSION_DELETE = "/version/delete/{projectId}/{repoName}"
    }

    private fun hasGroupId(): Boolean {
        return groupId!!.isNotBlank() && "NA" != groupId
    }

    private fun hasArtifactId(): Boolean {
        return artifactId!!.isNotBlank() && "NA" != artifactId
    }

    private fun hasVersion(): Boolean {
        return versionId!!.isNotBlank() && "NA" != versionId
    }

    fun isValid(): Boolean {
        return hasGroupId() && hasArtifactId() && hasVersion()
    }
}
