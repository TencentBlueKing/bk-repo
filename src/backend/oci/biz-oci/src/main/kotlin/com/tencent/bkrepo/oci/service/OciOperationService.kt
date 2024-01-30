/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.oci.service

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactChannel
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.pojo.FileInfo
import com.tencent.bkrepo.oci.pojo.artifact.OciArtifactInfo
import com.tencent.bkrepo.oci.pojo.artifact.OciManifestArtifactInfo
import com.tencent.bkrepo.oci.pojo.digest.OciDigest
import com.tencent.bkrepo.oci.pojo.node.NodeProperty
import com.tencent.bkrepo.oci.pojo.response.OciImageResult
import com.tencent.bkrepo.oci.pojo.response.OciTagResult
import com.tencent.bkrepo.oci.pojo.user.PackageVersionInfo
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import javax.servlet.http.HttpServletRequest

interface OciOperationService {

    /**
     * 查询包版本详情
     */
    fun detailVersion(
        userId: String,
        artifactInfo: OciArtifactInfo,
        packageKey: String,
        version: String
    ): PackageVersionInfo

    /**
     * 删除包
     */
    fun deletePackage(userId: String, artifactInfo: OciArtifactInfo)

    /**
     * 删除包对应版本
     */
    fun deleteVersion(userId: String, artifactInfo: OciArtifactInfo)

    /**
     * 获取对应域名
     */
    fun getRegistryDomain(): String

    /**
     * 更新整个blob相关信息,blob相关的mediatype，version等信息需要从manifest中获取
     */
    fun updateOciInfo(
        ociArtifactInfo: OciManifestArtifactInfo,
        digest: OciDigest,
        nodeDetail: NodeDetail,
        storageCredentials: StorageCredentials?,
        sourceType: ArtifactChannel? = null
    )

    /**
     * 在先上传manifest.json，后上传blob的情况下，等所有blob上传成功后更新对应的package
     */
    fun createPackageForThirdPartyImage(
        ociArtifactInfo: OciManifestArtifactInfo,
        manifestPath: String,
    ): Boolean

    /**
     * 保存文件内容(当使用追加上传时，文件已存储，只需存储节点信息)
     * 特殊：对于manifest文件，node存tag
     */
    fun storeArtifact(
        ociArtifactInfo: OciArtifactInfo,
        artifactFile: ArtifactFile,
        storageCredentials: StorageCredentials?,
        fileInfo: FileInfo? = null,
        proxyUrl: String? = null
    ): NodeDetail?

    /**
     * 获取对应存储节点路径
     * 特殊：manifest文件按tag存储， 但是查询时存在tag/digest
     */
    fun getNodeFullPath(artifactInfo: OciArtifactInfo): String?

    /**
     * 根据sha256值和path获取对应的node fullpath,md5,size
     */
    fun getNodeByDigest(
        projectId: String,
        repoName: String,
        digestStr: String,
        path: String? = null
    ): NodeProperty?

    /**
     * 针对老的docker仓库的数据做兼容性处理
     * 老版数据node存储格式不一样：
     * 1 docker-local/nginx/latest 下存所有manifest和blobs
     * 2 docker-local/nginx/_uploads/ 临时存储上传的blobs，待manifest文件上传成功后移到到对应版本下，如docker-local/nginx/latest
     */
    fun getDockerNode(artifactInfo: OciArtifactInfo): String?

    /**
     * 根据request生成response url
     */
    fun getReturnDomain(request: HttpServletRequest): String

    /**
     * 获取对应的image的manifest文件内容
     */
    fun getManifest(artifactInfo: OciManifestArtifactInfo): String

    /**
     * 获取指定projectId和repoName下的所有镜像
     */
    fun getImageList(
        projectId: String,
        repoName: String,
        pageNumber: Int,
        pageSize: Int,
        name: String?
    ): OciImageResult

    /**
     * 获取repo的所有tag
     */
    fun getRepoTag(
        projectId: String,
        repoName: String,
        pageNumber: Int,
        packageName: String,
        pageSize: Int,
        tag: String?
    ): OciTagResult

    /**
     * 拉取第三方镜像仓库package信息
     */
    fun getPackagesFromThirdPartyRepo(projectId: String, repoName: String)

    /**
     * oci blob 路径调整，由/packageName/blobs/XXX -> /packageName/blobs/version/XXX
     */
    fun refreshBlobNode(
        projectId: String,
        repoName: String,
        pName: String,
        pVersion: String,
        userId: String = SecurityUtils.getUserId()
    ): Boolean


    /**
     * 当package下所有版本的blob路径都刷新到新的路径下后，删除/packageName/blobs目录
     */
    fun deleteBlobsFolderAfterRefreshed(
        projectId: String,
        repoName: String,
        pName: String,
        userId: String = SecurityUtils.getUserId()
    )
}
