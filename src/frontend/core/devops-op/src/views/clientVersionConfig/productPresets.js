/** 展示名 -> 入库 productId（后端会转小写） */
export const PRODUCT_PRESETS = [
  { key: 'bk-artifact', label: 'BKArtifacts', productId: 'bk_artifacts_ui' },
  { key: 'bk-driver', label: 'BKDrive', productId: 'bkdrive' }
]

export const PLATFORM_PRESETS = [
  { key: 'windows', label: 'Windows', value: 'windows' },
  { key: 'darwin', label: 'macOS', value: 'darwin' }
]

export const ARCH_PRESETS = [
  { label: 'amd64', value: 'amd64' },
  { label: 'arm64', value: 'arm64' },
  { label: 'x64', value: 'x64' }
]

export function productLabel(productId) {
  const id = (productId || '').trim()
  const hit = PRODUCT_PRESETS.find(p => p.productId === id)
  return hit ? hit.label : id
}

export function platformLabel(platform) {
  const v = (platform || '').trim().toLowerCase()
  const hit = PLATFORM_PRESETS.find(p => p.value === v)
  return hit ? hit.label : platform
}
