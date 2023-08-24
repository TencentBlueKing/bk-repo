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

package com.tencent.bkrepo.ddc.pojo

import java.time.LocalDateTime

data class Reference(
    val namespace: String,
    val bucket: String,
    val name: RefId,
    val lastAccess: LocalDateTime,
    var blobIdentifier: ContentHash? = null,
    val isFinalized: Boolean,
    var inlineBlob: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Reference

        if (namespace != other.namespace) return false
        if (bucket != other.bucket) return false
        if (name != other.name) return false
        if (lastAccess != other.lastAccess) return false
        if (blobIdentifier != other.blobIdentifier) return false
        if (isFinalized != other.isFinalized) return false
        if (inlineBlob != null) {
            if (other.inlineBlob == null) return false
            if (!inlineBlob.contentEquals(other.inlineBlob)) return false
        } else if (other.inlineBlob != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = namespace.hashCode()
        result = 31 * result + bucket.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + lastAccess.hashCode()
        result = 31 * result + blobIdentifier.hashCode()
        result = 31 * result + isFinalized.hashCode()
        result = 31 * result + (inlineBlob?.contentHashCode() ?: 0)
        return result
    }
}
