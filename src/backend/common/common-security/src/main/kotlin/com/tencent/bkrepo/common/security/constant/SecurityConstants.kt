package com.tencent.bkrepo.common.security.constant

/**
 * 认证相关
 */
const val AUTHORIZATION = "Authorization"
const val AUTHORIZATION_PROMPT = "Authentication Required"
const val BAD_CREDENTIALS_PROMPT = "Bad Credentials"
const val PROXY_AUTHORIZATION = "Proxy-Authorization"
const val BASIC_AUTH_HEADER_PREFIX = "Basic "
const val BASIC_AUTH_RESPONSE_HEADER = "WWW-Authenticate"
const val BASIC_AUTH_RESPONSE_VALUE = "Basic realm=\"$AUTHORIZATION_PROMPT\""
const val PLATFORM_AUTH_HEADER_PREFIX = "Platform "
const val BEARER_AUTH_HEADER_PREFIX = "Bearer "

/**
 * 权限相关
 */
const val ACCESS_DENIED_PROMPT = "Access Denied"
const val PERMISSION_PROMPT = "Forbidden"
