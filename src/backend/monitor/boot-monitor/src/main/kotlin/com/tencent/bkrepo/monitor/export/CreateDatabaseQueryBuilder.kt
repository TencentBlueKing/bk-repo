package com.tencent.bkrepo.monitor.export

class CreateDatabaseQueryBuilder(databaseName: String) {
    private val databaseName: String
    private val retentionPolicyClauses = arrayOfNulls<String>(4)

    init {
        require(databaseName.isNotBlank()) { "The database name cannot be null or empty" }
        this.databaseName = databaseName
    }

    fun setRetentionDuration(retentionDuration: String?): CreateDatabaseQueryBuilder {
        if (!retentionDuration.isNullOrBlank()) {
            retentionPolicyClauses[0] = DURATION_CLAUSE_TEMPLATE.format(retentionDuration)
        }
        return this
    }

    fun setRetentionReplicationFactor(retentionReplicationFactor: Int?): CreateDatabaseQueryBuilder {
        if (retentionReplicationFactor != null) {
            retentionPolicyClauses[1] = REPLICATION_FACTOR_CLAUSE_TEMPLATE.format(retentionReplicationFactor)
        }
        return this
    }

    fun setRetentionShardDuration(retentionShardDuration: String?): CreateDatabaseQueryBuilder {
        if (!retentionShardDuration.isNullOrBlank()) {
            retentionPolicyClauses[2] = SHARD_DURATION_CLAUSE_TEMPLATE.format(retentionShardDuration)
        }
        return this
    }

    fun setRetentionPolicyName(retentionPolicyName: String?): CreateDatabaseQueryBuilder {
        if (!retentionPolicyName.isNullOrBlank()) {
            retentionPolicyClauses[3] = NAME_CLAUSE_TEMPLATE.format(retentionPolicyName)
        }
        return this
    }

    fun build(): String {
        val queryStringBuilder = StringBuilder(QUERY_MANDATORY_TEMPLATE.format(databaseName))
        if (hasAnyRetentionPolicy()) {
            queryStringBuilder.append(RETENTION_POLICY_INTRODUCTION)
            queryStringBuilder.append(retentionPolicyClauses.filterNotNull().joinToString(""))
        }
        return queryStringBuilder.toString()
    }

    private fun hasAnyRetentionPolicy(): Boolean {
        return retentionPolicyClauses.any { !it.isNullOrBlank() }
    }

    companion object {
        private const val QUERY_MANDATORY_TEMPLATE = "CREATE DATABASE \"%s\""
        private const val RETENTION_POLICY_INTRODUCTION = " WITH"
        private const val DURATION_CLAUSE_TEMPLATE = " DURATION %s"
        private const val REPLICATION_FACTOR_CLAUSE_TEMPLATE = " REPLICATION %d"
        private const val SHARD_DURATION_CLAUSE_TEMPLATE = " SHARD DURATION %s"
        private const val NAME_CLAUSE_TEMPLATE = " NAME %s"
    }
}
