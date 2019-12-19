package com.tencent.bkrepo.common.api.message

enum class CommonMessageCode(private val businessCode: Int, private val key: String) : MessageCode {

    SYSTEM_ERROR(1, "system.error"),
    PARAMETER_MISSING(2, "system.parameter.missing"),
    PARAMETER_INVALID(3, "system.parameter.invalid"),
    RESOURCE_EXISTED(4, "system.resource.existed"),
    RESOURCE_NOT_FOUND(5, "system.resource.notfound"),
    OPERATION_UNSUPPORTED(6, "system.operation.unsupported"),
    PERMISSION_DENIED(7, "system.permission.denied"),
    SERVICE_CIRCUIT_BREAKER(8, "system.service.circuit-breaker"),
    SERVICE_CALL_ERROR(9, "system.service.call-error"),
    SUCCESS(0, "success") { override fun getCode() = 0 };

    override fun getBusinessCode() = businessCode
    override fun getKey() = key
    override fun getModuleCode() = 10
}
