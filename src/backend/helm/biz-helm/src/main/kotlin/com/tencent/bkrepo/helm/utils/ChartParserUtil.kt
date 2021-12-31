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

import com.tencent.bkrepo.common.api.util.readYamlString
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactFileMap
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContext
import com.tencent.bkrepo.common.artifact.resolve.file.multipart.MultipartArtifactFile
import com.tencent.bkrepo.helm.constants.CHART
import com.tencent.bkrepo.helm.constants.CHART_PACKAGE_FILE_EXTENSION
import com.tencent.bkrepo.helm.constants.FULL_PATH
import com.tencent.bkrepo.helm.constants.META_DETAIL
import com.tencent.bkrepo.helm.constants.NAME
import com.tencent.bkrepo.helm.constants.PROV
import com.tencent.bkrepo.helm.constants.VERSION
import com.tencent.bkrepo.helm.exception.HelmErrorInvalidProvenanceFileException
import com.tencent.bkrepo.helm.pojo.metadata.HelmChartMetadata
import com.tencent.bkrepo.helm.utils.DecompressUtil.getArchivesContent
import java.io.InputStream

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
            throw HelmErrorInvalidProvenanceFileException("invalid provenance file")
        }
        return Pair(nameMatch[1], versionMatch[1])
    }

    fun parseNameAndVersion(context: ArtifactContext) {
        val fullPath = context.getStringAttribute(FULL_PATH)
        fullPath?.let {
            parseNameAndVersion(fullPath)[NAME]?.let { it1 -> context.putAttribute(NAME, it1) }
            parseNameAndVersion(fullPath)[VERSION]?.let { it1 -> context.putAttribute(VERSION, it1) }
            parseNameAndVersion(fullPath).let { it1 -> context.putAttribute(META_DETAIL, it1) }
        }
    }

    fun parseNameAndVersion(fullPath: String): Map<String, Any> {
        val substring = fullPath.trimStart('/').substring(0, fullPath.lastIndexOf('.') - 1)
        val name = substring.substringBeforeLast('-')
        val version = substring.substringAfterLast('-')
        return mapOf("name" to name, "version" to version)
    }
}
