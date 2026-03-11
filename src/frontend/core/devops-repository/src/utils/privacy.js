const phones = {
    'ar-DZ': /^(\+?213|0)(5|6|7)\d{8}$/,
    'ar-SY': /^(!?(\+?963)|0)?9\d{8}$/,
    'ar-SA': /^(!?(\+?966)|0)?5\d{8}$/,
    'en-US': /^(\+?1)?[2-9]\d{2}[2-9](?!11)\d{6}$/,
    'cs-CZ': /^(\+?420)? ?[1-9][0-9]{2} ?[0-9]{3} ?[0-9]{3}$/,
    'de-DE': /^(\+?49[ \.\-])?([\(]{1}[0-9]{1,6}[\)])?([0-9 \.\-\/]{3,20})((x|ext|extension)[ ]?[0-9]{1,4})?$/,
    'da-DK': /^(\+?45)?(\d{8})$/,
    'el-GR': /^(\+?30)?(69\d{8})$/,
    'en-AU': /^(\+?61|0)4\d{8}$/,
    'en-GB': /^(\+?44|0)7\d{9}$/,
    'en-HK': /^(\+?852\-?)?[569]\d{3}\-?\d{4}$/,
    'en-IN': /^(\+?91|0)?[789]\d{9}$/,
    'en-NZ': /^(\+?64|0)2\d{7,9}$/,
    'en-ZA': /^(\+?27|0)\d{9}$/,
    'en-ZM': /^(\+?26)?09[567]\d{7}$/,
    'es-ES': /^(\+?34)?(6\d{1}|7[1234])\d{7}$/,
    'fi-FI': /^(\+?358|0)\s?(4(0|1|2|4|5)?|50)\s?(\d\s?){4,8}\d$/,
    'fr-FR': /^(\+?33|0)[67]\d{8}$/,
    'he-IL': /^(\+972|0)([23489]|5[0248]|77)[1-9]\d{6}/,
    'hu-HU': /^(\+?36)(20|30|70)\d{7}$/,
    'it-IT': /^(\+?39)?\s?3\d{2} ?\d{6,7}$/,
    'ja-JP': /^(\+?81|0)\d{1,4}[ \-]?\d{1,4}[ \-]?\d{4}$/,
    'ms-MY': /^(\+?6?01){1}(([145]{1}(\-|\s)?\d{7,8})|([236789]{1}(\s|\-)?\d{7}))$/,
    'nb-NO': /^(\+?47)?[49]\d{7}$/,
    'nl-BE': /^(\+?32|0)4?\d{8}$/,
    'nn-NO': /^(\+?47)?[49]\d{7}$/,
    'pl-PL': /^(\+?48)? ?[5-8]\d ?\d{3} ?\d{2} ?\d{2}$/,
    'pt-BR': /^(\+?55|0)\-?[1-9]{2}\-?[2-9]{1}\d{3,4}\-?\d{4}$/,
    'pt-PT': /^(\+?351)?9[1236]\d{7}$/,
    'ru-RU': /^(\+?7|8)?9\d{9}$/,
    'sr-RS': /^(\+3816|06)[- \d]{5,9}$/,
    'tr-TR': /^(\+?90|0)?5\d{9}$/,
    'vi-VN': /^(\+?84|0)?((1(2([0-9])|6([2-9])|88|99))|(9((?!5)[0-9])))([0-9]{7})$/,
    'zh-CN': /^(\+?0?86\-?)?1[345789]\d{9}$/,
    'zh-TW': /^(\+?886\-?|0)?9\d{8}$/,
    'zh-HK': /^([69]\d{7})$/
}

export function checkPhone (phoneNum) {
    for (const key in phones) {
        const regex = phones[key]
        if (regex.test(phoneNum)) {
            return true
        }
    }
    return false
}

// 隐私转换手机号码(海外暂时没有)
export function transformPhone (phoneNum) {
    // 判断异常手机号
    if (phoneNum.length < 7) return phoneNum
    let str = ''
    // 判定为国内手机
    if (phones['zh-CN'].test(phoneNum)) {
        for (let i = 0; i < 4; i++) {
            str = str + '*'
        }
        if (phoneNum.startsWith('+86') || phoneNum.startsWith('086')) {
            return phoneNum.substr(0, 6) + str + phoneNum.substr(10)
        } else if (phoneNum.startsWith('86')) {
            return phoneNum.substr(0, 5) + str + phoneNum.substr(9)
        } else if (phoneNum.startsWith('+086')) {
            return phoneNum.substr(0, 7) + str + phoneNum.substr(11)
        } else {
            return phoneNum.substr(0, 3) + str + phoneNum.substr(7)
        }
    } else if (phones['zh-TW'].test(phoneNum)) {
        for (let i = 0; i < 4; i++) {
            str = str + '*'
        }
        if (phoneNum.startsWith('886')) {
            return phoneNum.substr(0, 5) + str + phoneNum.substr(9)
        } else if (phoneNum.startsWith('0')) {
            return phoneNum.substr(0, 3) + str + phoneNum.substr(7)
        } else if (phoneNum.startsWith('+886')) {
            return phoneNum.substr(0, 6) + str + phoneNum.substr(10)
        } else {
            return phoneNum.substr(0, 2) + str + phoneNum.substr(6)
        }
    } else if (phones['zh-HK'].test(phoneNum)) {
        for (let i = 0; i < 4; i++) {
            str = str + '*'
        }
        return phoneNum.substr(0, 2) + str + phoneNum.substr(6)
    } else {
        // 判断国内固定电话(区号3位或4位，电话长度7位或8位，前2位小于03，则区号3位)
        // 不加区号固定电话
        if (!phoneNum.startsWith('0')) {
            for (let i = 0; i < phoneNum.length - 4; i++) {
                str = str + '*'
            }
            return str + phoneNum.substr(phoneNum.length - 4)
        } else {
            // 加区号固定电话
            if (parseInt(phoneNum.substr(0, 2)) > 3) {
                for (let i = 0; i < phoneNum.length - 8; i++) {
                    str = str + '*'
                }
                return phoneNum.substr(0, 4) + str + phoneNum.substr(phoneNum.length - 8)
            } else {
                for (let i = 0; i < phoneNum.length - 7; i++) {
                    str = str + '*'
                }
                return phoneNum.substr(0, 3) + str + phoneNum.substr(phoneNum.length - 7)
            }
        }
    }
}

// 隐私转换邮箱
export function transformEmail (email) {
    const num = email.lastIndexOf('@')
    if (num < 3) return email
    let str = ''
    for (let i = 0; i < num - 2; i++) {
        str = str + '*'
    }
    return email.substr(0, 2) + str + email.substr(num)
}
