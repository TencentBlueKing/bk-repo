package com.tencent.bkrepo.job.backup.pojo.query.common

import com.tencent.bkrepo.auth.pojo.token.Token
import com.tencent.bkrepo.job.backup.pojo.query.enums.BackupUserSource
import java.time.LocalDateTime

data class BackupUser(
    var id: String?,
    val userId: String,
    val name: String,
    val pwd: String,
    val admin: Boolean = false,
    val locked: Boolean = false,
    val tokens: List<Token> = emptyList(),
    val roles: List<String> = emptyList(),
    val asstUsers: List<String> = emptyList(),
    val group: Boolean = false,
    val email: String? = null,
    val phone: String? = null,
    var accounts: List<String>? = emptyList(),
    val source: BackupUserSource = BackupUserSource.REPO,
    val createdDate: LocalDateTime? = LocalDateTime.now(),
    val lastModifiedDate: LocalDateTime? = LocalDateTime.now()
)