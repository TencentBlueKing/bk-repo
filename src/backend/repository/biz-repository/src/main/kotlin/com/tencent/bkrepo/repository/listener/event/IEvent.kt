package com.tencent.bkrepo.repository.listener.event

import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.repository.pojo.log.OperateType
import com.tencent.bkrepo.repository.pojo.log.ResourceType

interface IEvent {
    fun getOperateType(): OperateType
    fun getResourceType(): ResourceType
    fun getResourceKey(): String
    fun getUserId(): String
    fun getDescription(): String
    fun getClientAddress() = HttpContextHolder.getClientAddress()
}