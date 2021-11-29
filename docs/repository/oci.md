### 操作指引

---

1、新建项目和仓库    [test/oci-local]

2、登录到注册中心 (手动输入密码)
```shell script
$ helm registry login -u admin localhost:25907
Password:
Login succeeded
```

3、从注册中心注销
```shell script
$ helm registry logout localhost:25907
Logout succeeded
```

4、helm创建包
```shell script
# 创建helm包
$ helm create helloworld
```

5、helm保存chart目录到本地缓存
```shell script
# 前面项目名称和仓库名称固定，后面目录结构可自定义
$ helm chart save hello-world/ localhost:25907/test/oci-local/myrepo/hello-world:0.1.5


ref:     localhost:25907/test/oci-local/myrepo/hello-world:0.1.5
digest:  a0bd8e81436795c21e44e049401049c5489bbdfb7f4e1ed7d1804bd371a9a325
size:    3.5 KiB
name:    hello-world
version: 0.1.0
0.1.5: saved
```

6、push 推送chart到远程
```shell script
$ helm chart push localhost:25907/test/oci-local/myrepo/hello-world:0.1.5


The push refers to repository [localhost:25907/test/oci-local/myrepo/hello-world]
ref:     localhost:25907/test/oci-local/myrepo/hello-world:0.1.5
digest:  8b39e8dc5dd170a966ec34fccad793945a82b5a3cbdd6d007e420ffc2bcb30ea
size:    3.5 KiB
name:    hello-world
version: 0.1.0
0.1.5: pushed to remote (1 layer, 3.5 KiB total)
```

7、pull 从远程拉取chart
```shell script
$ helm chart pull localhost:25907/test/oci-local/myrepo/hello-world:0.1.5


0.1.5: Pulling from localhost:25907/test/oci-local/myrepo/hello-world
ref:     localhost:25907/test/oci-local/myrepo/hello-world:0.1.5
digest:  8b39e8dc5dd170a966ec34fccad793945a82b5a3cbdd6d007e420ffc2bcb30ea
size:    3.5 KiB
name:    hello-world
version: 0.1.0
Status: Downloaded newer chart for localhost:25907/test/oci-local/myrepo/hello-world:0.1.5
```

8、列举出所有的chart
```shell script
$ helm chart list


REF                                                    	NAME       	VERSION	DIGEST 	SIZE   	CREATED
localhost:25907/test/oci-local/myrepo/hello-world:0.1.5	hello-world	0.1.0  	8b39e8d	3.5 KiB	6 days
localhost:5000/myrepo/hello-world:0.1.5                	hello-world	0.1.0  	8b39e8d	3.5 KiB	3 weeks
```

9、导出chart到目录
```shell script
$ helm chart export localhost:25907/test/oci-local/myrepo/hello-world:0.1.5


ref:     localhost:25907/test/oci-local/myrepo/hello-world:0.1.5
digest:  8b39e8dc5dd170a966ec34fccad793945a82b5a3cbdd6d007e420ffc2bcb30ea
size:    3.5 KiB
name:    hello-world
version: 0.1.0
Exported chart to hello-world/
```
