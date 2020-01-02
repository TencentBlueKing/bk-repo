package com.tencent.bkrepo.common.api.message

enum class CommonMessageCode(private val businessCode: Int, private val key: String) : MessageCode {

    SYSTEM_ERROR(1, "system.error"),
    PARAMETER_MISSING(2, "system.parameter.missing"),
    PARAMETER_INVALID(3, "system.parameter.invalid"),
    REQUEST_CONTENT_INVALID(4, "system.request.content.invalid"),
    RESOURCE_EXISTED(5, "system.resource.existed"),
    RESOURCE_NOT_FOUND(6, "system.resource.notfound"),
    RESOURCE_EXPIRED(7, "system.resource.expired"),
    OPERATION_UNSUPPORTED(8, "system.operation.unsupported"),
    PERMISSION_DENIED(9, "system.permission.denied"),
    SERVICE_CIRCUIT_BREAKER(10, "system.service.circuit-breaker"),
    SERVICE_CALL_ERROR(11, "system.service.call-error"),
    SUCCESS(0, "success") { override fun getCode() = 0 };

    override fun getBusinessCode() = businessCode
    override fun getKey() = key
    override fun getModuleCode() = 1
}
