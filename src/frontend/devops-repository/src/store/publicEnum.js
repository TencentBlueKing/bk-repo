// 仓库类型
export const repoEnum = MODE_CONFIG === 'ci'
    ? [
        { label: 'Generic', value: 'generic' },
        { label: 'DDC', value: 'ddc' },
        { label: 'Helm', value: 'helm' },
        { label: 'Docker', value: 'docker' },
        // { label: 'Rds', value: 'rds' },
        { label: 'Nuget', value: 'nuget' }
    ]
    : [
        { label: 'Generic', value: 'generic' },
        { label: 'DDC', value: 'ddc' },
        { label: 'Docker', value: 'docker' },
        { label: 'Maven', value: 'maven' },
        { label: 'Pypi', value: 'pypi' },
        { label: 'Npm', value: 'npm' },
        { label: 'Helm', value: 'helm' },
        // { label: 'Rds', value: 'rds' },
        { label: 'Composer', value: 'composer' },
        { label: 'Rpm', value: 'rpm' },
        { label: 'Git', value: 'git' },
        { label: 'Nuget', value: 'nuget' }
    ]

// 文件类型
export const fileType = [
    'apk',
    'babelrc', 'bat',
    'c', 'cpp', 'css',
    'docx',
    'eslintrc', 'exe',
    'h', 'html',
    'ico',
    'js', 'json',
    'log',
    'md', 'mp4', 'mpd',
    'pdf', 'php', 'png', 'pptx', 'py',
    'sh', 'svg',
    'ts', 'txt',
    'vue',
    'xlsx', 'xmind', 'xml',
    'yaml',
    'zip'
]

export function getIconName (name) {
    let type = name.split('.').pop()
    type = {
        gif: 'png',
        jpg: 'png',
        psd: 'png',
        jpge: 'png',
        mov: 'mp4',
        avi: 'mp4',
        asf: 'mp4',
        wmv: 'mp4',
        rmvb: 'mp4',
        rm: 'mp4',
        jar: 'zip',
        rar: 'zip',
        map: 'js',
        pyc: 'py',
        xsd: 'xml'
    }[type] || type
    return fileType.includes(type) ? type : 'file'
}

// 节点类型
export const nodeTypeEnum = {
    CENTER: '中心节点',
    EDGE: '边缘节点',
    STANDALONE: '独立节点',
    REMOTE: '远程节点'
}

// 同步计划执行状态
export const asyncPlanStatusEnum = {
    RUNNING: '进行中',
    SUCCESS: '已完成',
    FAILED: '同步异常'
}

// 扫描方案类型
export const scanTypeEnum = {
    GENERIC: 'Generic制品分析',
    DOCKER: 'Docker制品分析',
    MAVEN: 'Maven制品分析',
    NPM: 'Npm制品分析'
    // PYPI: 'Pypi制品分析'
}

export const SCAN_TYPE_SECURITY = 'SECURITY'
export const SCAN_TYPE_LICENSE = 'LICENSE'
export const SCAN_TYPE_SENSITIVE = 'SENSITIVE'
// 扫描类型
export const scanTypes = {
    [SCAN_TYPE_SECURITY]: {
        key: SCAN_TYPE_SECURITY,
        name: '漏洞扫描'
    },
    [SCAN_TYPE_LICENSE]: {
        key: SCAN_TYPE_LICENSE,
        name: '许可证扫描'
    },
    [SCAN_TYPE_SENSITIVE]: {
        key: SCAN_TYPE_SENSITIVE,
        name: '敏感信息扫描'
    }
}

// 扫描方案执行状态
export const scanStatusEnum = {
    INIT: '等待扫描',
    RUNNING: '扫描中',
    STOP: '扫描中止',
    SUCCESS: '扫描完成',
    UN_QUALITY: '未设置质量规则',
    QUALITY_PASS: '质量规则通过',
    QUALITY_UNPASS: '质量规则未通过',
    FAILED: '扫描异常'
}

// 漏洞风险等级
export const leakLevelEnum = {
    CRITICAL: '严重',
    HIGH: '高危',
    MEDIUM: '中危',
    LOW: '低危'
}

// 匹配规则时忽略
export const FILTER_RULE_IGNORE = 0

// 未匹配规则时忽略
export const FILTER_RULE_INCLUDE = 1

// 通过漏洞ID过滤
export const FILTER_METHOD_VUL_ID = 0

// 通过漏洞等级过滤
export const FILTER_METHOD_SEVERITY = 1

// 通过风险组件名过滤
export const FILTER_METHOD_RISKY_COMPONENT = 2
// 通过风险组件版本过滤
export const FILTER_METHOD_RISKY_COMPONENT_VERSION = 3

// 特殊的4个repo仓名称
export const specialRepoEnum = [
    'log',
    'pipeline',
    'report',
    'custom'
]
