package com.tencent.bkrepo.npm.service

import com.google.gson.JsonObject
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.artifact.constant.OCTET_STREAM
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo
import com.tencent.bkrepo.npm.constants.NAME
import com.tencent.bkrepo.npm.constants.NPM_FILE_FULL_PATH
import com.tencent.bkrepo.npm.constants.NPM_PKG_FULL_PATH
import com.tencent.bkrepo.npm.constants.TIME
import com.tencent.bkrepo.npm.pojo.fixtool.DateTimeFormatResponse
import com.tencent.bkrepo.npm.utils.GsonUtils
import com.tencent.bkrepo.npm.utils.TimeUtil
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class NpmFixToolService {

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    fun fixDateFormat(artifactInfo: NpmArtifactInfo, pkgName: String): DateTimeFormatResponse {
        val pkgNameSet = pkgName.split(',').filter { it.isNotBlank() }.map { it.trim() }.toMutableSet()
        logger.info("fix time format with package: $pkgNameSet, size : [${pkgNameSet.size}]")
        val successSet = mutableSetOf<String>()
        val errorSet = mutableSetOf<String>()
        val context = ArtifactSearchContext()
        val repository = ArtifactContextHolder.getRepository(context.repositoryDetail.category)
        pkgNameSet.forEach { it ->
            try {
                val fullPath = String.format(NPM_PKG_FULL_PATH, it)
                context.contextAttributes[NPM_FILE_FULL_PATH] = fullPath
                val pkgFileInfo = repository.search(context) as? JsonObject
                if (pkgFileInfo == null) {
                    errorSet.add(it)
                    return@forEach
                }
                val timeJsonObject = pkgFileInfo[TIME].asJsonObject
                timeJsonObject.entrySet().forEach {
                    if (!it.value.asString.contains('T')) {
                        timeJsonObject.add(it.key, GsonUtils.gson.toJsonTree(formatDateTime(it.value.asString)))
                    }
                }
                reUploadPkgJson(pkgFileInfo)
                successSet.add(it)
            } catch (ignored: Exception) {
                errorSet.add(it)
            }
        }
        return DateTimeFormatResponse(successSet, errorSet)
    }

    private fun reUploadPkgJson(pkgFileInfo: JsonObject) {
        val name = pkgFileInfo[NAME].asString
        val pkgMetadata = ArtifactFileFactory.build(GsonUtils.gsonToInputStream(pkgFileInfo))
        val context = ArtifactUploadContext(pkgMetadata)
        val fullPath = String.format(NPM_PKG_FULL_PATH, name)
        context.contextAttributes[OCTET_STREAM + "_full_path"] = fullPath
        val repository = ArtifactContextHolder.getRepository(context.repositoryDetail.category)
        repository.upload(context)
    }

    fun formatDateTime(time: String): String {
        val dateFormat = "yyyy-MM-dd HH:mm:ss.SSS'Z'"
        val dateTime = LocalDateTime.parse(time, DateTimeFormatter.ofPattern(dateFormat))
        return TimeUtil.getGMTTime(dateTime)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NpmFixToolService::class.java)
    }
}
