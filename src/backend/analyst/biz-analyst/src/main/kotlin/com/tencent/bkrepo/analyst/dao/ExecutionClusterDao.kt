package com.tencent.bkrepo.analyst.dao

import com.tencent.bkrepo.analyst.model.TExecutionCluster
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository

@Repository
class ExecutionClusterDao : ScannerSimpleMongoDao<TExecutionCluster>() {
    fun existsByName(name: String): Boolean {
        return exists(nameQuery(name))
    }

    fun findByName(name: String): TExecutionCluster? {
        return findOne(nameQuery(name))
    }

    fun deleteByName(name: String): Long {
        return remove(nameQuery(name)).deletedCount
    }

    private fun nameQuery(name: String): Query {
        return Query(TExecutionCluster::name.isEqualTo(name))
    }
}
