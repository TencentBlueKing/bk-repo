package com.tencent.secapi.checkurl;

import static com.tencent.secapi.checkurl.ValidateUrlFunction.IS_DOMAIN;
import static com.tencent.secapi.checkurl.ValidateUrlFunction.IS_SUB_DOMAIN;
import static com.tencent.secapi.checkurl.ValidateUrlFunction.IS_SUB_DOMAIN_REGEX;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class CheckUrl {

    static final String DOMAIN_REGEX =
            "^(?=^.{3,255}$)[a-zA-Z0-9][-a-zA-Z0-9]{0,62}"
                    + "(\\.[a-zA-Z0-9][-a-zA-Z0-9]{0,62})+$";

    static final Pattern DOMAIN_PATTERN = Pattern.compile(DOMAIN_REGEX);

    private static final List<String> MODE_VALUES = Arrays.asList("equal", "subhost", "regex");

    private static final Map<String, ValidateUrlFunction> MODE_MATCH_METHOD_MAP;

    static {
        MODE_MATCH_METHOD_MAP = new HashMap<>();
        MODE_MATCH_METHOD_MAP.put("equal", IS_DOMAIN);
        MODE_MATCH_METHOD_MAP.put("subhost", IS_SUB_DOMAIN);
        MODE_MATCH_METHOD_MAP.put("regex", IS_SUB_DOMAIN_REGEX);
    }

    public static void checkUrl(String url, Config config)
            throws IllegalArgumentException, MalformedURLException {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("Url is null.");
        }

        if (config.getSchemes() == null || config.getSchemes().isEmpty()
                || config.getRules() == null || config.getRules().isEmpty()
                || config.getMode() == null || config.getMode().trim().isEmpty()
                || !MODE_VALUES.contains(config.getMode())) {
            throw new IllegalArgumentException("Config is illegal.");
        }

        if (url.indexOf('/') == 0) {
            return;
        }

        URL urlParsed;
        try {
            urlParsed = new URL(url);
        } catch (MalformedURLException e) {
            throw new MalformedURLException("URL can not be parsed.");
        }

        if (!isHostnameValid(urlParsed.getHost())) {
            throw new MalformedURLException("Hostname is not valid");
        }

        if (!config.getSchemes().contains(urlParsed.getProtocol())) {
            throw new MalformedURLException("Scheme is not valid.");
        }

        if (!MODE_MATCH_METHOD_MAP.containsKey(config.getMode())) {
            throw new IllegalArgumentException("Mode is not valid.");
        }
        if (!MODE_MATCH_METHOD_MAP.get(config.getMode()).validateUrl(
                config.getRules(), urlParsed.getHost())) {
            throw new MalformedURLException("URL does not meet the conditions.");
        }
    }

    private static boolean isHostnameValid(String hostname) {
        return DOMAIN_PATTERN.matcher(hostname).find();
    }
}
