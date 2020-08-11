package com.tencent.bkrepo.common.security.permission

import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.security.exception.PermissionException
import com.tencent.bkrepo.common.security.manager.ArtifactContextHolder
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

    @Around("@annotation(com.tencent.bkrepo.common.security.permission.Permission)")
    @Throws(Throwable::class)
    fun around(point: ProceedingJoinPoint): Any? {
        val signature = point.signature as MethodSignature
        val method = signature.method
        val permission = method.getAnnotation(Permission::class.java)

        val userId = HttpContextHolder.getRequest().getAttribute(USER_KEY) as? String ?: ANONYMOUS_USER
        val repositoryInfo = ArtifactContextHolder.getRepositoryInfo()!!

        return try {
            permissionCheckHandler.onPermissionCheck(userId, permission, repositoryInfo)
            logger.debug("User[$userId] check permission [$permission] on [$repositoryInfo] success.")
            permissionCheckHandler.onPermissionCheckSuccess()
            point.proceed()
        } catch (exception: PermissionException) {
            logger.warn("User[$userId] check permission [$permission] on [$repositoryInfo] failed.")
            permissionCheckHandler.onPermissionCheckFailed(exception)
            null
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PermissionAspect::class.java)
    }
}
