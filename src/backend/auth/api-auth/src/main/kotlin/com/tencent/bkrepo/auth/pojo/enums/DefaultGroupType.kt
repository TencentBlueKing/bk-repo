/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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
 * 默认用户组
 */
enum class DefaultGroupType(val value: String, val displayName: String) {
    PROJECT_MANAGER("project_manager", "项目管理组"), // 管理员
    PROJECT_UPLOAD_DELETE("project_upload_delete", "项目操作组"), // 上传下载删除权限
    PROJECT_DOWNLOAD("project_download", "项目访问组"), // 下载权限
    REPO_MANAGER("repo_manager", "仓库管理组"), // 管理员
    REPO_UPLOAD_DELETE("repo_upload_delete", "仓库操作组"),
    REPO_DOWNLOAD("repo_download", "仓库访问组"); // 下载权限
    companion object {
        fun get(value: String): DefaultGroupType {
            values().forEach {
                if (value == it.value) return it
            }
            throw IllegalArgumentException("No enum for constant $value")
        }

        fun contains(value: String): Boolean {
            values().forEach {
                if (value == it.value) return true
            }
            return false
        }

        fun containsDisplayName(displayName: String): Boolean {
            values().forEach {
                if (displayName == it.displayName) return true
            }
            return false
        }
    }
}
