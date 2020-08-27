package com.tencent.bkrepo.common.security.http

import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.constant.PLATFORM_KEY
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.service.util.HttpContextHolder

object SecurityUtils {

    fun getUserId(): String {
        return HttpContextHolder.getRequestOrNull()?.getAttribute(USER_KEY) as? String ?: ANONYMOUS_USER
    }

    fun getPlatformId(): String? {
        return HttpContextHolder.getRequestOrNull()?.getAttribute(PLATFORM_KEY) as? String
    }

    fun getPrincipal(): String {
        return getPlatformId()?.let { "$it-${getUserId()}" } ?: getUserId()
    }
}