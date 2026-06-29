function registerEvent (target, eventType, cb) {
    if (target.addEventListener) {
        target.addEventListener(eventType, cb)
        return {
            remove () {
                target.removeEventListener(eventType, cb)
            }
        }
    }
    target.attachEvent(eventType, cb)
    return {
        remove () {
            target.detachEvent(eventType, cb)
        }
    }
}

function createHiddenIframe (target, uri) {
    const iframe = document.createElement('iframe')
    iframe.src = uri
    iframe.id = 'hiddenIframe'
    iframe.style.display = 'none'
    target.appendChild(iframe)

    return iframe
}

function openUriWithHiddenFrame (uri, failCb, successCb) {
    const timeout = setTimeout(() => {
        failCb()
        handler.remove()
    }, 4500)

    let iframe = document.querySelector('#hiddenIframe')
    if (!iframe) {
        iframe = createHiddenIframe(document.body, 'about:blank')
    }

    const handler = registerEvent(window, 'blur', onBlur)

    function onBlur () {
        clearTimeout(timeout)
        handler.remove()
        successCb()
    }

    iframe.contentWindow.location.href = uri
}

function openUriWithTimeoutHack (uri, failCb, successCb) {
    const timeout = setTimeout(() => {
        failCb()
        handler.remove()
    }, 5000)

    // handle page running in an iframe (blur must be registered with top level window)
    let target = window
    while (target !== target.parent) {
        target = target.parent
    }

    const handler = registerEvent(target, 'blur', onBlur)

    function onBlur () {
        clearTimeout(timeout)
        handler.remove()
        successCb()
    }

    window.location = uri
}

function openUriUsingFirefox (uri, failCb, successCb) {
    let iframe = document.querySelector('#hiddenIframe')

    if (!iframe) {
        iframe = createHiddenIframe(document.body, 'about:blank')
    }

    try {
        iframe.contentWindow.location.href = uri
        successCb()
    } catch (e) {
        if (e.name === 'NS_ERROR_UNKNOWN_PROTOCOL') {
            failCb()
        }
    }
}

function openUriUsingIEInOlderWindows (uri, failCb, successCb) {
    if (getInternetExplorerVersion() === 10) {
        openUriUsingIE10InWindows7(uri, failCb, successCb)
    } else if (getInternetExplorerVersion() === 9 || getInternetExplorerVersion() === 11) {
        openUriWithHiddenFrame(uri, failCb, successCb)
    } else {
        openUriInNewWindowHack(uri, failCb, successCb)
    }
}

function openUriUsingIE10InWindows7 (uri, failCb, successCb) {
    const timeout = setTimeout(failCb, 1000)
    window.addEventListener('blur', () => {
        clearTimeout(timeout)
        successCb()
    })

    let iframe = document.querySelector('#hiddenIframe')
    if (!iframe) {
        iframe = createHiddenIframe(document.body, 'about:blank')
    }
    try {
        iframe.contentWindow.location.href = uri
    } catch (e) {
        failCb()
        clearTimeout(timeout)
    }
}

function openUriInNewWindowHack (uri, failCb, successCb) {
    const myWindow = window.open('', '', 'width=0,height=0')

    myWindow.document.write(`<iframe src='${uri}'></iframe>`)

    setTimeout(() => {
        try {
            myWindow.location.href
            myWindow.setTimeout('window.close()', 4500)
            successCb()
        } catch (e) {
            myWindow.close()
            failCb()
        }
    }, 4500)
}

function openUriWithMsLaunchUri (uri, failCb, successCb) {
    navigator.msLaunchUri(
        uri,
        successCb,
        failCb
    )
}

function checkBrowser () {
    const isOpera = !!window.opera || navigator.userAgent.indexOf(' OPR/') >= 0
    const ua = navigator.userAgent.toLowerCase()
    return {
        isOpera,
        isFirefox: typeof InstallTrigger !== 'undefined',
        isSafari: (~ua.indexOf('safari') && !~ua.indexOf('chrome')) || Object.prototype.toString.call(window.HTMLElement).indexOf('Constructor') > 0,
        isIOS: /iPad|iPhone|iPod/.test(navigator.userAgent) && !window.MSStream,
        isChrome: !!window.chrome && !isOpera,
        isIE: /* @cc_on!@*/ false || !!document.documentMode // At least IE6
    }
}

function getInternetExplorerVersion () {
    let rv = -1
    const ua = navigator.userAgent
    let re
    if (navigator.appName === 'Microsoft Internet Explorer') {
        re = new RegExp('MSIE ([0-9]{1,}[.0-9]{0,})')
        if (re.exec(ua) !== null) rv = parseFloat(RegExp.$1)
    } else if (navigator.appName === 'Netscape') {
        re = new RegExp('Trident/.*rv:([0-9]{1,}[.0-9]{0,})')
        if (re.exec(ua) !== null) {
            rv = parseFloat(RegExp.$1)
        }
    }
    return rv
}

export default function (uri, successCb, failCb, unsupportedCb) {
    function failCallback () {
        failCb && failCb()
    }

    function successCallback () {
        successCb && successCb()
    }

    if (navigator.msLaunchUri) { // for IE and Edge in Win 8 and Win 10
        openUriWithHiddenFrame(uri, failCallback, successCallback) // for IE11 IE10 and Edge in Win 10
    } else {
        const browser = checkBrowser()

        if (browser.isFirefox) {
            openUriUsingFirefox(uri, failCallback, successCallback)
        } else if (browser.isChrome || browser.isIOS) {
            openUriWithTimeoutHack(uri, failCallback, successCallback)
        } else if (browser.isIE) {
            openUriUsingIEInOlderWindows(uri, failCallback, successCallback)
        } else if (browser.isSafari) {
            openUriWithHiddenFrame(uri, failCallback, successCallback)
        } else {
            unsupportedCb()
            // not supported, implement please
        }
    }
}
