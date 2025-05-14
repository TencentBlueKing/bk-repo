/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 *  A copy of the MIT License is included in this file.
 *
 *
 *  Terms of the MIT License:
 *  ---------------------------------------------------
 *  Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 *  documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 *  rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 *  permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 *  the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 *  LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 *  NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 *  WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 *  SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.s3.utils

import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.s3.constant.S3HttpHeaders.X_AMZ_META_MTIME
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object TimeUtil {

    fun getLastModified(nodeDetail: NodeDetail): String {
        val mTime = nodeDetail.metadata[X_AMZ_META_MTIME]?.toString()
            ?: nodeDetail.metadata[X_AMZ_META_MTIME.toLowerCase()]?.toString()
        return if (mTime.isNullOrEmpty()) {
            LocalDateTime.parse(nodeDetail.lastModifiedDate)
                .atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneId.of("GMT"))
                .format(DateTimeFormatter.RFC_1123_DATE_TIME)
        } else {
            val instant = Instant.ofEpochSecond(mTime.toDouble().toLong())
            ZonedDateTime.ofInstant(instant, ZoneId.of("GMT")).format(DateTimeFormatter.RFC_1123_DATE_TIME)
        }
    }
}
