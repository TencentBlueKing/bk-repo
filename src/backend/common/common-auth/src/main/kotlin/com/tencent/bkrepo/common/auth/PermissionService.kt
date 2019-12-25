package com.tencent.bkrepo.common.auth

import com.tencent.bkrepo.auth.api.ServicePermissionResource
import com.tencent.bkrepo.auth.pojo.CheckPermissionRequest
import com.tencent.bkrepo.common.api.constant.APP_KEY
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/**
 * 权限服务类
 *
 * @author: carrypan
 * @date: 2019-10-18
 */
@Service
class PermissionService @Autowired constructor(
    private val servicePermissionResource: ServicePermissionResource,
    private val authProperties: AuthProperties
) {

    fun checkPermission(request: CheckPermissionRequest) {
        if (!hasPermission(request)) {
            throw ErrorCodeException(CommonMessageCode.PERMISSION_DENIED)
        }
    }

    fun hasPermission(request: CheckPermissionRequest): Boolean {
        return if (authProperties.enabled) {
            val httpRequest = (RequestContextHolder.getRequestAttributes() as ServletRequestAttributes).request
            if (httpRequest.getAttribute(APP_KEY) != null) {
                // 平台账号 直接跳过
                return true
            }
            servicePermissionResource.checkPermission(request).data ?: false
        } else true
    }
}
