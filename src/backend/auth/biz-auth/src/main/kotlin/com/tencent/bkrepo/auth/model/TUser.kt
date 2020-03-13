package com.tencent.bkrepo.auth.model

import com.tencent.bkrepo.auth.pojo.Token
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document

/**
 * 角色
 */
@Document("user")
@CompoundIndexes(
    CompoundIndex(name = "userId_idx", def = "{'userId': 1}", unique = true, background = true),
    CompoundIndex(name = "tokens_id_idx", def = "{'tokens.id': 1}", background = true),
    CompoundIndex(name = "roles_idx", def = "{'roles': 1}", background = true)
)
data class TUser(
    val userId: String,
    val name: String,
    val pwd: String,
    val admin: Boolean = false,
    val locked: Boolean = false,
    val tokens: List<Token> = emptyList(),
    val roles: List<String> = emptyList()
)
