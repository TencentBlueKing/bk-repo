package com.tencent.bkrepo.media.artifact

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo

/**
 * COS 加密归档制品路径。
 */
class CosArchiveArtifactInfo(
    projectId: String,
    repoName: String,
    artifactUri: String,
) : ArtifactInfo(projectId, repoName, artifactUri) {

    companion object {
        const val COS_ARCHIVE_MAPPING_URI = "/{projectId}/{repoName}/**"
    }
}
