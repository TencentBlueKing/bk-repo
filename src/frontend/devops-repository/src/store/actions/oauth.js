import Vue from 'vue'

const prefix = 'auth/api/oauth'

export default {
    getAuthorizeInfo (_, { clientId, state, scope, nonce }) {
        console.log(clientId, state, scope, nonce)
        return Vue.prototype.$ajax.get(
            `${prefix}/authorize`,
            {
                params: {
                    client_id: clientId,
                    state,
                    scope,
                    nonce
                }
            }
        )
    }
}
