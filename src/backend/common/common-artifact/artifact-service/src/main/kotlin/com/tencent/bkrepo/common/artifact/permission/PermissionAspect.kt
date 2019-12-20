package com.tencent.bkrepo.common.artifact.permission

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.config.ARTIFACT_INFO_KEY
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.artifact.exception.PermissionCheckException
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/**
 *
 * @author: carrypan
 * @date: 2019/11/22
 */
@Aspect
class PermissionAspect {

    @Autowired
    private lateinit var permissionCheckHandler: PermissionCheckHandler

    @Around("@annotation(com.tencent.bkrepo.common.artifact.permission.Permission)")
    @Throws(Throwable::class)
    fun around(point: ProceedingJoinPoint): Any? {
        val signature = point.signature as MethodSignature
        val method = signature.method
        val permission = method.getAnnotation(Permission::class.java)

        val requestAttributes = RequestContextHolder.getRequestAttributes() as ServletRequestAttributes
        val request = requestAttributes.request
        val userId = request.getAttribute(USER_KEY) as? String ?: ""

        return try {
            val artifactInfo = findArtifactInfo(point.args) ?: throw PermissionCheckException("Missing ArtifactInfo argument.")
            permissionCheckHandler.onPermissionCheck(userId, permission, artifactInfo)
            logger.trace("User[$userId] check permission [$permission] on [$artifactInfo] success.")
            permissionCheckHandler.onPermissionCheckSuccess()
            point.proceed()
        } catch (exception: PermissionCheckException) {
            permissionCheckHandler.onPermissionCheckFailed(exception)
            null
        }
    }

    private fun findArtifactInfo(args: Array<Any>): ArtifactInfo? {
        for (argument in args) {
            if (argument is ArtifactInfo) return argument
        }
        return null
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PermissionAspect::class.java)
    }
}
