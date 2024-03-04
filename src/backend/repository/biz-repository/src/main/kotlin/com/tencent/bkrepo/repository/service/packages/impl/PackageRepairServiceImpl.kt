package com.tencent.bkrepo.repository.service.packages.impl

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.util.HumanReadable
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.metadata.dao.PackageDao
import com.tencent.bkrepo.common.metadata.dao.PackageVersionDao
import com.tencent.bkrepo.common.metadata.model.TPackage
import com.tencent.bkrepo.repository.pojo.packages.VersionListOption
import com.tencent.bkrepo.repository.service.packages.PackageRepairService
import com.tencent.bkrepo.common.metadata.service.packages.PackageService
import com.tencent.bkrepo.common.metadata.util.PackageQueryHelper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDateTime
import kotlin.system.measureNanoTime

@Service
class PackageRepairServiceImpl(
    private val packageService: PackageService,
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

    private fun doRepairPackageHistoryVersion(tPackage: TPackage) {
        with(tPackage) {
            val allVersion = packageService.listAllVersion(projectId, repoName, key, VersionListOption())
                .map { it.name }
            historyVersion = historyVersion.toMutableSet().apply { addAll(allVersion) }
            packageDao.save(this)
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
    }
}
