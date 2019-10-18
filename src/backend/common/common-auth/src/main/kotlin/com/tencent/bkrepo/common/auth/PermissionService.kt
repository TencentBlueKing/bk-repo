package com.tencent.bkrepo.common.auth

import com.tencent.bkrepo.auth.api.ServicePermissionResource
import com.tencent.bkrepo.auth.pojo.CheckPermissionRequest
import com.tencent.bkrepo.common.api.constant.CommonMessageCode.PERMISSION_DENIED
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.exception.ExternalErrorCodeException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
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
    fun checkPermission(request: CheckPermissionRequest) {
        val response = servicePermissionResource.checkPermission(request)
        if (response.isNotOk()) {
            logger.error("Check permission [$request] error: [${response.code}, ${response.message}]")
            throw ExternalErrorCodeException(response.code, response.message)
        }
        val hasPermission = response.data ?: false
        takeIf { hasPermission } ?: throw ErrorCodeException(PERMISSION_DENIED)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PermissionService::class.java)
    }
}
