/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.auth.pojo.enums

import java.util.Locale.getDefault

/**
 * 账号权限限制枚举
 * 用于限制账号可以执行的操作类型
 */
enum class AccountLimit(
    val allowedActions: Set<PermissionAction>
) {
    /**
     * 所有权限：允许所有操作
     */
    ALL(
        setOf(
            PermissionAction.MANAGE,
            PermissionAction.WRITE,
            PermissionAction.READ,
            PermissionAction.DOWNLOAD,
            PermissionAction.VIEW,
            PermissionAction.UPDATE,
            PermissionAction.DELETE
        )
    ),
    
    /**
     * 管理权限：允许管理、写入、读取等操作
     */
    MANAGE(
        setOf(
            PermissionAction.MANAGE
        )
    ),
    
    /**
     * 写入权限：允许写入、更新、删除、读取等操作
     */
    WRITE(
        setOf(
            PermissionAction.WRITE,
            PermissionAction.READ,
            PermissionAction.DOWNLOAD,
            PermissionAction.VIEW,
            PermissionAction.UPDATE,
            PermissionAction.DELETE
        )
    ),
    
    /**
     * 只读权限：仅允许读取、下载、查看操作
     */
    READONLY(
        setOf(
            PermissionAction.READ,
            PermissionAction.DOWNLOAD,
            PermissionAction.VIEW
        )
    );

    fun id() = name.lowercase(getDefault())
    
    /**
     * 检查指定的action是否被允许
     * 
     * @param action 要检查的权限动作
     * @return true 如果该action被允许
     */
    fun isActionAllowed(action: String): Boolean {
        val permissionAction = try {
            PermissionAction.valueOf(action.uppercase())
        } catch (e: IllegalArgumentException) {
            return false
        }
        return allowedActions.contains(permissionAction)
    }
    
    /**
     * 检查指定的action是否被允许
     * 
     * @param action 要检查的权限动作枚举
     * @return true 如果该action被允许
     */
    fun isActionAllowed(action: PermissionAction): Boolean {
        return allowedActions.contains(action)
    }
}
