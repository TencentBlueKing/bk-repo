package com.tencent.bkrepo.common.security.http

/**
 * HttpAuthSecurity 配置器
 */
interface HttpAuthSecurityCustomizer {
    fun customize(httpAuthSecurity: HttpAuthSecurity) { }
}
