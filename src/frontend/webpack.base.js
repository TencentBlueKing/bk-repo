const path = require('path')
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
        ...(isDev ? { devtool: 'source-map' } : {}),
        entry,
        output: {
            publicPath,
            chunkFilename: !isDev ? '[name].[chunkhash].js' : '[name].js',
            filename: !isDev ? '[name].[contenthash].min.js' : '[name].js',
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
                    test: /\.css$/,
                    use: [MiniCssExtractPlugin.loader, {
                        loader: 'css-loader',
                        options: {
                            url: {
                                filter: url => !url.startsWith('/')
                            }
                        }
                    }]
                },
                {
                    test: /\.scss$/,
                    use: [MiniCssExtractPlugin.loader, {
                        loader: 'css-loader',
                        options: {
                            url: {
                                filter: url => !url.startsWith('/')
                            }
                        }
                    }, 'sass-loader']
                },
                {
                    test: /\.(js|vue)$/,
                    loader: 'eslint-loader',
                    enforce: 'pre',
                    include: [path.resolve(__dirname, 'devops-repository/src'), path.resolve('src')],
                    exclude: [/node_modules/],
                    options: {
                        fix: true,
                        formatter: require('eslint-friendly-formatter')
                    }
                },
                {
                    test: /\.svg$/,
                    loader: 'svg-sprite-loader',
                    include: [
                        path.resolve(__dirname, 'devops-repository/src/images'),
                        path.resolve('src/images')
                    ]
                }
            ]
        },
        plugins: [
            new VueLoaderPlugin(),
            new MiniCssExtractPlugin({
                filename: !isDev ? '[name].[contenthash].css' : '[name].css',
                chunkFilename: '[id].css',
                ignoreOrder: true
            }),
            new CopyWebpackPlugin({
                patterns: [{ from: path.join(__dirname, 'locale', dist), to: buildDist }],
                options: { concurrency: 100 }
            })
        ],
        optimization: {
            chunkIds: isDev ? 'named' : 'deterministic',
            moduleIds: 'deterministic',
            minimize: !isDev
        },
        resolve: {
            extensions: ['.js', '.vue', '.json', '.ts', '.scss', '.css'],
            fallback: { path: false },
            alias: {
                '@': path.resolve('src'),
                '@repository': path.resolve(__dirname, 'devops-repository/src'),
                '@locale': path.resolve(__dirname, 'locale'),
                'vue$': 'vue/dist/vue.esm.js'
            }
        },
        devServer: {
            static: path.join(__dirname, envDist),
            allowedHosts: 'all',
            historyApiFallback: true,
            client: {
                webSocketURL: 'auto://127.0.0.1:' + port + '/ws'
            },
            hot: isDev,
            port
        }
    }
}
