package com.tencent.bkrepo.fs.server.request.drive

import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.constant.PROJECT_ID
import com.tencent.bkrepo.common.artifact.constant.REPO_NAME
import com.tencent.bkrepo.common.metadata.pojo.drive.DriveMetadataQueryRule
import com.tencent.bkrepo.common.metadata.pojo.drive.DriveNameQueryRule
import com.tencent.bkrepo.common.query.enums.OperationType
import org.springframework.data.domain.Sort
import org.springframework.web.reactive.function.server.ServerRequest
import java.time.LocalDateTime

/**
 * 整仓文件搜索游标分页请求
 */
class DriveNodeSearchRequest(
    request: ServerRequest,
    payload: DriveNodeSearchPayload,
) : DriveNodeRequest(
    projectId = request.pathVariable(PROJECT_ID),
    repoName = request.pathVariable(REPO_NAME),
) {
    val pageSize: Int = payload.pageSize.takeIf { it > 0 } ?: DEFAULT_PAGE_SIZE
    val name: DriveNameQueryRule? = payload.name
    val metadata: List<DriveMetadataQueryRule> = payload.metadata
    val lastModifiedDate: LocalDateTime? = payload.lastModifiedDate
    val lastId: String? = payload.lastId?.takeIf { it.isNotBlank() }
    val direction: Sort.Direction

    init {
        DriveNodeSearchQueryParams.validateNameRule(name)
        DriveNodeSearchQueryParams.validateMetadataRules(metadata)
        DriveNodeSearchQueryParams.validateCursorPair(lastModifiedDate != null, lastId != null)
        direction = parseDirection(payload.direction)
    }

    private fun parseDirection(raw: String?): Sort.Direction {
        if (raw.isNullOrBlank()) return Sort.Direction.DESC
        return try {
            Sort.Direction.fromString(raw)
        } catch (_: IllegalArgumentException) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "direction")
        }
    }
}

data class DriveNodeSearchPayload(
    val pageSize: Int = DEFAULT_PAGE_SIZE,
    val name: DriveNameQueryRule? = null,
    val direction: String? = null,
    val lastModifiedDate: LocalDateTime? = null,
    val lastId: String? = null,
    val metadata: List<DriveMetadataQueryRule> = emptyList(),
)

/**
 * 整仓文件搜索数量统计请求
 */
class DriveNodeSearchCountRequest(
    request: ServerRequest,
    payload: DriveNodeSearchCountPayload,
) : DriveNodeRequest(
    projectId = request.pathVariable(PROJECT_ID),
    repoName = request.pathVariable(REPO_NAME),
) {
    val name: DriveNameQueryRule? = payload.name
    val metadata: List<DriveMetadataQueryRule> = payload.metadata
    val distinctByMetadataKeys: List<String> = payload.distinctByMetadataKeys.orEmpty()
    val groupByMetadataKey: String? = payload.groupByMetadataKey?.trim()?.takeIf { it.isNotEmpty() }

    init {
        DriveNodeSearchQueryParams.validateNameRule(name)
        DriveNodeSearchQueryParams.validateMetadataRules(metadata)
        DriveNodeSearchQueryParams.validateDistinctByMetadataKeys(distinctByMetadataKeys)
        DriveNodeSearchQueryParams.validateGroupByMetadataKey(payload.groupByMetadataKey)
    }
}

data class DriveNodeSearchCountPayload(
    val name: DriveNameQueryRule? = null,
    val metadata: List<DriveMetadataQueryRule> = emptyList(),
    val distinctByMetadataKeys: List<String>? = null,
    val groupByMetadataKey: String? = null,
)

internal object DriveNodeSearchQueryParams {
    fun validateNameRule(name: DriveNameQueryRule?) {
        if (name == null) return
        validateOperationValue("name", name.value, name.operation)
    }

    fun validateMetadataRules(metadata: List<DriveMetadataQueryRule>) {
        metadata.forEachIndexed { index, rule ->
            if (rule.key.isBlank()) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "metadata[$index].key")
            }
            validateOperationValue("metadata[$index]", rule.value, rule.operation)
        }
    }

    fun validateCursorPair(lastModifiedDatePresent: Boolean, lastIdPresent: Boolean) {
        if (lastModifiedDatePresent != lastIdPresent) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "lastModifiedDate/lastId")
        }
    }

    fun validateDistinctByMetadataKeys(distinctByMetadataKeys: List<String>) {
        distinctByMetadataKeys.forEachIndexed { index, key ->
            if (key.isBlank()) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "distinctByMetadataKeys[$index]")
            }
        }
    }

    fun validateGroupByMetadataKey(groupByMetadataKey: String?) {
        if (groupByMetadataKey == null) return
        if (groupByMetadataKey.isBlank()) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "groupByMetadataKey")
        }
    }

    private fun validateOperationValue(field: String, value: Any, operation: OperationType) {
        when (operation) {
            OperationType.IN, OperationType.NIN -> {
                if (value !is List<*>) {
                    throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "$field.value")
                }
            }
            OperationType.NULL, OperationType.NOT_NULL -> Unit
            else -> Unit
        }
    }
}
