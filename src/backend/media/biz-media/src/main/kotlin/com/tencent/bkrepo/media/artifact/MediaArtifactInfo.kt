package com.tencent.bkrepo.media.artifact

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.media.STREAM_PATH

class MediaArtifactInfo(
    projectId: String,
    repoName: String,
    artifactUri: String,
) : ArtifactInfo(projectId, repoName, artifactUri) {

    companion object {
        const val DEFAULT_STREAM_MAPPING_URI = "/{projectId}/{repoName}$STREAM_PATH/**"
    }
}
