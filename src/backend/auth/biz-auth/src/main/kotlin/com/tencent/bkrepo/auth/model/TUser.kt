package com.tencent.bkrepo.auth.model

import io.swagger.annotations.ApiModelProperty
import java.time.LocalDateTime
import org.springframework.data.mongodb.core.mapping.Document
import com.tencent.bkrepo.auth.pojo.Token

/**
 * 角色
 */
@Document("user")
data class TUser(
    val uId: String? = null,
    val name: String,
    val pwd: String,
    val admin: Boolean? = false,
    val locked: Boolean? = false,
    val tokens: List<Token>? = emptyList(),
    val roles: List<String>? = emptyList()
)
