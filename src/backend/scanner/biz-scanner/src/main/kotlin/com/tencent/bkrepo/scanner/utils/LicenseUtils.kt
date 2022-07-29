package com.tencent.bkrepo.scanner.utils


object LicenseUtils {
    fun convertLicenseScanType(repoType : String):String {
        return "${repoType}_LICENSE"
    }
}
