package com.tencent.bkrepo.common.security.http.platform

import com.tencent.bkrepo.common.security.http.credentials.HttpAuthCredentials

/**
 * 平台账号认证信息
 */
data class PlatformAuthCredentials(val accessKey: String, val secretKey: String) : HttpAuthCredentials
