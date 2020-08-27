package com.tencent.bkrepo.common.artifact.exception

import com.tencent.bkrepo.common.api.constant.HttpStatus

/**
 * 操作不支持
 */
class UnsupportedMethodException(override val message: String? = null) : ArtifactException(HttpStatus.METHOD_NOT_ALLOWED, message)
