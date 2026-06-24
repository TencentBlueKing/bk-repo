package com.tencent.secapi.checkurl;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class Config {

    private List<String> schemes = Arrays.asList("http", "https");

    private List<String> rules = new LinkedList<>();

    private String mode = "subhost";

    public List<String> getSchemes() {
        return schemes;
    }

    public void setSchemes(List<String> schemes) {
        this.schemes = schemes;
    }

    public List<String> getRules() {
        return rules;
    }

    public void setRules(List<String> rules) {
        this.rules = rules;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }
}
