module.exports = {
    'extends': ['@commitlint/config-conventional'],
    'rules': {
        'type-enum': [2, 'always', ['feature', 'feat', 'bug', 'fix', 'bugfix', 'refactor', 'perf', 'test', 'docs', 'info', 'format', 'merge', 'depend', 'chore', 'del', 'upgrade']]
        // 'subject-valid': [2, 'always']
    },
    'plugins': [
        {
            'rules': {
                // 'subject-valid': function ({ subject }) {
                //     console.log('it is a subject', subject)
                //     return [
                //         /p1_\d+/i.test(subject),
                //         `commit-msg should include ({teamworkId})`
                //     ]
                // }
            }
        }
    ]
}
