package com.tencent.bkrepo.repository.pojo.search

import com.tencent.bkrepo.common.query.enums.OperationType

class PackageQueryBuilder : AbstractQueryBuilder<PackageQueryBuilder>() {

    /**
     * 添加包名规则
     */
    fun name(value: String, operation: OperationType = OperationType.EQ): PackageQueryBuilder {
        return this.rule(true, NAME_FILED, value, operation)
    }

    /**
     * 添加包唯一key规则
     */
    fun key(value: String, operation: OperationType = OperationType.EQ): PackageQueryBuilder {
        return this.rule(true, KEY_FILED, value, operation)
    }

    /**
     * 添加类型规则
     */
    fun type(value: String, operation: OperationType = OperationType.EQ): PackageQueryBuilder {
        return this.rule(true, TYPE_FILED, value, operation)
    }


    companion object {
        private const val KEY_FILED = "key"
        private const val NAME_FILED = "name"
        private const val TYPE_FILED = "type"
    }
}
