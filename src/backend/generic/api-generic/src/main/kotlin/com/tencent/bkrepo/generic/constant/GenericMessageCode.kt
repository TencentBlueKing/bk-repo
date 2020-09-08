package com.tencent.bkrepo.generic.constant

import com.tencent.bkrepo.common.api.message.MessageCode

/**
 * 通用文件错误码
 */
enum class GenericMessageCode(private val businessCode: Int, private val key: String) : MessageCode {
    UPLOAD_ID_NOT_FOUND(1, "generic.uploadId.notfound");

    override fun getBusinessCode() = businessCode
    override fun getKey() = key
    override fun getModuleCode() = 12
}
