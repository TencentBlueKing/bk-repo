package com.tencent.bkrepo.common.artifact.event

import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext

data class ArtifactUpdatedEvent(
    override val context: ArtifactUploadContext
) : ArtifactEvent(context, ArtifactEventType.UPLOADED)
