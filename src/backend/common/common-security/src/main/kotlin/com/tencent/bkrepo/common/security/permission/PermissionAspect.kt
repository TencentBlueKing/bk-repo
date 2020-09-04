package com.tencent.bkrepo.common.security.permission

import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.security.exception.PermissionException
import com.tencent.bkrepo.common.security.http.SecurityUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

@Aspect
class PermissionAspect {

    @Autowired
    private lateinit var permissionCheckHandler: PermissionCheckHandler

    /**
     * 要求接口路径上必须使用标准格式/{projectId}/{repoName}，否则无法解析到仓库信息
     */
    @Around("@annotation(com.tencent.bkrepo.common.security.permission.Permission)")
    @Throws(Throwable::class)
    fun around(point: ProceedingJoinPoint): Any? {
        val signature = point.signature as MethodSignature
        val method = signature.method
        val permission = method.getAnnotation(Permission::class.java)
        val userId = HttpContextHolder.getRequest().getAttribute(USER_KEY) as? String ?: ANONYMOUS_USER

        return try {
            permissionCheckHandler.onPermissionCheck(userId, permission)
            if (logger.isDebugEnabled) {
                logger.debug("User[$SecurityUtils.getPrincipal()] check permission [$permission] success.")
            }
            permissionCheckHandler.onPermissionCheckSuccess()
            point.proceed()
        } catch (exception: PermissionException) {
            logger.warn("User[$SecurityUtils.getPrincipal()] check permission [$permission] failed.")
            permissionCheckHandler.onPermissionCheckFailed(exception)
            null
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PermissionAspect::class.java)
    }
}
