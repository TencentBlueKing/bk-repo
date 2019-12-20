package com.tencent.bkrepo.auth.model

import com.tencent.bkrepo.auth.pojo.CredentialSet
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

@Document("account")
data class TAccount(
    var appId: String,
    var locked: Boolean,
    var credentials:List<CredentialSet>
)