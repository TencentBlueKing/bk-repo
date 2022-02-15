/**
 * Created by PanJiaChen on 16/11/18.
 */

/**
 * @param {string} path
 * @returns {Boolean}
 */
export function isExternal(path) {
  return /^(https?:|mailto:|tel:)/.test(path)
}

export const FILE_PATH_REGEX = /^(((\/?[\w-]+)+\/?)|([A-Za-z]:(\\[\w-~]+)+)\\?)$/
