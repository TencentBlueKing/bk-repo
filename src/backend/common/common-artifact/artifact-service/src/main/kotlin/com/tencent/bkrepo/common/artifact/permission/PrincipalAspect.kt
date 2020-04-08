package com.tencent.bkrepo.common.artifact.permission

import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.artifact.exception.PermissionCheckException
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

@Aspect
class PrincipalAspect {

    @Autowired
    private lateinit var permissionCheckHandler: PermissionCheckHandler

    @Around("@within(com.tencent.bkrepo.common.artifact.permission.Principal) || @annotation(com.tencent.bkrepo.common.artifact.permission.Principal)")
    @Throws(Throwable::class)
    fun around(point: ProceedingJoinPoint): Any? {
        val signature = point.signature as MethodSignature
        val method = signature.method
        var principal = method.getAnnotation(Principal::class.java)
        if (principal == null) {
            principal = point.target.javaClass.getAnnotation(Principal::class.java)
            if (principal == null) { // 获取接口上的注解
                for (clazz in point.javaClass.interfaces) {
                    principal = clazz.getAnnotation(Principal::class.java)
                    if (principal != null) {
                        break
                    }
                }
            }
        }
        val request = HttpContextHolder.getRequest()
        val userId = request.getAttribute(USER_KEY) as? String ?: ANONYMOUS_USER

        return try {
            permissionCheckHandler.onPrincipalCheck(userId, principal)
            logger.debug("User[$userId] check principal [$principal] success.")
            permissionCheckHandler.onPermissionCheckSuccess()
            point.proceed()
        } catch (exception: PermissionCheckException) {
            logger.warn("User[$userId] check principal [$principal] failed.")
            permissionCheckHandler.onPermissionCheckFailed(exception)
            null
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PrincipalAspect::class.java)
    }
}
