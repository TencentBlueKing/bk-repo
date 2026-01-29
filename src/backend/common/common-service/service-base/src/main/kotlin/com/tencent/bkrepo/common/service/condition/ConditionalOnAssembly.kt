package com.tencent.bkrepo.common.service.condition

import org.springframework.context.annotation.Conditional
import java.lang.annotation.Inherited

/**
 * 在Assembly架构下匹配生效
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Inherited
@MustBeDocumented
@Conditional(OnAssemblyCondition::class)
annotation class ConditionalOnAssembly
