import request from '@/utils/request'

const PREFIX_REPOSITORY = '/repository/api/repo'

export const REPO_TYPE_GENERIC = 'GENERIC'
export const REPO_TYPE_DOCKER = 'DOCKER'
export const REPO_TYPE_MAVEN = 'MAVEN'
export const REPO_TYPE_PYPI = 'PYPI'
export const REPO_TYPE_NPM = 'NPM'
export const REPO_TYPE_HELM = 'HELM'
export const REPO_TYPE_RDS = 'RDS'
export const REPO_TYPE_COMPOSER = 'COMPOSER'
export const REPO_TYPE_RPM = 'RPM'
export const REPO_TYPE_NUGET = 'NUGET'
export const REPO_TYPE_GIT = 'GIT'
export const REPO_TYPE_OCI = 'OCI'

export const repoTypes = [
  REPO_TYPE_GENERIC,
  REPO_TYPE_DOCKER,
  REPO_TYPE_MAVEN,
  REPO_TYPE_PYPI,
  REPO_TYPE_NPM,
  REPO_TYPE_HELM,
  REPO_TYPE_RDS,
  REPO_TYPE_COMPOSER,
  REPO_TYPE_RPM,
  REPO_TYPE_NUGET,
  REPO_TYPE_GIT,
  REPO_TYPE_OCI
]

export function listRepositories(projectId) {
  return request({
    url: `${PREFIX_REPOSITORY}/list/${projectId}`,
    method: 'get'
  })
}
