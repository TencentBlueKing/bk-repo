package com.tencent.bkrepo.fs.server.utils

import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.api.util.Preconditions
import com.tencent.bkrepo.fs.server.context.ReactiveRequestContextHolder
import com.tencent.bkrepo.repository.constant.SYSTEM_USER

object DriveServiceUtils {
    fun validateProjectRepoAndParent(projectId: String, repoName: String, parent: Long?) {
        validateProjectRepo(projectId, repoName)
        Preconditions.checkArgument(parent != null, "parent")
    }

    fun validateProjectRepo(projectId: String, repoName: String) {
        Preconditions.checkArgument(projectId.isNotBlank(), "projectId")
        Preconditions.checkArgument(repoName.isNotBlank(), "repoName")
    }

    fun validateName(name: String, nameFieldName: String) {
        Preconditions.checkArgument(name.isNotBlank(), nameFieldName)
    }

    fun validateLength(value: String?, fieldName: String, maxLength: Int) {
        if (value != null) {
            Preconditions.checkArgument(value.length <= maxLength, fieldName)
        }
    }

    fun validatePage(pageNumber: Int, pageSize: Int, maxPageSize: Int) {
        Preconditions.checkArgument(pageNumber >= 0, "pageNumber")
        Preconditions.checkArgument(pageSize in 1..maxPageSize, "pageSize")
    }

    suspend fun getUserOrSystem(): String {
        return ReactiveRequestContextHolder.getWebExchange().attributes[USER_KEY] as? String ?: SYSTEM_USER
    }
}
