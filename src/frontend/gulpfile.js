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

gulp.task('build', cb => {
    const spinner = new Ora(`building bkrepo frontend project, mode: ${mode}`).start()
    const cp = require('child_process')
    cp.execSync('cd ./core/devops-repository && yarn sprite')
    spinner.succeed('create sprite.svg finished')
    
    const repositoryCmd = `lerna run public:${env} --scope=devops-repository -- --env dist=${dist} --env lsVersion=${lsVersion}`
    const opCmd = `cross-env dist=${dist} lsVersion=${lsVersion} lerna run public:${env} --scope=devops-op`
    
    cp.exec(`${repositoryCmd} && ${opCmd}`, {
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
