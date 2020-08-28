package com.tencent.bkrepo.common.api.message

/**
 * 消息码
 */
interface MessageCode {
    /**
     * 业务码
     */
    fun getBusinessCode(): Int

    /**
     * 消息i18n key
     */
    fun getKey(): String

    /**
     * 模块码
     */
    fun getModuleCode(): Int

    /**
     * 平台码
     */
    fun getPlatformCode(): Int = PLATFORM_CODE

    /**
     * 消息code
     */
    fun getCode() = getPlatformCode() * 10000 + getModuleCode() * 100 + getBusinessCode()

    companion object {
        const val PLATFORM_CODE: Int = 25
    }
}
