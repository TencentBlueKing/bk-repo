package com.tencent.bkrepo.common.artifact.permission

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.exception.PermissionCheckException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 *
 * @author: carrypan
 * @date: 2019/11/25
 */
interface PermissionCheckHandler {

    /**
     * 进行权限校验
     * 校验不通过跑PermissionCheckException异常
     */
    @Throws(PermissionCheckException::class)
    fun onPermissionCheck(userId: String, permission: Permission, artifactInfo: ArtifactInfo)

    /**
     * 认证失败回调
     * 可以根据各自依赖源的协议返回不同的数据格式
     */
    fun onPermissionCheckFailed(request: HttpServletRequest, response: HttpServletResponse)

    /**
     * 认证成功回调
     */
    fun onPermissionCheckSuccess(request: HttpServletRequest, response: HttpServletResponse)
}