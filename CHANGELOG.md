# 更新日志

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
| Activiti 工作流引擎未集成 | ⏳ 待兼容版本发布 |

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
| 工作流 | Activiti 7（前端 UI 已就绪，后端因 Spring Boot 3.5 兼容性暂未集成） |

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
- Activiti 7 工作流引擎因 Spring Boot 3.5 兼容性问题暂未集成（前端 UI 已就绪）
- BOM 用量链接（PartUsageLink）尚未实现，部件结构树待补全
- 功能架构模块仅完成基础 CRUD，分解结构/物理映射/基线对比待实现
- 变更管理（ECR/ECO）尚未实现

### 后续规划
- Activiti 工作流引擎集成（待兼容版本发布）
- BOM 多视图管理与用量链接
- 功能架构完整能力（结构/映射/基线）
- 变更管理（ECR/ECO）
- 需求管理集成

---

### 致谢

CK-PLM 由深圳乘恺科技有限公司开源，感谢所有 PLM 行业同仁的关注与支持。

我们相信，国产 PLM 需要一个开放的核心来凝聚行业力量。欢迎参与共建。
