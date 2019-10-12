package com.tencent.bkrepo.common.service.util

import com.tencent.bkrepo.common.api.constant.BK_LANGUAGE
import com.tencent.bkrepo.common.api.constant.DEFAULT_LANGUAGE
import com.tencent.bkrepo.common.api.constant.PROJECT_CODE_PREFIX
import com.tencent.bkrepo.common.api.pojo.MessageCodeDetail
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.redis.RedisOperation
import java.text.MessageFormat
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/**
 * code信息工具类
 * @since: 2018-11-10
 *
 */
@Component
class MessageCodeUtils @Autowired constructor() {
    companion object {
        private val logger = LoggerFactory.getLogger(MessageCodeUtils::class.java)
        private val simpleCnLanList = listOf("ZH_CN", "ZH-CN")
        private val twCnLanList = listOf("ZH_TW", "ZH-TW", "ZH_HK", "ZH-HK")
        /**
         * 生成请求响应对象
         * @param messageCode 状态码
         */
        fun <T> generateResponseDataObject(
            messageCode: Int
        ): Response<T> {
            return generateResponseDataObject(messageCode, null, null, null)
        }

        /**
         * 生成请求响应对象
         * @param messageCode 状态码
         */
        fun <T> generateResponseDataObject(
            messageCode: Int,
            defaultMessage: String?
        ): Response<T> {
            return generateResponseDataObject(messageCode, defaultMessage, null, null)
        }

        /**
         * 生成请求响应对象
         * @param messageCode 状态码
         * @param data 数据对象
         */
        fun <T> generateResponseDataObject(
            messageCode: Int,
            defaultMessage: String?,
            data: T?
        ): Response<T> {
            return generateResponseDataObject(messageCode, defaultMessage, null, data)
        }

        /**
         * 生成请求响应对象
         * @param messageCode 状态码
         * @param params 替换状态码描述信息占位符的参数数组
         */
        fun <T> generateResponseDataObject(
            messageCode: Int,
            defaultMessage: String?,
            params: Array<out String>
        ): Response<T> {
            return generateResponseDataObject(messageCode, defaultMessage, params, null)
        }

        /**
         * 生成请求响应对象
         * @param messageCode 状态码
         * @param params 替换状态码描述信息占位符的参数数组
         * @param data 数据对象
         */
        @Suppress("UNCHECKED_CAST")
        fun <T> generateResponseDataObject(
            messageCode: Int,
            defaultMessage: String?,
            params: Array<out String>?,
            data: T?
        ): Response<T> {
            val message = getCodeMessage(messageCode, defaultMessage, params)
                    ?: "System service busy, please try again later"
            return Response(messageCode, message, data) // 生成Result对象
        }

        /**
         * 获取code对应的中英文信息
         * @param messageCode code
         */
        fun getCodeLanMessage(messageCode: Int): String {
            return getCodeMessage(messageCode, null, null)
                    ?: messageCode.toString()
        }

        /**
         * 获取code对应的中英文信息
         * @param messageCode code
         * @param params 替换描述信息占位符的参数数组
         */
        private fun getCodeMessage(messageCode: Int, defaultMessage: String?, params: Array<out String>?): String? {
            var message: String? = null
            try {
                val redisOperation: RedisOperation = SpringContextUtils.getBean(RedisOperation::class.java)
                // 根据code从redis中获取该状态码对应的信息信息(PROJECT_CODE_PREFIX前缀保证code码在redis中的唯一性)
                val messageCodeDetailStr = redisOperation.get(PROJECT_CODE_PREFIX + messageCode)
                    ?: defaultMessage ?: return message
                val attributes = RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes
                val locale = if (null != attributes) {
                    val request = attributes.request
                    val cookieLan = CookieUtils.getCookieValue(request, BK_LANGUAGE)
                    cookieLan ?: LocaleContextHolder.getLocale().toString() // 获取字符集（与http请求头中的Accept-Language有关）
                } else {
                    DEFAULT_LANGUAGE // 取不到语言信息默认为中文
                }
                val messageCodeDetail =
                    JsonUtils.getObjectMapper().readValue(messageCodeDetailStr, MessageCodeDetail::class.java)
                message = getMessageByLocale(messageCodeDetail, locale) // 根据字符集取出对应的状态码描述信息
                if (null != params) {
                    val mf = MessageFormat(message)
                    message = mf.format(params) // 根据参数动态替换状态码描述里的占位符
                }
            } catch (ignored: Exception) {
                logger.error("$messageCode get message error is :$ignored", ignored)
            }
            return message
        }

        private fun getMessageByLocale(messageCodeDetail: MessageCodeDetail, locale: String): String {
            return when {
                simpleCnLanList.contains(locale.toUpperCase()) -> messageCodeDetail.messageDetailZhCn // 简体中文描述
                twCnLanList.contains(locale.toUpperCase()) -> messageCodeDetail.messageDetailZhTw ?: "" // 繁体中文描述
                else -> messageCodeDetail.messageDetailEn ?: "" // 英文描述
            }
        }
    }
}
