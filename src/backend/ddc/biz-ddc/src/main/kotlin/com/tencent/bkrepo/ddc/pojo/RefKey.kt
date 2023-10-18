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

import com.tencent.bkrepo.common.api.exception.BadRequestException
import com.tencent.bkrepo.common.api.message.CommonMessageCode.PARAMETER_INVALID

data class RefKey(
    private var text: String,
    val legacy: Boolean = false
) {
    init {
        if (legacy) {
            checkLegacyKey(text)
        } else {
            checkKey(text)
            text = text.toLowerCase()
        }
    }

    private fun checkKey(text: String) {
        if (text.length != 40) {
            throw BadRequestException(PARAMETER_INVALID, "IoHashKeys must be exactly 40 bytes.")
        }
        if (text.any { !isValidCharacter(it) }) {
            throw BadRequestException(PARAMETER_INVALID, "${this.text} contains invalid character.")
        }
    }

    private fun checkLegacyKey(text: String) {
        if (text.isEmpty()) {
            throw BadRequestException(PARAMETER_INVALID, "Keys must have at least one character.")
        }

        if (text.length > LEGACY_KEY_MAX_LENGTH) {
            throw BadRequestException(
                PARAMETER_INVALID,
                "Keys may not be longer than $LEGACY_KEY_MAX_LENGTH characters"
            )
        }
    }

    override fun toString(): String = text

    private fun isValidCharacter(c: Char): Boolean = c in 'a'..'z' || c in '0'..'9'

    companion object {
        private const val LEGACY_KEY_MAX_LENGTH = 250
        fun create(s: String, legacy: Boolean = false): RefKey = RefKey(s, legacy)
    }
}
