const textType = [
    'txt',
    'sh',
    'bat',
    'json',
    'yaml',
    'yml',
    'xml',
    'log',
    'ini',
    'log',
    'properties',
    'toml'
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
    'xls', 'xlsm', 'xlt', 'xltm', 'et', 'ett', 'xlam'
]

const excelType = [
    'xlsx'
]

const picType = [
    'jpg', 'jpeg', 'png', 'gif', 'bmp', 'ico', 'jfif', 'webp'
]

// 判断文本类型
export function isText (param) {
    return textType.find(type => param.endsWith(type))
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

// 判断可预览的类型(不包括pic)
export function isDisplayType (param) {
    const isExcel = excelType.find(type => param.endsWith(type))
    return isText(param) || isFormatType(param) || isExcel
}
