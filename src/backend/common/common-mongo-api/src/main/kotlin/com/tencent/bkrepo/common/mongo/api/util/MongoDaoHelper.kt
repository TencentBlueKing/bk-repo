package com.tencent.bkrepo.common.mongo.api.util

import com.mongodb.BasicDBList
import com.tencent.bkrepo.common.api.mongo.ShardingDocument
import com.tencent.bkrepo.common.api.mongo.ShardingKey
import com.tencent.bkrepo.common.api.mongo.ShardingKeys
import com.tencent.bkrepo.common.mongo.api.util.sharding.ShardingUtils
import org.apache.commons.lang3.reflect.FieldUtils
import org.apache.commons.lang3.reflect.FieldUtils.getAllFieldsList
import org.apache.commons.lang3.reflect.FieldUtils.getFieldsListWithAnnotation
import org.bson.Document
import org.springframework.core.annotation.AnnotationUtils
import org.springframework.data.mongodb.core.aggregation.Aggregation
import java.lang.reflect.Field
import java.lang.reflect.Modifier

object MongoDaoHelper {
    /**
     * 查找[clazz]包含的注解声明的collection name
     *
     * @param clazz 数据类
     *
     * @return 有则返回对应的collection name，否则返回null
     */
    fun determineShardingCollectionName(clazz: Class<*>): String? {
        if (clazz.isAnnotationPresent(ShardingDocument::class.java)) {
            val document = clazz.getAnnotation(ShardingDocument::class.java)
            return document.collection
        }
        return null
    }

    /**
     * 获取[clazz]上的[ShardingKey]或[ShardingKeys]注解，解析出sharding column
     *
     * @param clazz 数据类
     * @param customShardingColumns 自定义sharding columns，优先级最高，如有指定则使用指定的值作为sharding key
     *
     * @return key为sharding column，value为column对应的field
     */
    fun determineShardingFields(clazz: Class<*>, customShardingColumns: List<String>): LinkedHashMap<String, Field> {
        val shardingKeysAnnotation = clazz.getAnnotation(ShardingKeys::class.java)
        val fieldsWithShardingKey = getFieldsListWithAnnotation(clazz, ShardingKey::class.java)
        require(fieldsWithShardingKey.size <= 1) {
            "Only one property could be annotated with ShardingKey annotation but find ${fieldsWithShardingKey.size}!"
        }

        // sharding key不存在
        val hasShardingKeys = shardingKeysAnnotation != null && shardingKeysAnnotation.columns.isNotEmpty()
        if (!hasShardingKeys && fieldsWithShardingKey.isEmpty()) {
            throw IllegalArgumentException("No ShardingKey found for ${clazz.name}")
        }

        // 存在多个sharding key
        if (hasShardingKeys && fieldsWithShardingKey.isNotEmpty()) {
            throw IllegalArgumentException("ShardingKey of ${clazz.name} conflict")
        }

        val columns = if (customShardingColumns.isNotEmpty()) {
            customShardingColumns
        } else if (fieldsWithShardingKey.isNotEmpty()) {
            // 单个字段作为sharding key
            val shardingField = fieldsWithShardingKey[0]
            val shardingKey = AnnotationUtils.getAnnotation(shardingField, ShardingKey::class.java)!!
            listOf(shardingKey.column.ifEmpty { determineColumnName(shardingField) })
        } else {
            // 多个字段作为sharding key
            if (shardingKeysAnnotation.columns.distinct().size != shardingKeysAnnotation.columns.size) {
                throw IllegalArgumentException(
                    "Duplicate ShardingKeys ${shardingKeysAnnotation.columns.joinToString(",")}]"
                )
            }
            shardingKeysAnnotation.columns.toList()
        }
        val columnFieldMap = columnFieldMap(clazz)
        val shardingColumnFieldMap = LinkedHashMap<String, Field>(columns.size)
        columns.forEach {
            val field = columnFieldMap[it]
            requireNotNull(field)
            shardingColumnFieldMap[it] = field
        }
        return shardingColumnFieldMap
    }

