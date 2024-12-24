package com.tencent.bkrepo.job.backup.pojo.query.enums

import com.tencent.bkrepo.job.backup.pojo.query.BackupMavenMetadata
import com.tencent.bkrepo.job.backup.pojo.query.BackupNodeInfo
import com.tencent.bkrepo.job.backup.pojo.query.BackupPackageInfo
import com.tencent.bkrepo.job.backup.pojo.query.BackupPackageVersionInfoWithKeyInfo
import com.tencent.bkrepo.job.backup.pojo.query.BackupProjectInfo
import com.tencent.bkrepo.job.backup.pojo.query.BackupRepositoryInfo
import com.tencent.bkrepo.job.backup.pojo.query.common.BackupAccount
import com.tencent.bkrepo.job.backup.pojo.query.common.BackupConanMetadata
import com.tencent.bkrepo.job.backup.pojo.query.common.BackupPermission
import com.tencent.bkrepo.job.backup.pojo.query.common.BackupRole
import com.tencent.bkrepo.job.backup.pojo.query.common.BackupTemporaryToken
import com.tencent.bkrepo.job.backup.pojo.query.common.BackupUser

enum class BackupDataEnum(
    val collectionName: String,
    val fileName: String,
    val backupClazz: Class<*>,
    val type: String,
    val parentData: Boolean = true,
    val relatedData: String? = null,
    val specialData: Boolean = false,
) {
    USER_DATA(
        "user",
        "user.json",
        BackupUser::class.java,
        "PUBLIC"
    ),
    ROLE_DATA(
        "role",
        "role.json",
        BackupRole::class.java,
        "PUBLIC"
    ),
    TEMPORARY_TOKEN_DATA(
        "temporary_token",
        "temporary_token.json",
        BackupTemporaryToken::class.java,
        "PUBLIC"
    ),
    PERMISSION_DATA(
        "permission",
        "permission.json",
        BackupPermission::class.java,
        "PUBLIC"
    ),
    ACCOUNT_DATA(
        "account",
        "account.json",
        BackupAccount::class.java,
        "PUBLIC"
    ),
    PROJECT_DATA(
        "project",
        "project.json",
        BackupProjectInfo::class.java,
        "PRIVATE"
    ),
    REPOSITORY_DATA(
        "repository",
        "repository.json",
        BackupRepositoryInfo::class.java,
        "PRIVATE"
    ),
    PACKAGE_DATA(
        "package",
        "package.json",
        BackupPackageInfo::class.java,
        "PRIVATE",
        relatedData = "package_version"
    ),
    PACKAGE_VERSION_DATA(
        "package_version",
        "package-version.json",
        BackupPackageVersionInfoWithKeyInfo::class.java,
        "PRIVATE",
        false
    ),
    NODE_DATA(
        "node",
        "node.json",
        BackupNodeInfo::class.java,
        "PRIVATE"
    ),
    MAVEN_METADATA_DATA(
        "maven_metadata",
        "maven-metadata.json",
        BackupMavenMetadata::class.java,
        "PRIVATE",
        false,
        specialData = true
    ),
    CONAN_METADATA_DATA(
        "conan_metadata",
        "conan-metadata.json",
        BackupConanMetadata::class.java,
        "PRIVATE",
        false,
        specialData = true
    ),
    ;

    companion object {
        fun get(backupClazz: Class<*>): BackupDataEnum {
            values().forEach {
                if (backupClazz == it.backupClazz) return it
            }
            throw IllegalArgumentException("No enum for constant $backupClazz")
        }

        fun getByCollectionName(collectionName: String): BackupDataEnum {
            values().forEach {
                if (collectionName == it.collectionName) return it
            }
            throw IllegalArgumentException("No enum for constant $collectionName")
        }

        fun getParentAndSpecialDataList(type: String): List<BackupDataEnum> {
            return values().filter {
                it.type == type && it.parentData == true
            }
        }

        fun getNonSpecialDataList(type: String): List<BackupDataEnum> {
            return values().filter { it.type == type && it.specialData == false }
        }

        const val PUBLIC_TYPE = "PUBLIC"
        const val PRIVATE_TYPE = "PRIVATE"

    }
}
