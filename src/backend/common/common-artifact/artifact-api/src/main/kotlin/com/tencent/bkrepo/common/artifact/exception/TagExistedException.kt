package com.tencent.bkrepo.common.artifact.exception

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode

class TagExistedException(
    val tag: String
) : ErrorCodeException(ArtifactMessageCode.TAG_EXISTED, tag)