import request from '@/utils/request'

const PREFIX_REFERENCE = '/repository/api/references'

export function reference(sha256, projectId, repoName, credentialKey) {
  return request({
    url: `${PREFIX_REFERENCE}/${sha256}`,
    method: 'get',
    params: {
      projectId,
      repoName,
      credentialKey
    }
  })
}

