package com.tencent.bkrepo.migrate.pojo

data class BkProduct(
    val productCode: String?,
    val productName: String?,
    val createTime: String?,
    val productTypeCode: String?,
    val productTypeName: String?,
    val productCodeVar: String?,
    val codeType: String?,
    val level: String?
) {
    override fun equals(other: Any?): Boolean {
        return when (other) {
            !is BkProduct -> false
            else ->
                this === other ||
                        (
                                productCode == other.productCode &&
                                        productName == other.productName &&
                                        createTime == other.createTime &&
                                        productTypeCode == other.productTypeCode &&
                                        productTypeName == other.productTypeName &&
                                        productCodeVar == other.productCodeVar
                                )
        }
    }

    override fun hashCode(): Int {
        var result = productCode?.hashCode() ?: 0
        result = 31 * result + (productName?.hashCode() ?: 0)
        result = 31 * result + (createTime?.hashCode() ?: 0)
        result = 31 * result + (productTypeCode?.hashCode() ?: 0)
        result = 31 * result + (productTypeName?.hashCode() ?: 0)
        result = 31 * result + (productCodeVar?.hashCode() ?: 0)
        return result
    }
}
