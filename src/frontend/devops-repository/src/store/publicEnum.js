// 仓库类型
export const repoEnum = MODE_CONFIG === 'ci'
    ? [
        'generic',
        'helm',
        'rds'
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
    STANDALONE: '独立节点'
}

// 同步计划执行状态
export const asyncPlanStatusEnum = {
    RUNNING: '进行中',
    SUCCESS: '已完成',
    FAILED: '同步异常'
}

// 扫描方案类型
export const scanTypeEnum = {
    GENERIC: 'Generic仓库文件扫描',
    MAVEN: 'Maven仓库依赖包扫描'
}

// 扫描方案执行状态
export const scanStatusEnum = {
    INIT: '等待扫描',
    RUNNING: '正在扫描',
    STOP: '扫描中止',
    SUCCESS: '扫描完成',
    FAILED: '扫描异常'
}

// 漏洞风险等级
export const leakLevelEnum = {
    CRITICAL: '危急',
    HIGH: '高级',
    MEDIUM: '中级',
    LOW: '低级'
}
