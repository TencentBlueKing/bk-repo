/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.analyst.service.impl

import com.tencent.bkrepo.analyst.dao.SpdxLicenseDao
import com.tencent.bkrepo.analyst.exception.LicenseNotFoundException
import com.tencent.bkrepo.analyst.model.TSpdxLicense
import com.tencent.bkrepo.analyst.pojo.license.SpdxLicenseInfo
import com.tencent.bkrepo.analyst.pojo.license.SpdxLicenseJsonInfo
import com.tencent.bkrepo.analyst.service.SpdxLicenseService
import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.artifact.manager.StorageManager
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.api.StorageCredentialsClient
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class SpdxLicenseServiceImpl(
    private val licenseDao: SpdxLicenseDao,
    private val nodeClient: NodeClient,
    private val repositoryClient: RepositoryClient,
    private val storageCredentialsClient: StorageCredentialsClient,
    private val storageManager: StorageManager,
) : SpdxLicenseService {
    override fun importLicense(path: String): Boolean {
        val licenseJsonInfo = try {
            JsonUtils.objectMapper.readValue(File(path), SpdxLicenseJsonInfo::class.java)
        } catch (e: FileNotFoundException) {
            logger.error("import license data failed, file path [$path] not found ", e)
            return false
        } catch (e: IOException) {
            logger.error("import license data failed", e)
            return false
        }
        importLicense(licenseJsonInfo)
        return true
    }

    override fun importLicense(projectId: String, repoName: String, fullPath: String): Boolean {
        val repo = repositoryClient.getRepoInfo(projectId, repoName).data
            ?: throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND, projectId, repoName)
        val storageCredentials = repo.storageCredentialsKey?.let { storageCredentialsClient.findByKey(it).data }
        val node = nodeClient.getNodeDetail(projectId, repoName, fullPath).data
            ?: throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND, projectId, repoName, fullPath)
        storageManager.loadFullArtifactInputStream(node, storageCredentials)?.use {
            importLicense(it.readJsonString<SpdxLicenseJsonInfo>())
        } ?: throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND, projectId, repoName, fullPath)
        return true
    }

    override fun listLicensePage(
        name: String?,
        isTrust: Boolean?,
        pageNumber: Int,
        pageSize: Int
    ): Page<SpdxLicenseInfo> {
        val criteria = Criteria()
        name?.let {
            criteria.orOperator(
                Criteria.where(TSpdxLicense::name.name).regex(".*$name.*"),
                Criteria.where(TSpdxLicense::licenseId.name).regex(".*$name.*")
            )
        }
        isTrust?.let { criteria.and(TSpdxLicense::isTrust.name).`is`(it) }
        val query = Query(criteria).with(Sort.by(TSpdxLicense::createdDate.name).descending())
        val pageRequest = Pages.ofRequest(pageNumber, pageSize)
        val totalRecords = licenseDao.count(query)
        val records = licenseDao.find(query.with(pageRequest)).map { convert(it) }
        return Pages.ofResponse(pageRequest, totalRecords, records)
    }


    override fun listLicense(): List<SpdxLicenseInfo> {
        return licenseDao.findAll().map { convert(it) }
    }

    override fun getLicenseInfo(licenseId: String): SpdxLicenseInfo? {
        return licenseDao.findByLicenseId(licenseId)?.let { convert(it) }
    }

    override fun toggleStatus(licenseId: String) {
        val tLicense = checkLicenseExist(licenseId)
        tLicense.isTrust = !tLicense.isTrust
        tLicense.lastModifiedBy = SecurityUtils.getUserId()
        tLicense.lastModifiedDate = LocalDateTime.now()
        licenseDao.save(tLicense)
    }

    override fun listLicenseByIds(licenseIds: List<String>): Map<String, SpdxLicenseInfo> {
        return licenseDao.findByLicenseIds(licenseIds, true).associate { Pair(it.licenseId, convert(it)) }
    }

    private fun importLicense(licenseInfo: SpdxLicenseJsonInfo) {
        val operator = SecurityUtils.getUserId()
        licenseInfo.licenses.forEach {
            val result = licenseDao.findByLicenseId(it.licenseId)
            if (result == null) {
                val license = TSpdxLicense(
                    createdBy = operator,
                    createdDate = LocalDateTime.now(),
                    lastModifiedBy = operator,
                    lastModifiedDate = LocalDateTime.now(),
                    name = it.name,
                    licenseId = it.licenseId,
                    seeAlso = it.seeAlso,
                    reference = it.reference,
                    isDeprecatedLicenseId = it.isDeprecatedLicenseId,
                    isOsiApproved = it.isOsiApproved,
                    isFsfLibre = it.isFsfLibre,
                    detailsUrl = it.detailsUrl
                )
                licenseDao.save(license)
            } else {
                result.lastModifiedBy = operator
                result.lastModifiedDate = LocalDateTime.now()
                result.name = it.name
                result.licenseId = it.licenseId
                result.seeAlso = it.seeAlso
                result.reference = it.reference
                result.isDeprecatedLicenseId = it.isDeprecatedLicenseId
                result.isOsiApproved = it.isOsiApproved
                result.isFsfLibre = it.isFsfLibre
                result.detailsUrl = it.detailsUrl
                licenseDao.save(result)
            }
        }
    }

    private fun checkLicenseExist(licenseId: String): TSpdxLicense {
        return licenseDao.findByLicenseId(licenseId) ?: throw LicenseNotFoundException(licenseId)
    }

    private fun convert(tSpdxLicense: TSpdxLicense): SpdxLicenseInfo {
        return with(tSpdxLicense) {
            SpdxLicenseInfo(
                createdBy = createdBy,
                createdDate = createdDate.format(DateTimeFormatter.ISO_DATE_TIME),
                lastModifiedBy = lastModifiedBy,
                lastModifiedDate = lastModifiedDate.format(DateTimeFormatter.ISO_DATE_TIME),
                name = name,
                licenseId = licenseId,
                seeAlso = seeAlso,
                reference = reference,
                isDeprecatedLicenseId = isDeprecatedLicenseId,
                isOsiApproved = isOsiApproved,
                isFsfLibre = isFsfLibre,
                detailsUrl = detailsUrl,
                isTrust = isTrust,
                risk = risk
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SpdxLicenseServiceImpl::class.java)
    }
}
