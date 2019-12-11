package com.tencent.bkrepo.common.auth

import com.tencent.bkrepo.auth.api.ServicePermissionResource
import com.tencent.bkrepo.auth.pojo.CheckPermissionRequest
import com.tencent.bkrepo.common.api.constant.CommonMessageCode.PERMISSION_DENIED
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * 权限服务类
 *
 * @author: carrypan
 * @date: 2019-10-18
 */
@Service
class PermissionService @Autowired constructor(
    private val servicePermissionResource: ServicePermissionResource
) {
    @Value("\${auth.enabled:true}")
    private val authEnabled: Boolean = true

    fun checkPermission(request: CheckPermissionRequest) {
        if (!hasPermission(request)) {
            throw ErrorCodeException(PERMISSION_DENIED)
        }
    }

    fun hasPermission(request: CheckPermissionRequest): Boolean {
        return if (authEnabled) {
            servicePermissionResource.checkPermission(request).data ?: false
        } else {
            true
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PermissionService::class.java)
    }
}
