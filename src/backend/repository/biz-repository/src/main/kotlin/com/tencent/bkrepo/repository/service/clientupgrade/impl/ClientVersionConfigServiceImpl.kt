package com.tencent.bkrepo.repository.service.clientupgrade.impl

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.exception.BadRequestException
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.repository.dao.ClientVersionConfigDao
import com.tencent.bkrepo.repository.model.TClientVersionConfig
import com.tencent.bkrepo.repository.pojo.clientupgrade.ClientVersionConfigUpsertRequest
import com.tencent.bkrepo.repository.pojo.clientupgrade.ClientVersionConfigVo
import com.tencent.bkrepo.repository.pojo.clientupgrade.ClientUpgradeCheckResponse
import com.tencent.bkrepo.repository.service.clientupgrade.ClientVersionConfigCache
import com.tencent.bkrepo.repository.service.clientupgrade.ClientVersionConfigService
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class ClientVersionConfigServiceImpl(
    private val clientVersionConfigDao: ClientVersionConfigDao,
    private val clientVersionConfigCache: ClientVersionConfigCache,
) : ClientVersionConfigService {

    override fun upsert(userId: String, request: ClientVersionConfigUpsertRequest) {
        val command = request.toUpsertCommand()
        validateUpsertCommand(command)
        persistUpsert(userId = userId, command = command).invoke()
    }

    override fun batchUpsert(userId: String, requests: List<ClientVersionConfigUpsertRequest>) {
        if (requests.isEmpty()) return
        if (requests.size > BATCH_UPSERT_MAX_SIZE) {
            throw BadRequestException(
                code = CommonMessageCode.PARAMETER_INVALID,
                "batch size ${requests.size} exceeds limit $BATCH_UPSERT_MAX_SIZE",
            )
        }
        val commands = requests.map { request ->
            request.toUpsertCommand().also(::validateUpsertCommand)
        }
        validateBatchUpsert(commands)
        commands.forEach { command ->
            persistUpsert(userId = userId, command = command).invoke()
        }
    }

    override fun remove(id: String) {
        if (id.isBlank()) {
            throw BadRequestException(code = CommonMessageCode.PARAMETER_EMPTY, "id")
        }
        val record = findRequiredRecord(id)
        clientVersionConfigDao.removeById(id)
        record.cacheAsNotFound()
        record.refreshUserScopeExistsCache()
    }

    override fun batchRemove(ids: List<String>) {
        if (ids.isEmpty()) return
        if (ids.size > BATCH_UPSERT_MAX_SIZE) {
            throw BadRequestException(
                code = CommonMessageCode.PARAMETER_INVALID,
                "batch size ${ids.size} exceeds limit $BATCH_UPSERT_MAX_SIZE",
            )
        }
        val records = ids.map { id ->
            if (id.isBlank()) {
                throw BadRequestException(code = CommonMessageCode.PARAMETER_EMPTY, "id")
            }
            findRequiredRecord(id)
        }
        records.forEach { record ->
            clientVersionConfigDao.removeById(record.id!!)
            record.cacheAsNotFound()
            record.refreshUserScopeExistsCache()
        }
    }

    override fun listPage(productId: String?, pageNumber: Int, pageSize: Int): Page<ClientVersionConfigVo> {
        val pid = productId?.trim()?.takeIf { it.isNotEmpty() }?.lowercase()
        val page = clientVersionConfigDao.pageByProductId(pid, pageNumber, pageSize)
        return Page(
            pageNumber = page.pageNumber,
            pageSize = page.pageSize,
            totalRecords = page.totalRecords,
            totalPages = page.totalPages,
            records = page.records.map { it.toVo() },
        )
    }

    override fun checkUpgrade(
        forUserId: String,
        currentVersion: String,
        productId: String,
        platform: String,
        arch: String,
    ): ClientUpgradeCheckResponse {
        val uid = forUserId.trim()
        validateCheckUpgradeParams(uid, currentVersion, productId, platform, arch)
        val cacheRequest = ClientVersionConfigCache.UpgradeCacheRequest(
            productId = normalizeKey(productId),
            platform = normalizeKey(platform),
            arch = normalizeKey(arch),
            targetUserId = uid,
        )
        val row = resolveWithCache(cacheRequest)
        return buildResponse(row = row, currentVersion = currentVersion)
    }

    // 长周期兜底刷新，仅回填正向缓存，实时一致性由写路径负责
    @Scheduled(fixedDelayString = "\${bkrepo.client-version-config.cache-refresh-ms:21600000}")
    @SchedulerLock(name = "ClientVersionConfigCacheRefresh", lockAtMostFor = "PT30M")
    fun refreshCache() {
        clientVersionConfigDao.forEachInBatches(REFRESH_PAGE_SIZE) { record ->
            try {
                record.cacheCurrentState()
                record.cacheUserScopeExistsIfNeeded()
            } catch (e: Exception) {
                logger.warn(
                    "[client-upgrade-cache] Refresh failed for id={}",
                    record.id,
                    e,
                )
            }
        }
    }

    companion object {
        private const val BATCH_UPSERT_MAX_SIZE = 50
        private const val REFRESH_PAGE_SIZE = 500
        private val VERSION_REGEX = Regex(
            pattern = (
                "^(?<major>\\d+)\\.(?<minor>\\d+)\\.(?<patch>\\d+)" +
                    "(?<preRelease>(?:[-.][0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?)?$"
                ),
            option = RegexOption.IGNORE_CASE,
        )
        private val logger = LoggerFactory.getLogger(ClientVersionConfigServiceImpl::class.java)
    }

    private fun resolveWithCache(
        request: ClientVersionConfigCache.UpgradeCacheRequest,
    ): TClientVersionConfig? {
        val bestGlobal = getOrLoadGlobal(request.toGlobalCacheRequest())
        if (!getOrLoadUserScopeExists(request.toUserScopeRequest())) {
            return bestGlobal
        }
        val bestUser = getOrLoadUser(request)
        return pickBetter(bestUser, bestGlobal)
    }

    private fun getOrLoadGlobal(
        request: ClientVersionConfigCache.GlobalCacheRequest,
    ): TClientVersionConfig? {
        clientVersionConfigCache.getGlobal(request)?.let { return it.value }
        return clientVersionConfigDao.findByKey(request.productId, request.platform, request.arch, null)
            ?.takeIf { it.enabled }
            .also { clientVersionConfigCache.putGlobal(request, it) }
    }

    private fun getOrLoadUser(
        request: ClientVersionConfigCache.UpgradeCacheRequest,
    ): TClientVersionConfig? {
        clientVersionConfigCache.getUser(request)?.let { return it.value }
        return clientVersionConfigDao.findByKey(
            request.productId, request.platform, request.arch, request.targetUserId,
        )?.takeIf { it.enabled }
            .also { clientVersionConfigCache.putUser(request, it) }
    }

    private fun getOrLoadUserScopeExists(
        request: ClientVersionConfigCache.UserScopeRequest,
    ): Boolean {
        clientVersionConfigCache.getUserScopeExists(request)?.let { return it }
        return clientVersionConfigDao.existsEnabledUserConfigByScope(
            productId = request.productId,
            platform = request.platform,
            arch = request.arch,
        ).also { clientVersionConfigCache.putUserScopeExists(request, it) }
    }

    private fun pickBetter(a: TClientVersionConfig?, b: TClientVersionConfig?): TClientVersionConfig? {
        if (a == null) return b
        if (b == null) return a
        return if (compareVersion(a.latestVersion, b.latestVersion) >= 0) a else b
    }

    private fun ClientVersionConfigCache.UpgradeCacheRequest.toGlobalCacheRequest() =
        ClientVersionConfigCache.GlobalCacheRequest(
            productId = productId,
            platform = platform,
            arch = arch,
        )

    private fun ClientVersionConfigCache.UpgradeCacheRequest.toUserScopeRequest() =
        ClientVersionConfigCache.UserScopeRequest(
            productId = productId,
            platform = platform,
            arch = arch,
        )

    private fun buildResponse(row: TClientVersionConfig?, currentVersion: String): ClientUpgradeCheckResponse {
        row ?: return ClientUpgradeCheckResponse(
            repositoryManaged = false,
            needUpgrade = false,
            forceUpgrade = false,
            latestVersion = null,
            downloadUrl = null,
            releaseNotes = null,
        )
        val cv = currentVersion.trim()
        val latest = row.latestVersion
        val need = compareVersion(cv, latest) < 0
        val force = row.minVersion?.takeIf { it.isNotBlank() }?.let { min ->
            compareVersion(left = cv, right = min) < 0
        } ?: false
        return ClientUpgradeCheckResponse(
            repositoryManaged = true,
            needUpgrade = need || force,
            forceUpgrade = force,
            latestVersion = latest,
            downloadUrl = row.downloadUrl,
            releaseNotes = row.releaseNotes,
        )
    }

    private fun compareVersion(left: String, right: String): Int {
        val leftVersion = parseComparableVersion(left)
        val rightVersion = parseComparableVersion(right)
        if (leftVersion != null && rightVersion != null) {
            return leftVersion.compareTo(rightVersion)
        }
        return left.compareTo(right)
    }

    private fun parseComparableVersion(value: String): ComparableVersion? {
        val normalized = value.trim().removePrefix("v").removePrefix("V")
        val parsed = VERSION_REGEX.matchEntire(normalized) ?: return null
        val preRelease = parsed.groups["preRelease"]?.value
            ?.trimStart('-', '.')
            ?.takeIf { it.isNotBlank() }
            ?.split('.')
            ?.map { VersionIdentifier.parse(it) }
            .orEmpty()
        return ComparableVersion(
            major = parsed.groups["major"]!!.value.toInt(),
            minor = parsed.groups["minor"]!!.value.toInt(),
            patch = parsed.groups["patch"]!!.value.toInt(),
            preRelease = preRelease,
        )
    }

    private fun syncCacheAfterUpsert(
        oldSnapshot: TClientVersionConfig?,
        savedEntity: TClientVersionConfig,
    ) {
        if (oldSnapshot != null && oldSnapshot.cacheIdentity() != savedEntity.cacheIdentity()) {
            oldSnapshot.cacheAsNotFound()
        }
        savedEntity.cacheCurrentState()
        oldSnapshot?.refreshUserScopeExistsCache()
        savedEntity.refreshUserScopeExistsCache()
    }

    private fun persistUpsert(userId: String, command: UpsertCommand): () -> Unit {
        val now = LocalDateTime.now()
        val existing = findUpsertTarget(command)
        val oldSnapshot = existing?.copy()
        val savedEntity: TClientVersionConfig
        try {
            if (existing == null) {
                val newEntity = command.toNewEntity(userId = userId, now = now)
                clientVersionConfigDao.insert(newEntity)
                savedEntity = newEntity
            } else {
                existing.updateFrom(command = command, userId = userId, now = now)
                clientVersionConfigDao.save(existing)
                savedEntity = existing
            }
        } catch (_: DuplicateKeyException) {
            throw ErrorCodeException(
                messageCode = CommonMessageCode.RESOURCE_EXISTED,
                command.keyConflictHint(),
            )
        }
        return {
            syncCacheAfterUpsert(oldSnapshot = oldSnapshot, savedEntity = savedEntity)
        }
    }

    private fun validateBatchUpsert(commands: List<UpsertCommand>) {
        val finalKeys = mutableSetOf<String>()
        commands.forEach { command ->
            val existing = findUpsertTarget(command)
            val byKey = clientVersionConfigDao.findByKey(
                productId = command.productId,
                platform = command.platform,
                arch = command.arch,
                targetUserId = command.targetUserId,
            )
            if (byKey != null && byKey.id != existing?.id) {
                throw ErrorCodeException(
                    messageCode = CommonMessageCode.RESOURCE_EXISTED,
                    command.keyConflictHint(),
                )
            }
            if (!finalKeys.add(command.keyIdentity())) {
                throw ErrorCodeException(
                    messageCode = CommonMessageCode.RESOURCE_EXISTED,
                    command.keyConflictHint(),
                )
            }
        }
    }

    /** enabled=true 时将记录写入缓存；enabled=false 时写入 null sentinel */
    private fun TClientVersionConfig.cacheCurrentState() {
        putToCache(if (enabled) this else null)
    }

    /** 记录已删除，写入 null sentinel */
    private fun TClientVersionConfig.cacheAsNotFound() {
        putToCache(null)
    }

    private fun TClientVersionConfig.refreshUserScopeExistsCache() {
        if (targetUserId == null) {
            return
        }
        clientVersionConfigCache.putUserScopeExists(
            ClientVersionConfigCache.UserScopeRequest(
                productId = productId,
                platform = platform,
                arch = arch,
            ),
            clientVersionConfigDao.existsEnabledUserConfigByScope(
                productId = productId,
                platform = platform,
                arch = arch,
            ),
        )
    }

    private fun TClientVersionConfig.cacheIdentity(): String {
        return listOf(productId, platform, arch, targetUserId.orEmpty()).joinToString("|")
    }

    private fun TClientVersionConfig.cacheUserScopeExistsIfNeeded() {
        if (!enabled || targetUserId == null) {
            return
        }
        clientVersionConfigCache.putUserScopeExists(
            ClientVersionConfigCache.UserScopeRequest(
                productId = productId,
                platform = platform,
                arch = arch,
            ),
            true,
        )
    }

    private fun TClientVersionConfig.putToCache(record: TClientVersionConfig?) {
        if (targetUserId == null) {
            clientVersionConfigCache.putGlobal(
                ClientVersionConfigCache.GlobalCacheRequest(productId, platform, arch),
                record,
            )
        } else {
            clientVersionConfigCache.putUser(
                ClientVersionConfigCache.UpgradeCacheRequest(
                    productId,
                    platform,
                    arch,
                    targetUserId!!,
                ),
                record,
            )
        }
    }

    private fun TClientVersionConfig.toVo() = ClientVersionConfigVo(
        id = id,
        productId = productId,
        platform = platform,
        arch = arch,
        targetUserId = targetUserId.orEmpty(),
        minVersion = minVersion,
        latestVersion = latestVersion,
        downloadUrl = downloadUrl,
        releaseNotes = releaseNotes,
        enabled = enabled,
        createdBy = createdBy,
        createdDate = createdDate,
        lastModifiedBy = lastModifiedBy,
        lastModifiedDate = lastModifiedDate,
    )

    private fun normalizeKey(value: String?): String = value?.trim()?.lowercase().orEmpty()

    private fun normalizeNotBlank(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }

    private fun ClientVersionConfigUpsertRequest.toUpsertCommand(): UpsertCommand {
        return UpsertCommand(
            id = normalizeNotBlank(id),
            productId = normalizeKey(productId),
            platform = normalizeKey(platform),
            arch = normalizeKey(arch),
            targetUserId = normalizeNotBlank(targetUserId),
            minVersion = normalizeNotBlank(minVersion),
            latestVersion = latestVersion.trim(),
            downloadUrl = downloadUrl.trim(),
            releaseNotes = normalizeNotBlank(releaseNotes),
            enabled = enabled,
        )
    }

    private fun validateCheckUpgradeParams(
        userId: String,
        currentVersion: String,
        productId: String,
        platform: String,
        arch: String,
    ) {
        if (userId.isBlank()) {
            throw BadRequestException(code = CommonMessageCode.PARAMETER_EMPTY, "userId")
        }
        val pid = normalizeKey(productId)
        if (pid.isBlank() || currentVersion.isBlank() || platform.isBlank()) {
            throw BadRequestException(
                code = CommonMessageCode.PARAMETER_INVALID,
                "productId, currentVersion, platform",
            )
        }
        if (normalizeKey(arch).isBlank()) {
            throw BadRequestException(code = CommonMessageCode.PARAMETER_EMPTY, "arch")
        }
    }

    private fun validateUpsertCommand(command: UpsertCommand) {
        if (command.productId.isBlank() || command.platform.isBlank() || command.arch.isBlank()) {
            throw BadRequestException(
                code = CommonMessageCode.PARAMETER_INVALID,
                "productId, platform or arch",
            )
        }
        if (command.latestVersion.isBlank() || command.downloadUrl.isBlank()) {
            throw BadRequestException(
                code = CommonMessageCode.PARAMETER_INVALID,
                "latestVersion or downloadUrl",
            )
        }
    }

    private fun findUpsertTarget(command: UpsertCommand): TClientVersionConfig? {
        command.id?.let { return findRequiredRecord(it) }
        return clientVersionConfigDao.findByKey(
            productId = command.productId,
            platform = command.platform,
            arch = command.arch,
            targetUserId = command.targetUserId,
        )
    }

    private fun findRequiredRecord(id: String): TClientVersionConfig {
        return clientVersionConfigDao.findById(id)
            ?: throw ErrorCodeException(messageCode = CommonMessageCode.RESOURCE_NOT_FOUND, id)
    }

    private fun TClientVersionConfig.updateFrom(command: UpsertCommand, userId: String, now: LocalDateTime) {
        lastModifiedBy = userId
        lastModifiedDate = now
        productId = command.productId
        platform = command.platform
        arch = command.arch
        targetUserId = command.targetUserId
        minVersion = command.minVersion
        latestVersion = command.latestVersion
        downloadUrl = command.downloadUrl
        releaseNotes = command.releaseNotes
        enabled = command.enabled
    }

    private fun UpsertCommand.toNewEntity(userId: String, now: LocalDateTime): TClientVersionConfig {
        return TClientVersionConfig(
            id = null,
            createdBy = userId,
            createdDate = now,
            lastModifiedBy = userId,
            lastModifiedDate = now,
            productId = productId,
            platform = platform,
            arch = arch,
            targetUserId = targetUserId,
            minVersion = minVersion,
            latestVersion = latestVersion,
            downloadUrl = downloadUrl,
            releaseNotes = releaseNotes,
            enabled = enabled,
        )
    }

    private fun UpsertCommand.keyConflictHint(): String {
        val who = targetUserId?.let { "user:$it" } ?: "global"
        return "$productId/$platform/$arch/$who"
    }

    private fun UpsertCommand.keyIdentity(): String {
        return listOf(productId, platform, arch, targetUserId.orEmpty()).joinToString("|")
    }

    private data class UpsertCommand(
        val id: String?,
        val productId: String,
        val platform: String,
        val arch: String,
        val targetUserId: String?,
        val minVersion: String?,
        val latestVersion: String,
        val downloadUrl: String,
        val releaseNotes: String?,
        val enabled: Boolean,
    )

    private data class ComparableVersion(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val preRelease: List<VersionIdentifier>,
    ) : Comparable<ComparableVersion> {
        override fun compareTo(other: ComparableVersion): Int {
            val majorCompare = major.compareTo(other.major)
            if (majorCompare != 0) {
                return majorCompare
            }
            val minorCompare = minor.compareTo(other.minor)
            if (minorCompare != 0) {
                return minorCompare
            }
            val patchCompare = patch.compareTo(other.patch)
            if (patchCompare != 0) {
                return patchCompare
            }
            return comparePreRelease(preRelease, other.preRelease)
        }

        companion object {
            private fun comparePreRelease(
                left: List<VersionIdentifier>,
                right: List<VersionIdentifier>,
            ): Int {
                if (left.isEmpty() && right.isEmpty()) {
                    return 0
                }
                if (left.isEmpty()) {
                    return 1
                }
                if (right.isEmpty()) {
                    return -1
                }
                val size = minOf(left.size, right.size)
                repeat(size) { index ->
                    val result = left[index].compareTo(right[index])
                    if (result != 0) {
                        return result
                    }
                }
                return left.size.compareTo(right.size)
            }
        }
    }

    private sealed interface VersionIdentifier : Comparable<VersionIdentifier> {
        data class Numeric(val value: String) : VersionIdentifier {
            override fun compareTo(other: VersionIdentifier): Int {
                return when (other) {
                    is Numeric -> {
                        val left = normalize(value)
                        val right = normalize(other.value)
                        left.length.compareTo(right.length)
                            .takeIf { it != 0 }
                            ?: left.compareTo(right)
                    }
                    is Text -> -1
                }
            }
        }

        data class Text(val value: String) : VersionIdentifier {
            override fun compareTo(other: VersionIdentifier): Int {
                return when (other) {
                    is Numeric -> 1
                    is Text -> value.compareTo(other.value, ignoreCase = true)
                }
            }
        }

        companion object {
            private fun normalize(value: String): String {
                return value.trimStart('0').ifEmpty { "0" }
            }

            fun parse(value: String): VersionIdentifier {
                return if (value.all { it.isDigit() }) {
                    Numeric(value)
                } else {
                    Text(value)
                }
            }
        }
    }
}
