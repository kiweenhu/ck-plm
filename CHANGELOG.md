# 更新日志

## [v0.2.1] - 2026-10-03

### 本次更新摘要

v0.2.1 是一次**以修缺陷为主的维护版本**。修掉了两个会让业务数据算错的毛病（版本规则的序号恒停在第 1 个字母、租户条件被拼到 `RETURNING` 之后导致 SQL 报错），补上自助修改密码缺失的服务端校验，新增「退出其他设备」。同时把 v0.2.0 开始的初始化治理又推进了一步：播种职责从 Service 与建表类里搬进独立的 `*Initializer`。

### 缺陷修复（Fixes）

#### 1. 版本规则自增序号取值失败，版本号恒停在第 1 个字母
- `UPDATE … RETURNING sequence_value` 走的是 `executeUpdate`，MyBatis 拿不到 `RETURNING` 的值，返回的实际是「影响行数」—— 于是每次读到的序号都是错的，版本生成一直停在序列的第一个字母
- 拆成两步：`incrementSequence()` 只负责 +1 并返回影响行数，`selectSequenceValue()` 再读新值；同一事务内 `UPDATE` 已持有行锁，读取安全
- **相关**：租户拦截器 `TenantStatementInterceptor` 的子句边界清单漏了 `RETURNING`，拼出
  `… WHERE code = ? RETURNING sequence_value AND tenant_oid = '…'`，触发
  「AND 的参数必需是类型 boolean，而不是类型 bigint」。改为正则词边界匹配并补入 `RETURNING`，
  顺带避免列名或字面量里出现 `limit`、`union` 时被误当成子句起点

#### 2. 自助修改密码缺少服务端校验
- 此前只校验旧密码，新密码的长度、是否与原密码相同都没人管 —— 走接口可以设成 1 位，甚至「改」成原密码（前端拦得住，但接口是公开契约）
- 补：长度不少于 6 位、不得与当前密码相同

### 新增功能（Features）

#### 退出其他设备
- 新增 `POST /api/auth/logout-others`：保留当前会话，其余 token 立即失效 —— 密码疑似泄露、或曾在别人电脑上登录过之后的处置手段
- 与注销共用同一套审计口径，操作日志可回溯「谁在什么时候把其他设备踢下线了」

#### 个人中心重构
- 个人中心改为「账号资料 / 安全设置」两个页签：资料页可编辑姓名与邮箱、查看角色明细，安全页承载改密与退出其他设备
- 主菜单「个人中心」更名「工作台」，「个人设置」不再提示「开发中」

#### 补全 PART 的四个页面布局
- PART 是零部件家族的模板根，各子类型自身没有属性定义（属性由能力宿主 PART 解析），前端表单的 `fallbackEntityCode` 也指向它；此前 PART 没有布局，这些表单全是空的
- 补 `list` / `create` / `update` / `detail` 四套布局

#### 类型页与容器选择器
- 类型页新增「按类型 / 按业务域」视图切换，域视图下显示对象计数与内置/自定义标签
- 资源容器选择器：取值落在产品系列、文件夹等非资源库容器时补一条兜底展示项，不再把内部 oid 直接显示给用户

### 重构（Refactor）

#### 初始化职责继续收敛
- 新增 `NumberRuleInitializer`（`@Order(1)`）、`VersionRuleInitializer`、`EcadDomainInitializer`，把默认编码规则、版本规则、ECAD 域的播种从 Service 与 Schema 类里搬出来
- 删除 `EcadSchemaInitializer`（建表职责已于 v0.2.0 交给 `schema.sql`）、`ProductLineSeeder`（早已停用）、`sql/ck_user_activity.sql` 留档脚本
- `TypeDefinitionInitializer` 不再播种编码规则，只负责把类型**绑**到规则上 —— 以后改编号段配置不必再动类型初始化器
- `schema.sql` 为 `ck_user_activity` 补 `(user_oid, activity_type, created_at DESC)` 索引

### 其他
- 全仓版权年份统一更新为 `2026~2028`
- 术语统一：个人中心 → 工作台；`EcadSchemaInitializer` 相关注释改指向 `schema.sql`

## [v0.2.0] - 2026-10-02

### 本次更新摘要

v0.2.0 重点补齐**BOM 替代料**这条主线，并第一次把库结构收敛成单一真相。新增 BOM 替代料（局部 / 成组两种粒度）、BOM 四种格式导出、业务域、通用件门槛与标准件入库配置；同时删除 15 个运行时自动建表类与 20 个从不执行的留档 SQL，建表职责全部交给 `schema.sql`。

