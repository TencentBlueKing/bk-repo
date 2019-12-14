package com.tencent.bkrepo.common.api.message

enum class CommonMessageCode(private val businessCode: Int, private val key: String) : MessageCode {

    SYSTEM_ERROR(1, "common.system.error"),
    PARAMETER_MISSING(2, "common.parameter.missing"),
    PARAMETER_EXIST(3, "common.parameter.exist"),
    PARAMETER_INVALID(4, "common.parameter.invalid"),
    RESOURCE_NOT_FOUND(5, "common.resource.notfound"),
    OPERATION_UNSUPPORTED(6, "common.operation.unsupported"),
    PERMISSION_DENIED(7, "common.permission.denied"),
    SERVICE_CIRCUIT_BREAKER(8, "common.service.circuit-breaker"),
    SERVICE_CALL_ERROR(9, "common.service.call-error"),
    SUCCESS(0, "common.success") { override fun getCode() = 0 };

    override fun getBusinessCode() = businessCode
    override fun getKey() = key
    override fun getModuleCode() = 10
}
