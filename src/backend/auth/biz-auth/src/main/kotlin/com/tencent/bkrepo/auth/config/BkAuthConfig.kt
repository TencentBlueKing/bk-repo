package com.tencent.bkrepo.auth.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class BkAuthConfig {

    /**
     * bk auth 服务器地址
     */
    @Value("\${auth.bkAuthServer:}")
    var bkAuthServer: String = ""

    /**
     * bk app code
     */
    @Value("\${auth.bkAppCode:}")
    var bkAppCode: String = ""

    /**
     * bk app secret
     */
    @Value("\${auth.bkAppSecret:}")
    var bkAppSecret: String = ""

    /**
     *  开启蓝鲸用户校验
     */
    @Value("\${auth.enableBkUser: false}")
    var enableBkUser: Boolean = false
}