package com.tencent.bkrepo.fs.server.utils

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.service.util.LocaleMessageUtils
import com.tencent.bkrepo.fs.server.context.ReactiveRequestContextHolder
import org.springframework.http.HttpHeaders
import java.util.*

object ExceptionUtils {
    suspend fun getMsg(exception: Exception): String? {
        return if (exception is ErrorCodeException) {
            LocaleMessageUtils.getLocalizedMessage(
                messageCode = exception.messageCode,
                params = exception.params,
                locale = getLocale()
            )
        } else {
            exception.message
        }
    }

    private suspend fun getLocale(): Locale {
        val language = ReactiveRequestContextHolder.getRequest().headers.getFirst(HttpHeaders.ACCEPT_LANGUAGE)
        return try {
            Locale.forLanguageTag(language)
        } catch (e: NullPointerException) {
            Locale.getDefault()
        }
    }
}
