/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.helm.utils

import com.tencent.bkrepo.common.api.util.toJsonString
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("yaml工具类测试")
class YamlUtilsTest {
    @Test
    @DisplayName("yaml转换为json测试")
    fun yaml2JsonTest() {
        val jsonString = yamlStr.toJsonString()
        println(jsonString)
    }

    companion object {
        const val yamlStr = "apiVersion: \"v1\"\n" +
            "entries:\n" +
            "  mychart:\n" +
            "  - apiVersion: \"v2\"\n" +
            "    appVersion: \"1.16.0\"\n" +
            "    created: \"2022-05-26T13:33:29.610Z\"\n" +
            "    description: \"A Helm chart for Kubernetes\"\n" +
            "    digest: \"e5b9ce565573cf5006d7c2fb35d124e30ead715885e2e1ad9822910e949a37d0\"\n" +
            "    keywords: []\n" +
            "    maintainers: []\n" +
            "    name: \"mychart\"\n" +
            "    sources: []\n" +
            "    urls:\n" +
            "    - \"test/helm-5/charts/mychart-0.1.6.tgz\"\n" +
            "    version: \"0.1.6\"\n" +
            "    type: \"application\"\n" +
            "generated: \"2022-05-26T12:40:19.296Z\"\n" +
            "serverInfo: {}\n"
    }
}
