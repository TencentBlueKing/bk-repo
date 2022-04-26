#!/bin/bash

username=$BK_REPO_USERNAME
password_md5=$(echo -n $BK_REPO_PASSWORD | md5sum | cut -d ' ' -f1)
sed -i "s/\"admin\"/\"$username\"/g" init-data.js
sed -i "s/5f4dcc3b5aa765d61d8327deb882cf99/$password_md5/g" init-data.js

mongo $BK_REPO_MONGODB_URI init-data.js

bcsToken=$BK_REPO_BCS_TOKEN
bcsAccessKey=$BK_REPO_BCS_ACCESSKEY
bcsSecretKey=$BK_REPO_BCS_SECRETKEY

if [ -n "$bcsToken" ]; then
    sed -i "s/609f9939e6944c5c8a842d88acf85edc/$bcsAccessKey/g" init-data-ext.js
    sed -i "s/e041dd34cd89466648a9b196150f75/$bcsSecretKey/g" init-data-ext.js
    mongo $BK_REPO_MONGODB_URI init-data-ext.js
fi

