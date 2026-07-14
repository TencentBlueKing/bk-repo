import request from '@/utils/request'

const PREFIX_PACKAGE_REPAIR = '/repository/api/package/metadata/repair'
const PREFIX_HISTORY_VERSION_REPAIR = '/repository/api/version/history/repair'
const PREFIX_VERSION_RECOUNT = '/repository/api/package/version/recount'

/**
 * 触发 Package 元数据修复（latest、historyVersion）
 * @param {Object} body - 请求体
 * @param {string} body.projectId - 项目 ID
 * @param {string} body.repoName - 仓库名
 * @param {string} [body.packageKey] - 可选，包唯一标识；不传则修复该仓库下全部 package
 * @returns {Promise}
 */
export function repairPackageMetadata(body) {
  const params = {
    projectId: body.projectId,
    repoName: body.repoName
  }
  if (body.packageKey) {
    params.packageKey = body.packageKey
  }
  return request({
    url: PREFIX_PACKAGE_REPAIR,
    method: 'post',
    params: params
  })
}

/**
 * 修复 npm 历史版本数据（全库 packageKey 迁移，异步任务）
 * @returns {Promise}
 */
export function repairHistoryVersion() {
  return request({
    url: PREFIX_HISTORY_VERSION_REPAIR,
    method: 'get'
  })
}

/**
 * 修正包的版本数（全库重算 packages.versions 字段，异步任务）
 * @returns {Promise}
 */
export function repairVersionCount() {
  return request({
    url: PREFIX_VERSION_RECOUNT,
    method: 'put'
  })
}
