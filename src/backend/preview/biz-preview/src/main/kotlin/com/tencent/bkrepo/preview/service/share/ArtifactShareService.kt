package com.tencent.bkrepo.preview.service.share

import com.tencent.bkrepo.auth.api.ServiceTemporaryTokenClient
import com.tencent.bkrepo.auth.api.ServiceUserClient
import com.tencent.bkrepo.auth.pojo.token.TemporaryTokenCreateRequest
import com.tencent.bkrepo.auth.pojo.token.TokenType
import com.tencent.bkrepo.auth.pojo.token.normalizeOrgIds
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.preview.config.ArtifactShareProperties
import com.tencent.bkrepo.preview.constant.PreviewMessageCode
import com.tencent.bkrepo.preview.dao.ArtifactShareDao
import com.tencent.bkrepo.preview.dao.ArtifactShareListCursor
import com.tencent.bkrepo.preview.model.TArtifactShare
import com.tencent.bkrepo.preview.pojo.share.AccessibleShareChannel
import com.tencent.bkrepo.preview.pojo.share.ArtifactShareBatchStatusRequest
import com.tencent.bkrepo.preview.pojo.share.ArtifactShareInfo
import com.tencent.bkrepo.preview.pojo.share.ArtifactShareKind
import com.tencent.bkrepo.preview.pojo.share.ArtifactShareOpenInfo
import com.tencent.bkrepo.preview.pojo.share.ArtifactSharePage
import com.tencent.bkrepo.preview.pojo.share.ArtifactShareResourceType
import com.tencent.bkrepo.preview.pojo.share.ArtifactShareStatusItem
import com.tencent.bkrepo.preview.pojo.share.ArtifactShareSummary
import com.tencent.bkrepo.preview.pojo.share.ArtifactShareUpsertRequest
import com.tencent.bkrepo.preview.pojo.share.ShareVisibility
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.web.util.UriUtils
import java.time.LocalDateTime
import java.util.UUID

/**
 * 作品分享：API 使用展示侧组织 ID；入库 / 临时 token / 访问校验使用库内组织 ID。
 *
 * 列表与打开接口以分享权限为独立访问域，不校验仓库 READ。
 */
