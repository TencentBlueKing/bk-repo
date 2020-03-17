package com.tencent.bkrepo.repository.pojo.list

data class ListViewObject (
    val title: String,
    val headerList: List<HeaderItem>,
    val rowList: List<RowItem>,
    val footer: String,
    val backTo: Boolean = false
)