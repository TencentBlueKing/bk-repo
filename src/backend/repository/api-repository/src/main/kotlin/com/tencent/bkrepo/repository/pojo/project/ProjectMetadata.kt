/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.repository.pojo.project

/**
 * 项目元数据
 */
data class ProjectMetadata(
    /**
     * 元数据键
     */
    val key: String,
    /**
     * 元数据值
     */
    var value: Any,
) {
    companion object {
        /**
         * 所属事业群ID
         */
        const val KEY_BG_ID = "bgId"

        /**
         * 所属事业群
         */
        const val KEY_BG_NAME = "bgName"

        /**
         * 所属部门ID
         */
        const val KEY_DEPT_ID = "deptId"

        /**
         * 所属部门
         */
        const val KEY_DEPT_NAME = "deptName"

        /**
         * 所属中心ID
         */
        const val KEY_CENTER_ID = "centerId"

        /**
         * 所属中心名
         */
        const val KEY_CENTER_NAME = "centerName"

        /**
         * 运营产品ID
         */
        const val KEY_PRODUCT_ID = "productId"

        /**
         * 是否启用
         */
        const val KEY_ENABLED = "enabled"
    }
}
