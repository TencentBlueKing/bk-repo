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

package com.tencent.bkrepo.repository.pojo.webhook

/**
 * 蓝盾开启DevX时发送的Webhook Payload
 */
data class BkCiDevXEnabledPayload(
    /**
     * 项目名称
     */
    val projectName: String,
    /**
     * 项目代码（蓝盾项目Id）
     */
    val projectCode: String,
    /**
     * 事业群ID
     */
    val bgId: String?,
    /**
     * 事业群名字
     */
    val bgName: String?,
    /**
     * 中心ID
     */
    val centerId: String?,
    /**
     * 中心名称
     */
    val centerName: String?,
    /**
     * 部门ID
     */
    val deptId: String?,
    /**
     * 部门名称
     */
    val deptName: String?,
    /**
     * 英文缩写
     */
    val englishName: String,
    /**
     * 运营产品ID
     */
    val productId: Int?,
)
