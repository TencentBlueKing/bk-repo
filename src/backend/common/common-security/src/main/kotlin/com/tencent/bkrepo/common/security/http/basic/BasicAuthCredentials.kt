package com.tencent.bkrepo.common.security.http.basic

import com.tencent.bkrepo.common.security.http.credentials.HttpAuthCredentials

/**
 * Http Basic认证信息
 */
data class BasicAuthCredentials(val username: String, val password: String) :
    HttpAuthCredentials
