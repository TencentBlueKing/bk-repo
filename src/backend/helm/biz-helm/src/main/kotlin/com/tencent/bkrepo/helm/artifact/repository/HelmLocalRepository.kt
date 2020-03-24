package com.tencent.bkrepo.helm.artifact.repository

import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.artifact.exception.ArtifactValidateException
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.util.HttpResponseUtils
import com.tencent.bkrepo.helm.constants.INDEX_CACHE_YAML
import com.tencent.bkrepo.helm.constants.INDEX_YAML
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class HelmLocalRepository : LocalRepository(){
    override fun download(context: ArtifactDownloadContext) {
        val artifactUri = getNodeFullPath(context)
        val userId = context.userId

        try {
            this.onDownloadValidate(context)
            this.onBeforeDownload(context)
            val file =
                this.onDownload(context) ?: throw ArtifactNotFoundException("Artifact[$artifactUri] does not exist")
            //val name = NodeUtils.getName(getNodeFullPath(context))
            HttpResponseUtils.response(INDEX_YAML, file)
            logger.info("User[$userId] download artifact[$artifactUri] success")
            this.onDownloadSuccess(context, file)
        } catch (validateException: ArtifactValidateException) {
            this.onValidateFailed(context, validateException)
        } catch (exception: Exception) {
            this.onDownloadFailed(context, exception)
        }
    }

    override fun onBeforeDownload(context: ArtifactDownloadContext) {
        //检查index-cache.yaml文件是否存在，如果不存在则说明是添加仓库
        val repositoryInfo = context.repositoryInfo
        val projectId = repositoryInfo.projectId
        val repoName = repositoryInfo.name
        val fullPath = getNodeFullPath(context)
        val exist = nodeResource.exist(projectId, repoName, fullPath)
        if(!exist.data!!){
            //新建index-cache.yaml文件

            // val uploadContext = ArtifactUploadContext()
        }
    }

    override fun getNodeFullPath(context: ArtifactDownloadContext): String {
        return INDEX_CACHE_YAML
    }

    companion object{
        val logger: Logger = LoggerFactory.getLogger(HelmLocalRepository::class.java)
    }
}
