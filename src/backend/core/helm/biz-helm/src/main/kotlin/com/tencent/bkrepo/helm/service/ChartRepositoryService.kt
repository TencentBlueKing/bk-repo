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

package com.tencent.bkrepo.helm.service

import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.helm.pojo.HelmDomainInfo
import com.tencent.bkrepo.helm.pojo.artifact.HelmArtifactInfo
import com.tencent.bkrepo.helm.pojo.metadata.HelmIndexYamlMetadata
import com.tencent.bkrepo.helm.pojo.user.PackageVersionInfo
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestParam
import java.time.LocalDateTime

interface ChartRepositoryService {

    /**
     * 查看chart列表
     */
    fun allChartsList(artifactInfo: HelmArtifactInfo, startTime: LocalDateTime? = null): ResponseEntity<Any>

    /**
     * 查看chart是否存在
     */
    fun isExists(artifactInfo: HelmArtifactInfo)

    /**
     * 查询版本详情
     */
    fun detailVersion(
        userId: String,
        artifactInfo: HelmArtifactInfo,
        packageKey: String,
        version: String
    ): PackageVersionInfo

    /**
     * 获取helm域名信息
     */
    fun getRegistryDomain(): HelmDomainInfo

    /**
     * get index.yaml
     */
    fun queryIndexYaml(@ArtifactPathVariable artifactInfo: HelmArtifactInfo)

    /**
     * install chart
     */
    fun installTgz(@ArtifactPathVariable artifactInfo: HelmArtifactInfo)

    /**
     * install prov
     */
    fun installProv(@ArtifactPathVariable artifactInfo: HelmArtifactInfo)

    /**
     * regenerate index.yaml
     */
    fun regenerateIndexYaml(@ArtifactPathVariable artifactInfo: HelmArtifactInfo, v1Flag: Boolean)

    /**
     * updatePackageForRemote
     */
    fun updatePackageForRemote(@ArtifactPathVariable artifactInfo: HelmArtifactInfo)

    /**
     * batch install chart
     */
    fun batchInstallTgz(@ArtifactPathVariable artifactInfo: HelmArtifactInfo, @RequestParam startTime: LocalDateTime)

    /**
     * fresh index yaml file
     */
    fun freshIndexFile(artifactInfo: HelmArtifactInfo)

    /**
     * 构造indexYaml元数据，[result]为自定义查询出来的node节点, [isInit] 是否需要初始化indexYaml元数据
     */
    fun buildIndexYamlMetadata(
        result: List<Map<String, Any?>>,
        artifactInfo: HelmArtifactInfo,
        isInit: Boolean = false
    ): HelmIndexYamlMetadata
}
