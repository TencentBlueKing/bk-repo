const path = require('path')
const HtmlWebpackPlugin = require('html-webpack-plugin')
const webpackBaseConfig = require('../webpack.base')

module.exports = (env, argv) => {
    const isProd = argv.mode === 'production'
    const envDist = env && env.dist ? env.dist : 'frontend'
    const dist = path.join(__dirname, `../${envDist}/software`)
    const config = webpackBaseConfig({
        env,
        argv,
        entry: {
            repository: './src/main.js'
        },
        publicPath: '/software/',
        dist: '/software',
        port: 8085
    })
    config.plugins.pop()
    config.plugins = [
        ...config.plugins,
        new HtmlWebpackPlugin({
            filename: isProd ? `${dist}/frontend#software#index.html` : `${dist}/index.html`,
            template: path.resolve('../devops-repository/index.html'),
            inject: true,
            title: '软件源CPack'
        })
    ]

    config.devServer.historyApiFallback = {
        rewrites: [
            { from: /^\/software/, to: '/software/index.html' }
        ]
    }
    return config
}
