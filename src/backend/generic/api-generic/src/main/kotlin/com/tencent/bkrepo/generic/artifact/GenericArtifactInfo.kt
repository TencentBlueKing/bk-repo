package com.tencent.bkrepo.generic.artifact

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo

class GenericArtifactInfo(
    projectId: String,
    repoName: String,
    artifactUri: String
) : ArtifactInfo(projectId, repoName, artifactUri) {
    companion object {
        const val GENERIC_MAPPING_URI = "/{projectId}/{repoName}/**"
        const val BLOCK_MAPPING_URI = "/block/{projectId}/{repoName}/**"
    }
}
