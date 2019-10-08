import dm5 from 'dm5'
import SHA256 from './lib/sha256'

const ENCODED_PASSWORD_PREFIX = '-SHA256-'

const state = {
  username: undefined,      // the logged in user (string); falsish if no user is logged in
  authMethods: ['Basic'],   // names of installed auth methods (array of string)
  visible: false            // Login dialog visibility
}

const actions = {

  login ({dispatch}, {credentials, authMethod}) {
    return dm5.restClient.login(credentials, authMethod).then(() => {
      const username = credentials.username
      console.log('登录', username)
      setUsername(username)
      dm5.permCache.clear()
      dispatch('loggedIn')
      return true
    }).catch(error => {
      console.log('登录失败', error)
      return false
    })
  },

  logout ({dispatch}) {
    console.log('退出', state.username)
    // Note: once logout request is sent we must succeed synchronously. Plugins may perform further
    // requests in their "loggedOut" handler which may rely on up-to-date login/logout state.
    dm5.restClient.logout().then(() => {
      setUsername()
      dm5.permCache.clear()
      dispatch('loggedOut')
    })
  },

  openLoginDialog () {
    state.visible = true
  },

  closeLoginDialog () {
    state.visible = false
  },

  createUserAccount (_, {username, password}) {
    return dm5.restClient.createUserAccount(username, encodePassword(password))
  }
}

// init state

dm5.restClient.getUsername().then(username => {
  state.username = username
})
dm5.restClient.getAuthorizationMethods().then(authMethods => {
  console.log('[DMX] 已安装的身份验证方法', authMethods)
  state.authMethods = state.authMethods.concat(authMethods)
})

// helper

function setUsername (username) {
  state.username = username
}

function encodePassword (password) {
    return ENCODED_PASSWORD_PREFIX + SHA256(password)
}

//

export default {
  state,
  actions
}
