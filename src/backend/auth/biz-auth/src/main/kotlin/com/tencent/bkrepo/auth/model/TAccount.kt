package com.tencent.bkrepo.auth.model

import com.tencent.bkrepo.auth.pojo.CredentialSet
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document

@Document("account")
@CompoundIndexes(
    CompoundIndex(name = "appId_idx", def = "{'appId': 1}", background = true),
    CompoundIndex(name = "credentials_accessKey_idx", def = "{'credentials.accessKey': 1}", background = true),
    CompoundIndex(name = "credentials_secretKey_idx", def = "{'credentials.secretKey': 1}", background = true)
)
data class TAccount(
    var appId: String,
    var locked: Boolean,
    var credentials:List<CredentialSet>
)