### 新增功能（Features）

#### 1. BOM 替代料：局部替代与成组替代
- **局部替代**（`bom-substitutes`）：挂在具体 BOM 行上，换"一颗料"，只在该行上下文生效；与物料主数据级的全局替代（`part-alternates`）区分，互不干扰
- **成组替代**（`bom-substitute-groups`）：挂在父件迭代上，换"一组行"，带 `atomicReplace` 整组替换约束，源侧 / 替代侧成员分别维护，可按组启用禁用
- 物料检出复制 BOM 结构时连同行上的局部替代一起复制，避免检出后替代关系整片丢失
- 业务价值：覆盖国产化替代、停产件替换、临时替代这些真实场景下的 BOM 变更

#### 2. BOM 导出 csv / xls / xlsx / pdf
- `BomExportService` + `bom/export` 包，四种格式统一入口
- POI 写 xls / xlsx，csv 走纯文本，PDF 用 OpenPDF（自带中文 CID 字体，无需额外打包字库）

#### 3. 业务域（BusinessDomain）
- 新增 `ck_business_domain`，给类型定义分域，避免所有软类型平铺在一层里
- `TypeDefinition` 与业务域打通，类型页面支持按域选择

#### 4. 通用件门槛与标准件入库配置
- 通用件门槛配置（`ck_gen_part_threshold_config`）：控制通用件认定阈值，避免通用件库无限膨胀
- 标准件入库配置（`ck_std_part_inbound_config`）：标准件走独立入库策略，与自制件 / 外购件分开

### 重构（Refactor）

#### 5. 库结构收敛到 schema.sql
- 删除 15 个运行时建表 / 迁移类（`*TableInitializer` / `*Migration`），建表职责统一交给 `schema.sql`
- 删除 `db/migration` 下 V008~V027 共 20 个留档脚本：项目未集成 Flyway/Liquibase，这些脚本从不会被自动执行，留着只会让人误以为改了就能升级
- `schema.sql` 补齐此前由 Java 类隐式创建的表（业务域、流程模板、工程文档、资源库、目标系统、通用件门槛与标准件入库配置等），成为库结构的唯一真相
- 保留的 `*Initializer` 只做数据初始化（生命周期状态、管理员、属性、页面布局），不再碰 DDL

#### 6. 前端版本号单一来源与类型检查
- vite 构建期从 `frontend/package.json` 注入 `__APP_VERSION__`，界面不再硬编码版本号
- 新增 `tsconfig.vue.json` 与 `npm run typecheck`（vue-tsc），`.vue` 文件也纳入类型检查

### 其他（Others）
- 登录页能力清单卡片化并展示当前版本号
- 流程实例补发起人显示名（此前同页内"流程信息"显示账号、"负责人"显示姓名，两套口径）
- 开发错误浮层忽略浏览器噪声（ResizeObserver loop），避免自适应弹窗误报红框
- 产品线仪表盘、业务配置、页面设计器、资源库分类、阶段模板配置等页面细节打磨

## [v0.1.5] - 2026-09-07

### 本次更新摘要

v0.1.5 是 v0.1.0 之后的第 5 个迭代版本。本版本**不引入破坏性架构变更**，重点完善业务模型与服务层，新增 4 个核心业务模块（文档-零部件关联、替代料、资源库、全文搜索），并完成分类架构重构与产品-产品线-阶段三级联动。

### 新增功能（Features）

#### 1. 文档-零部件关联模块（PartDocumentLink）
- 新增 `PartDocumentLink` 实体，支持零部件与文档多对多关联
- 配套 Controller / Service / Mapper / PostgreSQL 实现，文档可挂接到任意零部件视图
- `DocumentMapper` 支持按零部件反查关联文档清单
- 业务价值：建立零部件技术资料的完整证据链（CAD 图纸、技术规范、测试报告）

#### 2. 替代料业务模块（PartAlternateLink）
- 新增 `PartAlternateLink` 实体，支持零部件替换关系建模
- 双层 Service 抽象（api/impl），与 Checkout/Checkin 流程联动
- 业务价值：支持元器件国产化替代、停产件替换、临时替代审批

