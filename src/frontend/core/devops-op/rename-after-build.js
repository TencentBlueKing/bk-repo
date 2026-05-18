const fs = require('fs')
const path = require('path')

const distDir = path.resolve(__dirname, '../../frontend/ui')
const oldFile = path.join(distDir, 'frontend_admin_index.html')
const newFile = path.join(distDir, 'frontend#admin#index.html')

if (fs.existsSync(oldFile)) {
  fs.renameSync(oldFile, newFile)
  console.log(`Renamed ${oldFile} -> ${newFile}`)
} else {
  console.error(`File ${oldFile} not found!`)
}
