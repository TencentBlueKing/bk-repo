/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.pypi.artifact.repository

import com.tencent.bk.audit.annotations.ActionAuditRecord
import com.tencent.bk.audit.annotations.AuditAttribute
import com.tencent.bk.audit.annotations.AuditEntry
import com.tencent.bk.audit.annotations.AuditInstanceRecord
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.audit.ActionAuditContent
import com.tencent.bkrepo.common.artifact.audit.NODE_DELETE_ACTION
import com.tencent.bkrepo.common.artifact.audit.NODE_RESOURCE
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.resolve.file.multipart.MultipartArtifactFile
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.metadata.service.packages.StageService
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.query.model.Sort
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.pypi.artifact.PypiSimpleArtifactInfo
import com.tencent.bkrepo.pypi.artifact.url.UrlPatternUtil.parameterMaps
import com.tencent.bkrepo.pypi.artifact.xml.Value
import com.tencent.bkrepo.pypi.artifact.xml.XmlUtil
import com.tencent.bkrepo.pypi.constants.SUMMARY
import com.tencent.bkrepo.pypi.service.PypiSimpleIndexService
import com.tencent.bkrepo.pypi.pojo.Basic
import com.tencent.bkrepo.pypi.pojo.PypiArtifactVersionData
import com.tencent.bkrepo.pypi.util.PypiVersionUtils.toPypiPackagePojo
import com.tencent.bkrepo.pypi.util.XmlUtils
import com.tencent.bkrepo.pypi.util.XmlUtils.readXml
import com.tencent.bkrepo.repository.pojo.download.PackageDownloadRecord
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.packages.PackageType
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class PypiLocalRepository(
    private val stageService: StageService,
    private val pypiSimpleIndexService: PypiSimpleIndexService,
) : LocalRepository() {

    /**
     * 获取PYPI节点创建请求
     */
    override fun buildNodeCreateRequest(context: ArtifactUploadContext): NodeCreateRequest {
        val repositoryDetail = context.repositoryDetail
        val artifactFile = context.getArtifactFile("content")
        val metadata = context.request.parameterMaps().map { MetadataModel(key = it.key, value = it.value) }
        val filename = (artifactFile as MultipartArtifactFile).getOriginalFilename()
        val sha256 = artifactFile.getFileSha256()
        val md5 = artifactFile.getFileMd5()
        val name: String = context.request.getParameter("name")
        val version: String = context.request.getParameter("version")
        val artifactFullPath = "/$name/$version/$filename"

        return NodeCreateRequest(
            projectId = repositoryDetail.projectId,
            repoName = repositoryDetail.name,
            folder = false,
            overwrite = true,
            fullPath = artifactFullPath,
            size = artifactFile.getSize(),
            sha256 = sha256,
            md5 = md5,
            crc64ecma = artifactFile.getFileCrc64ecma(),
            operator = context.userId,
            nodeMetadata = metadata
        )
    }

    override fun onUpload(context: ArtifactUploadContext) {
        val nodeCreateRequest = buildNodeCreateRequest(context)
        val artifactFile = context.getArtifactFile("content")
        val name: String = context.request.getParameter("name")
        val version: String = context.request.getParameter("version")
        packageService.createPackageVersion(
            PackageVersionCreateRequest(
                projectId = context.projectId,
                repoName = context.repoName,
                packageName = name,
                packageKey = PackageKeys.ofPypi(name),
                packageType = PackageType.PYPI,
                versionName = version,
                size = context.getArtifactFile("content").getSize(),
                artifactPath = nodeCreateRequest.fullPath,
                overwrite = true,
                createdBy = context.userId,
                packageDescription = context.request.getParameter(SUMMARY)?.ifBlank { null },
                multiArtifact = true
            ),
            HttpContextHolder.getClientAddress()
        )
        store(nodeCreateRequest, artifactFile, context.storageCredentials)
        pypiSimpleIndexService.refreshAfterUpload(context.projectId, context.repoName, name, context.userId)
    }

    override fun onDownloadBefore(context: ArtifactDownloadContext) {
        super.onDownloadBefore(context)
        packageVersion(context)?.let { downloadIntercept(context, it) }
    }

    private fun combineSameParamQuery(entry: Map.Entry<String, List<String>>): Rule.NestedRule {
        val sameParamQueryList = mutableListOf<Rule>()
        for (value in entry.value) {
            sameParamQueryList.add(
                Rule.QueryRule("metadata.${entry.key}", "*$value*", OperationType.MATCH_I)
            )
        }
        return Rule.NestedRule(sameParamQueryList, Rule.NestedRule.RelationType.OR)
    }

    private fun combineParamQuery(
        map: Map<String, List<String>>,
        paramQueryList: MutableList<Rule>,
        operation: String
    ): Rule.NestedRule {
        for (param in map) {
            if (param.value.isNullOrEmpty()) continue
            if (param.value.size == 1) {
                paramQueryList.add(
                    Rule.QueryRule("metadata.${param.key}", "*${param.value[0]}*", OperationType.MATCH_I)
                )
            } else if (param.value.size > 1) {
                // 同属性值固定为`or` 参考：https://warehouse.readthedocs.io/api-reference/xml-rpc.html#
                // Within the spec, a field’s value can be a string or a list of strings
                // (the values within the list are combined with an OR)
                paramQueryList.add(combineSameParamQuery(param))
            }
        }
        val relationType = when (operation) {
            "or" -> Rule.NestedRule.RelationType.OR
            "and" -> Rule.NestedRule.RelationType.AND
            else -> Rule.NestedRule.RelationType.OR
        }
        return Rule.NestedRule(paramQueryList, relationType)
    }

    /**
     * pypi search
     */
    override fun search(context: ArtifactSearchContext): List<Value> {
        val pypiSearchPojo = XmlUtils.getPypiSearchPojo(context.request.reader.readXml())
        val projectId = Rule.QueryRule("projectId", context.projectId)
        val repoName = Rule.QueryRule("repoName", context.repoName)
        val filetypeQuery = Rule.QueryRule("metadata.filetype", "bdist_wheel")
        val paramQueryList = mutableListOf<Rule>()
        val paramQuery = if (pypiSearchPojo.map.isNotEmpty()) {
            combineParamQuery(pypiSearchPojo.map, paramQueryList, pypiSearchPojo.operation)
        } else Rule.NestedRule(paramQueryList)
        val rule = if (paramQueryList.isNotEmpty()) {
            Rule.NestedRule(
                mutableListOf(projectId, repoName, filetypeQuery, paramQuery), Rule.NestedRule.RelationType.AND
            )
        } else {
            Rule.NestedRule(
                mutableListOf(projectId, repoName, filetypeQuery), Rule.NestedRule.RelationType.AND
            )
        }
        val queryModel = QueryModel(
            page = PageLimit(pageLimitCurrent, pageLimitSize),
            sort = Sort(listOf("name"), Sort.Direction.ASC),
            select = mutableListOf("projectId", "repoName", "fullPath", "metadata"),
            rule = rule
        )
        val nodeList: List<Map<String, Any?>>? = nodeSearchService.searchWithoutCount(queryModel).records
        if (nodeList != null) {
            return XmlUtil.nodeLis2Values(nodeList)
        }
        return mutableListOf()
    }

    /**
     * pypi 产品删除接口
     */
    @AuditEntry(
        actionId = NODE_DELETE_ACTION
    )
    @ActionAuditRecord(
        actionId = NODE_DELETE_ACTION,
        instance = AuditInstanceRecord(
            resourceType = NODE_RESOURCE,
            instanceIds = "#artifactFullPath",
            instanceNames = "#artifactFullPath"
        ),
        attributes = [
            AuditAttribute(name = ActionAuditContent.PROJECT_CODE_TEMPLATE, value = "#context?.projectId"),
            AuditAttribute(name = ActionAuditContent.REPO_NAME_TEMPLATE, value = "#context?.repoName")
        ],
        scopeId = "#context?.projectId",
        content = ActionAuditContent.NODE_DELETE_CONTENT
    )
    override fun remove(context: ArtifactRemoveContext) {
        val packageKey = HttpContextHolder.getRequest().getParameter("packageKey")
        val name = PackageKeys.resolvePypi(packageKey)
        val version = HttpContextHolder.getRequest().getParameter("version")
        val contentPath = HttpContextHolder.getRequest().getParameter("contentPath")
        if (version.isNullOrBlank()) {
            // 删除包
            nodeService.deleteNode(
                NodeDeleteRequest(
                    context.projectId,
                    context.repoName,
                    "/$name",
                    context.userId
                )
            )
            packageService.deletePackage(
                context.projectId,
                context.repoName,
                packageKey,
                HttpContextHolder.getClientAddress()
            )
            pypiSimpleIndexService.refreshAfterRemove(
                context.projectId, context.repoName, name, deleteWholePackage = true, context.userId
            )
        } else {
            // 删除版本
            nodeService.deleteNode(
                NodeDeleteRequest(
                    context.projectId,
                    context.repoName,
                    contentPath ?: "/$name/$version",
                    context.userId
                )
            )
            packageService.deleteVersion(
                context.projectId,
                context.repoName,
                packageKey,
                version,
                HttpContextHolder.getClientAddress(),
                contentPath,
            )
            pypiSimpleIndexService.refreshAfterRemove(
                context.projectId, context.repoName, name, deleteWholePackage = false, context.userId
            )
        }
    }

    /**
     * 1，pypi 产品 版本详情
     * 2，pypi simple html页面
     */
    override fun query(context: ArtifactQueryContext): Any? {
        return when (val artifactInfo = context.artifactInfo) {
            is PypiSimpleArtifactInfo -> getSimpleHtml(artifactInfo)
            else -> getVersionDetail(context)
        }
    }

    // TODO 产品页面需要重新设计，支持同个版本包含多个制品
    fun getVersionDetail(context: ArtifactQueryContext): PypiArtifactVersionData? {
        val packageKey = context.request.getParameter("packageKey")
        val version = context.request.getParameter("version")
        logger.info("Get version detail. packageKey[$packageKey], version[$version]")
        val name = PackageKeys.resolvePypi(packageKey)
        val trueVersion = packageService.findVersionByName(
            context.projectId,
            context.repoName,
            packageKey,
            version
        ) ?: return null
        val artifactPath = trueVersion.contentPaths?.firstOrNull() ?: trueVersion.contentPath ?: return null
        with(context.artifactInfo) {
            val node = nodeService.getNodeDetail(
                ArtifactInfo(projectId, repoName, artifactPath)
            ) ?: return null
            val stageTag = stageService.query(projectId, repoName, packageKey, version)
            val packageVersion = packageService.findVersionByName(
                projectId, repoName, packageKey, version
            )
            val count = packageVersion?.downloads ?: 0
            val pypiArtifactBasic = Basic(
                name = name,
                version = version,
                size = node.size,
                fullPath = node.fullPath,
                createdBy = node.createdBy,
                createdDate = node.createdDate,
                lastModifiedBy = node.lastModifiedBy,
                lastModifiedDate = node.lastModifiedDate,
                downloadCount = count,
                sha256 = node.sha256,
                md5 = node.md5,
                stageTag = stageTag,
                description = null,
                federatedSource = packageVersion?.federatedSource,
            )
            return PypiArtifactVersionData(pypiArtifactBasic, packageVersion?.packageMetadata)
        }
    }

    fun getSimpleHtml(artifactInfo: PypiSimpleArtifactInfo): String {
        logger.info("Get simple html, artifactInfo: $artifactInfo")
        return pypiSimpleIndexService.getSimpleHtml(artifactInfo)
    }

    fun store(node: NodeCreateRequest, artifactFile: ArtifactFile, storageCredentials: StorageCredentials?) {
        storageManager.storeArtifactFile(node, artifactFile, storageCredentials)
        artifactFile.delete()
        with(node) { logger.info("Success to store$projectId/$repoName/$fullPath") }
        logger.info("Success to insert $node")
    }

    // pypi 客户端下载统计
    override fun buildDownloadRecord(
        context: ArtifactDownloadContext,
        artifactResource: ArtifactResource
    ): PackageDownloadRecord? {
        with(context) {
            val fullPath = context.artifactInfo.getArtifactFullPath()
            val pypiPackagePojo = fullPath.toPypiPackagePojo()
            val packageKey = PackageKeys.ofPypi(pypiPackagePojo.name)
            return PackageDownloadRecord(
                projectId, repoName,
                packageKey, pypiPackagePojo.version
            )
        }
    }

    private fun packageVersion(context: ArtifactDownloadContext): PackageVersion? {
        with(context) {
            val pypiPackagePojo = try {
                artifactInfo.getArtifactFullPath().toPypiPackagePojo()
            } catch (e: Exception) {
                logger.info("parse path[${artifactInfo.getArtifactFullPath()}] to pypi package version failed: ", e)
                null
            } ?: return null

            val packageKey = PackageKeys.ofPypi(pypiPackagePojo.name)
            return packageService.findVersionByName(projectId, repoName, packageKey, pypiPackagePojo.version)
        }
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(PypiLocalRepository::class.java)
        const val pageLimitCurrent = 0
        const val pageLimitSize = 10
    }
}
