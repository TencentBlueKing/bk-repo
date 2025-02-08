import request from '@/utils/request'

const PREFIX_SCAN = '/analyst/api/scan'
const PREFIX_SCANNER = '/analyst/api/scanners'
const PREFIX_EXECUTION_CLUSTER = '/analyst/api/execution/clusters'
const PREFIX_FILTER_RULE = '/analyst/api/filter/rules'
const PREFIX_PROJECT_SCAN_CONFIGURATION = `${PREFIX_SCAN}/configurations`

export const SCANNER_DISPATCHER_K8S = 'k8s'
export const SCANNER_DISPATCHER_DOCKER = 'docker'

export const SCANNER_TYPE_STANDARD = 'standard'
export const SCANNER_TYPE_ARROWHEAD = 'arrowhead'
export const SCANNER_TYPE_TRIVY = 'trivy'
export const SCANNER_TYPE_DEPENDENCY_CHECK = 'DependencyCheck'
export const SCANNER_TYPE_SCANCODE = 'scancodeToolkit'

export const SCAN_TYPE_SECURITY = 'SECURITY'
export const SCAN_TYPE_LICENSE = 'LICENSE'
export const SCAN_TYPE_SENSITIVE = 'SENSITIVE'
export const scanTypes = [
  SCAN_TYPE_SECURITY,
  SCAN_TYPE_LICENSE,
  SCAN_TYPE_SENSITIVE
]

export const RULE_TYPE_IGNORE = 0
export const RULE_TYPE_INCLUDE = 1

// 通过漏洞ID过滤
export const FILTER_METHOD_VUL_ID = 0

// 通过漏洞等级过滤
export const FILTER_METHOD_SEVERITY = 1

// 通过风险组件名过滤
export const FILTER_METHOD_RISKY_COMPONENT = 2

// 通过风险组件版本过滤
export const FILTER_METHOD_RISKY_COMPONENT_VERSION = 3

export function createScanner(scanner) {
  return request({
    url: `${PREFIX_SCANNER}`,
    method: 'post',
    data: scanner
  })
}

export function deleteScanner(name) {
  return request({
    url: `${PREFIX_SCANNER}/${name}`,
    method: 'delete'
  })
}

export function updateScanner(scanner) {
  return request({
    url: `${PREFIX_SCANNER}/${scanner.name}`,
    method: 'put',
    data: scanner
  })
}

export function scanners() {
  return request({
    url: `${PREFIX_SCANNER}`,
    method: 'get'
  })
}

export function scan(scanner, projectId, repoName, path) {
  return request({
    url: `${PREFIX_SCAN}`,
    method: 'post',
    data: {
      scanner: scanner,
      rule: {
        relation: 'AND',
        rules: [
          {
            'field': 'projectId',
            'value': projectId,
            'operation': 'EQ'
          },
          {
            'field': 'repoName',
            'value': repoName,
            'operation': 'EQ'
          },
          {
            'field': 'fullPath',
            'value': path,
            'operation': 'PREFIX'
          }
        ]
      }
    }
  })
}

export function createProjectScanConfiguration(configuration) {
  if (!configuration.dispatcher) {
    configuration.dispatcher = null
  }
  return request({
    url: `${PREFIX_PROJECT_SCAN_CONFIGURATION}`,
    method: 'post',
    data: configuration
  })
}

export function deleteProjectScanConfiguration(projectId) {
  return request({
    url: `${PREFIX_PROJECT_SCAN_CONFIGURATION}/${projectId}`,
    method: 'delete'
  })
}

export function updateProjectScanConfiguration(configuration) {
  if (!configuration.dispatcher) {
    configuration.dispatcher = null
  }
  return request({
    url: `${PREFIX_PROJECT_SCAN_CONFIGURATION}`,
    method: 'put',
    data: configuration
  })
}

export function projectScanConfigurations(pageNumber, pageSize, projectId) {
  return request({
    url: `${PREFIX_PROJECT_SCAN_CONFIGURATION}`,
    method: 'get',
    params: {
      pageNumber,
      pageSize,
      projectId
    }
  })
}

export function createFilterRule(rule) {
  return request({
    url: `${PREFIX_FILTER_RULE}`,
    method: 'post',
    data: rule
  })
}

export function deleteFilterRule(ruleId) {
  return request({
    url: `${PREFIX_FILTER_RULE}/${ruleId}`,
    method: 'delete'
  })
}

export function updateFilterRule(rule) {
  return request({
    url: `${PREFIX_FILTER_RULE}/${rule.id}`,
    method: 'put',
    data: rule
  })
}

export function getFilterRules(pageNumber, pageSize) {
  return request({
    url: `${PREFIX_FILTER_RULE}`,
    method: 'get',
    params: {
      pageNumber,
      pageSize
    }
  })
}

export function listDispatchers() {
  return request({
    url: `${PREFIX_EXECUTION_CLUSTER}`,
    method: 'get'
  })
}
