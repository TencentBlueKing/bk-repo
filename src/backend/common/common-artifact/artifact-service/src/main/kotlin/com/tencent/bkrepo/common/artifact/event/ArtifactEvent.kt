package com.tencent.bkrepo.common.artifact.event

import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContext

abstract class ArtifactEvent(
    open val context: ArtifactContext,
    open val type: ArtifactEventType
)
