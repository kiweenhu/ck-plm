# 更新日志

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

#### 1. 版本控制引擎（Master-Iteration 双层模型）
- 参考 Windchill 架构，Master（主数据）+ Iteration（版本迭代）双层版本模型
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
