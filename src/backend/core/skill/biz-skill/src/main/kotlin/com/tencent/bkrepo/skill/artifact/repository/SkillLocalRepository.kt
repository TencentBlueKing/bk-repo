/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.skill.artifact.repository

import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import com.tencent.bkrepo.common.api.exception.MethodNotAllowedException
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.artifact.exception.NodeNotFoundException
import com.tencent.bkrepo.common.artifact.exception.PackageNotFoundException
import com.tencent.bkrepo.common.artifact.exception.VersionConflictException
import com.tencent.bkrepo.common.artifact.exception.VersionNotFoundException
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactChannel
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.stream.artifactStream
import com.tencent.bkrepo.common.metadata.model.TPackage
import com.tencent.bkrepo.common.metadata.model.TPackageVersion
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.repository.constant.NAME
import com.tencent.bkrepo.repository.pojo.download.PackageDownloadRecord
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.packages.VersionListOption
import com.tencent.bkrepo.repository.pojo.search.PackageQueryBuilder
import com.tencent.bkrepo.skill.constant.DOWNLOADS
import com.tencent.bkrepo.skill.constant.KEY_FINGERPRINT
import com.tencent.bkrepo.skill.constant.FIELD_CHANGELOG
import com.tencent.bkrepo.skill.constant.FIELD_DISPLAY_NAME
import com.tencent.bkrepo.skill.constant.KEY_FILE_LIST
import com.tencent.bkrepo.skill.constant.LATEST
import com.tencent.bkrepo.skill.constant.MODERATION_VERDICT_CLEAN
import com.tencent.bkrepo.skill.constant.SORT_CREATED
import com.tencent.bkrepo.skill.constant.SORT_DOWNLOADS
import com.tencent.bkrepo.skill.constant.SORT_NEWEST
import com.tencent.bkrepo.skill.constant.VERSIONS
import com.tencent.bkrepo.skill.pojo.artifact.ClawHubArchiveDownloadInfo
import com.tencent.bkrepo.skill.pojo.artifact.ClawHubFileDownloadInfo
import com.tencent.bkrepo.skill.pojo.artifact.ClawHubPublishInfo
import com.tencent.bkrepo.skill.pojo.artifact.ClawHubResolveInfo
import com.tencent.bkrepo.skill.pojo.artifact.ClawHubSearchInfo
import com.tencent.bkrepo.skill.pojo.artifact.ClawHubSkillInfo
import com.tencent.bkrepo.skill.pojo.artifact.ClawHubSkillListInfo
import com.tencent.bkrepo.skill.pojo.artifact.ClawHubSkillModerationInfo
import com.tencent.bkrepo.skill.pojo.artifact.ClawHubSkillVersionInfo
import com.tencent.bkrepo.skill.pojo.artifact.ClawHubSkillVersionListInfo
import com.tencent.bkrepo.skill.pojo.artifact.SkillExtInfo
import com.tencent.bkrepo.skill.pojo.response.ClawHubModeration
import com.tencent.bkrepo.skill.pojo.response.ClawHubModerationResponse
import com.tencent.bkrepo.skill.pojo.response.ClawHubResolveResponse
import com.tencent.bkrepo.skill.pojo.response.ClawHubResolveVersion
import com.tencent.bkrepo.skill.pojo.response.ClawHubSearchResultItem
import com.tencent.bkrepo.skill.pojo.response.ClawHubSkill
import com.tencent.bkrepo.skill.pojo.response.ClawHubSkillBasicInfo
import com.tencent.bkrepo.skill.pojo.response.ClawHubSkillDetailResponse
import com.tencent.bkrepo.skill.pojo.response.ClawHubSkillListResponse
import com.tencent.bkrepo.skill.pojo.response.ClawHubSkillVersion
import com.tencent.bkrepo.skill.pojo.response.ClawHubSkillVersionDetail
import com.tencent.bkrepo.skill.pojo.response.ClawHubSkillVersionDetailResponse
import com.tencent.bkrepo.skill.pojo.response.ClawHubSkillVersionListResponse
import com.tencent.bkrepo.skill.pojo.response.ClawHubUser
import com.tencent.bkrepo.skill.pojo.response.SkillVersionItem
import com.tencent.bkrepo.skill.service.SkillPackageService
import com.tencent.bkrepo.skill.util.SkillUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.ZoneOffset
import java.util.Date
import kotlin.collections.get

