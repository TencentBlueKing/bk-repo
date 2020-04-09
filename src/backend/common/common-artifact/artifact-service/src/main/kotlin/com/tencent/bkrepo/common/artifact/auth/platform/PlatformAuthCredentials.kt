package com.tencent.bkrepo.common.artifact.auth.platform

import com.tencent.bkrepo.common.artifact.auth.core.AuthCredentials

/**
 * 平台账号
 */
data class PlatformAuthCredentials(val accessKey: String, val secretKey: String) : AuthCredentials()
