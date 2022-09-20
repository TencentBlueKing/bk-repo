// 仓库类型
export const repoEnum = MODE_CONFIG === 'ci'
    ? [
        'generic',
        'helm',
        'rds',
        'docker'
    ]
    : [
        'generic',
        'docker',
        'maven',
        'pypi',
        'npm',
        'helm',
        'rds',
        'composer',
        'rpm'
        // 'git',
        // 'nuget'
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
    GENERIC: 'Generic仓库漏洞扫描',
    DOCKER: 'Docker仓库漏洞扫描',
    MAVEN: 'Maven仓库漏洞扫描',
    // NPM: 'Npm仓库漏洞扫描',
    // PYPI: 'Pypi仓库漏洞扫描',
    GENERIC_LICENSE: 'Generic仓库许可证扫描',
    MAVEN_LICENSE: 'Maven仓库许可证扫描'
}

/**
 * 安全漏洞扫描
 * @type {string}
 */
export const SCAN_TYPE_SECURITY = 'SECURITY'

/**
 * License扫描
 * @type {string}
 */
export const SCAN_TYPE_LICENSE = 'LICENSE'

export const scannerTypeEnum = {
    // 科恩
    arrowhead: {
        GENERIC: '支持zip、tar、tgz、jar、war、exe、apk等多种常用文件格式',
        MAVEN: '',
        DOCKER: '',
        GENERIC_LICENSE: '支持apk、ipa、aab、jar格式的文件',
        MAVEN_LICENSE: ''
    },
    // DependencyCheck
    DependencyCheck: {
        GENERIC: '支持zip、tar、tgz、jar、war、exe、apk等多种常用文件格式',
        MAVEN: '',
        NPM: '',
        PYPI: ''
    },
    scancodeToolkit: {
        GENERIC_LICENSE: '支持zip、tar、tgz、jar、war、exe、apk等多种常用文件格式',
        MAVEN_LICENSE: ''
    },
    trivy: {
        DOCKER: ''
    }
}

export const genericScanFileTypes = [
    'zip', 'tar', 'tgz', 'jar', 'war', 'exe',
    'apk', 'ear', 'sar', 'nupkg', 'gz', 'bz2',
    'tbz2', 'rpm', 'ipa', 'aab'
]

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
    CRITICAL: '危急',
    HIGH: '高级',
    MEDIUM: '中级',
    LOW: '低级'
}
