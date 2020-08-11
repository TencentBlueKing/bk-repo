package com.tencent.bkrepo.common.security.http.jwt

import com.tencent.bkrepo.common.security.http.credentials.HttpAuthCredentials

/**
 * Json web token 认证信息
 */
data class JwtAuthCredentials(val token: String) : HttpAuthCredentials
