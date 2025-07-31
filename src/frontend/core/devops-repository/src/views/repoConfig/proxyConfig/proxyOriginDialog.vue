<template>
    <canway-dialog
        v-model="show"
        width="600"
        height-num="463"
        @cancel="cancel"
        :title="editProxyData.type === 'add' ? $t('addProxy') : $t('editProxy')"
    >
        <bk-form class="ml20 mr20" :label-width="100" :model="editProxyData" ref="proxyFrom" :rules="rules">
            <bk-form-item :label="$t('proxyName')" :required="true" property="name" error-display-type="normal">
                <bk-input v-model.trim="editProxyData.name" maxlength="32" show-word-limit :placeholder="$t('proxyNameRule')"></bk-input>
            </bk-form-item>
            <bk-form-item :label="$t('proxyAddress')" :required="true" property="url" error-display-type="normal">
                <bk-input v-model.trim="editProxyData.url" :placeholder="$t('proxyUrlRule')"></bk-input>
            </bk-form-item>
            <bk-form-item :label="$t('publicProxy')" property="proxyType">
                <bk-radio-group v-model="editProxyData.proxyType">
                    <bk-radio value="publicProxy">{{ $t('Yes') }}</bk-radio>
                    <bk-radio class="ml20" value="privateProxy">{{ $t('No') }}</bk-radio>
                </bk-radio-group>
            </bk-form-item>
            <bk-form-item v-if="editProxyData.proxyType === 'privateProxy'" :label="$t('username')" property="username">
                <bk-input v-model.trim="editProxyData.username"></bk-input>
            </bk-form-item>
            <bk-form-item v-if="editProxyData.proxyType === 'privateProxy'" :label="$t('password')" property="password">
                <bk-input type="password" v-model.trim="editProxyData.password"></bk-input>
            </bk-form-item>
            <bk-form-item v-if="repoType === 'helm'" property="connection">
                <div class="flex-center">
                    <bk-link theme="primary" style="margin-right: auto">{{ $t('connectionTest') }}</bk-link>
                    <Icon v-if="loading" name="loading" size="14" class="svg-loading" />
                    <Icon style="margin-right: 300px" v-if="condition && !loading && connected" name="right" size="14" />
                    <Icon style="margin-right: 300px" v-if="condition && !loading && !connected" name="wrong" size="14" />
                </div>
            </bk-form-item>
        </bk-form>
        <template #footer>
            <bk-button @click="cancel">{{ $t('cancel') }}</bk-button>
            <bk-button :disabled="!connected || !condition" class="ml10" theme="primary" @click="confirmProxyData">{{ $t('confirm') }}</bk-button>
        </template>
    </canway-dialog>
</template>
<script>
    import { mapActions } from 'vuex'
    import { debounce } from 'lodash'

    export default {
        name: 'ProxyOriginDialog',
        props: {
            show: Boolean,
            proxyData: Object,
            nameList: {
                type: Array,
                default: []
            },
            repoType: {
                type: String,
                default: 'npm'
            }
        },
        data () {
            return {
                editProxyData: {
                    proxyType: 'publicProxy', // 公有 or 私有
                    type: '', // 添加 or 编辑
                    name: '',
                    url: '',
                    username: '',
                    password: ''
                },
                condition: false,
                loading: false,
                connected: false,
                debouncedTestConnection: null,
                rules: {
                    name: [
                        {
                            required: true,
                            message: this.$t('proxyNameRule'),
                            trigger: 'blur'
                        },
                        {
                            validator: this.checkName,
                            message: this.$t('sameProxyExist'),
                            trigger: 'blur'
                        },
                        {
                            validator: this.checkValid,
                            trigger: 'blur'
                        }
                    ],
                    url: [
                        {
                            required: true,
                            message: this.$t('proxyUrlRule'),
                            trigger: 'blur'
                        },
                        {
                            validator: this.checkValid,
                            trigger: 'blur'
                        }
                    ],
                    username: [
                        {
                            validator: this.checkValid,
                            trigger: 'blur'
                        }
                    ],
                    password: [
                        {
                            validator: this.checkValid,
                            trigger: 'blur'
                        }
                    ]
                }
            }
        },
        watch: {
            proxyData (data) {
                if (data.type === 'add') {
                    this.editProxyData = {
                        proxyType: 'publicProxy',
                        type: 'add',
                        name: '',
                        url: '',
                        username: '',
                        password: ''
                    }
                } else {
                    this.editProxyData = {
                        ...this.editProxyData,
                        ...data
                    }
                    this.editProxyData.proxyType = data.public ? 'publicProxy' : 'privateProxy'
                    this.checkValid()
                }
            },
            show (val) {
                if (val) {
                    if (this.repoType !== 'helm') {
                        this.condition = true
                        this.connected = true
                    }
                }
            },
            'editProxyData.proxyType' () {
                this.checkValid()
            }
        },
        created () {
            this.debouncedTestConnection = debounce(this.testConnection, 300)
        },
        methods: {
            ...mapActions(['checkProxy', 'getRSAKey']),
            async confirmProxyData () {
                await this.$refs.proxyFrom.validate()
                this.$emit('confirm', { name: this.proxyData.name, data: this.editProxyData })
            },
            checkValid () {
                if (this.repoType !== 'helm') {
                    return true
                }
                if (this.editProxyData.proxyType === 'publicProxy' && this.editProxyData.name.trim().length > 0 && this.editProxyData.url.trim().length > 0) {
                    this.condition = true
                    this.debouncedTestConnection()
                    return true
                } else if (this.editProxyData.proxyType === 'privateProxy'
                    && this.editProxyData.name.trim().length > 0
                    && this.editProxyData.url.trim().length > 0
                    && this.editProxyData.username.trim().length > 0
                    && this.editProxyData.password.trim().length > 0
                ) {
                    this.condition = true
                    this.debouncedTestConnection()
                    return true
                } else {
                    this.condition = false
                    this.loading = false
                    return true
                }
            },
            cancel () {
                this.$refs.proxyFrom.clearError()
                this.condition = false
                this.connected = false
                this.$emit('cancel')
            },
            async testConnection () {
                this.loading = true
                const encrypt = new window.JSEncrypt()
                const rsaKey = await this.getRSAKey()
                encrypt.setPublicKey(rsaKey)
                const body = {
                    url: this.editProxyData.url,
                    userName: this.editProxyData.proxyType !== 'privateProxy' ? null : this.editProxyData.username,
                    password: this.editProxyData.proxyType !== 'privateProxy' ? null : encrypt.encrypt(this.editProxyData.password),
                    type: this.repoType
                }
                this.checkProxy({ body: body }).then(res => {
                    if (res === true) {
                        this.connected = true
                    } else {
                        this.connected = false
                    }
                }).catch(() => {
                    this.connected = false
                }).finally(
                    this.loading = false
                )
            },
            checkName () {
                if (this.editProxyData.type === 'add' && this.nameList.indexOf(this.editProxyData.name) > -1) {
                    return false
                }
                if (this.editProxyData.type === 'edit' && this.nameList.indexOf(this.editProxyData.name) > -1
                    && this.nameList.indexOf(this.editProxyData.name) !== this.nameList.indexOf(this.proxyData.name)) {
                    return false
                }
                return true
            }
        }
    }
</script>
<style lang="scss" scoped>
.form-label {
    display: block;
    font-weight: bold;
}
.svg-loading {
    margin-right: 300px;
    animation: rotate-loading 1s linear infinite;
}
@keyframes rotate-loading {
    0% {
        transform: rotateZ(0);
    }
    100% {
        transform: rotateZ(360deg);
    }
}
</style>
