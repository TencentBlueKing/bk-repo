const path = require('path')
const ESLintPlugin = require('eslint-webpack-plugin')
const MiniCssExtractPlugin = require('mini-css-extract-plugin')
const { VueLoaderPlugin } = require('vue-loader')
const CopyWebpackPlugin = require('copy-webpack-plugin')

module.exports = ({ entry, publicPath, dist, port = 8080, argv, env }) => {
    const isDev = argv.mode === 'development'
    const envDist = env && env.dist ? env.dist : 'frontend'
    const buildDist = path.join(__dirname, envDist, dist)
    return {
        cache: {
            type: 'filesystem',
            buildDependencies: {
                config: [__filename]
            }
        },
        ...(isDev ? { devtool: 'eval-cheap-module-source-map' } : {}),
        entry,
        output: {
            publicPath,
            chunkFilename: isDev ? '[name].js' : '[name].[chunkhash].js',
            filename: isDev ? '[name].js' : '[name].[contenthash].min.js',
            path: buildDist,
            assetModuleFilename: '[name].[ext]?[contenthash]'
        },
        module: {
            rules: [
                {
                    test: /\.vue$/,
                    include: [path.resolve(__dirname, 'devops-repository/src'), path.resolve('src')],
                    loader: 'vue-loader'
                },
                {
                    test: /\.js$/,
                    include: [path.resolve(__dirname, 'devops-repository/src'), path.resolve('src')],
                    use: [
                        {
                            loader: 'babel-loader'
                        }
                    ]
                },
                {
                    test: /\.scss$/,
                    use: [
                        MiniCssExtractPlugin.loader,
                        {
                            loader: 'css-loader',
                            options: {
                                url: {
                                    filter: url => !url.startsWith('/')
                                }
                            }
                        },
                        'sass-loader'
                    ]
                }
            ]
        },
        plugins: [
            new VueLoaderPlugin(),
            new MiniCssExtractPlugin({
                filename: isDev ? '[name].css' : '[name].[contenthash].css',
                chunkFilename: isDev ? '[id].css' : '[id].[contenthash].css',
                ignoreOrder: true
            }),
            new CopyWebpackPlugin({
                patterns: [{ from: path.join(__dirname, 'locale'), to: buildDist }],
                options: { concurrency: 100 }
            }),
            new ESLintPlugin({
                // 指定要检查的文件扩展名
                extensions: ['js', 'vue'],
                // 指定要检查的目录
                context: path.resolve(__dirname, 'core/devops-repository/src'), // 主要上下文目录
                // 其他选项
                fix: true, // 自动修复问题
                formatter: require('eslint-friendly-formatter'), // 使用友好的格式化程序
                // 你可以添加更多选项，例如：
                emitWarning: true, // 如果有 ESLint 警告，是否在控制台中显示
                failOnError: false, // 如果有错误，是否使构建失败
                failOnWarning: false, // 如果有警告，是否使构建失败
                // 使用 include 选项来指定多个路径
                files: [
                    path.resolve(__dirname, 'core/devops-repository/src/**/*.js'),
                    path.resolve(__dirname, 'core/devops-repository/src/**/*.vue'),
                    path.resolve(__dirname, 'src/**/*.js'),
                    path.resolve(__dirname, 'src/**/*.vue')
                ]
            })
        ],
        optimization: {
            chunkIds: isDev ? 'named' : 'deterministic',
            moduleIds: 'deterministic',
            minimize: !isDev
        },
        externals: {
            vue: 'Vue',
            'vue-router': 'VueRouter',
            vuex: 'Vuex'
        },
        resolve: {
            extensions: ['.js', '.vue', '.json', '.ts', '.scss', '.css'],
            fallback: { path: false },
            alias: {
                '@': path.resolve('src'),
                '@repository': path.resolve(__dirname, 'core/devops-repository/src'),
                '@locale': path.resolve(__dirname, 'locale')
            }
        },
        devServer: {
            static: path.join(__dirname, envDist),
            allowedHosts: 'all',
            historyApiFallback: true,
            client: {
                webSocketURL: `auto://127.0.0.1:${port}/ws`
            },
            hot: isDev,
            port
        }
    }
}
