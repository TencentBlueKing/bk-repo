package com.tencent.bkrepo.common.metadata.util.drive

import com.tencent.bkrepo.common.metadata.pojo.drive.DriveMetadataQueryRule
import com.tencent.bkrepo.common.query.enums.OperationType
import org.bson.Document
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.Aggregation.match
import org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation
import org.springframework.data.mongodb.core.aggregation.AggregationOperation
import org.springframework.data.mongodb.core.aggregation.AggregationOperationContext
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.domain.Sort

object DriveNodeSeriesCountHelper {

    const val LATEST_VERSION_TYPE_FIELD = "_seriesLatestType"
    const val GROUP_BY_VALUE_FIELD = "_groupByValue"

    fun seriesFieldName(metadataKey: String): String = "_series_$metadataKey"

    fun splitMetadataForSeriesCount(
        metadata: List<DriveMetadataQueryRule>,
        latestVersionFilterKey: String,
    ): Pair<List<DriveMetadataQueryRule>, List<DriveMetadataQueryRule>> {
        val latestFilterRules = metadata.filter { it.key == latestVersionFilterKey }
        val matchMetadata = metadata.filter { it.key != latestVersionFilterKey }
        return latestFilterRules to matchMetadata
    }

    fun buildAggregation(
        matchCriteria: Criteria,
        distinctByMetadataKeys: List<String>,
        latestVersionFilterKey: String?,
        latestFilterRules: List<DriveMetadataQueryRule>,
        groupByMetadataKey: String? = null,
    ): Aggregation {
        val operations = mutableListOf<AggregationOperation>()
        operations.add(match(matchCriteria))
        operations.add(
            addSeriesMetadataFields(
                distinctByMetadataKeys = distinctByMetadataKeys,
                latestVersionFilterKey = latestVersionFilterKey,
                groupByMetadataKey = groupByMetadataKey,
            ),
        )
        operations.add(match(allSeriesKeysPresentCriteria(distinctByMetadataKeys)))
        operations.add(
            Aggregation.sort(
                Sort.by(Sort.Direction.DESC, "lastModifiedDate")
                    .and(Sort.by(Sort.Direction.DESC, "ino")),
            ),
        )
        operations.add(
            groupBySeries(
                distinctByMetadataKeys = distinctByMetadataKeys,
                latestVersionFilterKey = latestVersionFilterKey,
                groupByMetadataKey = groupByMetadataKey,
            ),
        )
        if (latestFilterRules.isNotEmpty()) {
            operations.add(match(latestVersionFilterCriteria(latestFilterRules)))
        }
        if (groupByMetadataKey != null) {
            operations.add(groupByValueCount())
        } else {
            operations.add(Aggregation.count().`as`("total"))
        }
        return newAggregation(*operations.toTypedArray())
    }

    fun buildFileGroupByAggregation(
        matchCriteria: Criteria,
        groupByMetadataKey: String,
    ): Aggregation {
        return newAggregation(
            match(matchCriteria),
            addGroupByValueField(groupByMetadataKey),
            groupByValueCount(),
        )
    }

    private fun addSeriesMetadataFields(
        distinctByMetadataKeys: List<String>,
        latestVersionFilterKey: String?,
        groupByMetadataKey: String?,
    ): AggregationOperation {
        return AggregationOperation { _: AggregationOperationContext ->
            val fields = Document()
            distinctByMetadataKeys.forEach { key ->
                fields.append(seriesFieldName(key), metadataValueExpression(key))
            }
            latestVersionFilterKey?.let { key ->
                fields.append(LATEST_VERSION_TYPE_FIELD, metadataValueExpression(key))
            }
            groupByMetadataKey?.let { key ->
                fields.append(GROUP_BY_VALUE_FIELD, metadataValueExpression(key))
            }
            Document("\$addFields", fields)
        }
    }

    private fun addGroupByValueField(groupByMetadataKey: String): AggregationOperation {
        return AggregationOperation { _: AggregationOperationContext ->
            Document(
                "\$addFields",
                Document(GROUP_BY_VALUE_FIELD, metadataValueExpression(groupByMetadataKey)),
            )
        }
    }

    private fun allSeriesKeysPresentCriteria(distinctByMetadataKeys: List<String>): Criteria {
        val criteriaList = distinctByMetadataKeys.map { key ->
            Criteria.where(seriesFieldName(key))
                .exists(true)
                .nin(null, "")
        }
        return Criteria().andOperator(*criteriaList.toTypedArray())
    }

    private fun groupBySeries(
        distinctByMetadataKeys: List<String>,
        latestVersionFilterKey: String?,
        groupByMetadataKey: String?,
    ): AggregationOperation {
        return AggregationOperation { _: AggregationOperationContext ->
            val groupId = Document()
            distinctByMetadataKeys.forEach { key ->
                groupId.append(key, "\$${seriesFieldName(key)}")
            }
            val group = Document("_id", groupId)
            if (latestVersionFilterKey != null) {
                group.append(LATEST_VERSION_TYPE_FIELD, Document("\$first", "\$$LATEST_VERSION_TYPE_FIELD"))
            }
            if (groupByMetadataKey != null) {
                group.append(GROUP_BY_VALUE_FIELD, Document("\$first", "\$$GROUP_BY_VALUE_FIELD"))
            }
            Document("\$group", group)
        }
    }

    private fun groupByValueCount(): AggregationOperation {
        return AggregationOperation { _: AggregationOperationContext ->
            Document(
                "\$group",
                Document("_id", "\$$GROUP_BY_VALUE_FIELD")
                    .append("count", Document("\$sum", 1)),
            )
        }
    }

    private fun latestVersionFilterCriteria(latestFilterRules: List<DriveMetadataQueryRule>): Criteria {
        val criteriaList = latestFilterRules.map { rule ->
            resolveLatestTypeRuleCriteria(rule)
        }
        return if (criteriaList.size == 1) {
            criteriaList.first()
        } else {
            Criteria().andOperator(*criteriaList.toTypedArray())
        }
    }

    private fun resolveLatestTypeRuleCriteria(rule: DriveMetadataQueryRule): Criteria {
        val field = LATEST_VERSION_TYPE_FIELD
        return when (rule.operation) {
            OperationType.EQ -> Criteria.where(field).isEqualTo(rule.value)
            OperationType.NE -> Criteria.where(field).ne(rule.value)
            OperationType.IN -> Criteria.where(field).`in`(rule.value as Collection<*>)
            OperationType.NIN -> Criteria.where(field).nin(rule.value as Collection<*>)
            OperationType.NULL -> Criteria.where(field).isNull()
            OperationType.NOT_NULL -> Criteria.where(field).ne(null)
            else -> Criteria.where(field).isEqualTo(rule.value)
        }
    }

    private fun metadataValueExpression(metadataKey: String): Document {
        return Document(
            "\$let",
            Document()
                .append(
                    "vars",
                    Document(
                        "item",
                        Document(
                            "\$first",
                            Document(
                                "\$filter",
                                Document()
                                    .append("input", "\$metadata")
                                    .append("as", "m")
                                    .append(
                                        "cond",
                                        Document("\$eq", listOf("\$\$m.key", metadataKey)),
                                    ),
                            ),
                        ),
                    ),
                )
                .append("in", "\$\$item.value"),
        )
    }
}
