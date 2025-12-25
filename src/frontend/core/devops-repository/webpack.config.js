const path = require('path')
const HtmlWebpackPlugin = require('html-webpack-plugin')
const CopyWebpackPlugin = require('copy-webpack-plugin')
const webpackBaseConfig = require('../../webpack.base')

module.exports = (env, argv) => {
    const isProd = argv.mode === 'production'
    const envDist = env && env.dist ? env.dist : 'frontend'
    const dist = path.join(__dirname, `../../${envDist}/ui`)
    const config = webpackBaseConfig({
        env,
        argv,
        entry: {
            repository: './src/main.js'
        },
        publicPath: '',
        dist: '/ui',
        port: 8086
    })
    config.plugins.pop()
    config.plugins = [
        ...config.plugins,
        new HtmlWebpackPlugin({
            filename: isProd ? `${dist}/frontend#ui#index.html` : `${dist}/index.html`,
            template: 'index.html',
            inject: true,
            title: '制品库 | 腾讯蓝鲸智云'
        }),
        new CopyWebpackPlugin({
            patterns: [
                { from: path.join(__dirname, './static'), to: dist },
                { from: path.join(__dirname, '../../../../versionLogs'), to: `${dist}/versionLogs` },
                { from: path.join(__dirname, './public'), to: dist }
            ]
        })
    ]

    console.log('\n=== 启动信息 ===')
    console.log('📡 访问地址: http://localhost:8086/bkrepo/ui/')
    console.log('📁 输出目录:', dist)
    console.log('🔗 资源路径:', config.output.publicPath)

    config.devServer.historyApiFallback = {
        rewrites: [
            { from: /^\/ui/, to: '/ui/index.html' }
        ]
    }
    return config
}
