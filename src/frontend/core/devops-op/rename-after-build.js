const fs = require('fs')
const path = require('path')

const oldDistDir = path.resolve(__dirname, '../../frontend/admin')
const distDir = path.resolve(__dirname, '../../frontend/ui')

const oldFile = path.join(oldDistDir, 'index.html')
const newFile = path.join(distDir, 'frontend#admin#index.html')

if (fs.existsSync(oldFile)) {
  fs.renameSync(oldFile, newFile)
  console.log(`Renamed ${oldFile} -> ${newFile}`)
} else {
  console.error(`File ${oldFile} not found!`)
}
