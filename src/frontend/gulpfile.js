const gulp = require('gulp')
const Ora = require('ora')
const yargs = require('yargs')
const argv = yargs.alias({
    dist: 'd',
    env: 'e',
    lsVersion: 'l',
    mode: 'm'
}).default({
    dist: 'frontend',
    env: 'master',
    lsVersion: 'dev',
    mode: 'standalone'
}).describe({
    dist: 'build output dist directory',
    env: 'environment [dev, test, master]',
    lsVersion: 'localStorage version',
    mode: 'mode [ci, canway-ci, standalone]'
}).argv

const { dist, env, lsVersion, mode } = argv

// 严格白名单校验：仅允许字母、数字、-、_、.、/
const SAFE_PARAM_PATTERN = /^[a-zA-Z0-9\-_./]+$/
// 危险字符黑名单
const DANGEROUS_CHARS = /[;|&$()\\`<>!#^*?{}[\]"']/

/**
 * 校验参数安全性
 * @param {string} name 参数名
 * @param {string} value 参数值
 */
function validateParam (name, value) {
    if (typeof value !== 'string') {
        console.error(`[安全校验] 参数 "${name}" 必须为字符串类型`)
        process.exit(1)
    }
    if (DANGEROUS_CHARS.test(value)) {
        console.error(`[安全校验] 参数 "${name}" 包含非法字符（禁止使用 ; | & $ ( ) \\ \` < > 等特殊字符）：${value}`)
        process.exit(1)
    }
    if (!SAFE_PARAM_PATTERN.test(value)) {
        console.error(`[安全校验] 参数 "${name}" 包含非法字符（仅允许字母、数字、- _ . /）：${value}`)
        process.exit(1)
    }
}

validateParam('dist', dist)
validateParam('env', env)
validateParam('lsVersion', lsVersion)

gulp.task('build', cb => {
    const spinner = new Ora(`building bkrepo frontend project, mode: ${mode}`).start()
    const scopeCli = mode === 'canway-ci' ? '--scope=devops-{repository-ci}' : '--scope=devops-{repository,op}'
    const cp = require('child_process')
    cp.execSync('cd ./core/devops-repository && yarn sprite')
    spinner.succeed('create sprite.svg finished')
    cp.exec(`lerna run public:${env} ${scopeCli} --parallel -- --env dist=${dist} --env lsVersion=${lsVersion}`, {
        maxBuffer: 5000 * 1024
    }, (err, res) => {
        if (err) {
            console.log(err)
            process.exit(1)
        }
        spinner.succeed(`Finished building bkrepo frontend project: , mode: ${mode}`)
        cb()
    })
})

exports.default = gulp.parallel('build')
