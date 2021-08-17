<template>
    <bk-dialog
        v-model="show"
        width="600"
        :title="editProxyData.type === 'add' ? $t('addProxy') : $t('editProxy')"
        :mask-close="false"
        :close-icon="false"
    >
        <bk-tab
            :active.sync="editProxyData.proxyType"
            @tab-change="proxyTabChange"
            type="unborder-card">
            <bk-tab-panel v-if="editProxyData.type === 'add'" name="publicProxy" :label="$t('publicProxy')">
                <bk-form ref="publicProxy" :label-width="100" :model="editProxyData" :rules="rules">
                    <bk-form-item :label="$t('name')" :required="true" property="channelId">
                        <bk-select v-model="editProxyData.channelId" :clear="false">
                            <bk-option
                                v-for="option in publicProxy"
                                :key="option.channelId"
                                :id="option.channelId"
                                :name="option.name">
                            </bk-option>
                        </bk-select>
                    </bk-form-item>
                    <bk-form-item :label="$t('address')">
                        <span>{{ selectedPublicProxy.url || '' }}</span>
                    </bk-form-item>
                </bk-form>
            </bk-tab-panel>
            <bk-tab-panel name="privateProxy" :label="$t('privateProxy')">
                <bk-form ref="privateProxy" :label-width="100" :model="editProxyData" :rules="rules">
                    <bk-form-item :label="$t('privateProxy') + $t('name')" :required="true" property="name">
                        <bk-input v-model.trim="editProxyData.name"></bk-input>
                    </bk-form-item>
                    <bk-form-item :label="$t('privateProxy') + $t('address')" :required="true" property="url">
                        <bk-input v-model.trim="editProxyData.url"></bk-input>
                    </bk-form-item>
                    <bk-form-item :label="$t('ticket')" property="ticket">
                        <bk-checkbox v-model.trim="editProxyData.ticket"></bk-checkbox>
                    </bk-form-item>
                    <bk-form-item v-if="editProxyData.ticket" :label="$t('account')" :required="true" property="username">
                        <bk-input v-model.trim="editProxyData.username"></bk-input>
                    </bk-form-item>
                    <bk-form-item v-if="editProxyData.ticket" :label="$t('password')" :required="true" property="password">
                        <bk-input type="password" v-model.trim="editProxyData.password"></bk-input>
                    </bk-form-item>
                    <!-- <bk-form-item>
                        <bk-button text theme="primary" @click="testPrivateProxy">{{$t('test') + $t('privateProxy')}}</bk-button>
                    </bk-form-item> -->
                </bk-form>
            </bk-tab-panel>
        </bk-tab>
        <div slot="footer">
            <bk-button theme="primary" @click="confirmProxyData">{{$t('submit')}}</bk-button>
            <bk-button @click="$emit('cancel')">{{$t('cancel')}}</bk-button>
        </div>
    </bk-dialog>
</template>
<script>
    export default {
        name: 'proxyOriginDialog',
        props: {
            show: Boolean,
            proxyData: Object,
            publicProxy: Array
        },
        data () {
            return {
                editProxyData: {
                    proxyType: 'publicProxy', // 公有 or 私有
                    type: '', // 添加 or 编辑
                    channelId: '',
                    name: '',
                    url: '',
                    ticket: false,
                    username: '',
                    password: ''
                },
                rules: {
                    channelId: [
                        {
                            required: true,
                            message: this.$t('pleaseSelect') + this.$t('publicProxy'),
                            trigger: 'change'
                        }
                    ],
                    name: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('privateProxy') + this.$t('name'),
                            trigger: 'blur'
                        }
                    ],
                    url: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('privateProxy') + this.$t('address'),
                            trigger: 'blur'
                        }
                    ],
                    username: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('account'),
                            trigger: 'blur'
                        }
                    ],
                    password: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('password'),
                            trigger: 'blur'
                        }
                    ]
                }
            }
        },
        computed: {
            selectedPublicProxy () {
                return this.publicProxy.find(v => v.channelId === this.editProxyData.channelId) || {}
            }
        },
        watch: {
            proxyData () {
                this.proxyTabChange(this.proxyData.type === 'add' ? 'publicProxy' : 'privateProxy')
                this.editProxyData = {
                    ...this.editProxyData,
                    ...this.proxyData
                }
            }
        },
        methods: {
            proxyTabChange (name = 'publicProxy') {
                this.editProxyData = {
                    ...this.editProxyData,
                    proxyType: name,
                    channelId: '',
                    name: '',
                    url: '',
                    ticket: false,
                    username: '',
                    password: ''
                }
                this.$refs.publicProxy && this.$refs.publicProxy.clearError()
                this.$refs.privateProxy && this.$refs.privateProxy.clearError()
            },
            async confirmProxyData () {
                if (this.editProxyData.proxyType === 'publicProxy') {
                    await this.$refs.publicProxy.validate()
                } else {
                    await this.$refs.privateProxy.validate()
                }
                this.$emit('confirm', { name: this.proxyData.name, data: this.editProxyData })
            }
        }
    }
</script>
<style lang="scss" scoped>

</style>