@Component
class SkillLocalRepository(
    private val skillPackageService: SkillPackageService,
) : LocalRepository() {

    override fun query(context: ArtifactQueryContext): Any? {
        return when (context.artifactInfo) {
            is ClawHubSkillListInfo -> listSkills(context.artifactInfo as ClawHubSkillListInfo)
            is ClawHubSkillInfo -> getSkillDetail(context.artifactInfo as ClawHubSkillInfo)
            is ClawHubSkillVersionListInfo -> listSkillVersions(context.artifactInfo as ClawHubSkillVersionListInfo)
            is ClawHubSkillVersionInfo -> getSkillVersionDetail(context.artifactInfo as ClawHubSkillVersionInfo)
            is ClawHubResolveInfo -> resolveSkill(context.artifactInfo as ClawHubResolveInfo)
            is ClawHubSkillModerationInfo -> getSkillModeration(context.artifactInfo as ClawHubSkillModerationInfo)
            else -> throw MethodNotAllowedException()
        }
    }

    override fun onUploadBefore(context: ArtifactUploadContext) {
        with(context.artifactInfo as ClawHubPublishInfo) {
            packageService.findVersionByName(projectId, repoName, getPackageKey(), getArtifactVersion())?.run {
                throw VersionConflictException(getArtifactName(), getArtifactVersion())
            }
        }
        super.onUploadBefore(context)
    }

    override fun onUploadSuccess(context: ArtifactUploadContext) {
        with(context) {
            skillPackageService.createVersion(
                artifactInfo = artifactInfo as ClawHubPublishInfo,
                size = getArtifactFile().getSize(),
                sha256 = getArtifactSha256(),
                md5 = getArtifactMd5(),
            )
        }
    }

    override fun onDownload(context: ArtifactDownloadContext): ArtifactResource? {
        return when (context.artifactInfo) {
            is ClawHubArchiveDownloadInfo -> super.onDownload(context)
            is ClawHubFileDownloadInfo -> downloadSingleFileFromZip(
                context.artifactInfo as ClawHubFileDownloadInfo,
            )
            else -> throw MethodNotAllowedException()
        }
    }

    override fun remove(context: ArtifactRemoveContext) {
        with(context.artifactInfo as SkillExtInfo) {
            val realIpAddress = HttpContextHolder.getClientAddress()
            if (version == null) {
                // 删除package
                packageService.deletePackage(projectId, repoName, key, realIpAddress)
                nodeService.deleteNode(NodeDeleteRequest(projectId, repoName, "/$slug", context.userId))
            } else {
                // 删除version
                packageService.deleteVersion(projectId, repoName, key, version, realIpAddress)
                nodeService.deleteNode(NodeDeleteRequest(projectId, repoName, "/$slug/$version", context.userId))
            }
        }
    }

    override fun search(context: ArtifactSearchContext): List<ClawHubSearchResultItem> {
        with((context.artifactInfo as ClawHubSearchInfo)) {
            if (q.isBlank()) {
                return emptyList()
            }
            val queryModel = PackageQueryBuilder()
                .select(
                    TPackage::name.name,
                    TPackage::description.name,
                    TPackage::lastModifiedDate.name,
                    TPackage::extension.name,
                    TPackage::latest.name,
                    TPackage::createdBy.name,
                )
                .page(1, limit)
                .projectId(projectId)
                .repoName(repoName)
                .or()
                .name("*$q*", OperationType.MATCH_I)
                .rule(TPackage::description.name, "*$q*", OperationType.MATCH_I)
                .rule("extension.$FIELD_DISPLAY_NAME", "*$q*", OperationType.MATCH_I)
                .build()
            val page = packageService.searchPackage(queryModel)
            return page.records.map {
                val slug = it[NAME]!!.toString()
                val ownerHandle = it[TPackage::createdBy.name]?.toString()
                ClawHubSearchResultItem(
                    slug = slug,
                    displayName = (it[TPackage::extension.name] as Map<String, Any>?)
                        ?.get(FIELD_DISPLAY_NAME)?.toString() ?: slug,
                    summary = it[TPackage::description.name]?.toString() ?: "",
                    version = it[TPackage::latest.name]?.toString(),
                    updatedAt = (it[TPackage::lastModifiedDate.name]!! as Date).time,
                    ownerHandle = ownerHandle,
                    owner = ownerHandle?.let { handle ->
                        ClawHubUser(handle = handle, displayName = handle)
                    },
                )
            }
        }
    }

    override fun buildNodeCreateRequest(context: ArtifactUploadContext): NodeCreateRequest {
        return NodeCreateRequest(
            projectId = context.projectId,
            repoName = context.repoName,
            fullPath = context.artifactInfo.getArtifactFullPath(),
            folder = false,
            overwrite = false,
            size = context.getArtifactFile().getSize(),
            sha256 = context.getArtifactSha256(),
            md5 = context.getArtifactMd5(),
            operator = context.userId,
            nodeMetadata = (context.artifactInfo as ClawHubPublishInfo).generateMetadata()
        )
    }

    override fun buildDownloadRecord(
        context: ArtifactDownloadContext,
        artifactResource: ArtifactResource
    ): PackageDownloadRecord? {
        return if (context.artifactInfo is ClawHubArchiveDownloadInfo) {
            skillPackageService.buildDownloadRecord(context.artifactInfo as ClawHubArchiveDownloadInfo)
        } else null
    }

    private fun listSkills(artifactInfo: ClawHubSkillListInfo): ClawHubSkillListResponse {
        with(artifactInfo) {
            val pageNumber = cursor?.toIntOrNull() ?: 1
            val sortField = when (sort) {
                SORT_CREATED, SORT_NEWEST -> TPackage::createdDate.name
                SORT_DOWNLOADS -> TPackage::downloads.name
                else -> TPackage::lastModifiedDate.name
            }
            val queryModel = PackageQueryBuilder()
                .select(
                    TPackage::name.name,
                    TPackage::description.name,
                    TPackage::createdDate.name,
                    TPackage::lastModifiedDate.name,
                    TPackage::extension.name,
                    TPackage::latest.name,
                    TPackage::downloads.name,
                    TPackage::versions.name,
                    TPackage::key.name,
                )
                .sortByDesc(sortField)
                .page(pageNumber, limit)
                .projectId(projectId)
                .repoName(repoName)
                .build()
            val page = packageService.searchPackage(queryModel)

            val items = page.records.map {
                val slug = it[TPackage::name.name]!!.toString()
                val latest = it[TPackage::latest.name]?.toString()
                val latestVersion = if (latest != null) {
                    buildClawHubSkillVersion(projectId, repoName, it[TPackage::key.name]!!.toString(), latest)
                } else null
                ClawHubSkill(
                    slug = it[NAME]!!.toString(),
                    displayName = (it[TPackage::extension.name] as Map<String, Any>?)
                        ?.get(FIELD_DISPLAY_NAME)?.toString() ?: slug,
                    summary = it[TPackage::description.name]?.toString() ?: "",
                    tags = if (latest != null) mapOf(LATEST to latest) else emptyMap(),
                    stats = mapOf(
                        DOWNLOADS to (it[TPackage::downloads.name]?.toString()?.toLong() ?: 0),
                        VERSIONS to (it[TPackage::versions.name]?.toString()?.toLong() ?: 1),
                    ),
                    createdAt = (it[TPackage::createdDate.name]!! as Date).time,
                    updatedAt = (it[TPackage::lastModifiedDate.name]!! as Date).time,
                    latestVersion = latestVersion
                )
            }
            val nextCursor = if (pageNumber < page.totalPages) (pageNumber + 1).toString() else null
            return ClawHubSkillListResponse(items = items, nextCursor = nextCursor)
        }
    }

    private fun resolveSkill(artifactInfo: ClawHubResolveInfo): ClawHubResolveResponse {
        with(artifactInfo) {
            val packageKey = getPackageKey()
            val pkg = packageService.findPackageByKey(projectId, repoName, packageKey)
                ?: return ClawHubResolveResponse()
            val latestVersion = pkg.latest?.takeIf { it.isNotBlank() }
                ?.let { ClawHubResolveVersion(version = it) }
            var matchedVersion: ClawHubResolveVersion? = null
            var pageNumber = 1
            while (matchedVersion == null) {
                val option = VersionListOption(pageNumber = pageNumber, pageSize = 100)
                val page = packageService.listVersionPage(projectId, repoName, packageKey, option)
                matchedVersion = page.records.firstOrNull {
                    it.extension[KEY_FINGERPRINT]?.toString() == hash
                }?.let { ClawHubResolveVersion(version = it.name) }
                if (pageNumber >= page.totalPages) break
                pageNumber++
            }
            return ClawHubResolveResponse(match = matchedVersion, latestVersion = latestVersion)
        }
    }

    private fun getSkillModeration(artifactInfo: ClawHubSkillModerationInfo): ClawHubModerationResponse {
        with(artifactInfo) {
            packageService.findPackageByKey(projectId, repoName, getPackageKey())
                ?: throw PackageNotFoundException(slug)
            return ClawHubModerationResponse(
                moderation = ClawHubModeration(
                    isSuspicious = false,
                    isMalwareBlocked = false,
                    verdict = MODERATION_VERDICT_CLEAN,
                    reasonCodes = emptyList(),
                ),
            )
        }
    }

    private fun getSkillDetail(artifactInfo: ClawHubSkillInfo): ClawHubSkillDetailResponse {
        with(artifactInfo) {
            val packageKey = getPackageKey()
            val pkg = packageService.findPackageByKey(projectId, repoName, packageKey)
                ?: throw PackageNotFoundException(slug)
            val skill = ClawHubSkill(
                slug = slug,
                displayName = pkg.extension[FIELD_DISPLAY_NAME]?.toString() ?: slug,
                summary = pkg.description ?: "",
                tags = mapOf(LATEST to pkg.latest),
                stats = mapOf(DOWNLOADS to pkg.downloads, VERSIONS to pkg.versions),
                createdAt = pkg.createdDate.atZone(ZoneOffset.systemDefault()).toInstant().toEpochMilli(),
                updatedAt = pkg.lastModifiedDate.atZone(ZoneOffset.systemDefault()).toInstant().toEpochMilli(),
            )
            val latestVersion = buildClawHubSkillVersion(projectId, repoName, packageKey, pkg.latest)
                ?: throw VersionNotFoundException("$slug@${pkg.latest}")
            return ClawHubSkillDetailResponse(
                skill = skill,
                latestVersion = latestVersion,
                owner = ClawHubUser(
                    handle = pkg.createdBy,
                    displayName = pkg.createdBy,
                )
            )
        }
    }

    private fun listSkillVersions(artifactInfo: ClawHubSkillVersionListInfo): ClawHubSkillVersionListResponse {
        with(artifactInfo) {
            val pageNumber = cursor?.toIntOrNull() ?: 1
            val option = VersionListOption(
                pageNumber = pageNumber,
                pageSize = limit,
                sortProperty = TPackageVersion::createdDate.name,
            )
            val page = packageService.listVersionPage(projectId, repoName, getPackageKey(), option)
            val items = page.records.map {
                SkillVersionItem(
                    version = it.name,
                    createdAt = it.createdDate.atZone(ZoneOffset.systemDefault()).toInstant().toEpochMilli(),
                    changelog = it.extension[FIELD_CHANGELOG]?.toString() ?: "",
                )
            }
            val nextCursor = if (pageNumber < page.totalPages) (pageNumber + 1).toString() else null
            return ClawHubSkillVersionListResponse(items = items, nextCursor = nextCursor)
        }
    }

    private fun getSkillVersionDetail(artifactInfo: ClawHubSkillVersionInfo): ClawHubSkillVersionDetailResponse {
        with(artifactInfo) {
            val pkgVersion = packageService.findVersionByName(projectId, repoName, getPackageKey(), version)
                ?: throw VersionNotFoundException("$slug@$version")
            val clawHubVersionDetail = ClawHubSkillVersionDetail(
                version = version,
                createdAt = pkgVersion.createdDate.atZone(ZoneOffset.systemDefault()).toInstant().toEpochMilli(),
                changelog = pkgVersion.extension[FIELD_CHANGELOG]?.toString() ?: "",
                files = pkgVersion.extension[KEY_FILE_LIST]?.let {
                    JsonUtils.objectMapper.convertValue(it, jacksonTypeRef())
                },
            )
            return ClawHubSkillVersionDetailResponse(
                skill = ClawHubSkillBasicInfo(
                    slug = slug,
                    displayName = pkgVersion.extension[FIELD_DISPLAY_NAME]?.toString() ?: slug,
                ),
                version = clawHubVersionDetail
            )
        }
    }

    @Suppress("ThrowsCount")
    private fun downloadSingleFileFromZip(artifactInfo: ClawHubFileDownloadInfo): ArtifactResource {
        with(artifactInfo) {
            val node = nodeService.getNodeDetail(artifactInfo)
                ?: throw NodeNotFoundException(getArtifactFullPath())
            val credentials = ArtifactContextHolder.getRepoDetail()!!.storageCredentials
            val inputStream = storageManager.loadFullArtifactInputStream(node, credentials)
                ?: throw ArtifactNotFoundException(getArtifactFullPath())

            val normalizedPath = SkillUtils.normalizeZipPath(path)
            val fileBytes = SkillUtils.extractFileFromZip(inputStream, normalizedPath)
                ?: throw ArtifactNotFoundException("${getArtifactFullPath()}/$normalizedPath")
            val artifactStream = fileBytes.inputStream().artifactStream(Range.full(fileBytes.size.toLong()))
            val responseName = normalizedPath.substringAfterLast('/')
            return ArtifactResource(artifactStream, responseName, null, ArtifactChannel.LOCAL, false)
        }
    }

    private fun buildClawHubSkillVersion(
        projectId: String,
        repoName: String,
        packageKey: String,
        version: String,
    ): ClawHubSkillVersion? {
        return packageService.findVersionByName(projectId, repoName, packageKey, version)?.let {
            ClawHubSkillVersion(
                version = it.name,
                createdAt = it.createdDate.atZone(ZoneOffset.systemDefault()).toInstant()
                    .toEpochMilli(),
                changelog = it.extension[FIELD_CHANGELOG]?.toString() ?: "",
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SkillLocalRepository::class.java)
    }
}
