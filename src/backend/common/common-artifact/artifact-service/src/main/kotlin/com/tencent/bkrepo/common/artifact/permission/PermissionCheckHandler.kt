package com.tencent.bkrepo.common.artifact.permission

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.exception.PermissionCheckException
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo

/**
 *
 * @author: carrypan
 * @date: 2019/11/25
 */
interface PermissionCheckHandler {

    /**
     * 进行权限校验
     * 校验不通过抛PermissionCheckException异常
     */
    @Throws(PermissionCheckException::class)
    fun onPermissionCheck(userId: String, permission: Permission, artifactInfo: ArtifactInfo, repositoryInfo: RepositoryInfo)

    /**
     * 进行身份校验
     * 校验不通过抛PermissionCheckException异常
     */
    @Throws(PermissionCheckException::class)
    fun onPrincipalCheck(userId: String, principal: Principal)

    /**
     * 认证成功回调
     */
    fun onPermissionCheckSuccess()

    /**
     * 认证失败回调
     * 可以根据各自依赖源的协议返回不同的数据格式
     */
    fun onPermissionCheckFailed(exception: PermissionCheckException)
}
