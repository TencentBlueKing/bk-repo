package com.tencent.bkrepo.opdata.message

import com.tencent.bkrepo.common.api.message.MessageCode

enum class AuthMessageCode(private val businessCode: Int, private val key: String) : MessageCode {

    AUTH_DUP_UID(1, "auth.dup.uid");

    override fun getBusinessCode() = businessCode
    override fun getKey() = key
    override fun getModuleCode() = 20
}
