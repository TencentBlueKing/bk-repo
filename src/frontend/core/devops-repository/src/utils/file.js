const textType = [
    'txt',
    'sh',
    'bat',
    'json',
    'yaml',
    'yml',
    'xml',
    'log',
    'properties',
    'sql'
]

const formatType = [
    'docx',
    'pdf',
    'wps',
    'doc',
    'docm',
    'xls',
    'xlsm',
    'ppt',
    'pptx',
    'vsd',
    'rtf',
    'odt',
    'wmf',
    'emf',
    'dps',
    'et',
    'ods',
    'ots',
    'tsv',
    'odp',
    'otp',
    'sxi',
    'ott',
    'vsdx',
    'fodt',
    'fods',
    'xltx',
    'tga',
    'psd',
    'dotm',
    'ett',
    'xlt',
    'xltm',
    'wpt',
    'dot',
    'xlam',
    'dotx',
    'xla',
    'pages',
    'eps'
]

const isHtmlFormatType = [
    'xlsm', 'xlt', 'xltm', 'et', 'ett', 'xlam'
]

const excelType = [
    'xls', 'xlsx', 'csv'
]

const picType = [
    'jpg', 'jpeg', 'png', 'gif', 'bmp', 'ico', 'jfif', 'webp', 'svg'
]

const markdownType = [
    'md'
]

const jsxType = [
    'jsx'
]

const htmlFileType = [
    'html',
    'htm'
]

const xmindType = [
    'xmind'
]

const mediaVideoType = [
    'mp4',
    'webm'
]

const mediaAudioType = [
    'mp3',
    'wav',
    'ogg',
    'oga',
    'm4a'
]

// 与 preview 服务 FileType.CODES 对齐（html 已拆到 isHtmlFile 做渲染预览）
// 含原文本类后缀：统一走 onlinePreview + Monaco
const codeType = [
    'java',
    'c',
    'php',
    'go',
    'python',
    'py',
    'js',
    'ftl',
    'css',
    'lua',
    'sh',
    'rb',
    'yaml',
    'yml',
    'json',
    'h',
    'cpp',
    'cs',
    'aspx',
    'jsp',
    'sql',
    'ini',
    'toml',
    'txt',
    'bat',
    'xml',
    'log',
    'properties'
]

function getFileSuffix (param) {
    if (!param) {
        return ''
    }
    const normalized = String(param).replace(/\\/g, '/')
    const baseName = normalized.includes('/')
        ? normalized.slice(normalized.lastIndexOf('/') + 1)
        : normalized
    const dotIndex = baseName.lastIndexOf('.')
    if (dotIndex < 0) {
        // 远程预览接口可能直接传 suffix（如 java）
        return baseName.toLowerCase()
    }
    if (dotIndex === baseName.length - 1) {
        return ''
    }
    return baseName.slice(dotIndex + 1).toLowerCase()
}

// 判断文本类型
export function isText (param) {
    return textType.find(type => param.endsWith(type))
}

// 判断代码类型（走 preview onlinePreview + Monaco）
export function isCode (param) {
    const suffix = getFileSuffix(param)
    return codeType.find(type => type === suffix)
}

// 判断预览可转换的类型（转换的为pdf或者html）
export function isFormatType (param) {
    return formatType.find(type => param.endsWith(type))
}

// 判断转换成html的类型
export function isHtmlType (param) {
    return isHtmlFormatType.find(type => param.endsWith(type))
}

export function isPic (param) {
    return picType.find(type => param.endsWith(type))
}

export function isExcel (param) {
    return excelType.find(type => param.endsWith(type))
}

export function isMarkdown (param) {
    return markdownType.find(type => param.endsWith(type))
}

export function isJsx (param) {
    return jsxType.find(type => param.endsWith(type))
}

// 单 HTML 文件渲染预览（勿与 isHtmlType / Excel→HTML 混淆）
export function isHtmlFile (param) {
    const suffix = getFileSuffix(param)
    return htmlFileType.find(type => type === suffix)
}

export function isXmind (param) {
    return xmindType.find(type => param.endsWith(type))
}

export function isMediaVideo (param) {
    const suffix = getFileSuffix(param)
    return Boolean(mediaVideoType.find(type => type === suffix))
}

export function isMediaAudio (param) {
    const suffix = getFileSuffix(param)
    return Boolean(mediaAudioType.find(type => type === suffix))
}

export function isMedia (param) {
    return isMediaVideo(param) || isMediaAudio(param)
}

// 判断可预览的类型(不包括pic)
export function isDisplayType (param) {
    const isExcel = excelType.find(type => param.endsWith(type))
    return isText(param) || isFormatType(param) || isExcel || isXmind(param)
}

// 判断可预览的类型(包括pic)
export function isOutDisplayType (param) {
    const isExcel = excelType.find(type => param.endsWith(type))
    return isText(param) || isCode(param) || isFormatType(param) || isExcel || isPic(param) || isMarkdown(param) || isJsx(param) || isHtmlFile(param) || isXmind(param)
}
