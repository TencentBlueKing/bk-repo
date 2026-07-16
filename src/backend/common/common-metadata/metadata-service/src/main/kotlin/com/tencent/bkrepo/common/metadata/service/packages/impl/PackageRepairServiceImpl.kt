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
                    // 添加包管理
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
     * 修复单个 package 的 latest 与 historyVersion 字段。
     *
     * 以 package_version 集合为权威数据源：
     * - latest 复用 [PackageVersionDao.findLatest]（ordinal DESC + limit 1），
     *   与主流程 findLatestBySemVer / deleteVersion 后重算 latest 使用同一套逻辑，
     *   避免修复行为与业务上传/删除时的语义出现分歧。
     * - historyVersion = 当前所有版本名的集合，全量覆盖。
     *
     * @return true 表示实际发生了 DB 更新；false 表示元数据已一致，无需更新
     */
    private fun doRepairPackageMetadata(tPackage: TPackage): Boolean {
        val packageId = tPackage.id ?: return false
        // 仅需要版本名集合，走 projection 查询降低 IO/内存开销（避免全量加载 TPackageVersion 文档）
        val actualHistoryVersion: Set<String> = packageVersionDao.listVersionNamesByPackageId(packageId).toSet()
        val actualLatest: String? = packageVersionDao.findLatest(packageId)?.name

        val latestEquals = tPackage.latest == actualLatest
        val historyEquals = tPackage.historyVersion == actualHistoryVersion
        if (latestEquals && historyEquals) {
            return false
        }

        val query = PackageQueryHelper.packageQuery(tPackage.projectId, tPackage.repoName, tPackage.key)
        // latest 允许为 null（无版本时置 null），保持字段存在以便查询语义一致
        val update = Update()
            .set(TPackage::historyVersion.name, actualHistoryVersion)
            .set(TPackage::latest.name, actualLatest)
        val result = packageDao.updateFirst(query, update)
        logger.info(
            "Repair package metadata for [${tPackage.key}] in repo " +
                "[${tPackage.projectId}/${tPackage.repoName}]: " +
                "latest [${tPackage.latest}] -> [$actualLatest], " +
                "historyVersion size [${tPackage.historyVersion.size}] -> [${actualHistoryVersion.size}], " +
                "modified=${result.modifiedCount}"
        )
        return result.modifiedCount > 0L
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

    /**
     * 全量覆盖 package 的 historyVersion 字段，清理已不存在于 package_version 的脏数据。
     *
     * 使用 updateFirst 只更新 historyVersion 一个字段，避免 save 整个文档时把并发写入
     * 的 latest / versions 等字段覆盖回旧值造成 lost-update。
     */
    private fun doRepairPackageHistoryVersion(tPackage: TPackage) {
        with(tPackage) {
            // 直接查 package_version 集合并投影 name，避免经 Service 层拉整份 PackageVersion pojo
            val packageId = id ?: return
            val allVersion = packageVersionDao.listVersionNamesByPackageId(packageId).toSet()
            val query = PackageQueryHelper.packageQuery(projectId, repoName, key)
            val update = Update().set(TPackage::historyVersion.name, allVersion)
            packageDao.updateFirst(query, update)
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
    }
}
