# CK-PLM

> 国内首个开源的产品生命周期管理（PLM）平台
> The First Open-Source PLM Platform in China

![License](https://img.shields.io/badge/License-Apache%202.0-blue)
![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen)
![Vue](https://img.shields.io/badge/Vue-3.4-4FC08D)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-14+-336791)

CK-PLM 由**深圳乘恺科技有限公司**发起并开源，基于十余年 Windchill 实施经验与架构理解全新构建。面向离散制造业，提供版本控制、BOM 管理、生命周期、软类型扩展、分类体系、功能架构等核心 PLM 能力。

> ⚠️ **项目状态**：平台仍在持续构建中（Work In Progress）——核心架构已落地为代码，欢迎克隆体验与参与共建；暂不建议直接用于生产环境。

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

PLM 的本质不是一堆 CRUD 页面，而是**版本控制引擎 + 生命周期状态机 + 软类型扩展 + CAD 数模管理体系**的有机组合。

- **版本控制引擎**：Master → View → Iteration → BOM 四层版本模型，为上层业务场景提供统一的版本管理底座
- **生命周期状态机**：阶段化流程管控，状态流转可配置
- **软类型扩展**：Soft Type + IBA 动态属性体系，类型定义对标 Windchill SoftType
- **CAD 数模管理**：数模关联结构，支撑设计与制造数据一体化
- **功能架构能力**：Functional Architecture 连接"产品要做什么"与"产品是什么"，阶段（Stage）模型全链路可追溯——这是支撑**汽车早期规划 BOM** 与**军工构型管理**（GJB 3206 基线体系）的核心能力

## 关键特性

| 特性 | 说明 |
|------|------|
| 🔄 版本控制引擎 | 四层版本模型；修订版本（Revision A.B.C）+ 迭代版本（Iteration 1.2.3）；检出/检入协作机制 |
| ⏳ 生命周期管理 | 可配置生命周期模板；状态机驱动的状态流转；模板支持版本化管理 |
| 🧩 软类型与动态属性 | 类型定义（TypeDefinition）体系；IBA 动态属性扩展；类型可绑定生命周期/编号规则/版本规则/分类 |
| 📂 分类体系 | 多级分类树管理；分类绑定 IBA 属性集；分类专属页面布局 |
| 👁️ 视图管理 | Design / Manufacturing 等多视图定义；视图转换规则；同一对象在不同视图下独立迭代 |
| 🏗️ 产品结构管理 | 产品线 → 产品型号 → 阶段层级；团队与成员管理；文件夹组织结构 |
| 📦 业务对象 | 部件（Part）、文档（Document）、功能架构（Functional）三类核心对象，统一支持版本控制与生命周期 |
| 🔐 IAM 与多租户 | 多租户数据隔离；组织/用户/角色体系；Token 认证；站内通知 |
| 🗂️ 文件与媒体管理 | 多存储后端；附件管理；媒体空间；CAD 数模存储目录 |
| 🔢 编号规则引擎 | 可配置编号段；常量/日期/序列/分类值/分隔符多种值提供器 |

## 技术栈

| 层 | 技术 |
|----|------|
| 后端框架 | Spring Boot 3.5.16 + Java 17 |
| 持久层 | MyBatis 3.0.4 + PostgreSQL 14+ |
| API 文档 | Springdoc OpenAPI 2.8.0（Swagger UI） |
| 前端框架 | Vue 3.4 + Vite 5.2 + Ant Design Vue 4.2 |
| 状态管理 | Pinia 3 + Vue Router 4 + Axios |
| 工作流 | Activiti 7（前端 UI 已就绪，后端待集成） |

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

| 文档 | 说明 |
|------|------|
| [docs/](docs/) | 架构设计、领域模型、上线文章等 |
| [CHANGELOG.md](CHANGELOG.md) | 版本更新记录 |
| [官网博客](http://ckai-tech.group/blog/) | 《CK-PLM v1.0 正式发布：把经验还给行业》等深度文章 |

## 已知限制

- Activiti 7 工作流引擎因 Spring Boot 3.5 兼容性问题暂未集成（前端 UI 已就绪）
- BOM 用量链接（PartUsageLink）尚未实现，部件结构树待补全
- 功能架构模块已完成基础能力，分解结构/物理映射/基线对比待完善
- 变更管理（ECR/ECO）尚未实现

## 后续规划

- Activiti 工作流引擎集成
- BOM 多视图管理与用量链接
- 功能架构完整能力（结构/映射/基线）
- 变更管理（ECR/ECO）
- 需求管理集成

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
- **公司**：深圳乘恺科技有限公司
- **邮箱**：459024003@qq.com

---

CK-PLM 由深圳乘恺科技有限公司开源。我们相信，国产 PLM 需要一个开放的核心来凝聚行业力量，欢迎参与共建。
