package com.tencent.bkrepo.docker.artifact

import com.tencent.bkrepo.repository.api.PackageClient
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class DockerPackageRepo @Autowired constructor(
    private val packageClient: PackageClient
) {

    /**
     * check is the node is exist
     * @param request the request to create version
     * @return Boolean is the file exist
     */
    fun createVersion(request: PackageVersionCreateRequest): Boolean {
        return packageClient.createVersion(request).isOk()
    }

    /**
     * check is the node is exist
     * @param projectId project of the repo
     * @param repoName name of the repo
     * @param packageKey package key
     * @return Boolean is the file exist
     */
    fun deletePackage(projectId: String, repoName: String, packageKey: String): Boolean {
        return packageClient.deletePackage(projectId, repoName, packageKey).isOk()
    }

    /**
     * check is the node is exist
     * @param projectId project of the repo
     * @param repoName name of the repo
     * @param packageKey package key
     * @param version package version
     * @return Boolean is the file exist
     */
    fun deletePackageVersion(projectId: String, repoName: String, packageKey: String, version: String): Boolean {
        return packageClient.deleteVersion(projectId, repoName, packageKey, version).isOk()
    }
}
