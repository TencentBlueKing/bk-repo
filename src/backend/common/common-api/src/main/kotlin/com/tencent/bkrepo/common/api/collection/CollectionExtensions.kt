package com.tencent.bkrepo.common.api.collection

/**
 * 支持大数据集的聚合，元素会优先加入相似的组，而不是最相似的组。
 * 因为该聚合算法最后的分组不是一个最佳的聚合方式，这是对时间复杂度和数据准确度的一个折中。
 * 算法思路：
 * 1. 先对key进行排序（排序的复杂度远小于聚合，同时我们认为key排序接近的，也是应该是相似的）
 * 2. 遍历每一项。我们使用三个分组来处理数据，一个新数据，要么加入当前组，要么加入之前的分组（为了防止噪点），要么自己新成立一个组。
 * 3. 分组完成 （这里如果是最佳聚合，则需要不断进行初始分组的调整，使得分组最后不再变化，类似K-means）
 * @param key 用于分组的key
 * @param similar 相似判断函数
 * @return 聚合后的分组结果
 * */
fun <E> Collection<E>.groupBySimilar(key: (x1: E) -> String, similar: (x1: E, x2: E) -> Boolean): List<List<E>> {
    val sortedNodes = this.sortedBy { key(it) }
    val groups = mutableListOf<MutableList<E>>()
    var group = mutableListOf<E>()
    groups.add(group)
    group.add(sortedNodes.first())
    var previousGroup: MutableList<E>? = null
    for (i in 1 until sortedNodes.size) {
        val str2 = sortedNodes[i]
        /*
        * 一个新数据，只存在以下三种情况
        * 1. 加入当前组
        * 2. 中间存在干扰数据，加入之前的组
        * 3. 新建一个组
        * */
        // 判断新加入的元素与一头一尾是否相似，控制单个分类不要过于离散
        if (similar(group.first(), str2) && similar(group.last(), str2)) {
            group.add(str2)
        } else if (previousGroup != null && similar(group.first(), str2) && similar(previousGroup.last(), str2)) {
            previousGroup.add(str2)
        } else {
            if (group.size > 1) {
                previousGroup = group
            }
            group = mutableListOf()
            groups.add(group)
            group.add(str2)
        }
    }
    return groups
}
