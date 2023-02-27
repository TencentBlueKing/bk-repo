/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.fs.server

import com.tencent.bkrepo.fs.server.model.Node
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import java.time.format.DateTimeFormatter

fun NodeInfo.toNode(): Node {
    return Node(
        createdBy = this.createdBy,
        createdDate = this.createdDate.format(DateTimeFormatter.ISO_DATE_TIME),
        lastModifiedBy = this.lastModifiedBy,
        lastModifiedDate = this.lastModifiedDate.format(DateTimeFormatter.ISO_DATE_TIME),
        projectId = this.projectId,
        repoName = this.repoName,
        folder = this.folder,
        path = this.path,
        name = this.name,
        fullPath = this.fullPath,
        size = this.size,
        sha256 = this.sha256,
        md5 = this.md5,
        metadata = this.metadata,
        lastAccessDate = this.lastAccessDate?.format(DateTimeFormatter.ISO_DATE_TIME)
    )
}
