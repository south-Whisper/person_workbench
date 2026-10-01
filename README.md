# 人才管理

人才管理是 SetHub 旗下的人才与招聘管理分支。

本项目把人才、岗位、应聘、面试、Offer、入职、离职、返聘、项目合作和跟进任务放进同一份长期人物档案。界面采用低饱和的企业风格，人才详情用按年份分组的时间轴保留实际发生时间、结果和原因。

## 已实现

- 工作台与人才库：服务端分页，每页最多 10 人；姓名、岗位、联系方式、来源和状态筛选。
- 人才建档：姓名/昵称、至少一种联系方式、男女按钮、来源、状态、结果和双边意愿按钮；期望薪资按最低值 — 最高值（K/月）录入。
- 岗位管理：岗位薪资范围、负责人、招聘状态、版本变更原因和历史版本；人才及应聘记录使用同一岗位 ID，应聘保存当时的岗位版本快照。
- 永久人物档案：画像、联系方式、应聘、面试、Offer 与薪资事实、附件、成长经历、任职、项目合作、任务。
- 统一时间轴：可补录历史日期；按横向阶段展示沟通、拒绝、面试和 Offer，结束、拒绝和淘汰必须保留原因。
- 招聘工作空间：主动寻访、Opportunity 转 Application、应聘看板、流程推进、面试评价、Offer 多版本、员工生命周期、项目合作和任务。
- 数据迁移：CSV 字段映射、逐行校验、重复提示、导入预演、失败重试和批次幂等；人才摘要可按当前筛选导出。
- 数据治理：组织隔离、JWT 登录、首次管理员设置、审计日志、并发版本检查、附件类型/大小/路径校验。
- 数据分析：人才阶段、来源、结果和岗位分布。

PRD 中仍标记为“待确认”的双人合并审批、跨主体授权规则、正式评价模型、Offer 法律审批、SetHub 外部接口和 AI 自动操作没有擅自固化。人才管理为这些能力保留了稳定 Person ID、组织 ID、版本号、审计和独立业务记录。

## 本地运行

需要 Java 17、Maven、Node.js、npm 和 MySQL 8。

后端本地开发使用 MySQL 8 的 `person_workbench` 数据库。全新电脑第一次运行按下面的固定顺序操作：

1. 启动 MySQL 8，打开 MySQL Workbench，并用安装 MySQL 时设置的管理员账号连接。
2. 在 MySQL Workbench 中打开 `back/tools/mysql-server-bootstrap.sql`，点击闪电按钮执行。这一步相当于先给系统配一把数据库钥匙：它会建立 `person_workbench` 数据库和只能管理这个数据库的 `sethub_app` 账号。
3. 把 `back/config/mysql-secret.example.yml` 复制一份，改名为 `mysql-secret.yml`。这个文件保存本机数据库账号和密码，已经被 Git 忽略，不会提交进代码仓库。
4. 在 PowerShell 中进入后端目录并启动：

```powershell
cd back
mvn "-Dspring-boot.run.profiles=mysql" spring-boot:run
```

第一次连接时，后端会自动建表并执行 `back/src/main/resources/db/initial-data.sql`。它会建立管理员账号、HR“昱宁”、“南通宣通文化投资”、5 个办公地点和 3 个基础招聘岗位。脚本不会创建假人才、假应聘、假面试或假 Offer；重复启动也不会重复插入。

如果以后手动删除了整个 `person_workbench` 数据库，只要 MySQL 服务和 `sethub_app` 账号还在，直接重新启动后端即可。连接地址中的 `createDatabaseIfNotExist=true` 意思是“数据库不存在就创建”，后端随后会重新建表和初始化基础资料。

前端：

```powershell
cd front
npm install
npm run dev
```

打开终端显示的前端地址。当前本地管理员账号是 `admin`，密码是 `123456`，数据库中仅保存 BCrypt 哈希。后端只使用 MySQL，不再包含其它数据库驱动或本地数据库文件。生产部署应通过环境变量提供数据库密码和至少 32 字节的 JWT 密钥，并把 CORS 来源设置为实际前端域名。

