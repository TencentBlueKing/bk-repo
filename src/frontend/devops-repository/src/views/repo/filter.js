export default {
    generic: [
        {
            id: 'name',
            name: window.repositoryVue.$i18n.t('name')
        },
        {
            id: 'stageTag',
            name: window.repositoryVue.$i18n.t('status'),
            children: [
                {
                    id: '@prerelease',
                    name: '@prerelease'
                },
                {
                    id: '@release',
                    name: '@release'
                },
                {
                    id: '@prerelease,@release',
                    name: '@prerelease && @release'
                }
            ]
        }
    ],
    docker: [
        {
            id: 'name',
            name: window.repositoryVue.$i18n.t('name')
        }
    ],
    npm: [
        {
            id: 'name',
            name: window.repositoryVue.$i18n.t('name')
        },
        {
            id: 'stageTag',
            name: window.repositoryVue.$i18n.t('status'),
            children: [
                {
                    id: '@prerelease',
                    name: '@prerelease'
                },
                {
                    id: '@release',
                    name: '@release'
                },
                {
                    id: '@prerelease,@release',
                    name: '@prerelease && @release'
                }
            ]
        }
    ],
    maven: [
        {
            id: 'name',
            name: window.repositoryVue.$i18n.t('name')
        },
        {
            id: 'stageTag',
            name: window.repositoryVue.$i18n.t('status'),
            children: [
                {
                    id: '@prerelease',
                    name: '@prerelease'
                },
                {
                    id: '@release',
                    name: '@release'
                },
                {
                    id: '@prerelease,@release',
                    name: '@prerelease && @release'
                }
            ]
        }
    ]
}
