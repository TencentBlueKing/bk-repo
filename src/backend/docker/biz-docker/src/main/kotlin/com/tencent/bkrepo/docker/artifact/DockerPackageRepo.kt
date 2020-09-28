package com.tencent.bkrepo.docker.artifact

import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.docker.context.RequestContext
import com.tencent.bkrepo.repository.api.PackageClient
import com.tencent.bkrepo.repository.api.PackageDownloadStatisticsClient
import com.tencent.bkrepo.repository.pojo.download.service.DownloadStatisticsAddRequest
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class DockerPackageRepo @Autowired constructor(
    private val packageClient: PackageClient,
    private val packageDownloadStatisticsClient: PackageDownloadStatisticsClient
) {

    /**
     * check is the node is exist
     * @param request the request to create version
     * @return Boolean is the package version create success
     */
    fun createVersion(request: PackageVersionCreateRequest): Boolean {
        return packageClient.createVersion(request).isOk()
    }

    /**
     * check is the node is exist
     * @param context  the request context
     * @return Boolean is the package version delete success
     */
    fun deletePackage(context: RequestContext): Boolean {
        with(context) {
            return packageClient.deletePackage(projectId, repoName, PackageKeys.ofDocker(artifactName)).isOk()
        }
    }

    /**
     * check is the node is exist
     * @param context  the request context
     * @param version package version
     * @return Boolean is the package version exist
     */
    fun deletePackageVersion(context: RequestContext, version: String): Boolean {
        with(context) {
            return packageClient.deleteVersion(projectId, repoName, PackageKeys.ofDocker(artifactName), version).isOk()
        }
    }

    /**
     * check is the node is exist
     * @param context  the request context
     * @param version package version
     * @return PackageVersion the package version detail
     */
    fun getPackageVersion(context: RequestContext, version: String): PackageVersion? {
        with(context) {
            return packageClient.findVersionByName(projectId, repoName, PackageKeys.ofDocker(artifactName), version).data
        }
    }

    /**
     * check is the node is exist
     * @param context  the request context
     * @param version package version
     * @return Boolean is add download static success
     */
    fun addDownloadStatic(context: RequestContext, version: String): Boolean {
        with(context) {
            val request = DownloadStatisticsAddRequest(
                projectId,
                repoName,
                PackageKeys.ofDocker(artifactName),
                artifactName,
                version
            )
            return packageDownloadStatisticsClient.add(request).isOk()
        }
    }
}
