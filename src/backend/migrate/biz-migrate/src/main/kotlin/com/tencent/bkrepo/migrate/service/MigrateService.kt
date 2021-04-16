package com.tencent.bkrepo.migrate.service

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.migrate.artifact.MigrateArtifactInfo
import com.tencent.bkrepo.migrate.artifact.MigrateLocalRepository
import com.tencent.bkrepo.migrate.pojo.SyncResult
import com.tencent.bkrepo.migrate.pojo.suyan.SuyanArtifactInfo
import com.tencent.bkrepo.migrate.pojo.suyan.SuyanSyncRequest
import org.springframework.stereotype.Service

@Service
class MigrateService {

    /**
     * 同步蓝鲸制品
     */
    @Permission(ResourceType.REPO, PermissionAction.MANAGE)
    fun sync(migrateArtifactInfo: MigrateArtifactInfo, syncRequest: SuyanSyncRequest): SyncResult {
        val context = ArtifactSearchContext()
        val repository = ArtifactContextHolder.getRepository(context.repositoryDetail.category)
        return (repository as MigrateLocalRepository).sync(context, syncRequest)
    }

    @Permission(ResourceType.REPO, PermissionAction.MANAGE)
    fun clean(migrateArtifactInfo: MigrateArtifactInfo, metaField: String, value: String): Boolean {
        val context = ArtifactRemoveContext()
        val repository = ArtifactContextHolder.getRepository(context.repositoryDetail.category)
        return (repository as MigrateLocalRepository).clean(context, metaField, value)
    }

    @Permission(ResourceType.REPO, PermissionAction.READ)
    fun query(migrateArtifactInfo: MigrateArtifactInfo): SuyanArtifactInfo? {
        val context = ArtifactQueryContext()
        val repository = ArtifactContextHolder.getRepository(RepositoryCategory.LOCAL)
        return (repository as MigrateLocalRepository).query(context)
    }
}
