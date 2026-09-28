# 人才招聘

人才招聘是 SetHub 旗下的独立招聘管理分支。

本项目把人才、岗位、应聘、面试、Offer、入职、离职、返聘、项目合作和跟进任务放进同一份长期人物档案。界面采用低饱和的企业风格，人才详情用按年份分组的时间轴保留实际发生时间、结果和原因。

## 已实现

- 工作台与人才库：服务端分页，每页最多 10 人；姓名、岗位、联系方式、来源和状态筛选。
- 人才建档：姓名/昵称、至少一种联系方式、男女按钮、来源、状态、结果和双边意愿按钮；期望薪资按最低值 — 最高值（K/月）录入。
- 岗位管理：岗位薪资范围、负责人、招聘状态、版本变更原因和历史版本；人才及应聘记录使用同一岗位 ID，应聘保存当时的岗位版本快照。
- 永久人物档案：画像、联系方式、应聘、面试、Offer 与薪资事实、附件、成长经历、任职、项目合作、任务。
- 统一时间轴：可补录历史日期；按年份展示沟通、应聘、面试、Offer、任职与合作，结束、拒绝和未入职必须保留原因。
- 招聘工作空间：主动寻访、Opportunity 转 Application、应聘看板、流程推进、面试评价、Offer 多版本、员工生命周期、项目合作和任务。
- 数据迁移：CSV 字段映射、逐行校验、重复提示、导入预演、失败重试和批次幂等；人才摘要可按当前筛选导出。
- 数据治理：组织隔离、JWT 登录、首次管理员设置、审计日志、并发版本检查、附件类型/大小/路径校验。
- 数据分析：人才阶段、来源、结果和岗位分布。

PRD 中仍标记为“待确认”的双人合并审批、跨主体授权规则、正式评价模型、Offer 法律审批、SetHub 外部接口和 AI 自动操作没有擅自固化。人才招聘为这些能力保留了稳定 Person ID、组织 ID、版本号、审计和独立业务记录。

## 本地运行

需要 Java 17、Maven、Node.js 和 npm。

后端使用本地持久化 H2。推荐使用彼此隔离的 user / agent 环境：

```powershell
cd back
mvn spring-boot:run -Dspring-boot.run.profiles=user
```

用户前端：

```powershell
cd front
npm install
npm run dev:user
```

Agent 测试环境分别使用后端 8081、前端 5174 和 `sethub-agent.mv.db`，启动命令为 `mvn spring-boot:run -Dspring-boot.run.profiles=agent` 与 `npm run dev:agent`。两套环境可同时运行。

打开终端显示的前端地址。当前本地演示数据的管理员账号是 `admin`，密码是 `123456`，数据库中仅保存 BCrypt 哈希；全新数据库第一次启动时仍由登录页创建管理员。生产环境默认连接 MySQL，配置见 `back/src/main/resources/application.yml`。生产部署应通过 `SETHUB_JWT_SECRET` 提供至少 32 字节的密钥，并把 CORS 来源设置为实际前端域名。

## 验证

```powershell
cd front
npm test
npm run build

cd ..\back
mvn clean test
```

后端集成测试覆盖首次设置与登录、每页最多 10 人、薪资校验、并发覆盖保护、2021 年历史时间轴、岗位快照、Offer 版本和多组织隔离。前端测试覆盖 CSV 解析、字段映射、校验、防公式注入和导出。

## 目录

- `front/`：Vue、Vue Router、Element Plus 和 Vite 前端。
- `back/`：Spring Boot、JDBC、JWT、MySQL/H2 后端。
- `SetHub_人才全生命周期管理系统_PRD.md`：产品范围、状态与待确认决策。
