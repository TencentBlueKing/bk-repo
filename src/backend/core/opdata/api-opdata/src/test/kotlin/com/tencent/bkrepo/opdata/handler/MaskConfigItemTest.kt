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

package com.tencent.bkrepo.opdata.handler

import com.tencent.bkrepo.common.metadata.util.DesensitizedUtils
import com.tencent.bkrepo.opdata.pojo.config.ConfigItem
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class MaskConfigItemTest {
    @Test
    @Suppress("UNCHECKED_CAST")
    fun test() {
        val configItems = listOf(
            ConfigItem("token", "ttookkeenn"),
            ConfigItem("test.secret", "sseeccrreett"),
            ConfigItem("other", "other"),
            ConfigItem(
                "spring.mongodb.uri", "http://hqwoiudhaosuhd:adhiuashdiahds@" +
                "aushdiuqwhdoiuahsd.ashgdiuasohdoiad.asdhiaushd?ahdiuasd=whdgakjsdg"
            ),
            ConfigItem("expired", 1000),
            ConfigItem("auth", 666666)
        )
        val result = DesensitizedUtils.desensitizeObject(configItems) as List<ConfigItem>
        Assertions.assertEquals("token", result[0].key)
        Assertions.assertEquals("******", result[0].value)
        Assertions.assertEquals("test.secret", result[1].key)
        Assertions.assertEquals("******", result[1].value)
        Assertions.assertEquals(configItems[2], result[2])
        Assertions.assertEquals("spring.mongodb.uri", result[3].key)
        Assertions.assertEquals("******", result[3].value)
        Assertions.assertEquals(configItems[4], result[4])
        Assertions.assertEquals("auth", result[5].key)
        Assertions.assertEquals("******", result[5].value)
    }
}
