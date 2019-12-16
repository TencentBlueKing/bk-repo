package com.tencent.bkrepo.common.service.util

import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.message.MessageCode
import java.util.Locale
import org.slf4j.LoggerFactory
import org.springframework.context.MessageSource
import org.springframework.context.NoSuchMessageException
import org.springframework.context.i18n.LocaleContextHolder

object LocaleMessageUtils {
    private val logger = LoggerFactory.getLogger(LocaleMessageUtils::class.java)
    private val DEFAULT_MESSAGE_CODE = CommonMessageCode.SYSTEM_ERROR
    /**
     * 获取本地化消息
     * @param messageCode messageCode
     * @param params 替换描述信息占位符的参数数组
     */
    fun getLocalizedMessage(messageCode: MessageCode, params: Array<out String>?): String {
        val messageSource = SpringContextUtils.getBean(MessageSource::class.java)
        return try {
            messageSource.getMessage(messageCode.getKey(), params, getLocale())
        } catch (exception: NoSuchMessageException) {
            logger.warn("Can not find [${messageCode.getKey()}] localized message, use default message.")
            messageSource.getMessage(DEFAULT_MESSAGE_CODE.getKey(), null, getLocale())
        }
    }

    private fun getLocale(): Locale {
        return LocaleContextHolder.getLocale()
    }
}
