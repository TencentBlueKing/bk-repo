const path = require('path')
const SpriteLoaderPlugin = require('svg-sprite-loader/plugin')

module.exports = () => {
    return {
        entry: './sprite',
        output: {
            filename: 'sprite_chunk.js',
            path: path.resolve(__dirname)
        },

        module: {
            rules: [
                {
                    test: /\.svg$/,
                    use: [
                        {
                            loader: 'svg-sprite-loader',
                            options: {
                                extract: true,
                                publicPath: 'static/'
                            }
                        }
                    ]
                }
            ]
        },
      
        plugins: [
            new SpriteLoaderPlugin()
        ]
    }
}
