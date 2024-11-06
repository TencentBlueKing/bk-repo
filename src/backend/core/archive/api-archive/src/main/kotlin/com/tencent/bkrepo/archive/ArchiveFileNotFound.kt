package com.tencent.bkrepo.archive

import com.tencent.bkrepo.archive.constant.ArchiveMessageCode
import com.tencent.bkrepo.common.api.exception.NotFoundException

class ArchiveFileNotFound(file: String) : NotFoundException(ArchiveMessageCode.ARCHIVE_FILE_NOT_FOUND, file)
