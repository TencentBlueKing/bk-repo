# Jfrog -> BKRepo 数据迁移脚本

> 脚本兼容python2.7+和python3+

```
usage: migration.py [-h] [-o] -p PROJECT [-e {dev,prod}] [-f FILE]

-p: 项目名称
-o: 是否强制覆盖
-e: 环境，默认为prod环境
-f: 配置文件，可以配置指定路径或者指定文件进行迁移

迁移过程日志会记录在logs/{project}.log下
```

## 迁移项目
```
nohup python -u migration.py -p bkrepo 2>&1 &
```

## 迁移指定路径/文件
```
nohup python -u migration.py -p bkrepo -f config/bkrepo.ini 2>&1 &

# test.ini 内容
# 指定节点文件
[node]
list=1.txt
     2.txt
     a/b/c/3.txt
     # ...
# 指定路径
[path]
list=a
     b
     c
     # ...
```
