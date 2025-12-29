package com.tencent.bkrepo.media.job.pojo

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo


class MediaArtifactInfo(
    projectId: String,
    repoName: String,
    artifactUri: String,
) : ArtifactInfo(projectId, repoName, artifactUri)
