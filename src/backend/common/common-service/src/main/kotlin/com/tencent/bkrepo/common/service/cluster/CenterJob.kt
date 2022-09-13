package com.tencent.bkrepo.common.service.cluster

import java.lang.annotation.Inherited

/**
 * 中心节点任务
 * 只在中心节点执行
 * */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Inherited
@MustBeDocumented
annotation class CenterJob
