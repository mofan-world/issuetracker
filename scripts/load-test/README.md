# 压测数据生成

该工具以 PostgreSQL 集合运算批量生成：

- 压测项目 1 个：`LOAD_TEST`
- 测试人员 1000 个：`load_tester_0001` 至 `load_tester_1000`
- 开发人员 1000 个：`load_developer_0001` 至 `load_developer_1000`
- 管理员 50 个：`load_admin_001` 至 `load_admin_050`
- 每个测试人员 1000 张问题单，共 1,000,000 张
- 每人 900 张已关闭、100 张处理中，比例为 9:1

以上用户都会加入 `LOAD_TEST` 项目，生成的问题单也全部归属于该项目。

默认登录密码为 `LoadTest@123`。可通过参数修改：

```powershell
.\scripts\load-test\generate-load-test-data.ps1 -Password "YourPassword123"
```

脚本通过项目 Maven 缓存中的 PostgreSQL JDBC 驱动直连 `127.0.0.1:5432`，可通过参数修改数据库连接。脚本可重复执行，相同账号和问题单会按固定编号更新，不会重复增加。执行前应确保 PostgreSQL 已启动，并为数据库预留足够磁盘空间。百万数据会显著增加 PostgreSQL 数据文件和索引体积。

这些数据直接写入 PostgreSQL，不会自动同步到 Elasticsearch。进行该数据集的关键词检索压测时，应先执行独立的 Elasticsearch 批量重建索引；普通分页、状态过滤和用户范围查询不受影响。
