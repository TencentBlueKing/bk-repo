package com.tencent.bkrepo.common.metadata.util

import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.metadata.model.TNode
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where

object NodeDeleteHelper {
    fun buildCriteria(
        projectId: String,
        repoName: String,
        fullPath: String,
    ): Criteria {
        val normalizedFullPath = PathUtils.normalizeFullPath(fullPath)
        val escapedFullPath = PathUtils.escapeRegex(normalizedFullPath)
        // { projectId:"x", repoName:"y", deleted:null, $or: [ {fullPath:/^prefix/}, {fullPath:"exact"} ] }
        // $or 存在时，优化器对每个子句单独评估索引，两个子句都只涉及 fullPath，不涉及 path，理论上应该走 FULL_PATH_IDX。但实际走了 PATH_IDX，原因是：
        // MongoDB 处理顶层 $or 时，会用 index intersection 或 subplan 策略，
        // 如果某个子计划的 winning plan 是 PATH_IDX（例如之前有过一次 path 字段查询把 PATH_IDX 的缓存评分刷高了），
        // 优化器会复用缓存计划，导致错选。这是 MongoDB plan cache 污染问题，不是索引定义问题。
        // 单条 regex 同时匹配节点本身和所有子节点，消灭 $or 避免优化器选错索引：
        // ^/p/b$    匹配节点本身
        // ^/p/b/.*  匹配子节点
        // 根目录 "/" 需特殊处理：escapeRegex("/") = "\/"，生成的 regex "^\/(\/.*)?$" 只能匹配 "/" 和 "//..."
        // 而实际子节点路径形如 "/a/1"，不以 "//" 开头，因此必须用 "^\/.*" 匹配所有路径
        val regex = if (PathUtils.isRoot(normalizedFullPath)) {
            "^\\/.*"
        } else {
            "^$escapedFullPath(/.*)?$"
        }
        return where(TNode::projectId).isEqualTo(projectId)
            .and(TNode::repoName).isEqualTo(repoName)
            .and(TNode::deleted).isEqualTo(null)
            .and(TNode::fullPath).regex(regex)
    }
}
