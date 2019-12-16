package com.tencent.bkrepo.maven.service

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.context.RepositoryHolder
import com.tencent.bkrepo.maven.artifact.MavenArtifactInfo
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class MavenHttpService {


    fun deploy(mavenArtifactInfo: MavenArtifactInfo,
               file: ArtifactFile)
    {
        val context = ArtifactUploadContext(file)
        val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
        repository.upload(context)
//        val request = HttpContextHolder.getRequest()
//
//        val projectId = mavenArtifactInfo.projectId
//        val repoName = mavenArtifactInfo.repoName
//        val fullPath = mavenArtifactInfo.fullPath
//        val formattedFullPath = NodeUtils.formatFullPath(fullPath)
//
//        // 解析参数
//        val sha256 = HeaderUtils.getHeader(HEADER_SHA256)
//        val expires = HeaderUtils.getLongHeader(HEADER_EXPIRES)
//        val overwrite = HeaderUtils.getBooleanHeader(HEADER_OVERWRITE)
//        val metadata = parseMetadata(request)
//        val contentLength = request.contentLengthLong
//        val size = file.getSize()
//
//        // 参数校验
//        expires.takeIf { it >= 0 } ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "expires")
//        contentLength.takeIf { it > 0 } ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "content length")
//        size.takeIf { it > 0 } ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_IS_NULL, "file content")
//
//        // 判断仓库是否存在
//        repositoryResource.detail(projectId, repoName, REPO_TYPE).data ?: run {
//            logger.warn("User[$userId] preCheck [${mavenArtifactInfo.artifactUri}] failed: $repoName not found")
//            throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, repoName)
//        }
//
//        // 校验sha256
//        val calculatedSha256 = fileSha256(listOf(file.getInputStream()))
//        if (sha256 != null && calculatedSha256 != sha256) {
//            logger.warn("User[$userId] simply upload file [${mavenArtifactInfo.artifactUri}] failed: file sha256 verification failed")
//            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "sha256")
//        }
//
//        // 判断仓库是否存在
//        val repository = repositoryResource.detail(projectId, repoName, REPO_TYPE).data ?: run {
//            logger.warn("User[$userId] simply upload file  [${mavenArtifactInfo.artifactUri}] failed: $repoName not found")
//            throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, repoName)
//        }
//
//        // 保存节点
//        val result = nodeResource.create(
//                NodeCreateRequest(
//                        projectId = projectId,
//                        repoName = repoName,
//                        folder = false,
//                        fullPath = formattedFullPath,
//                        expires = expires,
//                        overwrite = overwrite,
//                        size = size,
//                        sha256 = calculatedSha256,
//                        metadata = metadata,
//                        operator = userId
//                )
//        )
//
//        if (result.isOk()) {
//            val storageCredentials = CredentialsUtils.readString(repository.storageCredentials?.type, repository.storageCredentials?.credentials)
//            fileStorage.store(calculatedSha256, file.getInputStream(), storageCredentials)
//            logger.info("User[$userId] simply upload file [${mavenArtifactInfo.artifactUri}] success")
//        } else {
//            logger.warn("User[$userId] simply upload file [${mavenArtifactInfo.artifactUri}] failed: [${result.code}, ${result.message}]")
//            throw ExternalErrorCodeException(result.code, result.message)
//        }
//
//        return ResponseEntity(HttpStatus.OK)

    }

    fun dependency(mavenArtifactInfo: MavenArtifactInfo) {
        val context = ArtifactDownloadContext()
        val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
        //todo 第一次上传时metadata.xml的处理
        repository.download(context)
    }

    companion object{
        val logger: Logger = LoggerFactory.getLogger(MavenHttpService::class.java)
    }
}