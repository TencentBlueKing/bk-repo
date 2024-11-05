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

package com.tencent.bkrepo.analyst.model

import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

@Document("spdx_license")
@CompoundIndexes(
    CompoundIndex(name = "license_idx", def = "{'licenseId': 1, 'name': 1}", unique = true)
)
data class TSpdxLicense(
    var id: String? = null,
    var createdBy: String,
    var createdDate: LocalDateTime,
    var lastModifiedBy: String,
    var lastModifiedDate: LocalDateTime,

    /**
     * 许可证名称
     */
    var name: String,

    /**
     * 许可证标识符
     */
    var licenseId: String,

    /**
     * 指向其他许可证副本的交叉引用 URL
     */
    var seeAlso: MutableList<String>,

    /**
     * 对许可证文件的 HTML 格式的引用
     */
    var reference: String,

    /**
     * 是否被弃用
     */
    var isDeprecatedLicenseId: Boolean,

    /**
     *  OSI是否已批准许可证
     */
    var isOsiApproved: Boolean,

    /**
     * 是否FSF认证免费
     */
    var isFsfLibre: Boolean? = null,

    /**
     * 包含许可证详细信息的 JSON 文件的 URL
     */
    var detailsUrl: String,

    /**
     * 是否信任
     */
    var isTrust: Boolean = true,

    /**
     * 风险等级
     */
    var risk: String? = null
)
