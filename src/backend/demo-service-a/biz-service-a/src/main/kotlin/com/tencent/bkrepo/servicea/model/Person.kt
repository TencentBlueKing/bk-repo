package com.tencent.bkrepo.servicea.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.index.TextIndexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field

// @Document("d_person") 标记为mongodb的文档，对应collection名称为d_person
@Document // 标记为mongodb的文档，对应collection名称为person
data class Person(
    @Id // id字段，如果属性名称为id，@Id注解可以省略
    var id: String? = null,

    @TextIndexed // @TextIndexed标记该字段支持全文检索
    var name: String,

    @Indexed(unique = true) // @Indexed标记该字段需要创建索引, unique表示唯一索引
    var mobile: String,

    @Field("age") // @Field代表一个字段，如果属性名称和数据库属性相同可以不加@Field
    var age: Int = 0,

    var gender: Gender = Gender.UNKNOWN
)

enum class Gender {
    MALE,
    FEMALE,
    UNKNOWN
}
