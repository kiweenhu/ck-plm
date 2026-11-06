# CK-PLM

> The First Open-Source PLM Platform in China

[中文](README.md) | **English**

![License](https://img.shields.io/badge/License-Apache%202.0-blue)
![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.16-brightgreen)
![Vue](https://img.shields.io/badge/Vue-3.4-4FC08D)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-14+-336791)
![Flowable](https://img.shields.io/badge/Flowable-7.2.0-lightgrey)

CK-PLM is an open-source Product Lifecycle Management platform initiated and open-sourced by **Shenzhen Chengkai Technology Co., Ltd.** It is built from scratch based on over a decade of Windchill implementation experience and architectural understanding. Designed for discrete manufacturing, it provides core PLM capabilities including version control, BOM management, lifecycle management, soft type extension, classification system, engineering documents, workflow engine, and resource libraries.

> ⚠️ **Project Status**: Work In Progress — the core architecture is implemented in code. You are welcome to clone, try it out, and contribute. Not yet recommended for production use.
>
> Latest release: [v0.3.0](CHANGELOG.md) (2026-11-04) — v0.3 development cycle: added BOM management, change management (ECR/ECN/technical review), CAD visualization integration, methodology template system, MPN manufacturer management, and industry pack framework. Workflow engine switched from Activiti to Flowable 7.2.0.

> 🌐 **Live Demo**: <http://plm.ckai-tech.group/>
>
> Try CK-PLM directly in your browser — no installation required. Data is periodically reset; do not enter real business data.

## Table of Contents

- [Core Architecture](#core-architecture)
- [Key Features](#key-features)
- [Tech Stack](#tech-stack)
- [Quick Start](#quick-start)
- [Documentation](#documentation)
- [Known Limitations](#known-limitations)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [License](#license)
- [Contact](#contact)

## Core Architecture

PLM is not a pile of CRUD pages. Its essence is the organic combination of **version control engine + lifecycle state machine + soft type extension + engineering data model + workflow engine**.

- **Version Control Engine**: Master → View → Iteration → BOM four-layer version model, providing a unified version management foundation for business scenarios
- **Lifecycle State Machine**: Stage-based process control with configurable state transitions, supporting **automatic approval workflow triggering on state changes**
- **Soft Type Extension**: Soft Type + IBA dynamic attribute system, type definitions benchmarked against Windchill SoftType, supporting page layouts and type instance registration
- **Engineering Data Model**: Three core object types — Part, EngineeringDocument, and Functional — uniformly managed under versioning and lifecycle
- **Workflow Engine**: Flowable 7.2.0 as runtime kernel + **in-house visual flow designer** (recently replaced Activiti Modeler), enabling approval flow orchestration without writing expressions or configuring listeners
- **Universal Resource Library**: ResourceLibrary / ResourceContainer generalized model承载ing component libraries, package symbol libraries, classified resources, and other multi-form data
- **Functional Architecture**: Connects "what the product should do" with "what the product is", with Stage model for full-chain traceability — the core capability supporting **automotive early planning BOM** and **military configuration management** (GJB 3206 baseline system)

## Key Features

| Feature | Description |
|---------|-------------|
| 🔄 Version Control Engine | Four-layer version model; revision (A.B.C) + iteration (1.2.3); checkout/checkin collaboration |
| ⏳ Lifecycle Management | Configurable lifecycle templates; state-machine-driven transitions; "state-workflow" binding rules |
| 🧩 Soft Type & Dynamic Attributes | Type definition system + IBA dynamic attributes; bindable to lifecycle/numbering/version rules/classification; configurable page layouts |
| 📂 Classification System | Multi-level classification tree; classification-bound IBA attribute sets; classification-specific page layouts |
| 👁️ View Management | Design / Manufacturing multi-view; view transition rules; independent iterations per view |
| 🏗️ Product Structure | Product line → product model → stage hierarchy; team & member management; folder organization |
| 📦 Parts & Documents | Three object types under unified version control; engineering documents support iteration, member/reference/dependency links and structure parsing |
| 🔗 Document-Part Linkage | PartDocLink builds evidence chains for part technical data (drawings, specs, test reports) |
| 🔁 Alternate Parts | PartAlternateLink replacement modeling, supporting localization substitution, discontinued part replacement, temporary alternate approval |
| 🧮 BOM Management | Usage links (BomLinks), multi-view BOM, **version diff comparison** (BomDiff), snapshots (BomSnapshot), rolled-up cost calculation and reports |
| ⚡ ECAD Support | Electronic component semantics, package symbols (PackageSymbol) and package library management, CAD tool integration selection |
| 🗃️ Universal Resource Library | ResourceLibrary / ResourceContainer layered containers; resource classification; file and media multi-storage backends |
| 🔎 Global Search | Unified search entry across business objects |
| 🔐 IAM & Multi-Tenancy | Multi-tenant data isolation; organization/user/role system; token authentication; notification center with configurable channels |
| 🔔 Workflow & Tasks | Process templates/categories/form templates; automated service tasks; process instance monitoring, task center, todos and timeline |
| 🤖 AI Assistant | AI chat entry, configurable to connect your own model service |
| 🔢 Numbering Rule Engine | Configurable number segments; constant/date/serial/classification value/separator value providers |
| 🔌 External System Integration | Target system registration and outbound call governance, with URL whitelist protection |
| 🏠 Workspace | Recent objects, my todos, my recent activities and other homepage aggregated data |
| 🛠️ Change Management | ECR (change request) / ECN (change notice) / change task / technical review / component qualification full workflow, including countersign, branch termination, add-signer |
| 📐 CAD Visualization Integration | Integrates with CK-CADForge lightweight conversion service, STEP → GLB browser-direct rendering, assembly structure tree and 3D model linkage |
| 📋 Methodology Template System | Six template types — Gate / Deliverable / Review / Rule / Metric / Dashboard — auto-instantiated as snapshots when creating products |
| 🏭 MPN Manufacturer Management | Manufacturer / Supplier + AML (Approved Manufacturer List) / AVL (Approved Vendor List) |
| 🔄 Version Revision | Unified revision entry for parts / documents / engineering documents, auto-deriving new version iterations |
| 📦 Industry Pack Framework | industry-packs system; platform-base open-source with the repo; automotive APQP / electronics core / military GJB / smart hardware IPD / traditional stage-gate multi-industry delivery packs |

## Tech Stack

| Layer | Technology |
|-------|------------|
| Backend | Spring Boot 3.5.16 + Java 17 |
| Persistence | MyBatis 3.0.4 + PostgreSQL 14+ |
| API Docs | Springdoc OpenAPI 2.8.0 (Swagger UI) |
| Workflow Engine | Flowable 7.2.0 (BPMN, integrated) |
| Frontend | Vue 3.4 + Vite 5.2 + Ant Design Vue 4.2 + TypeScript 5.4 |
| State Management | Pinia 3 + Vue Router 4 + Axios |
| Flow Designer | In-house `frontend/src/flow-designer` (replaced Activiti Modeler) |

## Quick Start

### Option 1: Docker One-Click (Recommended for First Look)

With Docker installed, no need to prepare JDK / Maven / PostgreSQL / Node locally:

```bash
cp .env.example .env      # Optional: change DB password and exposed ports
docker compose up -d
```

- Frontend: <http://localhost:8080>
- Backend API: <http://localhost:8082>
- First build pulls Maven and npm dependencies (slower); subsequent builds use image cache

Common operations:

```bash
docker compose logs -f backend     # View backend startup logs
docker compose down                # Stop (data volumes preserved)
docker compose down -v             # Stop and wipe DB (re-imports schema.sql on next start)
```

> ⚠️ `schema.sql` is imported **only on first start when the data volume is empty**. For subsequent schema changes, either execute incremental SQL inside the container, or `docker compose down -v` to rebuild.

### Option 2: Local Manual Setup

### Prerequisites

- JDK 17+
- Maven 3.8+
- PostgreSQL 14+
- Node.js 18+

### 1. Initialize Database

```bash
# Create database
psql -U postgres -c "CREATE DATABASE ck_plm;"

# The app auto-executes schema.sql on startup (sql.init.mode: always),
# schema.sql is idempotent (CREATE TABLE IF NOT EXISTS), safe to re-run.
# To import manually:
psql -U postgres -d ck_plm -f src/main/resources/schema.sql
```

> 📌 The single source of truth for schema is `src/main/resources/schema.sql`. Auto-executed on app startup (`spring.sql.init.mode: always`), fully idempotent. No Flyway/Liquibase integrated; cross-version upgrades require manual incremental SQL per release notes.

### 2. Start Backend

```bash
# Configure DB connection (copy template and fill in credentials)
cp src/main/resources/application-template.yml src/main/resources/application-local.yml

# Start (default port 8082)
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

After startup:
- Swagger API docs: http://localhost:8082/swagger-ui.html
- OpenAPI JSON: http://localhost:8082/v3/api-docs

### 3. Start Frontend

```bash
cd frontend
npm install
npm run dev
```

Default access: http://localhost:5173

## Documentation

| Resource | Description |
|----------|-------------|
| [CHANGELOG.md](CHANGELOG.md) | Version update records |
| [CK-CADForge Install Guide](docs/CK-CADForge-安装指南.md) | CAD lightweight conversion service deployment & integration: Docker / bare metal, tokens & shared volumes, acceptance steps, troubleshooting |
| [Architecture & Domain Articles](http://ckai-tech.group/blog/) | Deep-dive articles on version control, alternate parts, ECAD domain model, flow designer |
| [Community Wiki](https://github.com/kiweenhu/ck-plm/wiki) | Design notes and FAQ |

## Known Limitations

- Project is in WIP stage; APIs and data models may change, not recommended for production
- **Internationalization (i18n)** not yet supported; UI and messages are Chinese-only
- BOM multi-view complete semantics (inter-view transition, effectivity management) still being refined
- Cross-version upgrades require manual incremental SQL (no auto-migration framework); `schema.sql` only describes the complete structure of the current version
- CAD visualization depends on independently deployed CK-CADForge service (license boundary: Apache-2.0 vs LGPL-3.0), not distributed with this repo

## Roadmap

- BOM effectivity and view transition
- MCAD / ECAD deep tool integration
- Requirements management and traceability matrix enhancement
- Internationalization (Chinese + English)
- Database auto-migration framework integration

## Contributing

Welcome to contribute in the following ways:

1. **Star / Fork** the repo to help more people discover CK-PLM
2. **Open Issues**: report bugs, request features, discuss design
3. **Submit PRs**: follow existing code style, include change description
4. **Share**: share your experience on Zhihu, CSDN, OSChina and other platforms

## License

CK-PLM is open-sourced under the [Apache License 2.0](LICENSE). Free to use, modify, and distribute commercially, with copyright notice retained.

## Contact

- **Community**: http://ckai-tech.group
- **GitHub**: https://github.com/kiweenhu/ck-plm
- **Gitee**: https://gitee.com/ck-plm/ck-plm
- **Company**: Shenzhen Chengkai Technology Co., Ltd.
- **Email**: 459024003@qq.com

---

CK-PLM is open-sourced by Shenzhen Chengkai Technology Co., Ltd. We believe that domestic PLM needs an open core to gather industry strength. Welcome to join us in building it.
