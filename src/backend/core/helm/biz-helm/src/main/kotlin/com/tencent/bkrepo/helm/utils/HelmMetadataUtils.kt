/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
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

import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.constant.SOURCE_TYPE
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactChannel
import com.tencent.bkrepo.helm.pojo.metadata.HelmChartMetadata
import com.tencent.bkrepo.repository.constant.PROXY_DOWNLOAD_URL
import com.tencent.bkrepo.common.metadata.pojo.metadata.MetadataModel

object HelmMetadataUtils {
    private val keys: Array<String> = arrayOf("annotations", "maintainers")

    fun convertToMap(chartInfo: HelmChartMetadata): Map<String, Any> {
        return chartInfo.toJsonString().readJsonString<Map<String, Any>>().minus(keys)
    }

    // 增加sourceType字段，用于区分该制品来源，是从代理源下载还是用户上传
    fun convertToMetadata(chartInfo: HelmChartMetadata, sourceType: ArtifactChannel? = null): List<MetadataModel> {
        val mutableMap: MutableList<MetadataModel> = convertToMap(chartInfo).map {
            MetadataModel(key = it.key, value = it.value)
        } as MutableList<MetadataModel>
        sourceType?.let {
            mutableMap.add(MetadataModel(SOURCE_TYPE, sourceType))
        }
        if (!chartInfo.proxyDownloadUrl.isNullOrEmpty()) {
            mutableMap.add(MetadataModel(PROXY_DOWNLOAD_URL, chartInfo.proxyDownloadUrl!!))
        }
        return mutableMap
    }

    fun convertToObject(map: Map<String, Any>): HelmChartMetadata {
        return map.toJsonString().readJsonString<HelmChartMetadata>()
    }

    fun convertToString(chartInfo: HelmChartMetadata): String {
        return chartInfo.toJsonString()
    }
}
