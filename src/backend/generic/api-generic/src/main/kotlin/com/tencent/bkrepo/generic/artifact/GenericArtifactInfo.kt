package com.tencent.bkrepo.generic.artifact

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo

/**
 *
 * @author: carrypan
 * @date: 2019/12/2
 */
class GenericArtifactInfo(
    projectId: String,
    repoName: String,
    artifactUri: String,
    val fullPath: String
): ArtifactInfo(projectId, repoName, artifactUri) {
    companion object {
        const val GENERIC_MAPPING_URI = "/{projectId}/{repoName}/**"
        const val BLOCK_MAPPING_URI = "/block/{projectId}/{repoName}/**"
    }
}
