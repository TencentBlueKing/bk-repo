package com.tencent.bkrepo.helm.utils

import com.tencent.bkrepo.common.api.util.JsonUtils.objectMapper
import com.tencent.bkrepo.helm.constants.CHART_NOT_FOUND
import com.tencent.bkrepo.helm.constants.NO_CHART_NAME_FOUND
import com.tencent.bkrepo.helm.constants.VERSION
import com.tencent.bkrepo.helm.pojo.IndexEntity
import java.io.InputStream

object JsonUtil {
    fun searchJson(inputStream: InputStream, urls: String): Map<String, *> {
        val jsonStr = YamlUtils.yaml2Json(inputStream)
        val indexEntity = objectMapper.readValue(jsonStr, IndexEntity::class.java)
        val urlList = urls.removePrefix("/").split("/").filter { it.isNotBlank() }
        return when (urlList.size) {
            // Without name and version
            0 -> {
                indexEntity.entries
            }
            // query with name
            1 -> {
                val chartName = urlList[0]
                val chartList = indexEntity.entries[chartName]
                chartList?.let { mapOf(chartName to chartList) } ?: CHART_NOT_FOUND
            }
            // query with name and version
            2 -> {
                val chartName = urlList[0]
                val chartVersion = urlList[1]
                val chartList = indexEntity.entries[chartName] ?: return NO_CHART_NAME_FOUND
                val chartVersionList = chartList.filter { chartVersion == it[VERSION] }.toList()
                if (chartVersionList.isNotEmpty()) {
                    mapOf(chartName to chartVersionList)
                } else {
                    mapOf("error" to "no chart version found for $chartName-$chartVersion")
                }
            }
            else -> {
                // ERROR_NOT_FOUND
                CHART_NOT_FOUND
            }
        }
    }
}
