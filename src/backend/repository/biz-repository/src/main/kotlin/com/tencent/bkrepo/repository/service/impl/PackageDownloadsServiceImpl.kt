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

package com.tencent.bkrepo.repository.service.impl

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.repository.dao.PackageDao
import com.tencent.bkrepo.repository.dao.PackageDownloadsDao
import com.tencent.bkrepo.repository.dao.PackageVersionDao
import com.tencent.bkrepo.repository.model.TPackage
import com.tencent.bkrepo.repository.model.TPackageDownloads
import com.tencent.bkrepo.repository.model.TPackageVersion
import com.tencent.bkrepo.repository.pojo.download.DownloadsQueryRequest
import com.tencent.bkrepo.repository.pojo.download.PackageDownloadRecord
import com.tencent.bkrepo.repository.pojo.download.PackageDownloadsDetails
import com.tencent.bkrepo.repository.pojo.download.PackageDownloadsSummary
import com.tencent.bkrepo.repository.service.PackageDownloadsService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class PackageDownloadsServiceImpl(
    private val packageDao: PackageDao,
    private val packageVersionDao: PackageVersionDao,
    private val packageDownloadsDao: PackageDownloadsDao
) : PackageDownloadsService, AbstractService() {

    override fun record(record: PackageDownloadRecord) {
        with(record) {
            val tPackage = checkPackage(projectId, repoName, packageKey)
            checkPackageVersion(tPackage.id.orEmpty(), packageVersion)

            // update package downloads
            val downloadsCriteria = downloadsCriteria(projectId, repoName, packageKey, packageVersion)
                .and(TPackageDownloads::date).isEqualTo(LocalDate.now().toString())
            val downloadsQuery = Query(downloadsCriteria)
            val downloadsUpdate = Update().inc(TPackageDownloads::count.name, 1)
                .setOnInsert(TPackageDownloads::name.name, tPackage.name)
            packageDownloadsDao.upsert(downloadsQuery, downloadsUpdate)

            // update package
            val versionQuery = Query(versionCriteria(tPackage.id.orEmpty(), packageVersion))
            val versionUpdate = Update().inc(TPackageVersion::downloads.name, 1)
            packageVersionDao.updateFirst(versionQuery, versionUpdate)

            // update package version
            val packageQuery = Query(packageCriteria(projectId, repoName, packageKey))
            val packageUpdate = Update().inc(TPackage::downloads.name, 1)
            packageDao.updateFirst(packageQuery, packageUpdate)

            if (logger.isDebugEnabled) {
                logger.debug("Create artifact download statistics [$record] success.")
            }
        }
    }

    override fun queryDetails(request: DownloadsQueryRequest): PackageDownloadsDetails {
        // 最早查询前三个月
        with(request) {
            val tPackage = checkPackage(projectId, repoName, packageKey)
            packageVersion?.let { checkPackageVersion(tPackage.id!!, it) }

            val today = LocalDate.now()
            val earliestDate = today.minusMonths(MAX_MONTH_TIME_SPAN)
            var normalizedFromDate = fromDate ?: today
            var normalizedToDate = toDate ?: today
            normalizedFromDate = if (normalizedFromDate.isBefore(earliestDate)) earliestDate else normalizedFromDate
            normalizedToDate = if (normalizedToDate.isAfter(today)) today else normalizedToDate

            val downloadsCriteria = downloadsCriteria(projectId, repoName, packageKey, packageVersion)
                .and(TPackageDownloads::date)
                    .gte(normalizedFromDate.toString())
                    .lte(normalizedToDate.toString())
            val aggregation = Aggregation.newAggregation(
                Aggregation.match(downloadsCriteria),
                Aggregation.group(TPackageDownloads::date.name)
                    .sum(TPackageDownloads::count.name).`as`(TPackageDownloads::count.name)
            )
            val result = packageDownloadsDao.aggregate(aggregation, HashMap::class.java)
            TODO("")
        }
    }

    override fun querySummary(request: DownloadsQueryRequest): PackageDownloadsSummary {
        with(request) {
            val tPackage = checkPackage(projectId, repoName, packageKey)
            packageVersion?.let { checkPackageVersion(tPackage.id!!, it) }

            TODO("")
        }
    }

    private fun downloadsCriteria(
        projectId: String,
        repoName: String,
        packageKey: String,
        version: String? = null
    ): Criteria {
        return where(TPackageDownloads::projectId).isEqualTo(projectId)
            .and(TPackageDownloads::repoName).isEqualTo(repoName)
            .and(TPackageDownloads::key).isEqualTo(packageKey)
            .apply {
                version?.let { and(TPackageDownloads::version.name).`is`(it) }
            }
    }

    private fun packageCriteria(projectId: String, repoName: String, packageKey: String): Criteria {
        return where(TPackage::projectId).isEqualTo(projectId)
            .and(TPackage::repoName).isEqualTo(repoName)
            .and(TPackage::key).isEqualTo(packageKey)
    }

    private fun versionCriteria(packageId: String, name: String): Criteria {
        return where(TPackageVersion::packageId).isEqualTo(packageId)
            .and(TPackageVersion::name).isEqualTo(name)
    }

    /**
     * 查找包，不存在则抛异常
     */
    private fun checkPackage(projectId: String, repoName: String, packageKey: String): TPackage {
        return packageDao.findByKey(projectId, repoName, packageKey)
            ?: throw ErrorCodeException(ArtifactMessageCode.PACKAGE_NOT_FOUND, packageKey)
    }

    /**
     * 查找版本，不存在则抛异常
     */
    private fun checkPackageVersion(packageId: String, versionName: String): TPackageVersion {
        return packageVersionDao.findByName(packageId, versionName)
            ?: throw ErrorCodeException(ArtifactMessageCode.VERSION_NOT_FOUND, packageId, versionName)
    }

    companion object {

        private val logger: Logger = LoggerFactory.getLogger(PackageDownloadsServiceImpl::class.java)

        /**
         * 最大的时间跨度（月份数量）
         */
        private const val MAX_MONTH_TIME_SPAN = 3L
    }
}
