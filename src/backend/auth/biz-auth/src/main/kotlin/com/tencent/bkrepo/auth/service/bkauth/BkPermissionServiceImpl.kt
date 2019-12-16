package com.tencent.bkrepo.auth.service.bkauth

import com.tencent.bkrepo.auth.pojo.CheckPermissionRequest
import com.tencent.bkrepo.auth.pojo.CreatePermissionRequest
import com.tencent.bkrepo.auth.pojo.Permission
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(prefix = "auth", name = ["realm"], havingValue = "bkauth")
class BkPermissionServiceImpl @Autowired constructor(

) {
    fun deletePermission(id: String) {
    }

    fun listPermission(resourceType: ResourceType?): List<Permission> {
        return listOf()
    }

    fun createPermission(request: CreatePermissionRequest) {
        throw ErrorCodeException(CommonMessageCode.OPERATION_UNSUPPORTED)
    }

    fun checkPermission(request: CheckPermissionRequest): Boolean {
        // todo 对接权限中心
        return true
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BkPermissionServiceImpl::class.java)
    }
}
