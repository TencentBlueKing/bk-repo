package com.tencent.bkrepo.preview.dao

import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.mongodb.core.MongoTemplate

/**
 * preview 业务 DAO 基类：使用可单独配置的 [previewMongoTemplate]。
 */
abstract class PreviewSimpleMongoDao<T> : SimpleMongoDao<T>() {

    @Autowired
    @Qualifier("previewMongoTemplate")
    private lateinit var previewMongoTemplate: MongoTemplate

    override fun determineMongoTemplate(): MongoTemplate = previewMongoTemplate
}
