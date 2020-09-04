package com.tencent.bkrepo.common.security.permission

import com.tencent.bkrepo.common.security.exception.PermissionException

interface PermissionCheckHandler {

    /**
     * 进行权限校验
     * 校验不通过抛PermissionException异常
     */
    @Throws(PermissionException::class)
    fun onPermissionCheck(userId: String, permission: Permission) { }

    /**
     * 进行身份校验
     *
     * 校验不通过抛[PermissionException]异常
     */
    @Throws(PermissionException::class)
    fun onPrincipalCheck(userId: String, principal: Principal) { }

    /**
     * 认证成功回调
     */
    fun onPermissionCheckSuccess() { }

    /**
     * 认证失败回调
     *
     * 可以根据各自依赖源的协议返回不同的数据格式
     */
    fun onPermissionCheckFailed(exception: PermissionException) {
        throw exception
    }
}
