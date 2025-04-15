import cookies from 'js-cookie'

// js不支持读取文件夹文件，故需先汇总所有版本
// 标准格式为V版本号_日期.md
const VersionLogs = [
    'V1.3.16-beta.2.md',
    'V1.3.16-beta.3.md',
    'V1.3.16-beta.4.md',
    'V1.3.16-beta.8.md',
    'V1.3.19-beta.1.md',
    'V1.3.20-beta.5.md',
    'V1.3.20-beta.6.md',
    'V1.3.20-beta.7.md',
    'V1.3.20-beta.10.md',
    'V1.3.21-beta.1.md',
    'V1.3.26-beta.6.md',
    'V1.3.27-alpha.2.md',
    'V1.3.29-beta.1.md',
    'V1.4.1-beta.2.md',
    'V1.4.1-beta.3.md',
    'V1.4.1-beta.4.md',
    'V1.5.1-beta.3.md',
    'V1.5.2-rc.1.md'
]

// 返回实际所有排序版本（注：版本号务必正确,不检查文件是否存在）
export function getTrueVersions () {
    const realLogs = []
    for (const version of VersionLogs) {
        const data = version.replace('.md', '').split('_', 2)
        let time = ''
        if (data.length < 2) {
            time = ''
        } else {
            time = data[1]
        }
        const logProperty = {
            version: data[0],
            time: time
        }
        realLogs.push(logProperty)
    }
    return sortLogs(realLogs)
}

export async function getVersionContext (version) {
    const language = cookies.get('blueking_language') || 'zh-cn'
    const languagePath = language === 'zh-cn' ? 'cn/' : 'en/'
    const markdownFilePath = '/ui/versionLogs/' + languagePath + version + '.md'
    const response = await fetch(markdownFilePath)
    const markdown = await response.text()
    return markdown
}

function sortLogs (realLogs) {
    return realLogs.sort((version1, version2) => {
        const versionArray1 = version1.version.replace('V', '').replace('-', '.').split('.')
        const versionArray2 = version2.version.replace('V', '').replace('-', '.').split('.')
        const versionLength1 = versionArray1.length
        const versionLength2 = versionArray2.length

        // 比较每个版本号的部分
        for (let index = 0; index < Math.min(versionLength1, versionLength2); index++) {
            const part1 = isNaN(versionArray1[index]) ? versionArray1[index] : parseInt(versionArray1[index])
            const part2 = isNaN(versionArray2[index]) ? versionArray2[index] : parseInt(versionArray2[index])
            // 如果部分是数字，进行数字比较；否则进行字符串比较
            let comparisonResult
            if (typeof part1 === 'number' && typeof part2 === 'number') {
                comparisonResult = part2 - part1 // 数字比较
            } else if (typeof part1 === 'string' && typeof part2 === 'string') {
                comparisonResult = part2.localeCompare(part1) // 字符串比较
            } else if (typeof part1 === 'number') {
                comparisonResult = -1
            } else if (typeof part2 === 'number') {
                comparisonResult = 1
            } else {
                comparisonResult = part2.localeCompare(part1)
            }

            if (comparisonResult !== 0) {
                return comparisonResult
            }
        }
        // 如果前面的部分相同，比较长度
        return versionLength2 - versionLength1
    })
}
