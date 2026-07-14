import request from '@/utils/request'

const PREFIX_PACKAGE_REPAIR = '/repository/api/package/metadata/repair'

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
