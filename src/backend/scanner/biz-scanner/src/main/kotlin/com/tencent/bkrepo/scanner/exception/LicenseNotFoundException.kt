package com.tencent.bkrepo.scanner.exception

import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.scanner.message.ScannerMessageCode

class LicenseNotFoundException (
    licenseId:String
): NotFoundException(ScannerMessageCode.LICENSE_NOT_FOUND, licenseId)
