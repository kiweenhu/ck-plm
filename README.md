# CK-PLM

> 国内首个开源的产品生命周期管理（PLM）平台
> The First Open-Source PLM Platform in China

![License](https://img.shields.io/badge/License-Apache%202.0-blue)
![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.16-brightgreen)
![Vue](https://img.shields.io/badge/Vue-3.4-4FC08D)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-14+-336791)
![Flowable](https://img.shields.io/badge/Flowable-7.2.0-lightgrey)

CK-PLM 由**深圳乘恺科技有限公司**发起并开源，基于十余年 Windchill 实施经验与架构理解全新构建。面向离散制造业，提供版本控制、BOM 管理、生命周期、软类型扩展、分类体系、工程文档、流程引擎、资源库等核心 PLM 能力。

> ⚠️ **项目状态**：平台仍在持续构建中（Work In Progress）——核心架构已落地为代码，欢迎克隆体验与参与共建；暂不建议直接用于生产环境。
> 最近一次发版：[v0.1.5](CHANGELOG.md)（2026-09-07），其后又完成了工程文档重构、资源库泛化、流程设计器自研替换等一批演进。

## 目录

- [核心架构](#核心架构)
- [关键特性](#关键特性)
- [技术栈](#技术栈)
- [快速开始](#快速开始)
- [项目文档](#项目文档)
- [已知限制](#已知限制)
- [后续规划](#后续规划)
- [贡献指南](#贡献指南)
- [开源协议](#开源协议)
- [联系我们](#联系我们)

## 核心架构

PLM 的本质不是一堆 CRUD 页面，而是**版本控制引擎 + 生命周期状态机 + 软类型扩展 + 工程数据模型 + 流程引擎**的有机组合。

- **版本控制引擎**：Master → View → Iteration → BOM 四层版本模型，为上层业务场景提供统一的版本管理底座
- **生命周期状态机**：阶段化流程管控，状态流转可配置，并支持**状态流转自动拉起审批流程**
- **软类型扩展**：Soft Type + IBA 动态属性体系，类型定义对标 Windchill SoftType，支持页面布局与类型实例注册
- **工程数据模型**：部件（Part）、工程文档（EngineeringDocument）、功能架构（Functional）三类核心对象统一纳入版本与生命周期管理
- **流程引擎**：Flowable 7.2.0 作为运行时内核 + **自研可视化流程设计器**（已于近期替换 Activiti Modeler），不写表达式、不配监听器即可编排审批流
- **通用资源库**：以 ResourceLibrary / ResourceContainer 泛化模型承载元器件库、封装符号库、分类资源等多形态数据
- **功能架构能力**：Functional Architecture 连接"产品要做什么"与"产品是什么"，阶段（Stage）模型全链路可追溯——这是支撑**汽车早期规划 BOM** 与**军工构型管理**（GJB 3206 基线体系）的核心能力

## 关键特性

| 特性 | 说明 |
|------|------|
| 🔄 版本控制引擎 | 四层版本模型；修订版本（Revision A.B.C）+ 迭代版本（Iteration 1.2.3）；检出/检入协作机制 |
| ⏳ 生命周期管理 | 可配置生命周期模板；状态机驱动流转；支持绑定「状态-流程」联动规则 |
| 🧩 软类型与动态属性 | 类型定义体系 + IBA 动态属性；可绑定生命周期/编号规则/版本规则/分类；页面布局可配置 |
| 📂 分类体系 | 多级分类树；分类绑定 IBA 属性集；分类专属页面布局 |
| 👁️ 视图管理 | Design / Manufacturing 等多视图；视图转换规则；同一对象在不同视图下独立迭代 |
| 🏗️ 产品结构管理 | 产品线 → 产品型号 → 阶段层级；团队与成员管理；文件夹组织结构 |
| 📦 部件与文档 | 部件、文档、工程文档三类对象统一版本控制；工程文档支持迭代、成员/引用/依赖三类链接与结构解析 |
| 🔗 文档-零部件关联 | PartDocLink 建立零部件技术资料证据链（图纸、规范、测试报告） |
| 🔁 替代料 | PartAlternateLink 替换关系建模，支撑国产化替代、停产件替换、临时替代审批 |
| 🧮 BOM 管理 | 用量链接（BomLinks）、多视图 BOM、**版本差异比较**（BomDiff）、快照（BomSnapshot）、逐层成本汇总与成本报表 |
| ⚡ ECAD 支持 | 电子元器件语义、封装符号（PackageSymbol）与封装库管理、CAD 工具集成选择 |
| 🗃️ 通用资源库 | ResourceLibrary / ResourceContainer 分层容器；资源配置化分类；文件与媒体多存储后端 |
| 🔎 全局检索 | 跨业务对象的统一检索入口 |
| 🔐 IAM 与多租户 | 多租户数据隔离；组织/用户/角色体系；Token 认证；通知中心与可配置通知渠道 |
| 🔔 流程与任务 | 流程模板/分类/表单模板；自动化服务任务；流程实例监控、任务中心、待办与流转时间线 |
| 🤖 AI 助手 | AI 对话入口，可配置接入自有模型服务 |
| 🔢 编号规则引擎 | 可配置编号段；常量/日期/序列/分类值/分隔符多种值提供器 |
| 🔌 外部系统集成 | 目标系统（TargetSystem）登记与出站调用治理，含 URL 白名单防护 |
| 🏠 工作台 | 最近访问对象、我的待办任务、我的近期对象等首页聚合数据 |

## 技术栈

| 层 | 技术 |
|----|------|
| 后端框架 | Spring Boot 3.5.16 + Java 17 |
| 持久层 | MyBatis 3.0.4 + PostgreSQL 14+ |
| API 文档 | Springdoc OpenAPI 2.8.0（Swagger UI） |
| 工作流内核 | Flowable 7.2.0（BPMN，已集成） |
| 前端框架 | Vue 3.4 + Vite 5.2 + Ant Design Vue 4.2 + TypeScript 5.4 |
| 状态管理 | Pinia 3 + Vue Router 4 + Axios |
| 流程设计器 | 自研 `frontend/src/flow-designer`（已替代 Activiti Modeler） |

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- PostgreSQL 14+
- Node.js 18+

### 1. 初始化数据库

```bash
# 创建数据库
psql -U postgres -c "CREATE DATABASE ck_plm;"

# 方式一：由应用自动初始化（推荐）
# 打开 src/main/resources/application.yml，取消 sql.init 配置注释：
#   spring:
#     sql:
#       init:
#         mode: always
#         schema-locations: classpath:schema.sql

# 方式二：手动导入 schema
psql -U postgres -d ck_plm -f src/main/resources/schema.sql
```

> 📌 `src/main/resources/db/migration/` 下的脚本是**演进留档**，供 DBA 在手建/升级库时按版本号顺序手工参考执行。当前项目未集成 Flyway/Liquibase，这些脚本不会由应用自动执行。

### 2. 启动后端

```bash
# 配置数据库连接（复制模板并填入账号密码）
cp src/main/resources/application-template.yml src/main/resources/application-local.yml

# 启动（默认端口 8082）
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

启动后访问：
- Swagger API 文档：http://localhost:8082/swagger-ui.html
- OpenAPI JSON：http://localhost:8082/v3/api-docs

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
```

默认访问 http://localhost:5173

## 项目文档

| 资源 | 说明 |
|------|------|
| [CHANGELOG.md](CHANGELOG.md) | 版本更新记录 |
| [架构与领域文章](http://ckai-tech.group/blog/) | 版本控制、替代料、ECAD 领域模型、流程设计器等深度文章 |
| [社区 Wiki](https://github.com/kiweenhu/ck-plm/wiki) | 设计说明与常见问题 |

## 已知限制

- 项目整体处于 WIP 阶段，接口与数据模型仍可能调整，不建议直接上生产
- **变更管理（ECR/ECO）**尚未实现，需求追溯能力仍依托于功能架构模块
- **国际化（多语言）**尚未支持，界面与提示目前仅中文
- BOM 多视图的完整语义（视图间转换、有效性管理）仍在打磨
- `db/migration` 脚本需人工执行，尚未接入自动迁移框架

## 后续规划

- 变更管理（ECR/ECO）模块
- BOM 有效性（Effectivity）与视图转换
- MCAD / ECAD 深度工具集成
- 需求管理与追溯矩阵增强
- 国际化（中英双语）
- 数据库自动迁移框架接入

## 贡献指南

欢迎通过以下方式参与共建：

1. **Star / Fork** 仓库，让更多人看到 CK-PLM
2. **提 Issue**：报告 Bug、反馈需求、讨论设计
3. **提 PR**：代码贡献请遵循现有代码风格，附上修改说明
4. **分享**：在知乎、CSDN、开源中国等平台分享你的使用体验

## 开源协议

CK-PLM 采用 [Apache License 2.0](LICENSE) 开源协议，可自由使用、修改与商用，需保留版权声明。

## 联系我们

- **开源社区**：http://ckai-tech.group
- **GitHub**：https://github.com/kiweenhu/ck-plm
- **Gitee**：https://gitee.com/ck-plm/ck-plm
- **公司**：深圳乘恺科技有限公司
- **邮箱**：459024003@qq.com

---

CK-PLM 由深圳乘恺科技有限公司开源。我们相信，国产 PLM 需要一个开放的核心来凝聚行业力量，欢迎参与共建。
