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
enum class DefaultGroupTypeAndActions(val value: String, val actions: Map<String, List<String>>) {
    PROJECT_MANAGER(
        DefaultGroupType.PROJECT_MANAGER.value,
        mapOf(
            ResourceType.PROJECT.id() to listOf(
                ActionTypeMapping.PROJECT_VIEW.id(),
                ActionTypeMapping.PROJECT_EDIT.id(),
                ActionTypeMapping.PROJECT_MANAGE.id(),
                ActionTypeMapping.REPO_CREATE.id()
            ),
            ResourceType.REPO.id() to listOf(
                    ActionTypeMapping.REPO_MANAGE.id(),
                    ActionTypeMapping.REPO_EDIT.id(),
                    ActionTypeMapping.REPO_VIEW.id(),
                    ActionTypeMapping.REPO_DELETE.id(),
                    ActionTypeMapping.NODE_CREATE.id()
                ),
            ResourceType.NODE.id() to listOf(
                ActionTypeMapping.NODE_DELETE.id(),
                ActionTypeMapping.NODE_EDIT.id(),
                ActionTypeMapping.NODE_WRITE.id(),
                ActionTypeMapping.NODE_VIEW.id(),
                ActionTypeMapping.NODE_DOWNLOAD.id()
            ),
        )
    ),
    PROJECT_DOWNLOAD(DefaultGroupType.PROJECT_DOWNLOAD.value,
                      mapOf(
                          ResourceType.PROJECT.id() to listOf(ActionTypeMapping.PROJECT_VIEW.id()),
                          ResourceType.REPO.id() to listOf(ActionTypeMapping.REPO_VIEW.id()),
                          ResourceType.NODE.id() to listOf(
                              ActionTypeMapping.NODE_DOWNLOAD.id(),
                              ActionTypeMapping.NODE_VIEW.id()
                          ),
                      )
    ),
    PROJECT_UPLOAD_DELETE(DefaultGroupType.PROJECT_UPLOAD_DELETE.value,
                   mapOf(
                       ResourceType.PROJECT.id() to listOf(ActionTypeMapping.PROJECT_VIEW.id()),
                       ResourceType.REPO.id() to listOf(
                           ActionTypeMapping.REPO_VIEW.id(),
                           ActionTypeMapping.NODE_CREATE.id()
                       ),
                       ResourceType.NODE.id() to listOf(
                           ActionTypeMapping.NODE_DELETE.id(),
                           ActionTypeMapping.NODE_EDIT.id(),
                           ActionTypeMapping.NODE_WRITE.id(),
                           ActionTypeMapping.NODE_VIEW.id(),
                           ActionTypeMapping.NODE_DOWNLOAD.id()
                       )
                   )
    ),
    REPO_MANAGER(DefaultGroupType.REPO_MANAGER.value,
                       mapOf(
                           ResourceType.PROJECT.id() to listOf(ActionTypeMapping.PROJECT_VIEW.id()),
                           ResourceType.REPO.id() to listOf(
                               ActionTypeMapping.REPO_MANAGE.id(),
                               ActionTypeMapping.REPO_EDIT.id(),
                               ActionTypeMapping.REPO_VIEW.id(),
                               ActionTypeMapping.REPO_DELETE.id(),
                               ActionTypeMapping.NODE_CREATE.id()
                           ),
                           ResourceType.NODE.id() to listOf(
                               ActionTypeMapping.NODE_DELETE.id(),
                               ActionTypeMapping.NODE_EDIT.id(),
                               ActionTypeMapping.NODE_WRITE.id(),
                               ActionTypeMapping.NODE_VIEW.id(),
                               ActionTypeMapping.NODE_DOWNLOAD.id()
                           )
                       )
    ),
    REPO_DOWNLOAD(
        DefaultGroupType.REPO_DOWNLOAD.value,
        mapOf(
            ResourceType.PROJECT.id() to listOf(ActionTypeMapping.PROJECT_VIEW.id()),
            ResourceType.REPO.id() to listOf(ActionTypeMapping.REPO_VIEW.id()),
            ResourceType.NODE.id() to listOf(
                ActionTypeMapping.NODE_VIEW.id(),
                ActionTypeMapping.NODE_DOWNLOAD.id()
            )
        )
    ),
    REPO_UPLOAD_DELETE(
        DefaultGroupType.REPO_UPLOAD_DELETE.value,
                      mapOf(
                          ResourceType.PROJECT.id() to listOf(ActionTypeMapping.PROJECT_VIEW.id()),
                          ResourceType.REPO.id() to listOf(
                              ActionTypeMapping.REPO_VIEW.id(),
                              ActionTypeMapping.NODE_CREATE.id()
                          ),
                          ResourceType.NODE.id() to listOf(
                              ActionTypeMapping.NODE_DELETE.id(),
                              ActionTypeMapping.NODE_EDIT.id(),
                              ActionTypeMapping.NODE_WRITE.id(),
                              ActionTypeMapping.NODE_VIEW.id(),
                              ActionTypeMapping.NODE_DOWNLOAD.id()
                          ),
                      )
    );

    companion object {
        fun get(value: String): DefaultGroupTypeAndActions {
            values().forEach {
                if (value == it.value) return it
            }
            throw IllegalArgumentException("No enum for constant $value")
        }
    }
}

