package com.tencent.bkrepo.common.security.constant

/**
 * 认证相关
 */
const val AUTHORIZATION_PROMPT = "Authentication Required"
const val BAD_CREDENTIALS_PROMPT = "Bad Credentials"
const val BASIC_AUTH_PREFIX = "Basic "
const val BASIC_AUTH_PROMPT = "Basic realm=\"$AUTHORIZATION_PROMPT\""
const val PLATFORM_AUTH_PREFIX = "Platform "
const val BEARER_AUTH_PREFIX = "Bearer "
const val AUTH_HEADER_UID = "X-BKREPO-UID"

/**
 * 权限相关
 */
const val ACCESS_DENIED_PROMPT = "Access Denied"
const val PERMISSION_PROMPT = "Forbidden"

/**
 * micro service header user id key
 */
const val MS_AUTH_HEADER_UID = "X-BKREPO-MS-UID"

/**
 * micro service header security token
 */
const val MS_AUTH_HEADER_SECURITY_TOKEN = "X-BKREPO-SECURITY-TOKEN"

const val ANY_URI_PATTERN = "/**"