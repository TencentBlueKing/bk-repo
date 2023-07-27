package com.tencent.bkrepo.npm.service.impl

import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.artifact.exception.RepoNotFoundException
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.npm.constants.LATEST
import com.tencent.bkrepo.npm.model.metadata.NpmPackageMetaData
import com.tencent.bkrepo.npm.service.ServiceNpmClientService
import com.tencent.bkrepo.npm.utils.NpmUtils
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import org.springframework.stereotype.Service

@Service
class ServiceNpmClientImpl : ServiceNpmClientService, AbstractNpmService() {

    override fun deleteVersion(
        projectId: String,
        repoName: String,
        packageKey: String,
        version: String,
        operator: String
    ) {
        logger.info("NPM delete package begin, projectId:[$projectId] " +
                "repoName:[$repoName] packageKey:[$packageKey] version:[$version]")
        // 删除包管理中对应的version
        packageClient.deleteVersion(projectId, repoName, packageKey, version, null)
        // 删除文件节点
        val name = PackageKeys.resolveNpm(packageKey)
        val tgzPath = NpmUtils.getTgzPath(name, version)
        val metadataPath = NpmUtils.getVersionPackageMetadataPath(name, version)
        nodeClient.deleteNode(NodeDeleteRequest(projectId, repoName, tgzPath, operator))
        nodeClient.deleteNode(NodeDeleteRequest(projectId, repoName, metadataPath, operator))
        updatePackageJson(projectId, repoName, packageKey, version, operator)
        logger.info(
            "delete package version success, projectId:[$projectId] " +
                    "repoName:[$repoName] packageKey:[$packageKey] version:[$version]"
        )
    }

    // 更新package.json文件的内容
    private fun updatePackageJson(
        projectId: String,
        repoName: String,
        packageKey: String,
        version: String,
        operator: String
    ) {
        val name = PackageKeys.resolveNpm(packageKey)
        val packageFullPath = NpmUtils.getPackageMetadataPath(name)
        val node = nodeClient.getNodeDetail(projectId, repoName, packageFullPath).data ?: return
        val repositoryDetail = repositoryClient.getRepoDetail(projectId, repoName, RepositoryType.NPM.name).data
            ?: throw RepoNotFoundException("repo not found: { projectId=$projectId, repoName=$repoName }")
        //获取文件流
        storageManager.loadArtifactInputStream(node, repositoryDetail.storageCredentials).use {
            val packageMetaData = JsonUtils.objectMapper.readValue(it, NpmPackageMetaData::class.java)
            val latest = NpmUtils.getLatestVersionFormDistTags(packageMetaData.distTags)
            if (version != latest) {
                deleteNpmVersion(packageMetaData, version)
            } else {
                val newLatest = packageClient.findPackageByKey(projectId, repoName, packageKey).data?.latest
                newLatest?.let {
                    packageMetaData.versions.map.remove(version)
                    packageMetaData.time.getMap().remove(version)
                    packageMetaData.distTags.set(LATEST, newLatest)
                }
            }
            //重新上传 package.json
            val fullPath = NpmUtils.getPackageMetadataPath(packageMetaData.name!!)
            val inputStream = JsonUtils.objectMapper.writeValueAsString(packageMetaData).byteInputStream()
            val artifactFile =
                inputStream.use { metadataInputStream -> ArtifactFileFactory.build(metadataInputStream) }
            val packageJsonNode = NodeCreateRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = fullPath,
                folder = false,
                overwrite = true,
                size = artifactFile.getSize(),
                sha256 = artifactFile.getFileSha256(),
                md5 = artifactFile.getFileMd5(),
                operator = operator
            )
            storageManager.storeArtifactFile(packageJsonNode, artifactFile, repositoryDetail.storageCredentials)
            artifactFile.delete()
        }
    }

    private fun deleteNpmVersion(packageMetaData: NpmPackageMetaData, version: String) {
        packageMetaData.versions.map.remove(version)
        packageMetaData.time.getMap().remove(version)
        val iterator = packageMetaData.distTags.getMap().entries.iterator()
        while (iterator.hasNext()) {
            if (version == iterator.next().value) {
                iterator.remove()
            }
        }
    }
}
