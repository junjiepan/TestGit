> 该程序用于通过资产编号，自动生成**资产**固化脚本文件，以及该**资产**包含的全部**实体**的固化文件

1. 下载 `zip`压缩包文件，解压缩
1. 根据需求修改 `application.yml` 文件内容

```yaml
# 自定义查询参数与文件输出路径参数
conf:
 assetId: xxx
 outputFilePath:  { PATH } \out\      # 注：仅需修改 {} 中 PATH 值

# 数据库连接信息
database:
 url: jdbc:mysql://xxx:3306/public_udmp
 username: xxx
 password:  xxx

# 资产 sql 语句参数配置
asset:
 queries:
  - select * from tableName where ASSETS_ID in (:assetsId);
  - ...

# 实体 sql 语句参数配置（ sql 语句需存在 `MDI_ENTITY_REPO` 这张表）
entity:
 queries:
  - select t1.*  from xxx t1 inner join xxx t2 on t1.PHY_ID=t2.PHY_ID where t2.ASSETS_ID in (:assetsId);
  - ...
```

2. 启动运行` run.bat `文件（注意：需要在`jdk1.8`环境运行）
3. 运行日志可以查看同目录下的 `out.log`文件
4. 查看配置的`PATH`路径下的 `out`文件夹下的内容，包含该资产编号对应的资产固化脚本文件与该资产包含的全部实体的固化文件，输出文件的结构如下所示：

```vue
{PATH} / out / {资产名}
               	  ├── asset
               	  |     └── { 资产名 }.sql
               	  └── entity
                        ├── {实体英文名1}.sql				
                        ├── {实体英文名2}.sql	
                        └── ...
```

**注意**：如果在该资产下，存在实体的英文名包含" `*` " 符号，由于`window`环境下文件名无法包含" `*` "符号，因此生成的sql文件名统一使用" `#` "符号代替" `*` " 符号，例如：

```txt
"FTP_FJGH_SOUR_*.sql"  ==>  "FTP_FJGH_SOUR_#.sql"
```

