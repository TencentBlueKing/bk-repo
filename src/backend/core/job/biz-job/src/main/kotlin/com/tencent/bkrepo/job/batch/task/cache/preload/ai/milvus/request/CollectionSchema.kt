package com.tencent.bkrepo.job.batch.task.cache.preload.ai.milvus.request

import com.fasterxml.jackson.annotation.JsonProperty

data class CollectionSchema(
    val enableDynamicField: Boolean = true,
    val fields: MutableList<FieldSchema> = ArrayList(),
    @JsonProperty("autoID")
    val autoId: Boolean = false,
) {
    fun addField(fieldSchema: FieldSchema): CollectionSchema {
        if (fieldSchema.dataType == DataType.Array.name) {
            if (fieldSchema.elementDataType.isNullOrEmpty() || fieldSchema.elementTypeParams?.maxCapacity == null) {
                throw IllegalArgumentException("Element type, maxCapacity are required for array field")
            }
        }

        val isVectorType = fieldSchema.dataType == DataType.FloatVector.name ||
                fieldSchema.dataType == DataType.BinaryVector.name ||
                fieldSchema.dataType == DataType.Float16Vector.name ||
                fieldSchema.dataType == DataType.BFloat16Vector.name

        if (isVectorType && fieldSchema.elementTypeParams?.dim == null) {
            throw IllegalArgumentException("Dimension is required for vector field")
        }

        fields.add(fieldSchema)
        return this
    }

    fun getField(fieldName: String): FieldSchema? {
        for (field in fields) {
            if (field.fieldName == fieldName) {
                return field
            }
        }
        return null
    }
}
