package com.tencent.bkrepo.job.backup.service.impl

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.storage.core.locator.HashFileLocator
import com.tencent.bkrepo.common.storage.message.StorageErrorException
import com.tencent.bkrepo.common.storage.message.StorageMessageCode
import com.tencent.bkrepo.job.backup.pojo.query.BackupMavenMetadata
import com.tencent.bkrepo.job.backup.pojo.query.BackupNodeInfo
import com.tencent.bkrepo.job.backup.pojo.query.BackupPackageInfo
import com.tencent.bkrepo.job.backup.pojo.query.BackupPackageVersionInfoWithKeyInfo
import com.tencent.bkrepo.job.backup.pojo.query.BackupProjectInfo
import com.tencent.bkrepo.job.backup.pojo.query.BackupRepositoryInfo
import com.tencent.bkrepo.job.backup.pojo.query.common.BackupAccount
import com.tencent.bkrepo.job.backup.pojo.query.common.BackupPermission
import com.tencent.bkrepo.job.backup.pojo.query.common.BackupRole
import com.tencent.bkrepo.job.backup.pojo.query.common.BackupTemporaryToken
import com.tencent.bkrepo.job.backup.pojo.query.common.BackupUser
import com.tencent.bkrepo.job.backup.pojo.record.BackupContext
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.nio.file.Paths

open class BaseService {

    // TODO 全存储在一个文件中，当数据过多会导致内容过大
    inline fun <reified T> storeData(data: T, context: BackupContext) {
        val fileName = when (T::class) {
            BackupUser::class -> USER_FILE_NAME
            BackupRole::class -> ROLE_FILE_NAME
            BackupTemporaryToken::class -> TEMPORARY_TOKEN_FILE_NAME
            BackupPermission::class -> PERMISSION_FILE_NAME
            BackupAccount::class -> ACCOUNT_FILE_NAME
            BackupProjectInfo::class -> PROJECT_FILE_NAME
            BackupRepositoryInfo::class -> REPOSITORY_FILE_NAME
            BackupNodeInfo::class -> NODE_FILE_NAME
            BackupPackageInfo::class -> PACKAGE_FILE_NAME
            BackupPackageVersionInfoWithKeyInfo::class -> PACKAGE_VERSION_FILE_NAME
            BackupMavenMetadata::class -> MAVEN_METADATA_FILE_NAME
            else -> return
        }
        try {
            context.tempClient.touch(StringPool.EMPTY, fileName)
            logger.info("Success to create file [$fileName]")
            val dataStr = data!!.toJsonString().replace(System.lineSeparator(), "")
            val inputStream = dataStr.byteInputStream()
            val size = dataStr.length.toLong()
            context.tempClient.append(StringPool.EMPTY, fileName, inputStream, size)
            val lineEndStr = "\n"
            context.tempClient.append(
                StringPool.EMPTY,
                fileName,
                lineEndStr.byteInputStream(),
                lineEndStr.length.toLong()
            )
            logger.info("Success to append file [$fileName]")
        } catch (exception: Exception) {
            logger.error("Failed to create file", exception)
            throw StorageErrorException(StorageMessageCode.STORE_ERROR)
            // TODO 异常该如何处理
        }
    }

    /**
     * 生成随机文件路径
     * */
    fun generateRandomPath(sha256: String): String {
        val fileLocator = HashFileLocator()
        return fileLocator.locate(sha256)
    }


    /**
     * 生成随机文件路径
     * */
    fun generateRandomPath(root: Path, sha256: String): Path {
        return Paths.get(root.toFile().path, generateRandomPath(sha256))
    }

    companion object {
        val logger = LoggerFactory.getLogger(BaseService::class.java)
        const val USER_FILE_NAME = "user.json"
        const val ROLE_FILE_NAME = "role.json"
        const val TEMPORARY_TOKEN_FILE_NAME = "temporary_token.json"
        const val PERMISSION_FILE_NAME = "permission.json"
        const val ACCOUNT_FILE_NAME = "account.json"
        const val PROJECT_FILE_NAME = "project.json"
        const val REPOSITORY_FILE_NAME = "repository.json"
        const val NODE_FILE_NAME = "node.json"
        const val PACKAGE_FILE_NAME = "package.json"
        const val PACKAGE_VERSION_FILE_NAME = "package-version.json"
        const val MAVEN_METADATA_FILE_NAME = "maven-metadata.json"
        const val MAVEN_METADATA_COLLECTION_NAME = "maven_metadata"
        const val USER_COLLECTION_NAME = "user"
        const val ROLE_COLLECTION_NAME = "role"
        const val TEMPORARY_TOKEN_COLLECTION_NAME = "temporary_token"
        const val PERMISSION_COLLECTION_NAME = "permission"
        const val ACCOUNT_COLLECTION_NAME = "account"
        const val ZIP_FILE_SUFFIX = ".zip"
        val COMMON_FILE_LIST = listOf(
            ROLE_COLLECTION_NAME,
            USER_COLLECTION_NAME,
            PERMISSION_COLLECTION_NAME,
            TEMPORARY_TOKEN_COLLECTION_NAME,
            ACCOUNT_COLLECTION_NAME,
        )
        val REPO_FILE_LIST = listOf(
            PROJECT_FILE_NAME,
            REPOSITORY_FILE_NAME,
            NODE_FILE_NAME,
            PACKAGE_FILE_NAME,
            PACKAGE_VERSION_FILE_NAME,
            MAVEN_METADATA_FILE_NAME
        )
    }
}