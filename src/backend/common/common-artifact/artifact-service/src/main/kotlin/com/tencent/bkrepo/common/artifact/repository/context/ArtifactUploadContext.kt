package com.tencent.bkrepo.common.artifact.repository.context

import com.tencent.bkrepo.common.artifact.api.ArtifactFile

/**
 * 构件上传context
 * @author: carrypan
 * @date: 2019/11/26
 */
class ArtifactUploadContext(val artifactFile: ArtifactFile) : ArtifactTransferContext()
