import { login, userInfo, userInfoById } from '@/api/user'
import { getToken, setToken, removeToken, getBkUid } from '@/utils/auth'
import { resetRouter } from '@/router'
import { MODE_CONFIG, MODE_CONFIG_STAND_ALONE } from '@/utils/login'

export const ROLE_ADMIN = 'ADMIN'
export const ROLE_USER = 'USER'

const getDefaultState = () => {
  return {
    token: getToken(),
    name: '',
    userId: '',
    admin: false,
    avatar: '',
    roles: []
  }
}

const state = getDefaultState()

const mutations = {
  RESET_STATE: (state) => {
    Object.assign(state, getDefaultState())
  },
  SET_TOKEN: (state, token) => {
    state.token = token
  },
  SET_ADMIN: (state, admin) => {
    state.admin = admin
  },
  SET_USER_ID: (state, userId) => {
    state.userId = userId
  },
  SET_NAME: (state, name) => {
    state.name = name
  },
  SET_AVATAR: (state, avatar) => {
    state.avatar = avatar
  },
  SET_ROLES: (state, roles) => {
    state.roles = roles
  }
}

const actions = {
  // user login
  login({ commit }, userInfo) {
    const { username, password } = userInfo
    const formData = new FormData()
    formData.append('uid', username)
    formData.append('token', password)
    return new Promise((resolve, reject) => {
      const uid = getBkUid()
      if (uid && MODE_CONFIG !== MODE_CONFIG_STAND_ALONE) {
        resolve()
      } else {
        login(formData).then(res => {
          if (!res.data) {
            reject(new Error('username or password is incorrect'))
            return
          }
          const token = getToken()
          commit('SET_TOKEN', token)
          setToken(token)
          resolve()
        }).catch(error => {
          reject(error)
        })
      }
    })
  },

  // get user info
  getInfo({ commit }) {
    const uid = getBkUid()
    if (uid && MODE_CONFIG !== MODE_CONFIG_STAND_ALONE) {
      return new Promise((resolve, reject) => {
        userInfoById(uid).then(response => {
          const { data } = response
          const { name, userId, admin } = data
          const roles = admin ? [ROLE_ADMIN] : [ROLE_USER]
          // roles must be a non-empty array
          if (!roles || roles.length <= 0) {
            reject('getInfo: roles must be a non-null array!')
          }

          const avatar = ''
          commit('SET_USER_ID', userId)
          commit('SET_NAME', name)
          commit('SET_ADMIN', admin)
          commit('SET_ROLES', roles)
          commit('SET_AVATAR', avatar)
          resolve({ name, userId, roles })
        })
      })
    }
    return new Promise((resolve, reject) => {
      userInfo().then(res => {
        return userInfoById(res.data.userId)
      }).then(response => {
        const { data } = response
        const { name, userId, admin } = data
        const roles = admin ? [ROLE_ADMIN] : [ROLE_USER]
        // roles must be a non-empty array
        if (!roles || roles.length <= 0) {
          reject('getInfo: roles must be a non-null array!')
        }

        const avatar = ''
        commit('SET_USER_ID', userId)
        commit('SET_NAME', name)
        commit('SET_ADMIN', admin)
        commit('SET_ROLES', roles)
        commit('SET_AVATAR', avatar)
        resolve({ name, userId, roles })
      }).catch(error => {
        reject(error)
      })
    })
  },

  // user logout
  logout({ commit }) {
    return new Promise((resolve) => {
      removeToken() // must remove  token  first
      resetRouter()
      commit('RESET_STATE')
      resolve()
    })
  },

  // remove token
  resetToken({ commit }) {
    return new Promise(resolve => {
      removeToken() // must remove  token  first
      commit('RESET_STATE')
      resolve()
    })
  }
}

export default {
  namespaced: true,
  state,
  mutations,
  actions
}

