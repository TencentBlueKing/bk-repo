package com.tencent.bkrepo.common.artifact.webhook

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.event.ArtifactEventType

data class ArtifactWebHookData(
    val projectId: String,
    val repoName: String,
    val artifact: String,
    val version: String?,
    val eventType: ArtifactEventType
) {
    constructor(artifactInfo: ArtifactInfo, eventType: ArtifactEventType) : this(
        artifactInfo.projectId,
        artifactInfo.repoName,
        artifactInfo.getArtifactName(),
        artifactInfo.getArtifactVersion(),
        eventType
    )
}