    /**
     * 获取分表数量
     *
     * @param clazz 数据类
     * @param shardingUtils 分片数计算工具类
     * @param customShardingCount 自定义分表数
     *
     * @return 分表数量
     */
    fun determineShardingCount(clazz: Class<*>, shardingUtils: ShardingUtils, customShardingCount: Int?): Int {
        if (customShardingCount != null) {
            return customShardingCount
        }

        val fieldsWithShardingKey = getFieldsListWithAnnotation(clazz, ShardingKey::class.java)
        if (fieldsWithShardingKey.isNotEmpty()) {
            val shardingKey = AnnotationUtils.getAnnotation(fieldsWithShardingKey[0], ShardingKey::class.java)!!
            return shardingUtils.shardingCountFor(shardingKey.count)
        }

        val shardingKeysAnnotation = clazz.getAnnotation(ShardingKeys::class.java)
        if (shardingKeysAnnotation != null) {
            return shardingUtils.shardingCountFor(shardingKeysAnnotation.count)
        }

        throw IllegalArgumentException("Determine sharding count failed, no ShardingKey found for ${clazz.name}")
    }

    /**
     * 获取分表键对应的值
     *
     * @param entity 数据对象
     * @param shardingFields 分表键对应的field
     *
     * @return 分表键对应的值
     */
    fun shardingValues(entity: Any, shardingFields: LinkedHashMap<String, Field>): List<Any> {
        val shardingValues = shardingFields.map {
            val shardingValue = FieldUtils.readField(it.value, entity, true)
            requireNotNull(shardingValue) { "Sharding value can not be empty!" }
            shardingValue
        }
        return shardingValues
    }

    /**
     * 获取分表键对应的值
     *
     * @param document mongo查询
     * @param shardingFields 分表键对应的field
     *
     * @return 分表键对应的值
     */
    fun shardingValuesOf(document: Document, shardingFields: LinkedHashMap<String, Field>): List<Any>? {
        val shardingValues = ArrayList<Any>(shardingFields.keys.size)
        for (column in shardingFields.keys) {
            val columnShardingValue = shardingValueOf(document, column) ?: return null
            shardingValues.add(columnShardingValue)
        }
        return shardingValues
    }

    /**
     * 获取分表键对应的值
     *
     * @param aggregation mongo聚合查询
     * @param shardingFields 分表键对应的field
     *
     * @return 分表键对应的值
     */
    fun shardingValuesOf(aggregation: Aggregation, shardingFields: LinkedHashMap<String, Field>): List<Any>? {
        val shardingValues = ArrayList<Any>(shardingFields.keys.size)
        for (column in shardingFields.keys) {
            val columnShardingValue = shardingValueOf(aggregation, column) ?: return null
            shardingValues.add(columnShardingValue)
        }
        return shardingValues
    }

    private fun shardingValueOf(aggregation: Aggregation, column: String): Any? {
        val pipeline = aggregation.toPipeline(Aggregation.DEFAULT_CONTEXT)
        for (document in pipeline) {
            if (!document.containsKey("\$match")) {
                continue
            }
            val subDocument = document["\$match"]
            require(subDocument is Document)
            val shardingValue = shardingValueOf(subDocument, column)
            if (shardingValue != null) {
                return shardingValue
            }
        }
        return null
    }

    private fun shardingValueOf(document: Document, column: String): Any? {
        for ((key, value) in document) {
            if (key == column) {
                return value
            }
            if (key != "\$and") {
                continue
            }
            require(value is BasicDBList)
            for (element in value) {
                require(element is Document)
                shardingValueOf(element, column)?.let { return it }
            }
        }
        return null
    }

    private fun columnFieldMap(clazz: Class<*>): Map<String, Field> {
        val columnFieldMap = HashMap<String, Field>()
        val allFields = getAllFieldsList(clazz)
        for (field in allFields) {
            if (Modifier.isStatic(field.modifiers)) {
                continue
            }
            val columnName = determineColumnName(field)
            if (columnFieldMap.contains(columnName)) {
                continue
            }
            columnFieldMap[columnName] = field
        }
        return columnFieldMap
    }

    private fun determineColumnName(field: Field): String {
        val fieldJavaClass = org.springframework.data.mongodb.core.mapping.Field::class.java
        val fieldAnnotation = AnnotationUtils.getAnnotation(field, fieldJavaClass)
        if (fieldAnnotation != null && fieldAnnotation.value.isNotEmpty()) {
            return fieldAnnotation.value
        }
        return field.name
    }
}
