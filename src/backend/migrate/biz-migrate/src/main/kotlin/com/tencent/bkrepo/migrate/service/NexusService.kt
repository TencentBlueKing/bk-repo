package com.tencent.bkrepo.migrate.service

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.migrate.BKREPO
import com.tencent.bkrepo.migrate.REPOSITORYID
import com.tencent.bkrepo.migrate.conf.NexusConf
import com.tencent.bkrepo.migrate.http.CanwayHttpUtils
import com.tencent.bkrepo.migrate.pojo.NexusAssetPojo
import com.tencent.bkrepo.migrate.pojo.TempMavenInfo
import com.tencent.bkrepo.migrate.pojo.MavenArtifact
import com.tencent.bkrepo.migrate.pojo.NexusPageAssets
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.Base64

@Service
class NexusService {

    @Autowired
    lateinit var nexusConf: NexusConf

    fun requireAuth(): Boolean {
        return nexusConf.auth
    }

    fun getAuth(): String? {
        if (!requireAuth()) return null
        val username = nexusConf.username
            ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_MISSING, "nexus.user must not be null")
        val password = nexusConf.password
            ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_MISSING, "nexus.password must not be null")
        return Base64.getMimeEncoder().encodeToString("$username:$password".toByteArray())
    }

    /**
     * 通过mvn 命令推送至制品库
     */
    fun mvnCliStr(tempMavenInfo: TempMavenInfo): String {
        val mvnPath = nexusConf.mvnPath
        val syncUrl = "${nexusConf.syncUrl.removeSuffix("/")}/$BKREPO"
        val url = "$syncUrl/${tempMavenInfo.repository}"
        logger.info("start deploy $tempMavenInfo")
        // 构造mvn命令
        val mvnCliStr = String.format(
            mvnCliTemplates,
            mvnPath,
            tempMavenInfo.jarFile.absolutePath,
            tempMavenInfo.groupId,
            tempMavenInfo.artifactId,
            tempMavenInfo.version,
            tempMavenInfo.extension,
            REPOSITORYID,
            url
        )
        logger.info("Shell $mvnCliStr")
        return mvnCliStr
    }

    /**
     * nexus search
     */
//    fun search(repository: String, bkArtifact: BkArtifact): List<NexusComponentPojo>? {
//        val nexusHost = nexusConf.host
//            ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_MISSING, "sync.nexus.host must not be null")
//        val searchUri = String.format(
//            nexusSearch, nexusConf.apiversion,
//            repository, bkArtifact.groupId, bkArtifact.artifactId, bkArtifact.version
//        )
//        val searchUrl = "$nexusHost$searchUri"
//        val response = CanwayHttpUtils.doGet(searchUrl, basicAuth = getAuth()).content
//        val nexusPageComponent = response.readJsonString<NexusPageComponent>()
//        return nexusPageComponent.items
//    }

    fun searchAssets(repository: String, mavenArtifact: MavenArtifact, requestVersion: String): List<NexusAssetPojo>? {
        val nexusHost = nexusConf.host
            ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_MISSING, "sync.nexus.host must not be null")
        val version: String = mavenArtifact.version ?: requestVersion
        val searchUri = String.format(
            nexusSearchAssets, nexusConf.apiversion,
            repository, mavenArtifact.groupId, mavenArtifact.artifactId, version, mavenArtifact.type
        )
        val searchUrl = "$nexusHost$searchUri"
        logger.info("Search url : $searchUrl")
        return try {
            val response = CanwayHttpUtils.doGet(searchUrl, basicAuth = getAuth()).content
            val nexusPageAssets = response.readJsonString<NexusPageAssets>()
            nexusPageAssets.items
        } catch (e: Exception) {
            logger.info("SearchAssets failed: $searchUrl")
            null
        }
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(NexusService::class.java)

        //        const val allRepo = "/service/rest/%s/repositories"
//        const val pageComponent = "/service/rest/%s/components?repository="
//        const val header = "accept: application/json"
        const val mvnCliTemplates =
            "%s deploy:deploy-file -Dmaven.test.skip=true -Dfile=%s -DgroupId=%s -DartifactId=%s -Dversion=%s -Dpackaging=%s -DrepositoryId=%s -Durl=%s"
        const val nexusSearch =
            "/service/rest/%s/search?repository=%s&maven.group=%s&maven.artifactId=%s&maven.baseVersion=%s"
        const val nexusSearchAssets =
            "/service/rest/%s/search/assets?repository=%s&maven.groupId=%s&maven.artifactId=%s&maven.baseVersion=%s&maven.extension=%s"
    }
}
