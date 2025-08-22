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

import com.tencent.bkrepo.common.api.util.readYamlString
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactFileMap
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContext
import com.tencent.bkrepo.common.artifact.resolve.file.multipart.MultipartArtifactFile
import com.tencent.bkrepo.common.artifact.util.FileNameParser
import com.tencent.bkrepo.helm.constants.CHART
import com.tencent.bkrepo.helm.constants.CHART_PACKAGE_FILE_EXTENSION
import com.tencent.bkrepo.helm.constants.FULL_PATH
import com.tencent.bkrepo.helm.constants.HelmMessageCode
import com.tencent.bkrepo.helm.constants.META_DETAIL
import com.tencent.bkrepo.helm.constants.NAME
import com.tencent.bkrepo.helm.constants.PROV
import com.tencent.bkrepo.helm.constants.VERSION
import com.tencent.bkrepo.helm.exception.HelmErrorInvalidProvenanceFileException
import com.tencent.bkrepo.helm.exception.HelmFileNotFoundException
import com.tencent.bkrepo.helm.pojo.metadata.HelmChartMetadata
import com.tencent.bkrepo.helm.pojo.metadata.HelmIndexYamlMetadata
import com.tencent.bkrepo.helm.utils.DecompressUtil.getArchivesContent
import org.apache.logging.log4j.util.Strings
import java.io.InputStream
import java.time.LocalDateTime
import java.util.SortedSet

object ChartParserUtil {

    fun parseChartFileInfo(artifactFileMap: ArtifactFileMap): HelmChartMetadata {
        return parseChartFileInfo(artifactFileMap[CHART] as MultipartArtifactFile)
    }

    fun parseChartFileInfo(artifactFile: ArtifactFile): HelmChartMetadata {
        val inputStream = artifactFile.getInputStream()
        return parseChartInputStream(inputStream)
    }

    fun parseChartInputStream(inputStream: InputStream): HelmChartMetadata {
        val result = inputStream.getArchivesContent(CHART_PACKAGE_FILE_EXTENSION)
        return result.byteInputStream().readYamlString()
    }

    fun parseProvFileInfo(artifactFileMap: ArtifactFileMap): Pair<String, String> {
        return parseProvFileInfo(artifactFileMap[PROV] as MultipartArtifactFile)
    }

    fun parseProvFileInfo(artifactFile: ArtifactFile): Pair<String, String> {
        val inputStream = artifactFile.getInputStream()
        return parseProvInputStream(inputStream)
    }

    fun parseProvInputStream(inputStream: InputStream): Pair<String, String> {
        val contentStr = String(inputStream.readBytes())
        val hasPGPBegin = contentStr.startsWith("-----BEGIN PGP SIGNED MESSAGE-----")
        val nameMatch = Regex("\nname:[ *](.+)").findAll(contentStr).toList().flatMap(MatchResult::groupValues)
        val versionMatch = Regex("\nversion:[ *](.+)").findAll(contentStr).toList().flatMap(MatchResult::groupValues)
        if (!hasPGPBegin || nameMatch.size != 2 || versionMatch.size != 2) {
            throw HelmErrorInvalidProvenanceFileException(HelmMessageCode.INVALID_PROVENANCE_FILE, Strings.EMPTY)
        }
        return Pair(nameMatch[1], versionMatch[1])
    }

    fun parseNameAndVersion(context: ArtifactContext) {
        val fullPath = context.getStringAttribute(FULL_PATH)
        fullPath?.let {
            FileNameParser.parseNameAndVersionWithRegex(fullPath)[NAME]
                ?.let { it1 -> context.putAttribute(NAME, it1) }
            FileNameParser.parseNameAndVersionWithRegex(fullPath)[VERSION]
                ?.let { it1 -> context.putAttribute(VERSION, it1) }
            FileNameParser.parseNameAndVersionWithRegex(fullPath)
                .let { it1 -> context.putAttribute(META_DETAIL, it1) }
        }
    }