#### 3. BOM 用量链接与差异比较（BomLinks / BomDiff）
- 完整 BOM 用量链接（`BomLinks`）模块：多视图用量、替代关系、用量属性
- BOM 差异比较（`BomDiff`）：基线对比、变更影响分析
- PostgreSQL Mapper + 双层 Service 抽象
- 业务价值：取代 v0.1.0 已知限制中的"PartUsageLink 尚未实现"

#### 4. 资源库与全文搜索基础设施
- 新增 `resource/` 模块：企业资源库入口
- 新增 `search/` 模块：全局检索服务（`GlobalSearchService` / `GlobalSearchServiceImpl` / `SearchResultVO`）
- 业务价值：跨零部件 / 文档 / 项目的统一搜索能力

### 增强与重构（Enhancements）

#### 5. 分类架构重构
- `ClsIbaDataMapper` 升级为多态查询，适配 V015 迁移后的 `plm_iba_code` 去唯一约束
- `ClassificationService / ClassificationController` 支持多级分类与属性聚合
- 业务价值：分类属性可重复挂载，支撑电子/结构/工艺多包并存

#### 6. 阶段模板（StageTemplate）
- 实体与服务层重构，支持与产品/产品线绑定
- `NumberSegmentMapper` 编号段位生成器适配多业务场景
- 业务价值：阶段模板复用，编号策略灵活可配置

#### 7. 产品线 / 产品 / 阶段 领域模型三级联动
- `ProductLine / ProductModel / Stage` 实体、Mapper、Service、Controller 全面更新
- 业务价值：产品 → 产品线 → 阶段 完整生命周期建模，支撑产品组合管理

#### 8. 基础数据启动注入（Initializers）
- `UnitInitializer`：计量单位基础数据
- `ViewInitializer`：视图基础数据
- `PageLayoutInitializer / TypeDefinitionInitializer`：软类型页面布局与类型定义预置
- 业务价值：开箱即用，无需手动初始化基础数据

### 数据库迁移

#### V015 - 移除 plm_iba_code 唯一约束
- 文件：`src/main/resources/db/migration/V015__drop_plm_iba_code_unique_constraint.sql`
- 说明：分类 IBA 数据从单态扩展为多态后，原唯一约束阻碍多分类属性共存
- **升级前请先备份数据库**

### 前端界面更新

- 新增 `ComponentLibrary.vue` 组件库页面
- `EnterpriseResource.vue`：资源库入口
- `PartDetail / ProductLineDashboard / ProductSeries.vue`：产品-产品线-阶段联动视图
- `ClassificationPage / StageTemplateConfig / TypePage / ViewConfig / OperationLogPage.vue`：配置页全面更新
- 基础组件：`ClassificationBoundSelect / DataTable / DynamicForm / RenderFields / UnitSelect`
- `API / Router / MainLayout` 同步适配新后端模块

### 兼容性

- ✅ 数据库结构向后兼容（V015 仅为放宽约束，不破坏现有数据）
- ✅ API 接口向后兼容（既有调用无需修改）
- ⚠️ 升级前请执行 `flyway migrate` 以应用新的数据库迁移

### 本版本解决的问题

| v0.1.0 已知限制 | v0.1.5 状态 |
|----------------|------------|
| BOM 用量链接（PartUsageLink）尚未实现 | ✅ 已实现（BomLinks） |
| 功能架构仅基础 CRUD | ⏳ 部分推进 |
| 变更管理（ECR/ECO）尚未实现 | ⏳ 待办 |
| 流程引擎 | ✅ 已由 Flowable 7 切换为 Flowable 7.2.0（依赖已引入，集成进行中） |

### 数据模型规模（v0.1.5）

- 核心数据表：49 → **51** 张（新增 `part_document_link`、`part_alternate_link`，V015 移除唯一约束后扩展 IBA 多态）
- 业务模块：12 → **14** 个（新增资源库、全文搜索）

### 后续规划

- v0.2.0：BOM 多视图管理与结构树补全
- v0.3.0：变更管理（ECR/ECO）流程引擎
- v0.4-5：功能架构完整能力（结构/映射/基线）
- v0.6-7：需求追溯（RTM）与显性建模工具集成
- v1.0：MVP 闭环 + Open Core 商业版

---

## [v0.1.0] - 2026-08-03

### 项目概述

CK-PLM v0.1.0 是国内首个开源 PLM（产品生命周期管理）产品的**核心架构首个开源版本**。

