<template>
    <article class="npm-guide-container">
        <section class="mb20" v-for="section in article" :key="section.title">
            <h2 class="mb10 section-header">{{ section.title }}</h2>
            <div class="section-main">
                <div class="sub-section flex-column" v-for="block in section.main" :key="block.subTitle">
                    <span class="mb10">{{ block.subTitle }}</span>
                    <code-area :code-list="block.codeList"></code-area>
                </div>
            </div>
        </section>
    </article>
</template>
<script>
    import CodeArea from '@/components/CodeArea'
    export default {
        name: 'npmGuide',
        components: { CodeArea },
        computed: {
            projectId () {
                return this.$route.params.projectId
            },
            repoName () {
                return this.$route.query.name
            },
            article () {
                return [
                    {
                        title: '设置凭证',
                        main: [
                            {
                                subTitle: '1、在项目根目录下（与package.json同级），添加文件.npmrc，拷贝如下信息',
                                codeList: [
                                    `registry=${location.origin}/${this.projectId}/${this.repoName}/`,
                                    `always-auth=true`,
                                    `//${location.origin}/${this.projectId}/${this.repoName}/:username=npmtest1-1597372299973`,
                                    `//${location.origin}/${this.projectId}/${this.repoName}/:_password=M2Q1NTY5MTQzOTZjYWFlMWRmNWQxM2M1MGRiMmI5MmYyNGI0NWM4ZA==`,
                                    `//${location.origin}/${this.projectId}/${this.repoName}/:email=XXXXXX@XX.XXX`
                                ]
                            },
                            {
                                subTitle: '2、设置 npm registry为当前制品库仓库，进入命令行根据用户凭证登录',
                                codeList: [
                                    `npm registry ${location.origin}/${this.projectId}/${this.repoName}/`,
                                    `npm login`
                                ]
                            }
                        ]
                    },
                    {
                        title: '推送',
                        main: [
                            {
                                codeList: [`npm publish`]
                            }
                        ]
                    },
                    {
                        title: '下载',
                        main: [
                            {
                                subTitle: '1、在设置仓库地址之后就可以使用如下命令去拉取包',
                                codeList: [`npm install <PACKAGE_NAME>`]
                            },
                            {
                                subTitle: '2、也可以通过指定registry的方式去拉取包，如下命令',
                                codeList: [`npm install <PACKAGE_NAME> --registry ${location.origin}/${this.projectId}/${this.repoName}/`]
                            }
                        ]
                    }
                ]
            }
        }
    }
</script>
<style lang="scss" scoped>
@import '@/scss/conf';
.npm-guide-container {
    padding: 20px;
    .section-header {
        padding: 10px 20px;
        background-color: #f1f2f3;
    }
    .section-main {
        padding: 20px;
        border: 2px dashed $borderWeightColor;
        border-radius: 5px;
        .sub-section {
            & + .sub-section {
                margin-top: 20px;
            }
        }
    }
}
</style>
