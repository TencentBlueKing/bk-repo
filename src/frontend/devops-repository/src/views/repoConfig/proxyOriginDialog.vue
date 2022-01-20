<template>
    <canway-dialog
        v-model="show"
        width="600"
        :height-num="editProxyData.proxyType === 'privateProxy' ? 463 : 328"
        :title="editProxyData.type === 'add' ? $t('addProxy') : $t('editProxy')"
        @cancel="$emit('cancel')"
        @confirm="confirmProxyData">
        <label class="ml20 mr20 mb10 form-label">{{ $t('baseInfo') }}</label>
        <bk-form class="ml20 mr20" ref="proxyOrigin" :label-width="85" :model="editProxyData" :rules="rules">
            <bk-form-item :label="$t('type')" property="proxyType">
                <bk-radio-group v-model="editProxyData.proxyType" @change="proxyTypeChange">
                    <bk-radio :disabled="editProxyData.type === 'edit'" value="publicProxy">{{ $t('publicProxy') }}</bk-radio>
                    <bk-radio :disabled="editProxyData.type === 'edit'" class="ml20" value="privateProxy">{{ $t('privateProxy') }}</bk-radio>
                </bk-radio-group>
            </bk-form-item>
            <template v-if="editProxyData.proxyType === 'privateProxy'">
                <bk-form-item :label="$t('name')" :required="true" property="name" error-display-type="normal">
                    <bk-input v-model.trim="editProxyData.name" maxlength="32" show-word-limit></bk-input>
                </bk-form-item>
            </template>
            <template v-else>
                <bk-form-item :label="$t('name')" :required="true" property="channelId" error-display-type="normal">
                    <bk-select v-model="editProxyData.channelId" @change="changeChannelId" :clear="false">
                        <bk-option
                            v-for="option in publicProxy"
                            :key="option.channelId"
                            :id="option.channelId"
                            :name="option.name">
                        </bk-option>
                    </bk-select>
                </bk-form-item>
            </template>
            <bk-form-item :label="$t('address')" :required="true" property="url" error-display-type="normal">
                <bk-input :disabled="editProxyData.proxyType === 'publicProxy'" v-model.trim="editProxyData.url"></bk-input>
            </bk-form-item>
        </bk-form>
        <template v-if="editProxyData.proxyType === 'privateProxy'">
            <label class="ml20 mr20 mt20 mb10 form-label">凭证信息</label>
            <bk-form class="ml20 mr20" :label-width="85">
                <bk-form-item :label="$t('account')" property="username">
                    <bk-input v-model.trim="editProxyData.username"></bk-input>
                </bk-form-item>
                <bk-form-item :label="$t('password')" property="password">
                    <bk-input type="password" v-model.trim="editProxyData.password"></bk-input>
                </bk-form-item>
            </bk-form>
        </template>
    </canway-dialog>
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
                    ]
                }
            }
        },
        watch: {
            proxyData () {
                this.proxyTypeChange(this.proxyData.type === 'add' ? 'publicProxy' : 'privateProxy')
                this.editProxyData = {
                    ...this.editProxyData,
                    ...this.proxyData
                }
            }
        },
        methods: {
            proxyTypeChange (name = 'publicProxy') {
                this.editProxyData = {
                    ...this.editProxyData,
                    proxyType: name,
                    channelId: '',
                    name: '',
                    url: '',
                    username: '',
                    password: ''
                }
                this.$refs.proxyOrigin && this.$refs.proxyOrigin.clearError()
            },
            changeChannelId (channelId) {
                const selectedPublicProxy = this.publicProxy.find(v => v.channelId === channelId) || {}
                this.editProxyData.url = selectedPublicProxy.url
            },
            async confirmProxyData () {
                await this.$refs.proxyOrigin.validate()
                this.$emit('confirm', { name: this.proxyData.name, data: this.editProxyData })
            }
        }
    }
</script>
<style lang="scss" scoped>
.form-label {
    display: block;
    font-weight: bold;
}
</style>
