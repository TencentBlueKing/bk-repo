package com.tencent.bkrepo.archive.constant

import com.tencent.bkrepo.common.api.message.MessageCode

enum class ArchiveMessageCode(private val key: String) : MessageCode {
    ARCHIVE_FILE_NOT_FOUND("archive-file.not.found"),
    RESTORE_COUNT_LIMIT("restore.count.limit"),
    ;

    override fun getBusinessCode() = ordinal + 1
    override fun getKey() = key
    override fun getModuleCode() = 25
}
