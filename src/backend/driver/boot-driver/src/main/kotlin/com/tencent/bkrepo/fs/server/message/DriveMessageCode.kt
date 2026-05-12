package com.tencent.bkrepo.fs.server.message

import com.tencent.bkrepo.common.api.message.MessageCode

enum class DriveMessageCode(private val key: String) : MessageCode {
    DIRECTORY_NOT_EMPTY("drive.directory.not-empty"),
    ;

    override fun getBusinessCode() = ordinal + 1
    override fun getKey() = key
    override fun getModuleCode() = 10
}
