package com.tencent.bkrepo.common.artifact.permission

import java.lang.annotation.Inherited

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Inherited
@MustBeDocumented
annotation class Principal(
    val type: PrincipalType
)
