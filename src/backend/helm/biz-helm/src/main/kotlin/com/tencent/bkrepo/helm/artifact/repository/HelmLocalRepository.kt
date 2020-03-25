package com.tencent.bkrepo.helm.artifact.repository

import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.helm.EMPTY
import com.tencent.bkrepo.helm.INDEX_YAML
import com.tencent.bkrepo.helm.NOT_FOUND_MES
import com.tencent.bkrepo.helm.artifact.util.YamlUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.ContentHandler

@Component
class HelmLocalRepository : LocalRepository(){

    fun searchYaml(context: ArtifactSearchContext): String {
        val artifactInfo = context.artifactInfo
        val fullPath = INDEX_YAML
        with(artifactInfo) {
            val node = nodeResource.detail(projectId, repoName, fullPath).data ?: return EMPTY
            val indexYamlFile = storageService.load(node.nodeInfo.sha256!!, context.storageCredentials) ?: return EMPTY
            val urls = artifactUri.split("/")
            return when (urls.size) {
                1 -> {
                    YamlUtil.searchYaml(indexYamlFile, urls[1])
                }
                2 ->{
                    YamlUtil.searchYaml(indexYamlFile, urls[1],urls[2])
                }
                else -> {
                    logger.error("$artifactInfo is invalid")
                    NOT_FOUND_MES
                }
            }
        }
    }

    fun isExists(context: ArtifactSearchContext) {
        val artifactInfo =  context.artifactInfo
        val response = HttpContextHolder.getResponse()
        with(artifactInfo) {
            val nodeDetail = nodeResource.detail(projectId, repoName, artifactUri).data
            if (nodeDetail == null) {
                response.status = 404
            } else {
                response.status = 200
            }
        }
    }



    companion object{
        val logger: Logger = LoggerFactory.getLogger(HelmLocalRepository::class.java)
    }
}
