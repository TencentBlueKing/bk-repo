#!/bin/bash

username=$BK_REPO_USERNAME
password_md5=$(echo -n $BK_REPO_PASSWORD | md5sum | cut -d ' ' -f1)
sed -i "s/\"admin\"/\"$username\"/g" init-data.js
sed -i "s/5f4dcc3b5aa765d61d8327deb882cf99/$password_md5/g" init-data.js
sed -i "s/5f4dcc3b5aa765d61d8327deb882cf99/$password_md5/g" init-data-tenant.js
access_key=$BK_REPO_ACCESSKEY
secret_key=$BK_REPO_SECRETKEY
multi_tenant=$BK_REPO_ENABLE_MULTI_TENANT_MODE

if [ "$access_key" != "" ] && [ "$secret_key" != "" ]; then
    sed -i "s/18b61c9c-901b-4ea3-89c3-1f74be944b66/$access_key/g" init-data.js
    sed -i "s/18b61c9c-901b-4ea3-89c3-1f74be944b66/$access_key/g" init-data-tenant.js
    sed -i "s/Us8ZGDXPqk86cwMukYABQqCZLAkM3K/$secret_key/g" init-data.js
    sed -i "s/Us8ZGDXPqk86cwMukYABQqCZLAkM3K/$secret_key/g" init-data-tenant.js
fi


bcs_access_key=$BK_REPO_BCS_ACCESSKEY
bcs_secret_key=$BK_REPO_BCS_SECRETKEY

if [ "$bcs_access_key" != "" ] && [ "$bcs_secret_key" != "" ]; then
     sed -i "s/609f9939e6944c5c8a842d88acf85edc/$bcs_access_key/g" init-data-ext.js
     sed -i "s/e041dd34cd89466648a9b196150f75/$bcs_secret_key/g" init-data-ext.js
     if [ "$multi_tenant" != "true" ]; then
       mongo --ipv6 $BK_REPO_MONGODB_URI init-data.js && mongo --ipv6 $BK_REPO_MONGODB_URI init-data-ext.js
     else
       mongo --ipv6 $BK_REPO_MONGODB_URI init-data-tenant.js && mongo --ipv6 $BK_REPO_MONGODB_URI init-data-ext.js
     fi
else
  if [ "$multi_tenant" != "true" ]; then
      mongo --ipv6 $BK_REPO_MONGODB_URI init-data.js
  else
      mongo --ipv6 $BK_REPO_MONGODB_URI init-data-tenant.js
  fi
fi