@Service
class ArtifactShareService(
    private val artifactShareDao: ArtifactShareDao,
    private val driveShareNodeResolver: DriveShareNodeResolver,
    private val temporaryTokenClient: ServiceTemporaryTokenClient,
    private val orgScopeIdMappingService: OrgScopeIdMappingService,
    private val serviceUserClient: ServiceUserClient,
    private val artifactShareProperties: ArtifactShareProperties,
) {

    fun upsert(userId: String, request: ArtifactShareUpsertRequest): ArtifactShareInfo {
        validateUpsertRequest(request)
        val nodeInfo = driveShareNodeResolver.resolveFileByIno(request.projectId, request.repoName, request.resourceId)
            ?: throw ErrorCodeException(PreviewMessageCode.PREVIEW_NODE_NOT_FOUND, request.resourceId)
        if (nodeInfo.node.createdBy != userId) {
            throw ErrorCodeException(PreviewMessageCode.PREVIEW_ARTIFACT_SHARE_FORBIDDEN)
        }
        val userIds = normalizeIds(request.visibility, request.userIds, "userIds")
        val orgIds = translateOrgIdsToStored(
            normalizeIds(request.visibility, request.orgIds, "orgIds"),
        )
        if (request.visibility == ShareVisibility.CUSTOM && userIds.isEmpty() && orgIds.isEmpty()) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "userIds/orgIds")
        }
        val now = LocalDateTime.now()
        val existing = artifactShareDao.findActiveByProjectRepoResourceId(
            request.projectId,
            request.repoName,
            request.resourceId,
        )
        val agentId = driveShareNodeResolver.metadataValue(
            nodeInfo.node,
            DriveShareNodeResolver.METADATA_AGENT_ID,
        )
        val conversationId = driveShareNodeResolver.metadataValue(
            nodeInfo.node,
            DriveShareNodeResolver.METADATA_CONVERSATION_ID,
        )
        val artifactName = existing?.artifactName?.trim()?.takeIf { it.isNotEmpty() }
            ?: driveShareNodeResolver.metadataValue(
                nodeInfo.node,
                DriveShareNodeResolver.METADATA_ARTIFACT_NAME,
            )
        val artifactType = ArtifactShareTypes.resolve(
            driveShareNodeResolver.metadataValue(
                nodeInfo.node,
                DriveShareNodeResolver.METADATA_ARTIFACT_TYPE,
            ),
            nodeInfo.fullPath,
        )
        val (authorizedUserSet, authorizedOrgList) = buildTokenAuthorization(
            userId,
            request.visibility,
            userIds,
            orgIds,
        )
        val shortShareId = existing?.let { ensureShortShareId(it) }?.shortShareId
            ?.takeIf { ArtifactShareShortIds.isValid(it) }
            ?: allocateShortShareId()
        val draft = if (existing == null) {
            TArtifactShare(
                id = generateShareId(),
                shortShareId = shortShareId,
                shareKind = ArtifactShareKind.MATERIAL,
                resourceType = ArtifactShareResourceType.DRIVE_NODE,
                createdBy = userId,
                createdDate = now,
                lastModifiedBy = userId,
                lastModifiedDate = now,
                projectId = request.projectId,
                repoName = request.repoName,
                resourceId = request.resourceId,
                fullPath = nodeInfo.fullPath,
                visibility = request.visibility,
                userIds = userIds,
                orgIds = orgIds,
                agentId = agentId,
                conversationId = conversationId,
                artifactName = artifactName,
                artifactType = artifactType,
            )
        } else {
            revokeStoredTokens(existing)
            existing.copy(
                resourceType = ArtifactShareResourceType.DRIVE_NODE,
                lastModifiedBy = userId,
                lastModifiedDate = now,
                fullPath = nodeInfo.fullPath,
                visibility = request.visibility,
                userIds = userIds,
                orgIds = orgIds,
                agentId = agentId,
                conversationId = conversationId,
                artifactName = artifactName,
                artifactType = artifactType,
                previewToken = null,
                downloadToken = null,
                shortShareId = shortShareId,
            )
        }
        val withTokens = issueAndAttachTokens(draft, authorizedUserSet, authorizedOrgList)
        return convert(persistShare(withTokens))
    }

    fun getByResourceId(
        userId: String,
        projectId: String,
        repoName: String,
        resourceId: Long,
    ): ArtifactShareInfo? {
        val record = artifactShareDao.findActiveByProjectRepoResourceId(projectId, repoName, resourceId)
            ?: return null
        if (record.createdBy != userId) {
            throw ErrorCodeException(PreviewMessageCode.PREVIEW_ARTIFACT_SHARE_FORBIDDEN)
        }
        return convert(record)
    }

    fun batchStatus(userId: String, request: ArtifactShareBatchStatusRequest): List<ArtifactShareStatusItem> {
        if (request.resourceIds.isEmpty()) {
            return emptyList()
        }
        val limited = request.resourceIds.distinct().take(MAX_BATCH_RESOURCE_IDS)
        val active = artifactShareDao.findActiveByResourceIds(request.projectId, request.repoName, limited)
            .filter { it.createdBy == userId }
            .associateBy { it.resourceId }
        return limited.map { resourceId ->
            val record = active[resourceId]
            ArtifactShareStatusItem(
                resourceId = resourceId,
                shared = record != null,
                shareId = record?.id,
            )
        }
    }

    fun listMine(
        userId: String,
        keyword: String?,
        cursor: String?,
        limit: Int?,
    ): ArtifactSharePage<ArtifactShareInfo> {
        val pageLimit = normalizeLimit(limit)
        val decoded = ArtifactShareListCursor.decode(cursor)
        val records = artifactShareDao.listMine(userId, keyword, decoded, pageLimit)
        return toPage(records, pageLimit) { convert(it) }
    }

    fun listAccessible(
        userId: String,
        keyword: String?,
        cursor: String?,
        limit: Int?,
        channel: String? = null,
        featured: Boolean? = null,
    ): ArtifactSharePage<ArtifactShareSummary> {
        val pageLimit = normalizeLimit(limit)
        val decoded = ArtifactShareListCursor.decode(cursor)
        val orgIds = loadUserOrgIds(userId)
        val records = artifactShareDao.listAccessible(
            userId,
            orgIds,
            keyword,
            decoded,
            pageLimit,
            parseAccessibleChannel(channel),
            featured,
        )
        return toPage(records, pageLimit) { convertSummary(it) }
    }

    fun rename(userId: String, shareId: String, artifactName: String): ArtifactShareInfo {
        val name = artifactName.trim()
        if (name.isEmpty()) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "artifactName")
        }
        val record = artifactShareDao.findByShareId(shareId)
            ?: throw ErrorCodeException(PreviewMessageCode.PREVIEW_ARTIFACT_SHARE_NOT_FOUND, shareId)
        if (record.createdBy != userId) {
            throw ErrorCodeException(PreviewMessageCode.PREVIEW_ARTIFACT_SHARE_FORBIDDEN)
        }
        val filled = ensureShortShareId(record)
        if (filled.artifactName == name) {
            return convert(filled)
        }
        val updated = filled.copy(
            artifactName = name,
            lastModifiedBy = userId,
            lastModifiedDate = LocalDateTime.now(),
        )
        logger.info(
            "artifact share renamed: user=[$userId], shareId=[$shareId], " +
                "projectId=[${filled.projectId}], repoName=[${filled.repoName}]",
        )
        return convert(artifactShareDao.save(updated))
    }

    fun revoke(userId: String, shareId: String) {
        val record = artifactShareDao.findByShareId(shareId) ?: return
        deleteOwnedShare(userId, record)
    }

    fun open(userId: String, shareId: String): ArtifactShareOpenInfo {
        val record = artifactShareDao.findByShareId(shareId)
            ?: throw ErrorCodeException(PreviewMessageCode.PREVIEW_ARTIFACT_SHARE_NOT_FOUND, shareId)
        return openRecord(userId, record, shareId)
    }

    fun openByShortShareId(userId: String, shortShareId: String): ArtifactShareOpenInfo {
        if (!ArtifactShareShortIds.isValid(shortShareId)) {
            throw ErrorCodeException(PreviewMessageCode.PREVIEW_ARTIFACT_SHARE_NOT_FOUND, shortShareId)
        }
        val record = artifactShareDao.findByShortShareId(shortShareId)
            ?: throw ErrorCodeException(PreviewMessageCode.PREVIEW_ARTIFACT_SHARE_NOT_FOUND, shortShareId)
        return openRecord(userId, record, shortShareId)
    }

    private fun openRecord(userId: String, record: TArtifactShare, requestedId: String): ArtifactShareOpenInfo {
        if (!canAccess(record, userId)) {
        logger.info(
            "artifact share access denied: user=[$userId], shareId=[${record.id}], requestedId=[$requestedId], " +
                "visibility=[${record.visibility}], projectId=[${record.projectId}], " +
                "repoName=[${record.repoName}]",
        )
            throw ErrorCodeException(PreviewMessageCode.PREVIEW_ARTIFACT_SHARE_ACCESS_DENIED, requestedId)
        }
        logger.info(
            "artifact share access: user=[$userId], shareId=[${record.id}], requestedId=[$requestedId], " +
                "visibility=[${record.visibility}], projectId=[${record.projectId}], " +
                "repoName=[${record.repoName}]",
        )
        val liveRecord = ensureTokensForCurrentPath(record)
        val previewToken = liveRecord.previewToken
            ?: throw ErrorCodeException(CommonMessageCode.SYSTEM_ERROR)
        val downloadToken = liveRecord.downloadToken
            ?: throw ErrorCodeException(CommonMessageCode.SYSTEM_ERROR)
        return ArtifactShareOpenInfo(
            share = convert(liveRecord, includePermissionIds = false),
            previewUrl = buildPreviewUrl(liveRecord, previewToken),
            downloadUrl = buildDownloadUrl(liveRecord, downloadToken),
        )
    }

    /**
     * path 漂移时废旧重签并回写；缺失 token 时同样自愈。
     */
    private fun ensureTokensForCurrentPath(record: TArtifactShare): TArtifactShare {
        val filled = ensureShortShareId(record)
        val nodeInfo = driveShareNodeResolver.resolveFileByIno(filled.projectId, filled.repoName, filled.resourceId)
            ?: throw ErrorCodeException(PreviewMessageCode.PREVIEW_NODE_NOT_FOUND, filled.resourceId)
        val pathChanged = nodeInfo.fullPath != filled.fullPath
        val missingToken = filled.previewToken.isNullOrBlank() || filled.downloadToken.isNullOrBlank()
        if (!pathChanged && !missingToken) {
            return filled
        }
        revokeStoredTokens(filled)
        val (authorizedUserSet, authorizedOrgList) = buildTokenAuthorization(
            creator = filled.createdBy,
            visibility = filled.visibility,
            userIds = filled.userIds,
            orgIds = filled.orgIds,
        )
        val refreshed = issueAndAttachTokens(
            filled.copy(
                fullPath = nodeInfo.fullPath,
                lastModifiedDate = LocalDateTime.now(),
                previewToken = null,
                downloadToken = null,
            ),
            authorizedUserSet,
            authorizedOrgList,
        )
        return persistShare(refreshed)
    }

    private fun issueAndAttachTokens(
        record: TArtifactShare,
        authorizedUserSet: Set<String>,
        authorizedOrgList: Set<String>,
    ): TArtifactShare {
        val previewToken = createAccessToken(record, authorizedUserSet, authorizedOrgList, TokenType.PREVIEW)
        val downloadToken = createAccessToken(record, authorizedUserSet, authorizedOrgList, TokenType.DOWNLOAD)
        return record.copy(previewToken = previewToken, downloadToken = downloadToken)
    }

    private fun createAccessToken(
        record: TArtifactShare,
        authorizedUserSet: Set<String>,
        authorizedOrgList: Set<String>,
        type: TokenType,
    ): String {
        val request = TemporaryTokenCreateRequest(
            projectId = record.projectId,
            repoName = record.repoName,
            fullPathSet = setOf(record.fullPath),
            authorizedUserSet = authorizedUserSet,
            authorizedOrgList = authorizedOrgList,
            expireSeconds = NEVER_EXPIRE_SECONDS,
            permits = null,
            type = type,
            createdBy = record.createdBy,
        )
        val tokens = temporaryTokenClient.createToken(request).data
            ?: throw ErrorCodeException(CommonMessageCode.SYSTEM_ERROR)
        return tokens.firstOrNull()?.token
            ?: throw ErrorCodeException(CommonMessageCode.SYSTEM_ERROR)
    }

    private fun deleteOwnedShare(userId: String, record: TArtifactShare) {
        if (record.createdBy != userId) {
            throw ErrorCodeException(PreviewMessageCode.PREVIEW_ARTIFACT_SHARE_FORBIDDEN)
        }
        revokeStoredTokens(record)
        artifactShareDao.removeByProjectRepoResourceId(record.projectId, record.repoName, record.resourceId)
        logger.info(
            "artifact share deleted: user=[$userId], shareId=[${record.id}], " +
                "projectId=[${record.projectId}], repoName=[${record.repoName}]",
        )
    }

    private fun revokeStoredTokens(record: TArtifactShare) {
        deleteTokenIfPresent(record.previewToken)
        deleteTokenIfPresent(record.downloadToken)
    }

    private fun deleteTokenIfPresent(token: String?) {
        val value = token?.trim().orEmpty()
        if (value.isEmpty()) {
            return
        }
        temporaryTokenClient.deleteToken(value)
    }

    private fun buildTokenAuthorization(
        creator: String,
        visibility: ShareVisibility,
        userIds: List<String>,
        orgIds: List<String>,
    ): Pair<Set<String>, Set<String>> {
        if (visibility == ShareVisibility.PUBLIC) {
            return emptySet<String>() to emptySet()
        }
        val users = linkedSetOf(creator)
        users.addAll(userIds)
        val orgIdsNormalized = orgIds.normalizeOrgIds()
        return users to orgIdsNormalized
    }

    private fun canAccess(record: TArtifactShare, userId: String): Boolean {
        if (record.visibility == ShareVisibility.PUBLIC) {
            return true
        }
        if (record.createdBy == userId || userId in record.userIds) {
            return true
        }
        val orgIds = loadUserOrgIds(userId).toSet()
        return record.orgIds.any { it in orgIds }
    }

    private fun loadUserOrgIds(userId: String): List<String> {
        return try {
            serviceUserClient.userDeptById(userId).data?.scopes.orEmpty()
                .map { it.scopeValue.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
        } catch (ex: Exception) {
            logger.warn("Failed to resolve user departments for artifact share, user=[$userId]", ex)
            emptyList()
        }
    }

    private fun validateUpsertRequest(request: ArtifactShareUpsertRequest) {
        if (request.projectId.isBlank() || request.repoName.isBlank() || request.resourceId <= 0) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "projectId/repoName/resourceId")
        }
        if (request.visibility == ShareVisibility.CUSTOM &&
            request.userIds.isEmpty() &&
            request.orgIds.isEmpty()
        ) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "userIds/orgIds")
        }
    }

    private fun normalizeIds(visibility: ShareVisibility, ids: List<String>, field: String): List<String> {
        if (visibility == ShareVisibility.PUBLIC) {
            return emptyList()
        }
        val normalized = ids.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (normalized.size > MAX_SCOPE_IDS) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, field)
        }
        return normalized
    }

    private fun translateOrgIdsToStored(orgIds: List<String>): List<String> {
        if (orgIds.isEmpty()) {
            return emptyList()
        }
        val mapping = runCatching { orgScopeIdMappingService.toStoredIds(orgIds) }
            .getOrElse { ex ->
                logger.error("artifact share displayId->storedId failed, displayIds=[$orgIds]", ex)
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "orgIds")
            }
        val missing = orgIds.filter { mapping[it].isNullOrBlank() }
        if (missing.isNotEmpty()) {
            logger.warn("artifact share displayId->storedId missing, displayIds=[$missing]")
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "orgIds")
        }
        return orgIds.map { mapping.getValue(it) }.distinct()
    }

    private fun translateOrgIdsToDisplay(orgIds: List<String>): List<String> {
        if (orgIds.isEmpty()) {
            return emptyList()
        }
        val mapping = runCatching { orgScopeIdMappingService.toDisplayIds(orgIds) }
            .onFailure { ex ->
                logger.warn("artifact share storedId->displayId failed, storedIds=[$orgIds]", ex)
            }
            .getOrDefault(emptyMap())
        return orgIds.map { storedId ->
            val displayId = mapping[storedId]
            if (displayId.isNullOrBlank()) {
                logger.warn("artifact share storedId->displayId missing, keep storedId=[$storedId]")
                storedId
            } else {
                displayId
            }
        }
    }

    private fun convert(record: TArtifactShare, includePermissionIds: Boolean = true): ArtifactShareInfo {
        val live = ensureShortShareId(record)
        val shareId = live.id ?: throw ErrorCodeException(PreviewMessageCode.PREVIEW_ARTIFACT_SHARE_NOT_FOUND)
        val shortShareId = live.shortShareId?.takeIf { ArtifactShareShortIds.isValid(it) }
            ?: throw ErrorCodeException(CommonMessageCode.SYSTEM_ERROR)
        return ArtifactShareInfo(
            shareId = shareId,
            shareKind = live.shareKind,
            resourceType = live.resourceType,
            projectId = live.projectId,
            repoName = live.repoName,
            resourceId = live.resourceId,
            fullPath = live.fullPath,
            visibility = live.visibility,
            userIds = if (includePermissionIds) live.userIds else emptyList(),
            orgIds = if (includePermissionIds) {
                translateOrgIdsToDisplay(live.orgIds)
            } else {
                emptyList()
            },
            featured = live.featured,
            agentId = live.agentId,
            conversationId = live.conversationId,
            artifactName = live.artifactName,
            type = ArtifactShareTypes.resolve(live.artifactType, live.fullPath),
            sharePath = "/share/$shortShareId",
            createdBy = live.createdBy,
            createdDate = live.createdDate,
            lastModifiedDate = live.lastModifiedDate,
        )
    }

    private fun convertSummary(record: TArtifactShare): ArtifactShareSummary {
        val shareId = record.id ?: throw ErrorCodeException(PreviewMessageCode.PREVIEW_ARTIFACT_SHARE_NOT_FOUND)
        return ArtifactShareSummary(
            shareId = shareId,
            shareKind = record.shareKind,
            projectId = record.projectId,
            repoName = record.repoName,
            resourceId = record.resourceId,
            artifactName = record.artifactName,
            type = ArtifactShareTypes.resolve(record.artifactType, record.fullPath),
            fullPath = record.fullPath,
            downloadToken = record.downloadToken?.trim()?.takeIf { it.isNotEmpty() },
            createdBy = record.createdBy,
            agentId = record.agentId,
            featured = record.featured,
            lastModifiedDate = record.lastModifiedDate,
        )
    }

    private fun <T> toPage(
        records: List<TArtifactShare>,
        limit: Int,
        transform: (TArtifactShare) -> T,
    ): ArtifactSharePage<T> {
        val hasMore = records.size > limit
        val pageRecords = if (hasMore) records.subList(0, limit) else records
        val nextCursor = if (hasMore) {
            val last = pageRecords.last()
            val shareId = last.id ?: throw ErrorCodeException(PreviewMessageCode.PREVIEW_ARTIFACT_SHARE_NOT_FOUND)
            ArtifactShareListCursor(last.lastModifiedDate, shareId).encode()
        } else {
            null
        }
        return ArtifactSharePage(
            records = pageRecords.map(transform),
            nextCursor = nextCursor,
            limit = limit,
        )
    }

    private fun parseAccessibleChannel(channel: String?): AccessibleShareChannel? {
        val value = channel?.trim().orEmpty()
        if (value.isEmpty()) {
            return null
        }
        return runCatching { AccessibleShareChannel.valueOf(value.uppercase()) }.getOrElse {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "channel")
        }
    }

    private fun normalizeLimit(limit: Int?): Int {
        if (limit == null) {
            return DEFAULT_PAGE_LIMIT
        }
        if (limit < 1) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "limit")
        }
        return minOf(limit, MAX_PAGE_LIMIT)
    }

    private fun buildPreviewUrl(record: TArtifactShare, token: String): String {
        val path = "/ui/${record.projectId}/filePreview/local/0/" +
            "${record.repoName}${encodeUriPath(record.fullPath)}?token=$token"
        return prefixDomain(path)
    }

    private fun buildDownloadUrl(record: TArtifactShare, token: String): String {
        val path = "/web/fs-server/drive/temporary/download/" +
            "${record.projectId}/${record.repoName}" +
            "${encodeUriPath(record.fullPath)}?token=$token"
        return prefixDomain(path)
    }

    private fun encodeUriPath(path: String): String {
        return path.split('/').joinToString("/") { encodeUriSegment(it) }
    }

    private fun encodeUriSegment(segment: String): String {
        if (segment.isEmpty()) {
            return segment
        }
        return UriUtils.encode(segment, Charsets.UTF_8)
    }

    private fun prefixDomain(path: String): String {
        return if (artifactShareProperties.domain.isBlank()) {
            path
        } else {
            artifactShareProperties.domain.trimEnd('/') + path
        }
    }

    private fun generateShareId(): String = UUID.randomUUID().toString().replace("-", "").lowercase()

    private fun allocateShortShareId(): String {
        repeat(MAX_SHORT_SHARE_ID_ATTEMPTS) {
            val candidate = ArtifactShareShortIds.generate()
            if (artifactShareDao.findByShortShareId(candidate) == null) {
                return candidate
            }
        }
        throw ErrorCodeException(CommonMessageCode.SYSTEM_ERROR)
    }

    private fun ensureShortShareId(record: TArtifactShare): TArtifactShare {
        val current = record.shortShareId.orEmpty()
        if (ArtifactShareShortIds.isValid(current)) {
            return record
        }
        val shareId = record.id ?: throw ErrorCodeException(PreviewMessageCode.PREVIEW_ARTIFACT_SHARE_NOT_FOUND)
        repeat(MAX_SHORT_SHARE_ID_ATTEMPTS) {
            val allocated = allocateShortShareId()
            try {
                if (artifactShareDao.assignShortShareIdIfAbsent(shareId, allocated)) {
                    return record.copy(shortShareId = allocated)
                }
                val latest = artifactShareDao.findByShareId(shareId)
                    ?: throw ErrorCodeException(PreviewMessageCode.PREVIEW_ARTIFACT_SHARE_NOT_FOUND, shareId)
                if (ArtifactShareShortIds.isValid(latest.shortShareId.orEmpty())) {
                    return latest
                }
            } catch (ex: DuplicateKeyException) {
                if (ex.message?.contains(SHORT_SHARE_ID_INDEX) != true) {
                    throw ex
                }
                logger.warn(
                    "artifact share shortShareId conflict, retry allocate, shareId=[$shareId]",
                )
            }
        }
        throw ErrorCodeException(CommonMessageCode.SYSTEM_ERROR)
    }

    private fun persistShare(record: TArtifactShare): TArtifactShare {
        var candidate = record
        repeat(MAX_SHORT_SHARE_ID_ATTEMPTS) {
            try {
                return artifactShareDao.save(candidate)
            } catch (ex: DuplicateKeyException) {
                if (ex.message?.contains(SHORT_SHARE_ID_INDEX) != true) {
                    throw ex
                }
                logger.warn(
                    "artifact share shortShareId conflict, retry allocate, shareId=[${candidate.id}]",
                )
                candidate = candidate.copy(shortShareId = allocateShortShareId())
            }
        }
        throw ErrorCodeException(CommonMessageCode.SYSTEM_ERROR)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArtifactShareService::class.java)
        private const val MAX_BATCH_RESOURCE_IDS = 500
        private const val NEVER_EXPIRE_SECONDS = 0L
        private const val DEFAULT_PAGE_LIMIT = 100
        private const val MAX_PAGE_LIMIT = 500
        private const val MAX_SCOPE_IDS = 500
        private const val MAX_SHORT_SHARE_ID_ATTEMPTS = 8
        private const val SHORT_SHARE_ID_INDEX = "short_share_id_uk"
    }
}
