package com.tencent.bkrepo.common.security.permission

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import java.lang.annotation.Inherited

/**
 * 注解表示需要对资源鉴权
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Inherited
@MustBeDocumented
annotation class Permission(
    val type: ResourceType,
    val action: PermissionAction
)

fun Permission.string(): String {
    return "type=$type, action=$action"
}
