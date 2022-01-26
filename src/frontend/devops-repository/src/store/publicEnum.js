export const repoEnum = MODE_CONFIG === 'ci'
    ? [
        'generic',
        'helm'
    ]
    : [
        'generic',
        'docker',
        'maven',
        'pypi',
        'npm',
        'helm',
        'composer',
        'rpm'
        // 'git',
        // 'nuget'
    ]

export const repoTypeEnum = {
    LOCAL: 'local', // 本地仓库。普通仓库，上传/下载构件都在本地进行
    REMOTE: 'remote', // 远程仓库。通过访问远程地址拉取构件，不支持上传
    VIRTUAL: 'virtual', // 虚拟仓库。可以组合多个本地仓库和远程仓库拉取构件，不支持上传
    COMPOSITE: 'composite' // 组合仓库。具有LOCAL的功能，同时也支持代理多个远程地址进行下载
}

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
