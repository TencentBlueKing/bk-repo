package com.tencent.bkrepo.common.service.cluster

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.scheduling.annotation.Scheduled

@Aspect
class CenterJobAspect(
    private val clusterProperties: ClusterProperties
) {
    @Around("@annotation(com.tencent.bkrepo.common.service.cluster.CenterJob)")
    @Throws(Throwable::class)
    fun around(point: ProceedingJoinPoint): Any? {
        val signature = point.signature as MethodSignature
        val method = signature.method
        val scheduled = method.getAnnotation(Scheduled::class.java)
        if (scheduled != null && clusterProperties.role != RoleType.CENTER) {
            return null
        }
        return point.proceed()
    }
}
