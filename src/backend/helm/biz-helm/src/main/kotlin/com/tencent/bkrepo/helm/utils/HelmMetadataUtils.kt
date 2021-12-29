
package com.tencent.bkrepo.helm.utils

import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.helm.pojo.metadata.HelmChartMetadata

object HelmMetadataUtils {

    fun convertToMap(chartInfo: HelmChartMetadata): Map<String, Any> {
        return chartInfo.toJsonString().readJsonString<Map<String, Any>>()
    }

    fun convertToObject(map: Map<String, Any>): HelmChartMetadata {
        return map.toJsonString().readJsonString<HelmChartMetadata>()
    }

    fun convertToString(chartInfo: HelmChartMetadata): String {
        return chartInfo.toJsonString()
    }
}