## 服务器运行

服务器使用 Docker Compose 同时运行 MySQL、后端和前端。完整的逐步说明见 [服务器部署说明.md](服务器部署说明.md)，服务器端可执行 `deploy-server.sh` 完成配置检查、构建和启动。

## 数据表用途

数据库里的“表”可以理解成不同用途的资料柜。当前结构要求沟通、面试、Offer 和入职各自使用独立的柜子，避免把不同业务混在一起；`record` 只负责记全局流水，不代替这些业务表。

### 正式使用的表

| 表名                 | 大白话用途                                                                   | 主要关联和边界                                                                                     |
| ------------------ | ----------------------------------------------------------------------- | ------------------------------------------------------------------------------------------- |
| `migration`        | 记录数据库已经执行过哪些结构升级，防止同一段升级重复执行。                                           | 只供系统启动和升级使用，不存人才业务数据。                                                                       |
| `user`             | 登录账号表，保存用户名、加密后的密码、账号角色和启用状态。                                           | HR 员工可通过 `employee_id` 与 `employee` 一对一关联；普通员工可以没有登录账号。                                     |
| `message`          | 消息表，保存当前 HR 收到的消息正文、类型、状态、接收时间、是否已读和已读时间。                               | 通过 `recipient_employee_id` 指定接收 HR。人才问卷消息只关联 `person`；面试安排消息只关联 `interview`；其它消息不关联这两张表。    |
| `employee`         | 员工主表，保存员工编号、姓名、部门、职务、员工角色、在职状态和入离职日期。                                   | 可关联 `person`、`application`、任职记录和岗位；角色为 HR 时可以关联一个 `user` 账号。                                |
| `company`          | 公司表，保存公司名称和是否启用。                                                        | 一个公司可以有多个办公地点和多个岗位。                                                                         |
| `company_location` | 公司办公地点表，保存城市、区县、街道和详细地址。                                                | 通过 `company_id` 归属某个 `company`。                                                             |
| `person`           | 人才主档案，保存姓名、联系方式、来源、当前画像、期望薪资和跟进状态等基础信息。附件、成长经历、任职经历和薪资经历也作为人才自身属性保存在这里。 | 一个人才可以有多次应聘、沟通、面试、Offer 和入职；个人资料不再拆到 `profile_record`。                                      |
| `position`         | 岗位表，同时保存当前岗位和岗位历史版本。岗位名称、公司、部门、薪资、介绍、要求等都在这里。                           | 当前岗位的 `parent_position_id` 为空；历史版本通过 `parent_position_id` 指向当前岗位，并用 `version_number` 标出版本号。 |
| `application`      | 应聘表，一行代表“某个人才应聘某个岗位的一次经历”。保存招聘阶段、岗位快照、负责人和双方意愿等。                        | 关联 `person`、`position` 和负责的 `employee`；沟通、面试、Offer、入职都应归到具体应聘记录。                            |
| `communication`    | 沟通表，每次联系单独一行，保存沟通时间、渠道、内容、结果、双方意愿和下一步。                                  | 关联 `person`、`application`、岗位和负责的 `employee`；不存面试、Offer 或入职内容。                               |
| `interview`        | 面试表，每一场面试单独一行，保存时间、轮次、方式、面试官、状态、评价、结论和不推荐原因。                            | 关联 `person` 和 `application`；进入面试流程后至少应有一条面试记录。                                              |
| `offer`            | Offer 表，每一版录用条件单独一行，保存版本、薪资、试用期、社保公积金、收件邮箱、发送日期、候选人回复和回复时间。             | 关联 `person` 和 `application`；面试结果通过后才允许真实发邮件。候选人通过邮件中的专属页面回复，已发送的 Offer 只读。                |
| `onboarding`       | 入职登记表，保存身份证、联系方式、银行卡、住址、紧急联系人、入职时间和入职材料。                                | 直接关联 `person` 和 `application`；入职成功后通过 `onboarding_id` 关联或更新 `employee`。                     |
| `record`           | 全局业务流水表，记录“发生了什么”，例如新建应聘、增加沟通、安排面试、填写结果、发送 Offer、办理入职。                  | `entity_type` 表示来源表名，`entity_id` 表示来源表那一行的编号。它保存摘要和时间线信息，不重复保存完整业务表内容。                      |
| `audit`            | 技术审计主表，记录谁在什么时间执行了哪类修改，并保存该次操作的上下文。                                     | 面向管理员排查问题；它和用户看到的业务时间线 `record` 用途不同。                                                       |
| `audit_change`     | 技术审计明细表，逐项记录某个字段修改前和修改后的值。                                              | 通过 `audit_id` 归属一条 `audit`；例如记录手机号从旧值改成新值。                                                  |

