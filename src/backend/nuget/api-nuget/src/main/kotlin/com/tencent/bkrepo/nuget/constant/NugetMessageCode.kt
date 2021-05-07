package com.tencent.bkrepo.nuget.constant

import com.tencent.bkrepo.common.api.message.MessageCode

enum class NugetMessageCode(private val key: String) : MessageCode {
    PACKAGE_CONTENT_INVALID("nuget.package.content.invalid"),
    VERSION_EXITED("nuget.version.existed"),
    ;

    override fun getBusinessCode() = ordinal + 1
    override fun getKey() = key
    override fun getModuleCode() = 11
}
