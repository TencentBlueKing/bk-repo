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

package com.tencent.bkrepo.oci.util

import com.tencent.bkrepo.common.api.util.toJsonString
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

internal class OciUtilsTest {
    @Test
    @DisplayName("str转成manifestV2")
    fun streamToManifestV2Test() {
        val jsonString = OciUtils.streamToManifestV2(manifestStr.byteInputStream()).toJsonString()
        println(jsonString)
    }

    companion object {
        const val manifestStr = "{\n" +
            "   \"schemaVersion\": 2,\n" +
            "   \"mediaType\": \"application/vnd.docker.distribution.manifest.v2+json\",\n" +
            "   \"config\": {\n" +
            "      \"mediaType\": \"application/vnd.docker.container.image.v1+json\",\n" +
            "      \"size\": 782,\n" +
            "      \"digest\": \"sha256:9bd9b30aa04cce7ebebc39e4f0b21a04cf1b6f12de8757525aef6649a3c68eb3\"\n" +
            "   },\n" +
            "   \"layers\": [\n" +
            "      {\n" +
            "         \"mediaType\": \"application/vnd.docker.image.rootfs.diff.tar.gzip\",\n" +
            "         \"size\": 2798889,\n" +
            "         \"digest\": \"sha256:2408cc74d12b6cd092bb8b516ba7d5e290f485d3eb9672efc00f0583730179e8\"\n" +
            "      },\n" +
            "      {\n" +
            "         \"mediaType\": \"application/vnd.docker.image.rootfs.diff.tar.gzip\",\n" +
            "         \"size\": 448,\n" +
            "         \"digest\": \"sha256:cbf6a738369848c9f78c68b81aaa9dfa54b64131ca7898fe5b9b9fcdc33061c8\"\n" +
            "      }\n" +
            "   ]\n" +
            "}"
        const val testAddress = "https://github.com/opencontainers/distribution-spec/tree/main/conformance"
    }
}