    /**
     * 将新增加的Chart包信息加入到index.yaml中
     */
    fun addIndexEntries(indexYamlMetadata: HelmIndexYamlMetadata, chartMetadata: HelmChartMetadata) {
        val chartName = chartMetadata.name
        val chartVersion = chartMetadata.version
        val isFirstChart = !indexYamlMetadata.entries.containsKey(chartMetadata.name)
        indexYamlMetadata.entries.let {
            if (isFirstChart) {
                it[chartMetadata.name] = sortedSetOf(chartMetadata)
            } else {
                // force upload
                run stop@{
                    it[chartName]?.forEachIndexed { _, helmChartMetadata ->
                        if (chartVersion == helmChartMetadata.version) {
                            it[chartName]?.remove(helmChartMetadata)
                            return@stop
                        }
                    }
                }
                it[chartName]?.add(chartMetadata)
            }
        }
    }

    /**
     * 根据创建时间过滤chart信息
     */
    fun filterByCreateTime(
        indexYamlMetadata: HelmIndexYamlMetadata,
        startTime: LocalDateTime = LocalDateTime.MIN
    ): Map<String, SortedSet<HelmChartMetadata>> {
        with(indexYamlMetadata) {
            when (startTime) {
                LocalDateTime.MIN -> entries
                else -> {
                    val nonMatchingPredicate: (Int, HelmChartMetadata) -> Boolean =
                        { _, it -> compareTime(startTime, it.created) }
                    entries.values.forEachIndexed { _, list ->
                        list.removeAll(list.filterIndexed(nonMatchingPredicate))
                    }
                }
            }
            return convertUtcTime(entries)
        }
    }

    /**
     * 比较两个时间大小
     */
    private fun compareTime(startTime: LocalDateTime, createTime: String?): Boolean {
        createTime?.let {
            return startTime.isAfter(TimeFormatUtil.convertToLocalTime(it))
        }
        return false
    }

    /**
     * 根据名字从index.yaml中找出对应chart信息，可能会包含多个版本
     */
    fun filterChart(
        indexYamlMetadata: HelmIndexYamlMetadata,
        startTime: LocalDateTime = LocalDateTime.MIN,
        name: String,
        version: String? = null
    ): Any {
        val chartList = indexYamlMetadata.entries[name]
        chartList?.let {
            when (startTime) {
                LocalDateTime.MIN -> chartList
                else -> {
                    val nonMatchingPredicate: (Int, HelmChartMetadata) -> Boolean =
                        { _, chart -> compareTime(startTime, chart.created) }
                    chartList.removeAll(chartList.filterIndexed(nonMatchingPredicate))
                }
            }
            version?.let {
                return filterByVersion(chartList, version, startTime)
            }
            chartList.forEach { convertUtcTime(it) }
        }
        return chartList ?:
        throw HelmFileNotFoundException(
            HelmMessageCode.HELM_FILE_NOT_FOUND, "$name|$version", Strings.EMPTY
        )
    }

    /**
     * 根据名字-版本从index.yaml中找出对应chart信息
     */
    private fun filterByVersion(
        chartList: SortedSet<HelmChartMetadata>,
        chartVersion: String,
        startTime: LocalDateTime = LocalDateTime.MIN
    ): HelmChartMetadata {
        val helmChartMetadataList = chartList.filter {
            chartVersion == it.version
        }.toList()
        return if (helmChartMetadataList.isNotEmpty()) {
            require(helmChartMetadataList.size == 1) {
                "find more than one version [$chartVersion] in package."
            }
            when (startTime) {
                LocalDateTime.MIN -> convertUtcTime(helmChartMetadataList.first())
                else -> {
                    if (!compareTime(startTime, helmChartMetadataList.first().created)) {
                        convertUtcTime(helmChartMetadataList.first())
                    } else {
                        throw HelmFileNotFoundException(
                            HelmMessageCode.HELM_FILE_NOT_FOUND, chartVersion, Strings.EMPTY
                        )
                    }
                }
            }
        } else {
            throw HelmFileNotFoundException(HelmMessageCode.HELM_FILE_NOT_FOUND, chartVersion, Strings.EMPTY)
        }
    }

