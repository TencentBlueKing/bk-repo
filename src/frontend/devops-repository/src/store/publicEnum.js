export const repoEnum = [
    'generic',
    'docker',
    'maven',
    'pypi',
    'npm',
    'helm',
    'composer',
    'rpm'
]

export const repoTypeEnum = {
    LOCAL: 'local', // 本地仓库。普通仓库，上传/下载构件都在本地进行
    REMOTE: 'remote', // 远程仓库。通过访问远程地址拉取构件，不支持上传
    VIRTUAL: 'virtual', // 虚拟仓库。可以组合多个本地仓库和远程仓库拉取构件，不支持上传
    COMPOSITE: 'composite' // 组合仓库。具有LOCAL的功能，同时也支持代理多个远程地址进行下载
}

export const fileType = [
    'access',
    'ai',
    'bat',
    'png',
    'docx',
    'html',
    'md',
    'mpd',
    'pdf',
    'pptx',
    'pub',
    'pyc',
    'rm',
    'sh',
    'txt',
    'wma',
    'xlsx',
    'xmind',
    'xml',
    'xsd',
    'zip'
]

export function getIconName (name) {
    let type = name.split('.').pop()
    type = {
        'gif': 'png',
        'svg': 'png',
        'jpg': 'png',
        'psd': 'png',
        'jpge': 'png',
        'json': 'txt',
        'jar': 'zip',
        'rar': 'zip'
    }[type] || type
    return fileType.includes(type) ? type : 'file'
}
