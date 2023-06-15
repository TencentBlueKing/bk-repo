package com.tencent.bkrepo.udt.lib;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NewLibraryLoaderUDT implements LibraryLoader {
    private final static Logger log = LoggerFactory
            .getLogger(NewLibraryLoaderUDT.class);
    private final String name = "bkrepo-udt";
    private final String libName = System.mapLibraryName(name);

    @Override
    public void load(String location) throws Exception {
        /*
         * 1. 从java.library.path中加载
         * 2. 从jar中加载
         * */
        try {
            System.loadLibrary(name);
        } catch (UnsatisfiedLinkError error) {
            try {
                String libPath = "/lib/";
                String sourcePath = libPath + libName;
                final String targetPath = location + sourcePath;
                ResourceManagerUDT.extractResource(sourcePath, targetPath);
                ResourceManagerUDT.systemLoad(targetPath);
            } catch (UnsatisfiedLinkError e2) {
                throw new IllegalStateException("Fatal: library load failed.");
            }
        }
    }
}
