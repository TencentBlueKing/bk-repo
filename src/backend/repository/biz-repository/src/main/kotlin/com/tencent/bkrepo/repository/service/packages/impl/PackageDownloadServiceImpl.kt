package com.tencent.bkrepo.repository.service.packages.impl

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.api.DefaultArtifactInfo
import com.tencent.bkrepo.common.artifact.constant.ARTIFACT_INFO_KEY
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.metadata.dao.packages.PackageDao
import com.tencent.bkrepo.common.metadata.dao.packages.PackageVersionDao
import com.tencent.bkrepo.common.metadata.model.TPackage
import com.tencent.bkrepo.common.metadata.model.TPackageVersion
import com.tencent.bkrepo.common.metadata.util.MetadataUtils
import com.tencent.bkrepo.common.metadata.util.PackageEventFactory
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.service.util.SpringContextUtils.Companion.publishEvent
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import com.tencent.bkrepo.repository.service.packages.PackageDownloadService
import org.springframework.stereotype.Service

@Service
class PackageDownloadServiceImpl(
    private val packageDao: PackageDao,
    private val packageVersionDao: PackageVersionDao,
) : PackageDownloadService {

    override fun downloadVersion(
        projectId: String,
        repoName: String,
        packageKey: String,
        versionName: String,
        realIpAddress: String?
    ) {
        val tPackage = findPackageExcludeHistoryVersion(projectId, repoName, packageKey)
        val tPackageVersion = checkPackageVersion(tPackage.id!!, versionName)
        if (tPackageVersion.artifactPath.isNullOrBlank()) {
            throw ErrorCodeException(CommonMessageCode.METHOD_NOT_ALLOWED, "artifactPath is null")
        }
        val artifactInfo = DefaultArtifactInfo(projectId, repoName, tPackageVersion.artifactPath!!)
        val context = ArtifactDownloadContext(artifact = artifactInfo, useDisposition = true)
        // 拦截package下载
        val packageVersion = convert(tPackageVersion)!!
        context.getPackageInterceptors().forEach { it.intercept(projectId, packageVersion) }
        // context 复制时会从request map中获取对应的artifactInfo， 而artifactInfo设置到map中是在接口url解析时
        HttpContextHolder.getRequestOrNull()?.setAttribute(ARTIFACT_INFO_KEY, artifactInfo)
        ArtifactContextHolder.getRepository().download(context)
        publishEvent(
            PackageEventFactory.buildDownloadEvent(
                projectId = projectId,
                repoName = repoName,
                packageType = tPackage.type,
                packageKey = packageKey,
                packageName = tPackage.name,
                versionName = versionName,
                createdBy = SecurityUtils.getUserId(),
                realIpAddress = realIpAddress ?: HttpContextHolder.getClientAddress()
            )
        )
    }

    /**
     * 查找包，不存在则抛异常
     */
    private fun findPackageExcludeHistoryVersion(projectId: String, repoName: String, packageKey: String): TPackage {
        return packageDao.findByKeyExcludeHistoryVersion(projectId, repoName, packageKey)
            ?: throw ErrorCodeException(ArtifactMessageCode.PACKAGE_NOT_FOUND, packageKey)
    }

    /**
     * 查找版本，不存在则抛异常
     */
    private fun checkPackageVersion(packageId: String, versionName: String): TPackageVersion {
        return packageVersionDao.findByName(packageId, versionName)
            ?: throw ErrorCodeException(ArtifactMessageCode.VERSION_NOT_FOUND, versionName)
    }

    fun convert(tPackageVersion: TPackageVersion?): PackageVersion? {
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
                packageMetadata = MetadataUtils.toList(it.metadata),
                tags = it.tags.orEmpty(),
                extension = it.extension.orEmpty(),
                contentPath = it.artifactPath,
                contentPaths = it.artifactPaths ?: it.artifactPath?.let { path -> setOf(path) },
                manifestPath = it.manifestPath,
                clusterNames = it.clusterNames
            )
        }
    }
}
