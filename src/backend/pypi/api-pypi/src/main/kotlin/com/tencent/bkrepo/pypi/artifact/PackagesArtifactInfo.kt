package com.tencent.bkrepo.pypi.artifact

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo

/**
 *
 * @author: carrypan
 * @date: 2019/12/4
 */
class PackagesArtifactInfo(
    projectId: String,
    repoName: String,
    artifactUri: String
) : ArtifactInfo(projectId, repoName, artifactUri) {

    fun isDirectory(): Boolean {
        return artifactUri.endsWith("/")
    }

    companion object {
        const val PACKAGES_MAPPING_URI = "/{projectId}/{repoName}/packages/**"
    }
}
