package com.tencent.bkrepo.common.artifact.permission

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import java.lang.annotation.Inherited

/**
 * 注解表示需要对资源鉴权
 *
 * @author: carrypan
 * @date: 2019/11/19
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Inherited
@MustBeDocumented
annotation class Permission (
    val type : ResourceType,
    val action : PermissionAction
)