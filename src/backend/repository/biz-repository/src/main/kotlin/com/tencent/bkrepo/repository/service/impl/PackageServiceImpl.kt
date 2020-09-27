package com.tencent.bkrepo.repository.service.impl

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.artifact.api.DefaultArtifactInfo
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.util.version.SemVer
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.repository.dao.PackageDao
import com.tencent.bkrepo.repository.dao.PackageVersionDao
import com.tencent.bkrepo.repository.model.TPackage
import com.tencent.bkrepo.repository.model.TPackageVersion
import com.tencent.bkrepo.repository.pojo.packages.PackageSummary
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import com.tencent.bkrepo.repository.search.packages.PackageQueryContext
import com.tencent.bkrepo.repository.search.packages.PackageSearchInterpreter
import com.tencent.bkrepo.repository.service.PackageService
import com.tencent.bkrepo.repository.util.MetadataUtils
import com.tencent.bkrepo.repository.util.PackageQueryHelper
import com.tencent.bkrepo.repository.util.Pages
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class PackageServiceImpl(
    private val packageDao: PackageDao,
    private val packageVersionDao: PackageVersionDao,
    private val packageSearchInterpreter: PackageSearchInterpreter
) : AbstractService(), PackageService {

    override fun findPackageByKey(projectId: String, repoName: String, packageKey: String): PackageSummary? {
        val tPackage = packageDao.findByKey(projectId, repoName, packageKey)
        return convert(tPackage)
    }

    override fun findVersionByName(
        projectId: String,
        repoName: String,
        packageKey: String,
        versionName: String
    ): PackageVersion? {
        val tPackage = packageDao.findByKey(projectId, repoName, packageKey) ?: return null
        return convert(checkPackageVersion(tPackage.id!!, versionName))
    }

    override fun listPackagePageByName(
        projectId: String,
        repoName: String,
        packageName: String?,
        pageNumber: Int,
        pageSize: Int
    ): Page<PackageSummary> {
        val query = PackageQueryHelper.packageListQuery(projectId, repoName, packageName)
        val totalRecords = packageDao.count(query)
        val pageRequest = Pages.ofRequest(pageNumber, pageSize)
        val records = packageDao.find(query.with(pageRequest)).map { convert(it)!! }
        return Pages.ofResponse(pageRequest, totalRecords, records)
    }

    override fun listVersionPage(
        projectId: String,
        repoName: String,
        packageKey: String,
        versionName: String?,
        stageTag: List<String>?,
        pageNumber: Int,
        pageSize: Int
    ): Page<PackageVersion> {
        val pageRequest = Pages.ofRequest(pageNumber, pageSize)
        val tPackage = packageDao.findByKey(projectId, repoName, packageKey)
        return if (tPackage == null) {
            Pages.ofResponse(pageRequest, 0, emptyList())
        } else {
            val query = PackageQueryHelper.versionListQuery(tPackage.id!!, versionName, stageTag)
            val totalRecords = packageVersionDao.count(query)
            val records = packageVersionDao.find(query.with(pageRequest)).map { convert(it)!! }
            Pages.ofResponse(pageRequest, totalRecords, records)
        }
    }

    override fun createPackageVersion(request: PackageVersionCreateRequest) {
        with(request) {
            // 先查询包是否存在，不存在先创建包
            val tPackage = findOrCreatePackage(request)
            // 检查版本是否存在
            val oldVersion = packageVersionDao.findByName(tPackage.id!!, versionName)
            val newVersion = if (oldVersion != null) {
                if (!overwrite) {
                    throw ErrorCodeException(CommonMessageCode.RESOURCE_EXISTED, versionName)
                }
                // overwrite
                oldVersion.apply {
                    lastModifiedBy = request.createdBy
                    lastModifiedDate = LocalDateTime.now()
                    size = request.size
                    manifestPath = request.manifestPath
                    artifactPath = request.artifactPath
                    stageTag = request.stageTag.orEmpty()
                    metadata = MetadataUtils.fromMap(request.metadata)
                }
            } else {
                // create new
                tPackage.versions += 1
                TPackageVersion(
                    createdBy = createdBy,
                    createdDate = LocalDateTime.now(),
                    lastModifiedBy = createdBy,
                    lastModifiedDate = LocalDateTime.now(),
                    packageId = tPackage.id!!,
                    name = versionName,
                    size = size,
                    ordinal = calculateOrdinal(versionName),
                    downloads = 0,
                    manifestPath = manifestPath,
                    artifactPath = artifactPath,
                    stageTag = stageTag.orEmpty(),
                    metadata = MetadataUtils.fromMap(metadata)
                )
            }
            packageVersionDao.save(newVersion)
            // 更新包
            tPackage.lastModifiedBy = newVersion.lastModifiedBy
            tPackage.lastModifiedDate = newVersion.lastModifiedDate
            tPackage.description = packageDescription
            tPackage.latest = versionName
            packageDao.save(tPackage)
        }
    }

    override fun deletePackage(projectId: String, repoName: String, packageKey: String) {
        val tPackage = checkPackage(projectId, repoName, packageKey)
        packageVersionDao.deleteByPackageId(tPackage.id!!)
        packageDao.deleteByKey(projectId, repoName, packageKey)
    }

    override fun deleteVersion(projectId: String, repoName: String, packageKey: String, versionName: String) {
        val tPackage = checkPackage(projectId, repoName, packageKey)
        val tPackageVersion = checkPackageVersion(tPackage.id!!, versionName)
        packageVersionDao.deleteByName(tPackageVersion.packageId, tPackageVersion.name)
        if (tPackage.latest == tPackageVersion.name) {
            val latestVersion = packageVersionDao.findLatest(tPackage.id!!)
            tPackage.latest = latestVersion?.name.orEmpty()
        }
        tPackage.versions -= 1
        packageDao.save(tPackage)
    }

    override fun downloadVersion(projectId: String, repoName: String, packageKey: String, versionName: String) {
        val tPackage = checkPackage(projectId, repoName, packageKey)
        val tPackageVersion = checkPackageVersion(tPackage.id!!, versionName)
        if (tPackageVersion.artifactPath.isNullOrBlank()) {
            throw ErrorCodeException(CommonMessageCode.OPERATION_UNSUPPORTED)
        }
        val artifactInfo = DefaultArtifactInfo(projectId, repoName, tPackageVersion.artifactPath!!)
        val context = ArtifactDownloadContext(artifact = artifactInfo)
        ArtifactContextHolder.getRepository().download(context)
    }

    override fun searchPackage(queryModel: QueryModel): Page<MutableMap<*, *>> {
        val context = packageSearchInterpreter.interpret(queryModel) as PackageQueryContext
        val query = context.mongoQuery
        val countQuery = Query.of(query).limit(0).skip(0)
        val totalRecords = packageDao.count(countQuery)
        val packageList = packageDao.find(query, MutableMap::class.java)
        val pageNumber = if (query.limit == 0) 0 else (query.skip / query.limit).toInt()
        return Page(pageNumber + 1, query.limit, totalRecords, packageList)
    }

    /**
     * 查找包，不存在则创建
     */
    private fun findOrCreatePackage(request: PackageVersionCreateRequest): TPackage {
        with(request) {
            return packageDao.findByKey(projectId, repoName, packageKey) ?: run {
                val tPackage = TPackage(
                    createdBy = createdBy,
                    createdDate = LocalDateTime.now(),
                    lastModifiedBy = createdBy,
                    lastModifiedDate = LocalDateTime.now(),
                    projectId = projectId,
                    repoName = repoName,
                    name = packageName,
                    key = packageKey,
                    type = packageType,
                    downloads = 0,
                    versions = 0
                )
                try {
                    packageDao.save(tPackage)
                } catch (exception: DuplicateKeyException) {
                    logger.warn("Create package[$tPackage] error: [${exception.message}]")
                    packageDao.findByKey(projectId, repoName, packageKey)!!
                }
            }
        }
    }

    /**
     * 查找包，不存在则抛异常
     */
    private fun checkPackage(projectId: String, repoName: String, packageKey: String): TPackage {
        return packageDao.findByKey(projectId, repoName, packageKey)
            ?: throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, packageKey)
    }

    /**
     * 查找版本，不存在则抛异常
     */
    private fun checkPackageVersion(packageId: String, versionName: String): TPackageVersion {
        return packageVersionDao.findByName(packageId, versionName)
            ?: throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, versionName)
    }

    /**
     * 计算语义化版本顺序
     */
    private fun calculateOrdinal(versionName: String): Long {
        return try {
            SemVer.parse(versionName).ordinal()
        } catch (exception: IllegalArgumentException) {
            LOWEST_ORDINAL
        }
    }

    companion object {

        private val logger = LoggerFactory.getLogger(PackageServiceImpl::class.java)
        private const val LOWEST_ORDINAL = 0L

        private fun convert(tPackage: TPackage?): PackageSummary? {
            return tPackage?.let {
                PackageSummary(
                    createdBy = it.createdBy,
                    createdDate = it.createdDate,
                    lastModifiedBy = it.lastModifiedBy,
                    lastModifiedDate = it.lastModifiedDate,
                    projectId = it.projectId,
                    repoName = it.repoName,
                    name = it.name,
                    key = it.key,
                    type = it.type,
                    latest = it.latest.orEmpty(),
                    downloads = it.downloads,
                    versions = it.versions,
                    description = it.description
                )
            }
        }

        private fun convert(tPackageVersion: TPackageVersion?): PackageVersion? {
            return tPackageVersion?.let {
                PackageVersion(
                    createdBy = it.createdBy,
                    createdDate = it.createdDate,
                    lastModifiedBy = it.lastModifiedBy,
                    lastModifiedDate = it.lastModifiedDate,
                    name = it.name,
                    size = it.size,
                    downloads = it.downloads,
                    stageTag = it.stageTag,
                    metadata = MetadataUtils.toMap(it.metadata),
                    contentPath = it.artifactPath
                )
            }
        }
    }
}