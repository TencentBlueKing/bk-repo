/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.auth.pojo.enums

/**
 * 权限中心v3 action映射关系
 */
enum class ActionTypeMapping(val resType: String, val pAction: String) {
    PROJECT_MANAGE(ResourceType.PROJECT.name, PermissionAction.MANAGE.name),
    PROJECT_VIEW(ResourceType.PROJECT.name, PermissionAction.READ.name),
    PROJECT_EDIT(ResourceType.PROJECT.name, PermissionAction.UPDATE.name),
    REPO_CREATE(ResourceType.PROJECT.name, PermissionAction.WRITE.name),
    REPO_MANAGE(ResourceType.REPO.name, PermissionAction.MANAGE.name),
    REPO_VIEW(ResourceType.REPO.name, PermissionAction.READ.name),
    REPO_EDIT(ResourceType.REPO.name, PermissionAction.UPDATE.name),
    REPO_DELETE(ResourceType.REPO.name, PermissionAction.DELETE.name),
    NODE_CREATE(ResourceType.REPO.name, PermissionAction.WRITE.name),
    NODE_VIEW(ResourceType.NODE.name, PermissionAction.VIEW.name),
    NODE_DOWNLOAD(ResourceType.NODE.name, PermissionAction.READ.name),
    NODE_EDIT(ResourceType.NODE.name, PermissionAction.UPDATE.name),
    NODE_WRITE(ResourceType.NODE.name, PermissionAction.WRITE.name),
    NODE_DELETE(ResourceType.NODE.name, PermissionAction.DELETE.name);

    fun id() = this.name.toLowerCase()

    companion object {

        fun lookup(resType: String, pAction: String): ActionTypeMapping {
            return values().find { it.resType == resType && it.pAction == pAction }
                ?:  throw IllegalArgumentException("No enum for resType $resType and pAction $pAction!")
        }
    }
}
