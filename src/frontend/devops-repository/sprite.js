// 打包svg文件
const requireAll = requireContext => requireContext.keys().map(requireContext)
const req = require.context('./src/images', false, /\.svg$/)
requireAll(req)
