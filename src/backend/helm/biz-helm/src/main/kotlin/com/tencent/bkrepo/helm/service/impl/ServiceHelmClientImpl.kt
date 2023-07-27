package com.tencent.bkrepo.helm.service.impl

import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.helm.service.ServiceHelmClientService
import com.tencent.bkrepo.helm.utils.HelmMetadataUtils
import com.tencent.bkrepo.helm.utils.HelmUtils
import com.tencent.bkrepo.helm.utils.ObjectBuilderUtil
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.PackageClient
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ServiceHelmClientImpl(
    private val packageClient: PackageClient,
    private val nodeClient: NodeClient
) : ServiceHelmClientService {
    override fun deleteVersion(
        projectId: String,
        repoName: String,
        packageKey: String,
        version: String,
        operator: String
    ) {
        packageClient.findVersionByName(projectId, repoName, packageKey, version).data?.let {
            packageClient.deleteVersion(projectId, repoName, packageKey, version)
            val name = PackageKeys.resolveHelm(packageKey)
            val chartPath = HelmUtils.getChartFileFullPath(name, version)
            val provPath = HelmUtils.getProvFileFullPath(name, version)
            if (chartPath.isNotBlank()) {
                val request = NodeDeleteRequest(projectId, repoName, chartPath, operator)
                nodeClient.deleteNode(request)
            }
            if (provPath.isNotBlank()) {
                nodeClient.deleteNode(NodeDeleteRequest(projectId, repoName, provPath, operator))
            }
            updatePackageExtension(projectId,repoName,packageKey)
        }?: logger.warn("[$projectId/$repoName/$packageKey/$version] version not found")
    }

    private fun updatePackageExtension(
        projectId: String,
        repoName: String,
        packageKey: String
    ){
        val name = PackageKeys.resolveHelm(packageKey)
        val version = packageClient.findPackageByKey(projectId, repoName, packageKey).data?.latest
        val chartPath = HelmUtils.getChartFileFullPath(name, version!!)
        val map = nodeClient.getNodeDetail(projectId, repoName, chartPath).data?.metadata
        val chartInfo = map?.let { it1 -> HelmMetadataUtils.convertToObject(it1) }
        chartInfo?.appVersion?.let {
            val packageUpdateRequest = ObjectBuilderUtil.buildPackageUpdateRequest(
                projectId,
                repoName,
                name,
                chartInfo.appVersion!!,
                chartInfo.description
            )
            packageClient.updatePackage(packageUpdateRequest)
        }
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(HelmOperationService::class.java)
    }
}
