package com.tencent.bkrepo.common.service.cluster

import com.tencent.bkrepo.common.api.pojo.ClusterArchitecture
import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import com.tencent.bkrepo.common.service.cluster.properties.ClusterProperties
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.scheduling.annotation.Scheduled

@Aspect
class StandaloneJobAspect(
    private val clusterProperties: ClusterProperties
) {
    @Around("@annotation(com.tencent.bkrepo.common.service.cluster.StandaloneJob)")
    @Throws(Throwable::class)
    fun around(point: ProceedingJoinPoint): Any? {
        val signature = point.signature as MethodSignature
        val method = signature.method
        val scheduled = method.getAnnotation(Scheduled::class.java)
        val centerNode = clusterProperties.role == ClusterNodeType.CENTER
        val commitEdgeEdgeNode = clusterProperties.role == ClusterNodeType.EDGE &&
            clusterProperties.architecture == ClusterArchitecture.COMMIT_EDGE
        if (scheduled != null && !(centerNode || commitEdgeEdgeNode)) {
            return null
        }
        return point.proceed()
    }
}
