package com.tencent.bkrepo.analyst.exception

import com.tencent.bkrepo.analyst.message.ScannerMessageCode
import com.tencent.bkrepo.common.api.exception.NotFoundException

class SubScanTaskNotFoundException(
    parentTaskId: String
) : NotFoundException(ScannerMessageCode.SUB_SCAN_TASK_NOT_FOUND, parentTaskId)
