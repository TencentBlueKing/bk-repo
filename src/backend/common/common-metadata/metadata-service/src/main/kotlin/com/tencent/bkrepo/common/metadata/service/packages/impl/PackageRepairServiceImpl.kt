package com.tencent.bkrepo.common.metadata.service.packages.impl

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.util.HumanReadable
import com.tencent.bkrepo.common.artifact.exception.PackageNotFoundException
import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.metadata.dao.packages.PackageDao
import com.tencent.bkrepo.common.metadata.dao.packages.PackageVersionDao
import com.tencent.bkrepo.common.metadata.model.TPackage
import com.tencent.bkrepo.common.metadata.service.packages.PackageRepairService
import com.tencent.bkrepo.common.metadata.util.PackageQueryHelper
import com.tencent.bkrepo.repository.pojo.packages.PackageMetadataRepairResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Conditional
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDateTime
import kotlin.system.measureNanoTime

@Service
@Conditional(SyncCondition::class)
class PackageRepairServiceImpl(
    private val packageDao: PackageDao,
    private val packageVersionDao: PackageVersionDao
) : PackageRepairService {
    override fun repairHistoryVersion() {
        // 查询仓库下面的所有package的包
        var successCount = 0L
        var failedCount = 0L
        var totalCount = 0L
        val startTime = LocalDateTime.now()

        // 查询所有的包
        logger.info("starting repair package history version.")
        // 分页查询包信息
        var page = 1
        val packagePage = queryPackage(page)
        var packageList = packagePage.records
        val total = packagePage.totalRecords
        if (packageList.isEmpty()) {
            logger.info("no package found, return.")
            return
        }
        while (packageList.isNotEmpty()) {
            packageList.forEach {
                logger.info(
                    "Retrieved $total packages to repair history version," +
                        " process: $totalCount/$total"
                )
                val projectId = it.projectId
                val repoName = it.repoName
                val key = it.key
                try {
                    // 只修 historyVersion，不动 latest，保持全库入口的原始语义
                    doRepairPackageHistoryVersion(it)
                    logger.info("Success to repair history version for [$key] in repo [$projectId/$repoName].")
                    successCount += 1
                } catch (exception: RuntimeException) {
                    logger.error(
                        "Failed to repair history version for [$key] in repo [$projectId/$repoName]," +
                            " message: $exception"
                    )
                    failedCount += 1
                } finally {
                    totalCount += 1
                }
            }
            page += 1
            packageList = queryPackage(page).records
        }
        val durationSeconds = Duration.between(startTime, LocalDateTime.now()).seconds
        logger.info(
            "Repair package history version, " +
                "total: $totalCount, success: $successCount, failed: $failedCount," +
                " duration $durationSeconds s totally."
        )
    }

    override fun repairVersionCount() {
        var updated = 0L
        var failed = 0L
        var total = 0L
        var pageNumber = 1

        measureNanoTime {
            while (true) {
                val packageList = queryPackage(pageNumber++).records.takeIf { it.isNotEmpty() } ?: break
                total += packageList.size
                logger.info("Retrieved ${packageList.size} packages to recount version")
                packageList.forEach {
                    try {
                        val result = updateVersionCount(it) ?: return@forEach
                        if (result) {
                            updated += 1
                        } else {
                            logger.error("Failed to update package[${it.key}] in repo[${it.projectId}/${it.repoName}]")
                            failed += 1
                        }
                    } catch (e: Exception) {
                        logger.error(
                            "Failed to recount or update. Package[${it.key}]. Repo[${it.projectId}/${it.repoName}]. " +
                            "Message: ${e.message}"
                        )
                        failed += 1
                    }
                }
            }
        }.apply {
            val elapsedTime = HumanReadable.time(this)
            logger.info(
                "Recounted version of all packages. Total recounted[$total], " +
                        "including updated[$updated], failed[$failed]. Elapse[$elapsedTime]"
            )
        }
    }

    override fun repairPackageMetadata(
        projectId: String,
        repoName: String,
        packageKey: String?
    ): PackageMetadataRepairResult {
        logger.info(
            "Start repair package metadata. projectId=[$projectId], repoName=[$repoName], " +
                "packageKey=[${packageKey ?: "<ALL>"}]"
        )
        val startTime = LocalDateTime.now()
        val failedItems = mutableListOf<PackageMetadataRepairResult.FailedItem>()
        var total = 0
        var updated = 0
        var skipped = 0

        val packageIterator: Sequence<TPackage> = if (!packageKey.isNullOrBlank()) {
            val pkg = packageDao.findByKey(projectId, repoName, packageKey)
                ?: throw PackageNotFoundException("$projectId/$repoName/$packageKey")
            sequenceOf(pkg)
        } else {
            iterateRepoPackages(projectId, repoName)
        }

        packageIterator.forEach { pkg ->
            total += 1
            try {
                val result = doRepairPackageMetadata(pkg)
                if (result) {
                    updated += 1
                } else {
                    skipped += 1
                }
            } catch (e: Exception) {
                logger.error(
                    "Failed to repair metadata for package [${pkg.key}] " +
                        "in repo [$projectId/$repoName]: ${e.message}",
                    e
                )
                failedItems.add(
                    PackageMetadataRepairResult.FailedItem(
                        packageKey = pkg.key,
                        reason = e.message
                    )
                )
            }
        }

        val duration = Duration.between(startTime, LocalDateTime.now()).seconds
        logger.info(
            "Finish repair package metadata. projectId=[$projectId], repoName=[$repoName], " +
                "total=$total, updated=$updated, skipped=$skipped, failed=${failedItems.size}, " +
                "elapse=${duration}s."
        )
        return PackageMetadataRepairResult(
            total = total,
            updated = updated,
            skipped = skipped,
            failed = failedItems.size,
            failedItems = failedItems
        )
    }

    /**
     * 以 package_version 为权威源修复单个 package 的 latest 与 historyVersion。
     *
     * @return true 表示实际发生 DB 更新，false 表示元数据已一致
     */
    private fun doRepairPackageMetadata(tPackage: TPackage): Boolean {
        val packageId = tPackage.id ?: return false
        val query = PackageQueryHelper.packageQuery(tPackage.projectId, tPackage.repoName, tPackage.key)
        val latestMutated = repairLatestField(tPackage, packageId, query)
        val historyMutated = doRepairPackageHistoryVersion(tPackage)
        return latestMutated || historyMutated
    }

    /** 若 latest 与 [PackageVersionDao.findLatest]（ordinal DESC）不一致，则 `$set` 覆盖。 */
    private fun repairLatestField(tPackage: TPackage, packageId: String, query: Query): Boolean {
        val actualLatest: String? = packageVersionDao.findLatest(packageId)?.name
        if (tPackage.latest == actualLatest) return false
        val r = packageDao.updateFirst(query, Update().set(TPackage::latest.name, actualLatest))
        logger.info(
            "Repair latest for [${tPackage.key}] in repo " +
                "[${tPackage.projectId}/${tPackage.repoName}]: [${tPackage.latest}] -> [$actualLatest]"
        )
        return r.modifiedCount > 0L
    }

    /**
     * 分批修复 historyVersion：补差 [addMissingVersionNames] + 删差 [removeOrphanVersionNames]，
     * 等价于以 package_version 为源全量覆盖。单批 payload 恒为 O([REPAIR_VERSION_BATCH_SIZE])，
     * 与包版本总数解耦，可安全处理 10w+ 版本的大包。
     *
     * @return true 表示触发过 DB 更新
     */
    private fun doRepairPackageHistoryVersion(tPackage: TPackage): Boolean {
        val packageId = tPackage.id ?: return false
        val originalHistory = tPackage.historyVersion

        val (addMutated, addedCount) = addMissingVersionNames(packageId, originalHistory)
        val (removeMutated, removedCount) = removeOrphanVersionNames(packageId, originalHistory)

        if (addedCount > 0 || removedCount > 0) {
            logger.info(
                "Repair historyVersion for [${tPackage.key}] in repo " +
                    "[${tPackage.projectId}/${tPackage.repoName}]: " +
                    "originalSize=${originalHistory.size}, added=$addedCount, removed=$removedCount"
            )
        }
        return addMutated || removeMutated
    }

    /**
     * 补差：按 `_id` 游标分页读 package_version，将 [originalHistory] 中缺失的 name 分批 `$addToSet`。
     * 用应用侧 [HashSet] 跨批去重仅为让计数精确，DB 侧 `$addToSet` 天然幂等。
     */
    private fun addMissingVersionNames(
        packageId: String,
        originalHistory: Set<String>
    ): Pair<Boolean, Int> {
        var mutated = false
        var addedCount = 0
        val addBuffer = ArrayList<String>(REPAIR_VERSION_BATCH_SIZE)
        val addedNames = HashSet<String>()
        var lastId: String? = null

        while (true) {
            val batch = packageVersionDao.pageVersionNamesAfterId(packageId, lastId, REPAIR_VERSION_BATCH_SIZE)
            if (batch.isEmpty()) break
            batch.forEach { (_, name) ->
                if (name !in originalHistory && addedNames.add(name)) addBuffer.add(name)
            }
            lastId = batch.last().first
            if (addBuffer.isNotEmpty()) {
                // 传入快照拷贝而非复用 buffer，避免下游持有引用后被 clear 清空
                if (packageDao.appendHistoryVersions(packageId, ArrayList(addBuffer))) mutated = true
                addedCount += addBuffer.size
                addBuffer.clear()
            }
            if (batch.size < REPAIR_VERSION_BATCH_SIZE) break
        }
        return mutated to addedCount
    }

    /**
     * 删差：将 [originalHistory] 按 [REPAIR_VERSION_BATCH_SIZE] 分块反查存在性，
     * 不存在的分批 `$pullAll`。仅清理修复前已存在的 name，与补差新增的天然不相交。
     */
    private fun removeOrphanVersionNames(
        packageId: String,
        originalHistory: Set<String>
    ): Pair<Boolean, Int> {
        var mutated = false
        var removedCount = 0
        originalHistory.chunked(REPAIR_VERSION_BATCH_SIZE).forEach { chunk ->
            val existing = packageVersionDao.findExistingNames(packageId, chunk)
            val orphans = chunk.filter { it !in existing }
            if (orphans.isNotEmpty()) {
                if (packageDao.removeHistoryVersions(packageId, orphans)) mutated = true
                removedCount += orphans.size
            }
        }
        return mutated to removedCount
    }

    /**
     * 按仓库范围分页遍历 package，返回 Sequence 以支持惰性消费。
     */
    private fun iterateRepoPackages(projectId: String, repoName: String): Sequence<TPackage> = sequence {
        var page = 1
        while (true) {
            val pageResult = queryPackageByRepo(projectId, repoName, page)
            if (pageResult.records.isEmpty()) break
            yieldAll(pageResult.records)
            if (pageResult.records.size < REPAIR_PAGE_SIZE) break
            page += 1
        }
    }

    private fun queryPackageByRepo(projectId: String, repoName: String, page: Int): Page<TPackage> {
        val criteria = where(TPackage::projectId).isEqualTo(projectId)
            .and(TPackage::repoName.name).isEqualTo(repoName)
        val query = Query(criteria).with(Sort.by(Sort.Direction.ASC, TPackage::key.name))
        val totalRecords = packageDao.count(query)
        val pageRequest = Pages.ofRequest(page, REPAIR_PAGE_SIZE)
        val records = packageDao.find(query.with(pageRequest))
        return Pages.ofResponse(pageRequest, totalRecords, records)
    }

    private fun updateVersionCount(tPackage: TPackage): Boolean? {
        val actualCount = packageVersionDao.countVersion(tPackage.id!!)
        return if (actualCount == tPackage.versions) {
            null
        } else {
            logger.info(
                "Updating version count of package[${tPackage.key}] " +
                    "in repo[${tPackage.projectId}/${tPackage.repoName}] (${tPackage.versions} -> $actualCount)"
            )
            val query = PackageQueryHelper.packageQuery(tPackage.projectId, tPackage.repoName, tPackage.key)
            val update = Update().set(TPackage::versions.name, actualCount)
            packageDao.updateFirst(query, update).modifiedCount == 1L
        }
    }

    private fun queryPackage(page: Int): Page<TPackage> {
        val query = Query().with(
            Sort.by(Sort.Direction.DESC, TPackage::projectId.name, TPackage::repoName.name, TPackage::key.name)
        )
        val totalRecords = packageDao.count(query)
        val pageRequest = Pages.ofRequest(page, 10000)
        val records = packageDao.find(query.with(pageRequest))
        return Pages.ofResponse(pageRequest, totalRecords, records)
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(PackageRepairServiceImpl::class.java)
        private const val REPAIR_PAGE_SIZE = 500

        /**
         * 修复 historyVersion 的分批大小，同时用于分页读取 package_version 与分块反查脏数据。
         * 取 1000：mongo `$addToSet.$each` / `$in` / `$pullAll` 在此量级下 payload 与性能最平衡。
         */
        private const val REPAIR_VERSION_BATCH_SIZE = 1_000
    }
}
