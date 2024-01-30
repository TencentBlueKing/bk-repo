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

package com.tencent.bkrepo.ddc.utils

import com.tencent.bkrepo.ddc.serialization.CbField
import com.tencent.bkrepo.ddc.serialization.CbFieldUtils
import com.tencent.bkrepo.ddc.serialization.CbObject

fun CbField.hasName() = CbFieldUtils.hasFieldName(typeWithFlags)

fun CbField.isNull() = CbFieldUtils.isNull(typeWithFlags)

fun CbField.isObject() = CbFieldUtils.isObject(typeWithFlags)

fun CbField.isArray() = CbFieldUtils.isArray(typeWithFlags)

fun CbField.isString() = CbFieldUtils.isString(typeWithFlags)

fun CbField.isBinary() = CbFieldUtils.isBinary(typeWithFlags)

fun CbField.isInteger() = CbFieldUtils.isInteger(typeWithFlags)

fun CbField.isFloat() = CbFieldUtils.isFloat(typeWithFlags)

fun CbField.isBool() = CbFieldUtils.isBool(typeWithFlags)

fun CbField.isObjectAttachment() = CbFieldUtils.isObjectAttachment(typeWithFlags)
fun CbField.isBinaryAttachment() = CbFieldUtils.isBinaryAttachment(typeWithFlags)
fun CbField.isAttachment() = CbFieldUtils.isAttachment(typeWithFlags)
fun CbField.isHash() = CbFieldUtils.isHash(typeWithFlags)
fun CbField.isUuid() = CbFieldUtils.isUuid(typeWithFlags)

fun CbField.hasAttachments(): Boolean {
    return if (isAttachment()) {
        true
    } else {
        any { it.isAttachment() || it.isObject() && it.hasAttachments() || it.isArray() && it.hasAttachments() }
    }
}

fun CbObject.hasAttachments(): Boolean {
    return any { it.hasAttachments() }
}
