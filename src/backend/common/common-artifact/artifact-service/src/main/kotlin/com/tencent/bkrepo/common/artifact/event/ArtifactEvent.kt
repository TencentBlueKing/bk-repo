package com.tencent.bkrepo.common.artifact.event

import com.tencent.bkrepo.common.artifact.repository.context.ArtifactTransferContext

abstract class ArtifactEvent(
    open val context: ArtifactTransferContext,
    open val type: ArtifactEventType
)
