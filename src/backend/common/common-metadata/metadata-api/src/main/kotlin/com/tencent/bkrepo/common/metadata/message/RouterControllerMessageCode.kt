package com.tencent.bkrepo.common.metadata.message

import com.tencent.bkrepo.common.api.message.MessageCode

enum class RouterControllerMessageCode(private val key: String) : MessageCode {
    ROUTER_POLICY_NOT_FOUND("router.policy.not.found"),
    ROUTER_NODE_NOT_FOUND("router.node.not.found"),

    ;

    override fun getBusinessCode() = ordinal + 1
    override fun getKey() = key
    override fun getModuleCode() = 5
}