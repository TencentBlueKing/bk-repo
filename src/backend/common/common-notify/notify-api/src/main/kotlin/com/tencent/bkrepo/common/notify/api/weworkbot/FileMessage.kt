package com.tencent.bkrepo.common.notify.api.weworkbot

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 企业微信机器人文件消息
 * @property mediaId 文件ID，通过上传文件接口获取
 */
class FileMessage(@JsonProperty("media_id") val mediaId: String) : MessageBody {
    override fun type() = type

    companion object {
        const val type = "file"
    }
}