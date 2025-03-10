<template>
    <div class="package-card-container flex-align-center">
        <div class="mr20 package-card-main flex-column">
            <div class="flex-align-center">
                <span class="card-name text-overflow" :title="cardData.name">{{ cardData.name }}</span>
            </div>
            <span class="package-card-description text-overflow" :title="cardData.address">{{ $t('address') + ' : ' + cardData.url }}</span>
            <span v-if="cardData.username" class="package-card-description text-overflow" :title="cardData.username">{{ $t('username') + ' : ' + cardData.username }}</span>
        </div>
        <bk-divider v-if="repoType === 'helm'" direction="vertical" />
        <div v-if="repoType === 'helm'" class="mr20 package-card-main flex-column">
            <div class="flex-align-center">
                <span class="package-card-description text-overflow">{{formatRecordDate()}}</span>
                <Icon v-if="status !== undefined && status" style="margin-left: 10px" name="right" size="14" />
                <Icon v-if="status !== undefined && !status" style="margin-left: 10px" name="wrong" size="14" />
            </div>
        </div>
        <div class="card-operation flex-center">
            <bk-link v-if="repoType === 'helm'" theme="primary" @click="syncRepo" style="margin-right: auto">{{ $t('syncRepo') }}</bk-link>
            <operation-list
                :list="[
                    { label: $t('edit'), clickEvent: () => updateCard() },
                    { label: $t('delete'), clickEvent: () => deleteCard() }
                ]"></operation-list>
        </div>
    </div>
</template>
<script>
    import OperationList from '@repository/components/OperationList'
    import { mapActions } from 'vuex'
    import { convertFileSize, formatDate } from '@repository/utils'
    export default {
        name: 'PackageCard',
        components: { OperationList },
        props: {
            cardData: {
                type: Object,
                default: {}
            },
            syncStatus: {
                type: Object,
                default: {}
            }
        },
        data () {
            return {
                last: '',
                status: undefined
            }
        },
        computed: {
            projectId () {
                return this.$route.params.projectId
            },
            repoName () {
                return this.$route.query.repoName
            },
            repoType () {
                return this.$route.params.repoType
            }
        },
        watch: {
            syncStatus (val) {
                const has = val.find(proxy => proxy.name === this.cardData.name)
                if (has) {
                    this.last = has.lastSyncDate
                    this.status = has.lastSyncStatus
                }
            }
        },
        methods: {
            ...mapActions(['syncHelmRepo']),
            convertFileSize,
            formatDate,
            deleteCard () {
                this.$emit('delete-card')
            },
            updateCard () {
                this.$emit('update-card', this.cardData)
            },
            syncRepo () {
                this.syncHelmRepo({ projectId: this.projectId, repoName: this.repoName }).then(() => {
                    this.$emit('refresh-sync-record')
                })
            },
            formatRecordDate () {
                let param = null
                if (this.last) {
                    param = this.formatDate(this.last)
                }
                return this.$t('latestSyncData', { 0: param })
            }
        }
    }
</script>
<style lang="scss" scoped>
.package-card-container {
    flex: 1;
    height: 100px;
    padding: 16px 20px;
    border-radius: 5px;
    background-color: var(--bgLighterColor);
    cursor: pointer;
    .card-icon {
        padding: 15px;
        background-color: white;
        border: 1px solid var(--borderColor);
        border-radius: 4px;
    }
    .package-card-main {
        flex: 1;
        height: 100%;
        justify-content: space-around;
        overflow: hidden;
        margin-left: 20px;
        .card-name {
            font-size: 14px;
            max-width: 500px;
            font-weight: bold;
            &:hover {
                color: var(--primaryColor);
            }
        }
        .package-card-description {
            font-size: 12px;
            color: var(--fontSubsidiaryColor);
        }
        .package-card-data {
            display: grid;
            grid-template: auto / repeat(1, 1fr);
            .card-metadata {
                padding: 0 5px;
                overflow: hidden;
                text-overflow: ellipsis;
                white-space: nowrap;
                &:after {
                    content: attr(title);
                    color: var(--fontSubsidiaryColor);
                }
            }
        }
        .package-card-data-more {
            display: grid;
            grid-template: auto / repeat(5, 1fr);
            .card-metadata {
                padding: 0 5px;
                overflow: hidden;
                text-overflow: ellipsis;
                white-space: nowrap;
                &:after {
                    content: attr(title);
                    color: var(--fontSubsidiaryColor);
                }
            }
        }
    }
    .card-operation {
        flex-basis: 60px;
        visibility: visible;
    }
    &:hover {
        background-color: var(--bgHoverLighterColor);
        // box-shadow: 0px 0px 6px 0px var(--primaryBoxShadowColor);
        .repo-tag {
            border-color: var(--primaryBoxShadowColor);
        }
    }
}
</style>
