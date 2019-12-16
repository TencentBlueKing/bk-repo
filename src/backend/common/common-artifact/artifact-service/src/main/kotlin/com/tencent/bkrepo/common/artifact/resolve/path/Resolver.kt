package com.tencent.bkrepo.common.artifact.resolve.path

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import kotlin.reflect.KClass

/**
 *
 * @author: carrypan
 * @date: 2019/11/28
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class Resolver(
    val value: KClass<out ArtifactInfo>,
    val default: Boolean = false
)
