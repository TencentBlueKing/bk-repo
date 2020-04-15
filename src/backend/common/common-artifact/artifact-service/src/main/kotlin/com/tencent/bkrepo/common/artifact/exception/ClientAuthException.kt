package com.tencent.bkrepo.common.artifact.exception

import com.tencent.bkrepo.common.artifact.config.AUTHORIZATION_PROMPT
import org.springframework.http.HttpStatus

/**
 *
 * @author: carrypan
 * @date: 2019/11/25
 */
class ClientAuthException(message: String = AUTHORIZATION_PROMPT) : ArtifactException(message, HttpStatus.UNAUTHORIZED.value())
