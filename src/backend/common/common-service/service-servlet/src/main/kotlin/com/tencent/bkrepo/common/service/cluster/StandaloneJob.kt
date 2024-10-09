package com.tencent.bkrepo.common.service.cluster

import com.tencent.bkrepo.common.api.pojo.ClusterArchitecture
import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import java.lang.annotation.Inherited

/**
 * 独立任务
 * 在[ClusterNodeType.CENTER]节点或[ClusterArchitecture.COMMIT_EDGE]组网方式的[ClusterNodeType.EDGE]节点执行，
 * */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Inherited
@MustBeDocumented
annotation class StandaloneJob