    /**
     * 查询对应的chart信息
     */
    fun searchJson(
        indexYamlMetadata: HelmIndexYamlMetadata,
        urls: String,
        startTime: LocalDateTime = LocalDateTime.MIN
    ): Any {
        val urlList = urls.removePrefix("/").split("/").filter { it.isNotBlank() }
        when (urlList.size) {
            // Without name and version
            0 -> {
                return filterByCreateTime(indexYamlMetadata, startTime)
            }
            // query with name
            1 -> {
                return filterChart(indexYamlMetadata, startTime, urlList[0])
            }
            // query with name and version
            2 -> {
                return filterChart(indexYamlMetadata, startTime, urlList[0], urlList[1])
            }
            else -> {
                // ERROR_NOT_FOUND
                throw HelmFileNotFoundException(HelmMessageCode.HELM_FILE_NOT_FOUND, urls, Strings.EMPTY)
            }
        }
    }

    /**
     * 比较新旧两个index文件中的chart包差异
     */
    fun compareIndexYamlMetadata(
        oldEntries: MutableMap<String, SortedSet<HelmChartMetadata>>,
        newEntries: MutableMap<String, SortedSet<HelmChartMetadata>>
    ): Pair<MutableMap<String, SortedSet<HelmChartMetadata>>, MutableMap<String, SortedSet<HelmChartMetadata>>> {
        // 初次创建时需要将所有index中的全部存储
        if (oldEntries.isEmpty()) {
            return Pair(mutableMapOf(), newEntries)
        }
        val deletedMetadata: MutableMap<String, SortedSet<HelmChartMetadata>> = mutableMapOf()
        val addedMetadata: MutableMap<String, SortedSet<HelmChartMetadata>> = mutableMapOf()
        // 旧index中存在，新index不存在chart的需要删除
        deletedMetadata.putAll(oldEntries.minus(newEntries.keys))
        // 新index中存在，旧index不存在的chart需要新增
        addedMetadata.putAll(newEntries.minus(oldEntries.keys))

        // 针对同一个chart下不同版本差异处理
        oldEntries.forEach { index ->
            if (!newEntries.containsKey(index.key)) {
                return@forEach
            }
            val newSet: SortedSet<HelmChartMetadata>? = newEntries[index.key]
            val oldSet: SortedSet<HelmChartMetadata>? = oldEntries[index.key]
            if (oldSet.isNullOrEmpty()) {
                newSet?.let {
                    addedMetadata[index.key] = it
                }
            } else {
                if (newSet.isNullOrEmpty()) {
                    deletedMetadata[index.key] = oldSet
                } else {
                    val deletedSet = oldSet.minus(newSet)
                    val addedSet = newSet.minus(oldSet)
                    deletedMetadata[index.key] = deletedSet.toSortedSet()
                    addedMetadata[index.key] = addedSet.toSortedSet()
                }
            }
        }
        return Pair(deletedMetadata, addedMetadata)
    }

    fun convertUtcTime(indexYamlMetadata: HelmIndexYamlMetadata): HelmIndexYamlMetadata {
        convertUtcTime(indexYamlMetadata.entries)
        return indexYamlMetadata
    }

    fun convertUtcTime(helmChartMetadata: HelmChartMetadata): HelmChartMetadata {
        helmChartMetadata.created?.let {
            helmChartMetadata.created = TimeFormatUtil.formatLocalTime(TimeFormatUtil.convertToLocalTime(it))
        }
        return helmChartMetadata
    }

    fun convertUtcTime(entries: Map<String, SortedSet<HelmChartMetadata>>):
        Map<String, SortedSet<HelmChartMetadata>> {
        entries.forEach {
            val chartMetadataSet = it.value
            chartMetadataSet.forEach { chartMetadata ->
                convertUtcTime(chartMetadata)
            }
        }
        return entries
    }
}
