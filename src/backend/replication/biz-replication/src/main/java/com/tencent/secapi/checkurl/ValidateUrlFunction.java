package com.tencent.secapi.checkurl;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@FunctionalInterface
public interface ValidateUrlFunction {

    Map<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();

    boolean validateUrl(List<String> rules, String domain);

    ValidateUrlFunction IS_DOMAIN = (domains, equalDomain) -> domains.contains(equalDomain);

    ValidateUrlFunction IS_SUB_DOMAIN = (domains, subdomain) -> {
        for (String domain : domains) {
            if (!domain.trim().isEmpty()) {
                if (subdomain.equals(domain) || subdomain.endsWith("." + domain)) {
                    return true;
                }
            }
        }
        return false;
    };

    ValidateUrlFunction IS_SUB_DOMAIN_REGEX = (domainRegexs, domain) -> {
        for (String regex : domainRegexs) {
            Pattern pattern = PATTERN_CACHE.computeIfAbsent(regex, Pattern::compile);
            Matcher matcher = pattern.matcher(domain);
            if (matcher.find()) {
                return true;
            }
        }
        return false;
    };
}
