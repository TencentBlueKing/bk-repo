package com.tencent.bkrepo.helm.utils

import com.tencent.bkrepo.helm.constants.CHART_PACKAGE_FILE_EXTENSION
import com.tencent.bkrepo.helm.constants.DATA_TIME_FORMATTER
import com.tencent.bkrepo.helm.constants.INDEX_CACHE_YAML
import com.tencent.bkrepo.helm.constants.PROVENANCE_FILE_EXTENSION
import com.tencent.bkrepo.helm.constants.V1
import com.tencent.bkrepo.helm.model.metadata.HelmIndexYamlMetadata
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object HelmUtils {

    fun getChartFileFullPath(name: String, version: String): String {
        return String.format("/%s-%s.%s", name, version, CHART_PACKAGE_FILE_EXTENSION)
    }

    fun getProvFileFullPath(name: String, version: String): String {
        return String.format("/%s-%s.%s", name, version, PROVENANCE_FILE_EXTENSION)
    }

    fun getIndexYamlFullPath():String{
        return "/$INDEX_CACHE_YAML"
    }

    fun initIndexYamlMetadata(): HelmIndexYamlMetadata {
        return HelmIndexYamlMetadata(
            apiVersion = V1,
            generated = LocalDateTime.now().format(DateTimeFormatter.ofPattern(DATA_TIME_FORMATTER))
        )
    }
}