package com.tencent.bkrepo.common.security.permission

import com.tencent.bkrepo.common.security.manager.PermissionManager

open class DefaultPermissionCheckHandler(
    private val permissionManager: PermissionManager
) : PermissionCheckHandler {

    override fun onPrincipalCheck(userId: String, principal: Principal) {
        permissionManager.checkPrincipal(userId, principal.type)
    }
}
