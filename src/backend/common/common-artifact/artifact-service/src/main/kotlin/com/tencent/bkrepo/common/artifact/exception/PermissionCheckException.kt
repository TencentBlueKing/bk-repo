package com.tencent.bkrepo.common.artifact.exception

import com.tencent.bkrepo.common.artifact.config.PERMISSION_PROMPT
import org.springframework.http.HttpStatus

/**
 *
 * @author: carrypan
 * @date: 2019/11/25
 */
class PermissionCheckException(message: String = PERMISSION_PROMPT) : ArtifactException(message, HttpStatus.FORBIDDEN.value())
