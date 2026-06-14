编程人员不应该“干等 Codex 写完”，更应该把这段时间当成**项目经理 + 测试员 + 审查员**的时间。Codex 本质上是能读代码、改代码、运行代码的 coding agent，也可以在 IDE 里和你并行工作或把长任务交给云端跑，所以人的价值要放在判断、验收和控制方向上。([OpenAI 开发者][1])

你可以按这个节奏来：

## 1. Codex 正在写代码时，你先整理“验收标准”

比如你现在这个问题单系统，别只让它“修一下附件上传”，你应该写清楚完成标准：

```text
完成标准：
1. 创建问题单时，未上传附件也能成功提交。
2. 上传 png/jpg/pdf/doc/docx/xlsx 文件能成功保存。
3. 单个文件超过 20MB 时，前端和后端都给出明确提示。
4. Docker 环境重启后，附件仍然存在。
5. 后端日志不能再出现 ATTACHMENT_SAVE_FAILED。
6. 执行 docker compose up -d --build 后前后端都能正常访问。
```

OpenAI 官方也建议给 Codex 任务时包含 Goal、Context、Constraints、Done when，这样结果更稳定、范围更可控。([OpenAI 开发者][2])

## 2. 趁它写代码，你去准备测试数据

你可以提前准备这些文件：

```text
1. 一个 1MB 内的 png 图片
2. 一个 PDF
3. 一个 Word 文档
4. 一个 Excel 表格
5. 一个超过 20MB 的文件
6. 一个中文文件名，比如：联想截图_20260523114029.png
```

等 Codex 写完，你不用再临时找文件，直接测试。

## 3. 同时盯日志和接口，不要只看页面

你现在项目很多问题都是后端日志暴露出来的，比如 Redis、Nacos、附件保存失败。Codex 修改时，你可以另开一个终端一直跑：

```powershell
docker compose logs -f --tail=300 issuetracker-end
```

前端也可以开 DevTools 的 Network，看接口是不是 200、400、500。这样 Codex 改完后，你能马上判断：**它是真的修好了，还是只是前端不报错了。**

## 4. Codex 写完后，你重点审查 diff，而不是全信

你重点看这几类地方：

```text
1. 有没有乱删功能
2. 有没有硬编码绝对路径
3. 有没有把异常直接吞掉
4. 有没有把安全校验去掉
5. 有没有新增没必要的依赖
6. 有没有只修本地、不适配 Docker
7. 有没有数据库表字段和实体不一致
```

比如你这个项目，尤其要看：

```text
application.yml
Dockerfile
docker-compose.yml
AttachmentService
TicketController
GlobalExceptionHandler
RedisConfig
AdminService
```

## 5. 把大问题拆成小任务，让 Codex 并行或逐个解决

不要一次说：

```text
帮我修完整个系统。
```

更好的方式是：

```text
任务一：只修附件上传失败，不要改动登录、权限、页面样式。
任务二：只修 Redis 缓存反序列化问题。
任务三：只修 Docker 环境下目录权限和 volume 挂载。
任务四：检查创建问题单流程，从前端提交到数据库保存是否完整。
```

复杂或模糊任务可以先让 Codex 进入规划模式，先调查、提问、出方案，再动代码；官方最佳实践也建议复杂任务先 Plan，再实现。([OpenAI 开发者][2])

## 6. 给项目建立一份 `AGENTS.md`

这个很重要。你可以在项目根目录放一个 `AGENTS.md`，告诉 Codex 以后都按你的规则做。Codex 会在开始工作前读取 `AGENTS.md`，用它作为项目级指导。([OpenAI 开发者][3])

你可以写：

```md
# AGENTS.md

## 项目规则
- 这是一个问题单跟踪系统，前端是 Vue + TypeScript + Element Plus，后端是 Spring Boot，使用 Docker Compose 部署。
- 不允许随意删除已有功能。
- 修改前先说明影响范围。
- 修 bug 时优先给出根因，再改代码。
- 所有 Docker 环境路径必须适配容器用户 appuser。
- 上传附件必须支持 Docker volume 持久化。
- 修改前端后必须执行 npm run build。
- 修改后端后必须执行 ./gradlew clean bootJar 或 docker compose build。
- 不要新增生产依赖，除非说明原因。
- 不要关闭安全校验来绕过问题。
```

## 7. 利用等待时间做“人工验收表”

比如这次附件问题，你可以建一个简单表：

```text
测试项                     结果
不带附件提交问题单          待测
带 png 附件提交             待测
带中文文件名附件提交        待测
带 PDF 附件提交             待测
超过 20MB 文件上传          待测
刷新问题详情页查看附件      待测
重启容器后附件是否还在      待测
后端日志是否无 500          待测
```

Codex 写完，你就按这个表一项一项点，不容易漏。

## 8. 人要负责最终架构判断

Codex 很适合修具体 bug，但你要判断这些问题背后的架构选择：

```text
Redis 缓存是否真的需要？
Nacos 本地单体项目是否应该关闭？
附件是存在本地 volume，还是未来要接对象存储？
日志目录是否统一？
Dockerfile 是否要固定 Node/JDK/nginx 版本？
数据库是否需要初始化脚本？
```

对于你现在这个系统，我的建议是：**本地部署阶段先追求稳定，不要引入太多中间件。** Nacos 可以关，Redis 缓存可以先关，附件先用 Docker volume 持久化，等系统跑稳再逐步加复杂功能。

一句话总结：
**Codex 编码时，人不要等待代码，而要准备验收标准、测试数据、观察日志、审查 diff、拆分下一步任务。这样 Codex 才是生产力，不是黑盒。**

[1]: https://developers.openai.com/codex/ide "IDE extension – Codex | OpenAI Developers"
[2]: https://developers.openai.com/codex/learn/best-practices "Best practices – Codex | OpenAI Developers"
[3]: https://developers.openai.com/codex/guides/agents-md "Custom instructions with AGENTS.md – Codex | OpenAI Developers"
