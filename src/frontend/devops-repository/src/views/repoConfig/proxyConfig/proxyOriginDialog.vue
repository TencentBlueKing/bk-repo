<template>
    <canway-dialog
        v-model="show"
        width="600"
        height-num="463"
        :title="editProxyData.type === 'add' ? $t('addProxy') : $t('editProxy')"
        @cancel="$emit('cancel')"
        @confirm="confirmProxyData">
        <label class="ml20 mr20 mb10 form-label">{{ $t('baseInfo') }}</label>
        <bk-form class="ml20 mr20" ref="proxyOrigin" :label-width="85" :model="editProxyData" :rules="rules">
            <bk-form-item :label="$t('type')" property="proxyType">
                <bk-radio-group v-model="editProxyData.proxyType">
                    <bk-radio value="publicProxy">{{ $t('publicProxy') }}</bk-radio>
                    <bk-radio class="ml20" value="privateProxy">{{ $t('privateProxy') }}</bk-radio>
                </bk-radio-group>
            </bk-form-item>
            <bk-form-item :label="$t('name')" :required="true" property="name" error-display-type="normal">
                <bk-input v-model.trim="editProxyData.name" maxlength="32" show-word-limit></bk-input>
            </bk-form-item>
            <bk-form-item :label="$t('address')" :required="true" property="url" error-display-type="normal">
                <bk-input v-model.trim="editProxyData.url"></bk-input>
            </bk-form-item>
        </bk-form>
        <label class="ml20 mr20 mt20 mb10 form-label">{{$t('credentialInformation')}}</label>
        <bk-form class="ml20 mr20" :label-width="85">
            <bk-form-item :label="$t('account')" property="username">
                <bk-input v-model.trim="editProxyData.username"></bk-input>
            </bk-form-item>
            <bk-form-item :label="$t('password')" property="password">
                <bk-input type="password" v-model.trim="editProxyData.password"></bk-input>
            </bk-form-item>
        </bk-form>
    </canway-dialog>
</template>
<script>
    export default {
        name: 'proxyOriginDialog',
        props: {
            show: Boolean,
            proxyData: Object
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
                rules: {
                    name: [
                        {
                            required: true,
                            message: this.$t('proxyNameRule'),
                            trigger: 'blur'
                        }
                    ],
                    url: [
                        {
                            required: true,
                            message: this.$t('proxyUrlRule'),
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
                }
            }
        },
        methods: {
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
