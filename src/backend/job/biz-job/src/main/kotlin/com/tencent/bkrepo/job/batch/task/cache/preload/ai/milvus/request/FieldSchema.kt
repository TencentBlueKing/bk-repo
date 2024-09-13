package com.tencent.bkrepo.job.batch.task.cache.preload.ai.milvus.request

import com.fasterxml.jackson.annotation.JsonProperty

data class FieldSchema (
    val fieldName: String,
    val dataType: String,
    val isPrimary: Boolean = false,
    val isPartitionKey: Boolean = false,
    /**
     * An optional parameter for Array field values
     */
    val elementDataType: String? = null,
    val elementTypeParams: ElementTypeParams? = null
)

data class ElementTypeParams(
    /**
     * An optional parameter for VarChar values that determines the maximum length of the value in the current field.
     */
    @JsonProperty("max_length")
    val maxLength: Int? = 65535,
    /**
     * An optional parameter for FloatVector or BinaryVector fields that determines the vector dimension.
     */
    val dim: Int? = null,
    /**
     * An optional parameter for Array field values that
     * determines the maximum number of elements in the current array field.
     */
    @JsonProperty("max_capacity")
    val maxCapacity: Int? = null,
)
