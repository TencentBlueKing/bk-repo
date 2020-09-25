const customeRules = {
    string: {
        validate: function (value, args) {
            return /^[\w,\d,\-_\(\)]+$/i.test(value)
        }
    },
    unique: {
        validate: function (value, args) {
            let repeatNum = 0
            for (let i = 0; i < args.length; i++) {
                if (repeatNum > 2) return false
                if (args[i] === value) {
                    repeatNum++
                }
            }
            return repeatNum <= 1
        }
    },
    pullmode: {
        validate: function (value, args) {
            return typeof value === 'object' && value.type !== '' && value.value !== ''
        }
    },
    excludeComma: {
        validate: function (value) {
            return !/\,/gm.test(value)
        }
    },
    varRule: {
        validate: function (value, args) {
            return /^[a-z_][a-z_\d]*$/gi.test(value)
        }
    },
    excludeEmptyCapital: {
        validate: function (value, args) {
            return /^(([a-z0-9_\/]+)|(\$\{[A-Z_]+\}))+$/g.test(value)
        }
    },
    mutualGroup: {
        validate: function (value, args) {
            return /^[A-Za-z0-9]+$/g.test(value) || /^\${(.*)}$/g.test(value)
        }
    },
    nonVarRule: {
        validate: function (value, args) {
            return !/^\${(.*)}$/g.test(value)
        }
    },
    notStartWithBKCI: {
        validate: function (value, args) {
            return !/^BK_CI/.test(value)
        }
    },
    paramsRule: {
        validate: function (value, args) {
            return /^[a-zA-Z0-9_]+$/g.test(value)
        }
    },
    sleepTimer: {
        validate: function (value, args) {
            return /^\d{1,8}$/g.test(value) || /^(((\d{3}[1-9]|\d{2}[1-9]\d{1}|\d{1}[1-9]\d{2}|[1-9]\d{3}))|(29-02-((\d{2})(0[48]|[2468][048]|[13579][26])|((0[48]|[2468][048]|[3579][26])00))))-((0[13578]|1[02])-((0[1-9]|[12]\d|3[01]))|((0[469]|11)-(0?[1-9]|[12]\d|30))|(0[2])-(0[1-9]|[1]\d|2[0-8])) ((0|[1])\d|2[0-3]):(0|[1-5])\d:(0|[1-5])\d$/g.test(value)
        }
    },
    notify: {
        validate: function (value, args) {
            return value instanceof Array && value.length > 0
        }
    },
    type: {
        validate: function (value, args) {
            return true
        }
    },
    args: {
        validate: function (value, args) {
            return true
        }
    },
    unsupport: {
        validate: function (value, args) {
            return false
        }
    }
}

function ExtendsCustomRules (_extends) {
    if (typeof _extends !== 'function') {
        console.warn('VeeValidate.Validator.extend must be a function')
        return
    }
    for (const key in customeRules) {
        if (customeRules.hasOwnProperty(key)) {
            _extends(key, customeRules[key])
        }
    }
}

export default ExtendsCustomRules
