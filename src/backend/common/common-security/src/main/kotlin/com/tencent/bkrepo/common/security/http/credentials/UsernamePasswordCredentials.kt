package com.tencent.bkrepo.common.security.http.credentials

/**
 * 用户名密码凭证
 */
data class UsernamePasswordCredentials(val username: String, val password: String) : HttpAuthCredentials