### `record` 如何与业务表配合

`record` 相当于一本总账，沟通、面试、Offer、入职等独立表相当于原始单据。新增或修改业务时，先把完整内容写入对应业务表，再向 `record` 增加一条便于查看时间线的流水。

例如，安排了一场面试：完整的面试时间、轮次、面试官和结果写入 `interview`；同时新增一条 `record`，其中 `entity_type` 为 `interview`，`entity_id` 为这场面试在 `interview` 表里的 `id`。这样既能从总时间线看到“安排了面试”，也能准确找到完整面试内容。

各阶段的写入关系如下：

1. 新建招聘经历：写入 `application`，同时在 `record` 记一条应聘流水。
2. 新增沟通：写入 `communication`，同时在 `record` 记一条沟通流水。
3. 安排面试或填写结果：写入或更新 `interview`，同时在 `record` 记对应流水。
4. 创建、发送或完成 Offer：写入或更新 `offer`，同时在 `record` 记对应流水。
5. 办理入职：写入 `onboarding`，把任职经历追加到 `person.employments`；正式入职后建立或更新 `employee`，每个关键动作都同步写入 `record`。

消息关联规则固定为：人才问卷提交后，系统自动创建 `person` 和 `application`，再创建一条只关联该 `person` 的消息；安排面试后，系统创建一条只关联该 `interview` 的消息。其它类型的消息可以正常写入 `message`，但 `person_id` 和 `interview_id` 都留空。是否已读由同一行的 `is_read` 属性保存。



## 验证

```powershell
cd front
npm test
npm run build

cd ..\back
mvn test
```

后端集成测试只允许连接专门的 MySQL 测试库，避免把测试数据写进正式使用的 `person_workbench` 数据库。运行前需要设置 `SETHUB_TEST_DB_URL`、`SETHUB_TEST_DB_USER`、`SETHUB_TEST_DB_PASSWORD` 三个环境变量。测试覆盖首次设置与登录、每页最多 10 人、薪资校验、并发覆盖保护、2021 年历史时间轴、岗位快照、Offer 版本和多组织隔离。前端测试覆盖 CSV 解析、字段映射、校验、防公式注入和导出。

## 目录

- `front/`：Vue、Vue Router、Element Plus 和 Vite 前端。
- `back/`：Spring Boot、JDBC、JWT、MySQL 后端。
- `back/tools/mysql-server-bootstrap.sql`：全新电脑第一次使用时，以 MySQL 管理员身份执行一次，建立数据库和专用账号。
- `back/config/mysql-secret.example.yml`：本地数据库账号配置样例，复制后改名为 `mysql-secret.yml`。
- `back/src/main/resources/db/initial-data.sql`：首次启动自动执行的公司、地点和岗位基础数据。
- `后端结构与函数说明.md`：按目录、类和函数说明后端每一部分在做什么。
- `SetHub_人才全生命周期管理系统_PRD.md`：产品范围、状态与待确认决策。
