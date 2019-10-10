package com.tencent.bkrepo.repository.model

import org.springframework.data.mongodb.core.mapping.Document

/**
 * 消息码详情
 *
 * @author: carrypan
 * @date: 2019-10-09
 */
@Document("message_code_detail")
data class TMessageCodeDetail(
    var id: String? = null,
    var messageCode: String,
    var moduleCode: String,
    var messageDetailZhCn: String,
    var messageDetailZhTw: String? = null,
    var messageDetailEn: String? = null
)