基于多年 Windchill 实施经验和架构理解全新构建，面向离散制造业，提供版本控制、BOM 管理、生命周期、软类型、工作流等核心 PLM 能力。采用 Open Core 商业模式，核心开源，商业版闭源。

### 技术栈

| 层 | 技术 |
|----|------|
| 后端框架 | Spring Boot 3.5.16 + Java 17 |
| 持久层 | MyBatis 3.0.4 + PostgreSQL |
| API 文档 | Springdoc OpenAPI 2.8.0 (Swagger UI) |
| 前端框架 | Vue 3 + Vite |
| 工作流 | Flowable 7.2.0（Flowable 原班团队分支；依赖已引入，后端集成进行中） |

### 核心特性

#### 1. 版本控制引擎（Master-View-Iteration-BOM 四层模型）
- Master（主数据）→ View（视图）→ Iteration（版本迭代）→ BOM（用量结构）四层架构
- 优化 Teamcenter/Windchill 的 View 架构：将 View 从 Iteration 的属性提升为独立层级，各视图下的版本演进相互独立
- 支持修订版本（Revision A.B.C）+ 迭代版本（Iteration 1.2.3）
- 检出/检入（Checkout/Checkin）协作机制，防止并发冲突
- 版本规则可配置，支持多种版本号方案

#### 2. 生命周期管理
- 可配置的生命周期模板（Lifecycle Template）
- 状态机驱动的状态流转（State Transition）
- 模板支持版本化管理
- 软类型可绑定不同生命周期模板

#### 3. 软类型与动态属性（Soft Type + IBA）
- 类型定义（TypeDefinition）体系 —— 对标 Windchill SoftType
- IBA（Instance-Based Attribute）动态属性扩展
- 类型可绑定：生命周期模板 / 编号规则 / 版本规则 / 分类
- 页面布局（Page Layout）可配置，支持可视化设计器

#### 4. 分类体系（Classification）
- 多级分类树管理
- 分类绑定 IBA 属性集
- 分类专属页面布局

#### 5. 视图管理（多视图 BOM 基础）
- Design / Manufacturing 等多视图定义
- 视图转换规则（View Transition）
- 同一对象在不同视图下可有独立迭代版本

#### 6. 产品结构管理
- 产品线（Product Line）→ 产品型号（Product Model）→ 阶段（Stage）层级
- 团队与成员管理
- 文件夹（Folder）组织结构

#### 7. 业务对象
- **部件管理（Part）** —— 物理 BOM 载体，支持版本控制与生命周期
- **文档管理（Document）** —— 技术文档版本化管理
- **功能架构（Functional）** —— 功能单元定义，对标 Windchill MPMLink
  - 功能单元 CRUD 与版本控制
  - 功能分解结构、功能-物理映射、功能基线（规划中）

#### 8. IAM 身份认证与多租户
- 多租户（Tenant）架构，数据隔离
- 组织（Organization）/ 用户（User）/ 角色（Role）体系
- Token 认证机制
- 站内通知系统

#### 9. 文件与媒体管理
- 文件存储配置（支持多存储后端）
- 附件管理（Attachment）
- 媒体空间（Media Space）

#### 10. 编号规则引擎
- 可配置的编号段（Number Segment）
- 多种值提供器：常量 / 日期 / 序列 / 分类值 / 分隔符
- 软类型可绑定编号规则

### 数据模型规模
- 49 张核心数据表
- 覆盖版本控制、生命周期、软类型、分类、视图、IAM、产品、文档、部件、功能架构、文件管理等完整领域

### 已知限制
- 流程引擎已切换为 **Flowable 7.2.0**（原 Flowable 7 已停止演进且与 Spring Boot 3.5 存在兼容阻塞），依赖已引入，后端集成与前后端打通进行中
- BOM 用量链接（PartUsageLink）尚未实现，部件结构树待补全
- 功能架构模块仅完成基础 CRUD，分解结构/物理映射/基线对比待实现
- 变更管理（ECR/ECO）尚未实现

### 后续规划
- Flowable 7 工作流引擎集成（依赖已引入，完成前后端打通）
- BOM 多视图管理与用量链接
- 功能架构完整能力（结构/映射/基线）
- 变更管理（ECR/ECO）
- 需求管理集成

---

### 致谢

CK-PLM 由深圳乘恺科技有限公司开源，感谢所有 PLM 行业同仁的关注与支持。

我们相信，国产 PLM 需要一个开放的核心来凝聚行业力量。欢迎参与共建。
