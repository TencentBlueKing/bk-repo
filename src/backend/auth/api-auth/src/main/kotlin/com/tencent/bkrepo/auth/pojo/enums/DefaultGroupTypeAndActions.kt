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
 * 默认用户组对应的操作权限
 */
enum class DefaultGroupTypeAndActions(val value: String, val actions: Map<String, String>) {
    PROJECT_MANAGER(
        "project_manager",
        mapOf(
            "project" to "project_view,project_edit,project_manage,repo_create",
            "repo" to "repo_manage,repo_edit,repo_view,repo_delete,node_create",
            "node" to "node_download,node_view,node_delete,node_edit",
        )
    ),
    PROJECT_DOWNLOAD("project_download",
                      mapOf(
                          "project" to "project_view",
                          "repo" to "repo_view",
                          "node" to "node_download,node_view",
                      )
    ),
    PROJECT_EDIT("project_edit",
                     mapOf(
                         "project" to "project_view",
                         "repo" to "repo_edit,node_create,repo_view",
                         "node" to "node_download,node_view,node_delete,node_edit",
                     )
    ),
    PROJECT_UPLOAD_DELETE("project_upload_delete",
                   mapOf(
                       "project" to "project_view",
                       "repo" to "repo_view,node_create",
                       "node" to "node_download,node_view,node_delete,node_edit",
                   )
    ),
    REPO_MANAGER("repo_manager",
                       mapOf(
                           "repo" to "repo_edit,repo_view,repo_manage,repo_delete,node_create",
                           "node" to "node_download,node_view,node_delete,node_edit",
                       )
    ),
    REPO_DOWNLOAD(
        "repo_download",
        mapOf(
            "repo" to "repo_view",
            "node" to "node_download,node_view",
        )
    ),
    REPO_UPLOAD_DELETE("repo_upload_delete",
                      mapOf(
                          "repo" to "repo_view,node_create",
                          "node" to "node_download,node_view,node_delete, node_edit",
                      )
    );

    companion object {
        fun get(value: String): DefaultGroupTypeAndActions {
            DefaultGroupTypeAndActions.values().forEach {
                if (value == it.value) return it
            }
            throw IllegalArgumentException("No enum for constant $value")
        }
    }
}

