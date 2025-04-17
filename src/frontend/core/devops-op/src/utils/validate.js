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
export const UUID_REGEX = /^[a-z0-9]{8}-[a-z0-9]{4}-[a-z0-9]{4}-[a-z0-9]{4}-[a-z0-9]{12}$/
export const IMAGE_REGEX = /^([.\w-]+\/?)+:[-.\w]+$/
export const URL_REGEX = /https?:\/\/[-\w@:%.+~#=]{1,256}/
export const IP_REGEX = /((2(5[0-5]|[0-4]\d))|[0-1]?\d{1,2})(\.((2(5[0-5]|[0-4]\d))|[0-1]?\d{1,2})){3}/g
