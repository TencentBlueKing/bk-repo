package com.tencent.bkrepo.repository.listener.event

import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.repository.pojo.log.OperateType
import com.tencent.bkrepo.repository.pojo.log.ResourceType

abstract class IEvent(val userId: String) {
    // 因为事件异步处理，所以使用成员变量方式提前初始化客户端地址，否则获取不到
    val clientAddress = HttpContextHolder.getClientAddress()
    abstract fun getOperateType(): OperateType
    abstract fun getResourceType(): ResourceType
    abstract fun getResourceKey(): String
}
