# 功能架构定义（Functional Architecture）设计方案

> **版本：** v1.0  
> **作者：** Kiween.Hu  
> **日期：** 2026-07-23  
> **目标：** 在CK-PLM中实现功能架构定义管理，覆盖军工构型管理和汽车早期规划BOM两大业务场景

---

## 目录

1. [业务背景与场景分析](#1-业务背景与场景分析)
2. [领域模型设计](#2-领域模型设计)
3. [数据库表结构设计](#3-数据库表结构设计)
4. [API接口设计](#4-api接口设计)
5. [服务层设计](#5-服务层设计)
6. [与现有架构的整合](#6-与现有架构的整合)
7. [业务场景覆盖分析](#7-业务场景覆盖分析)
8. [实施路线图](#8-实施路线图)

---

## 1. 业务背景与场景分析

### 1.1 军工构型管理（GJB 3206 / GJB 3206A）

军工行业的产品研制遵循严格的构型管理规范，核心概念包括：

```
┌─────────────────────────────────────────────────────┐
│                    军工构型管理体系                      │
│                                                         │
│  功能基线                   分配基线                   产品基线  │
│  (Functional Baseline)  →  (Allocated Baseline)  →  (Product Baseline) │
│                                                         │
│  描述"产品要做什么"         将功能分解并分配到        确定产品物理配置    │
│  通常以功能架构形式        各子系统/组件中                                 │
│  呈现                                                                        │
└─────────────────────────────────────────────────────┘
```

**关键需求：**
- 建立**功能架构树**，按系统/子系统/设备/组件的功能层级分解
- 功能架构与**物理产品结构**（部件BOM）建立可追溯的双向映射
- 按阶段冻结**功能基线**，作为后续设计变更的基准
- 支持**构型标识**：功能单元需有唯一编号标识，配合视图（View）机制区分不同构型视角

### 1.2 汽车早期规划BOM（F-BOM → E-BOM 演进）

汽车行业在概念设计阶段需要从功能视角定义产品：

```
F-BOM（功能BOM）              E-BOM（工程BOM）           P-BOM/M-BOM
─────────────────────────────────────────────────────────────────────
   动力总成系统                   发动机总成                   发动机装配线
   ├── 动力输出功能 ——满足——  ├── 曲轴连杆组件              ├── 缸体加工
   ├── 燃油供给功能 ——满足——  ├── 燃油喷射系统              ├── 活塞装配
   └── 冷却功能     ——满足——  └── 散热器总成                └── 水泵总成
```

**关键需求：**
- 建立**功能分解结构**（Functional Breakdown Structure, FBS）
- 功能到物理部件的**多对多映射**（一个功能可由多个部件联合实现，一个部件可满足多项功能）
- 支持从F-BOM到E-BOM的**追溯矩阵**
- 软类型扩展——不同类型的车辆（乘用车/商用车/新能源）需要不同的功能架构模板

### 1.3 两个场景的共同抽象

两者本质上都是**从功能视角描述产品结构，并与物理结构建立映射**：

| 维度 | 军工构型管理 | 汽车规划BOM | 共同抽象 |
|------|-------------|-------------|----------|
| 功能载体 | 功能基线中的功能单元 | F-BOM中的功能节点 | **FunctionalUnit** |
| 层级关系 | 系统→子系统→设备→组件 | 功能模块→子功能 | **FunctionalStructure** |
| 物理关联 | 功能基线到产品基线的分配 | F-BOM到E-BOM的映射 | **FunctionalPhysicalMapping** |
| 基线管理 | 功能基线/分配基线冻结 | 规划BOM版本快照 | **FunctionalBaseline** |
| 视图切换 | Design/Manufacturing视角 | 功能视角/工程视角/制造视角 | 复用现有**View**机制 |

---

## 2. 领域模型设计

### 2.1 整体继承关系

遵循CK-PLM现有的Master-Iteration版本控制模型和Windchill参考架构：

```
BaseEntity (oid, creator, createdAt, updater, updatedAt)
│
├── MasterEntity (name, number, description, containerOid, containerType)
│   ├── Part                  ← WTPartMaster
│   ├── Document             ← WTDocumentMaster
│   └── FunctionalUnit ★     ← 新增：功能单元Master
│
├── IterationEntity (masterOid, revision, iteration, view, status, ...)
│   ├── PartIteration        ← WTPart
│   ├── DocumentIteration    ← WTDocument
│   └── FunctionalUnitIteration ★  ← 新增：功能单元Iteration
│
├── (独立实体，不继承Master/Iteration)
│   ├── FunctionalStructureLink ★      ← 功能分解链接（直接绑定iteration）
│   ├── FunctionalPhysicalMapping ★    ← 功能-物理映射
│   └── FunctionalBaseline ★           ← 功能基线快照
```

### 2.2 FunctionalUnit（功能单元）— 核心载体

参考Part模型，但语义上描述"功能"而非"物理实体"。

#### FunctionalUnit（Master层）

```java
package cn.ck.plm.functional.entity;

/**
 * 功能单元主数据对象（FunctionalUnit Master）。
 *
 * <p>描述产品的一个功能节点，如"动力系统"、"制动功能"、"导航功能"。
 * 与 Part 并列，属于 MasterEntity 的一种新子类型。
 *
 * <p>生命周期绑定到 Iteration 层，Master 层只存储共享标识属性。
 *
 * <h3>字段设计说明</h3>
 * - name              功能名称（如"动力输出功能"）
 * - number            功能编号（按编码规则自动生成）
 * - description       功能描述（需求/指标说明）
 * - containerOid      所属产品上下文（产品线/型号OID）
 * - containerType     上下文类型（ProductLine/ProductModel）
 * - typeDefinitionCode 软类型编码（用于区分功能类别：安全功能/性能功能/环境适应性等）
 * - folderOid         所属文件夹（可选的目录组织）
 * - tenantOid         租户隔离
 */
public class FunctionalUnit extends MasterEntity implements TenantEntity {

    private String typeDefinitionCode;  // 软类型编码
    private String folderOid;           // 所属文件夹
    private String tenantOid;           // 租户

    // 构造方法、Getter/Setter 省略
}
```

#### FunctionalUnitIteration（Iteration层）

```java
package cn.ck.plm.functional.entity;

/**
 * 功能单元子版本数据对象（FunctionalUnit Iteration）。
 *
 * <p>承载功能单元的具体版本内容和生命周期。继承 IterationEntity
 * 的全部版本控制字段，新增功能领域特有的属性。
 *
 * <h3>功能特有字段</h3>
 * - functionalCategory  功能类别标签（安全功能、性能功能、环境适应性、可靠性等）
 * - requirementSource   需求来源（如"GJB 150A-2009"章节号、"客户SOR第3.2节"）
 * - allocationStatus    分配状态（UNALLOCATED/PARTIAL/FULLY_ALLOCATED）
 *                       用于追踪功能是否已映射到物理部件
 */
public class FunctionalUnitIteration extends IterationEntity implements TenantEntity {

    private String functionalCategory;   // 功能类别
    private String requirementSource;    // 需求来源
    private String allocationStatus;     // 分配状态
    private String tenantOid;

    // 构造方法、Getter/Setter 省略
}
```

#### 软类型 + IBA支持

FunctionalUnitIteration 实现 `IBAExtensible` 接口：

```java
public class FunctionalUnitIteration extends IterationEntity
        implements TenantEntity, IBAExtensible {

    @JsonIgnore
    private Map<String, Object> extAttrs;

    @Override public String getEntityType() { return "ck_functional_unit_iteration"; }
    @Override public String getEntityOid()  { return getOid(); }
    @Override public Map<String, Object> getExtAttrs() { return extAttrs; }
    @Override public void setExtAttrs(Map<String, Object> attrs) { this.extAttrs = attrs; }

    @JsonAnySetter
    public void setDynamicField(String key, Object value) {
        if (value == null) return;
        if (extAttrs == null) extAttrs = new LinkedHashMap<>();
        extAttrs.put(key, value);
    }
    @JsonAnyGetter
    public Map<String, Object> getDynamicFields() { return extAttrs; }
}
```

#### 预置软类型建议

| 类型编码 | 类型名称 | 说明 | 适用场景 |
|---------|---------|------|---------|
| `FUNC_SYS` | 系统级功能 | 顶层功能，如"动力系统" | 军工/汽车 |
| `FUNC_SUB` | 子系统功能 | 二级功能，如"燃油供给功能" | 军工/汽车 |
| `FUNC_CTQ` | 关键功能特性 | 需单独追溯的关键功能需求 | 汽车 |
| `FUNC_SAFETY` | 安全功能 | ISO 26262 ASIL等级关联 | 汽车 |
| `FUNC_PERF` | 性能指标功能 | 量化性能指标，如"百公里加速<6s" | 汽车 |
| `FUNC_MIL` | 战术技术指标 | 军工六性等指标 | 军工 |

---

### 2.3 FunctionalStructureLink（功能分解链接）

描述功能单元之间的层级/关联关系。**不继承版本控制**，直接绑定到具体的iteration版本。

```java
package cn.ck.plm.functional.entity;

/**
 * 功能结构链接 —— 描述功能单元之间的分解或关联关系。
 *
 * <h3>设计决策：为什么不版本化？</h3>
 * <p>参考 Windchill WTPartUsageLink，结构链接是"附加"在父节点上的，
 * 不独立版本化。当父功能单元的iteration变更（如新建修订版），链接
 * 指向的parentIterationOid自然变化，形成新的结构快照。
 *
 * <h3>linkType 枚举</h3>
 * - DECOMPOSITION  功能分解（父功能分解为子功能）
 * - INTERFACE      功能接口关联（两个功能之间有交互）
 * - SATISFIES      功能满足关系（子功能满足父功能的需求）
 */
public class FunctionalStructureLink extends BaseEntity implements TenantEntity {

    private String parentIterationOid;    // 父功能单元iteration OID（外键）
    private String childIterationOid;     // 子功能单元iteration OID（外键）
    private BigDecimal quantity;          // 用量（默认1）
    private String unitOfMeasure;         // 单位
    private String linkType;              // DECOMPOSITION / INTERFACE / SATISFIES
    private String description;           // 链接描述
    private Integer sortOrder;            // 排序（默认0）
    private String tenantOid;

    // 构造方法、Getter/Setter 省略
}
```

### 2.4 FunctionalPhysicalMapping（功能-物理映射）

功能到物理部件的多对多映射关系——这是连接F-BOM和E-BOM的桥梁。

```java
package cn.ck.plm.functional.entity;

/**
 * 功能-物理映射 —— 描述功能单元与物理部件之间的满足/实现关系。
 *
 * <h3>核心概念</h3>
 * F-BOM中的功能节点 ──mapping──→ E-BOM中的物理部件
 * 支持多对多：一个功能可由多个部件联合满足，一个部件可实现多项功能。
 *
 * <h3>mappingType 枚举</h3>
 * - SATISFIES       功能被部件满足（正向：F-BOM→E-BOM）
 * - ALLOCATED_TO    需求分配到部件（军工：分配基线→产品基线）
 * - IMPLEMENTS      部件实现了功能（反向：E-BOM→F-BOM）
 */
public class FunctionalPhysicalMapping extends BaseEntity implements TenantEntity {

    private String functionIterationOid;     // 功能单元iteration OID（外键）
    private String partIterationOid;         // 部件iteration OID（外键）
    private String mappingType;              // SATISFIES / ALLOCATED_TO / IMPLEMENTS
    private String allocationDescription;    // 分配/满足描述
    private BigDecimal allocationRatio;      // 分配比例（0-100，表示该部件承担多少比例的功能满足度）
    private String tenantOid;

    // 构造方法、Getter/Setter 省略
}
```

### 2.5 FunctionalBaseline（功能基线）

军工构型管理的基线快照机制——记录某一时刻完整的功能架构状态。

```java
package cn.ck.plm.functional.entity;

/**
 * 功能基线 —— 记录某一时刻产品功能架构的完整快照。
 *
 * <h3>构型基线类型（GJB 3206参考）</h3>
 * - FUNCTIONAL   功能基线   ← 方案阶段建立，描述功能架构
 * - ALLOCATED    分配基线   ← 初样阶段建立，描述功能到部件的分配
 *
 * <h3>快照机制</h3>
 * snapshotData 以 JSON 格式存储基线建立时的：
 * - 完整功能结构树（FunctionalUnit列表 + FunctionalStructureLink列表）
 * - 功能-物理映射关系（FunctionalPhysicalMapping列表）
 * - 各功能单元的iteration版本信息
 *
 * <h3>与View的关系</h3>
 * 基线关联到特定视图（View），不同视图下可以有独立的基线。
 * 例如：Design视图的功能基线与Manufacturing视图的功能基线可以不同。
 */
public class FunctionalBaseline extends BaseEntity implements TenantEntity {

    private String name;                    // 基线名称（如"TB-01 功能基线"）
    private String code;                    // 基线编码（如"BL-F-001"）
    private String description;             // 描述
    private String baselineType;            // FUNCTIONAL / ALLOCATED
    private String productOid;              // 关联的产品上下文OID
    private String productType;             // 产品上下文类型（ProductLine/ProductModel）
    private String viewCode;                // 关联的视图编码
    private String snapshotData;            // JSONB：完整快照数据
    private String status;                  // DRAFT / APPROVED / FROZEN / SUPERSEDED
    private LocalDateTime baselineDate;     // 基线日期
    private String approvedBy;              // 批准人
    private LocalDateTime approvedAt;       // 批准时间
    private String tenantOid;

    // 构造方法、Getter/Setter 省略
}
```

---

## 3. 数据库表结构设计

### 3.1 ck_functional_unit（功能单元Master）

```sql
CREATE TABLE IF NOT EXISTS ck_functional_unit (
    oid                   CHAR(36)     PRIMARY KEY,
    name                  VARCHAR(255) NOT NULL,
    number                VARCHAR(64)  NOT NULL,
    description           TEXT,
    container_oid         CHAR(36),
    container_type        VARCHAR(64),
    type_definition_code  VARCHAR(64),
    folder_oid            CHAR(36),
    tenant_oid            CHAR(36),
    creator               VARCHAR(100),
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater               VARCHAR(100),
    updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_func_unit_container ON ck_functional_unit(container_oid);
CREATE INDEX IF NOT EXISTS idx_func_unit_type      ON ck_functional_unit(type_definition_code);
CREATE INDEX IF NOT EXISTS idx_func_unit_tenant    ON ck_functional_unit(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_func_unit_number    ON ck_functional_unit(number);
```

### 3.2 ck_functional_unit_iteration（功能单元Iteration）

```sql
CREATE TABLE IF NOT EXISTS ck_functional_unit_iteration (
    oid                               CHAR(36)     PRIMARY KEY,
    master_oid                        CHAR(36)     NOT NULL,
    revision                          VARCHAR(10)  NOT NULL DEFAULT 'A',
    iteration                         INTEGER      NOT NULL DEFAULT 1,
    -- 功能特有字段
    functional_category               VARCHAR(64),
    requirement_source                TEXT,
    allocation_status                 VARCHAR(32)  NOT NULL DEFAULT 'UNALLOCATED',
    -- 版本控制通用字段
    checked_out                       BOOLEAN      NOT NULL DEFAULT FALSE,
    checked_out_by                    VARCHAR(100),
    checked_out_comment               VARCHAR(500),
    latest                            BOOLEAN      NOT NULL DEFAULT TRUE,
    derived_from_oid                  CHAR(36),
    derived_at                        TIMESTAMP,
    view_code                         VARCHAR(50),
    status_code                       VARCHAR(50),
    lifecycle_template_iteration_oid  CHAR(36),
    version_sort                      INTEGER      NOT NULL DEFAULT 0,
    branch_id                         VARCHAR(64),
    delete_mark                       BOOLEAN      NOT NULL DEFAULT FALSE,
    -- 租户与审计
    tenant_oid                        CHAR(36),
    creator                           VARCHAR(100),
    created_at                        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater                           VARCHAR(100),
    updated_at                        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (master_oid) REFERENCES ck_functional_unit(oid) ON DELETE CASCADE
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_fui_master    ON ck_functional_unit_iteration(master_oid);
CREATE INDEX IF NOT EXISTS idx_fui_latest    ON ck_functional_unit_iteration(master_oid, latest);
CREATE INDEX IF NOT EXISTS idx_fui_view      ON ck_functional_unit_iteration(view_code);
CREATE INDEX IF NOT EXISTS idx_fui_category  ON ck_functional_unit_iteration(functional_category);
CREATE INDEX IF NOT EXISTS idx_fui_alloc     ON ck_functional_unit_iteration(allocation_status);

-- 唯一约束：同一个master下revision+iteration唯一
CREATE UNIQUE INDEX IF NOT EXISTS uk_fui_version
    ON ck_functional_unit_iteration(master_oid, revision, iteration);
```

### 3.3 ck_functional_structure_link（功能分解链接）

```sql
CREATE TABLE IF NOT EXISTS ck_functional_structure_link (
    oid                      CHAR(36)    PRIMARY KEY,
    parent_iteration_oid     CHAR(36)    NOT NULL,
    child_iteration_oid      CHAR(36)    NOT NULL,
    quantity                 DECIMAL(20,6) DEFAULT 1,
    unit_of_measure          VARCHAR(32),
    link_type                VARCHAR(32) NOT NULL DEFAULT 'DECOMPOSITION',
    description              TEXT,
    sort_order               INTEGER     NOT NULL DEFAULT 0,
    tenant_oid               CHAR(36),
    creator                  VARCHAR(100),
    created_at               TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater                  VARCHAR(100),
    updated_at               TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (parent_iteration_oid) REFERENCES ck_functional_unit_iteration(oid) ON DELETE CASCADE,
    FOREIGN KEY (child_iteration_oid)  REFERENCES ck_functional_unit_iteration(oid) ON DELETE CASCADE,

    -- 同一父子对不能有重复的链接类型
    CONSTRAINT uk_func_link UNIQUE (parent_iteration_oid, child_iteration_oid, link_type)
);

CREATE INDEX IF NOT EXISTS idx_fsl_parent ON ck_functional_structure_link(parent_iteration_oid);
CREATE INDEX IF NOT EXISTS idx_fsl_child  ON ck_functional_structure_link(child_iteration_oid);
```

### 3.4 ck_functional_physical_mapping（功能-物理映射）

```sql
CREATE TABLE IF NOT EXISTS ck_functional_physical_mapping (
    oid                         CHAR(36)    PRIMARY KEY,
    function_iteration_oid      CHAR(36)    NOT NULL,
    part_iteration_oid          CHAR(36)    NOT NULL,
    mapping_type                VARCHAR(32) NOT NULL DEFAULT 'SATISFIES',
    allocation_description      TEXT,
    allocation_ratio            DECIMAL(5,2),
    tenant_oid                  CHAR(36),
    creator                     VARCHAR(100),
    created_at                  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater                     VARCHAR(100),
    updated_at                  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (function_iteration_oid) REFERENCES ck_functional_unit_iteration(oid) ON DELETE CASCADE,
    FOREIGN KEY (part_iteration_oid)      REFERENCES ck_part_iteration(oid) ON DELETE CASCADE,

    -- 同一个功能-部件对不能有重复映射
    CONSTRAINT uk_func_part_map UNIQUE (function_iteration_oid, part_iteration_oid, mapping_type)
);

CREATE INDEX IF NOT EXISTS idx_fpm_function  ON ck_functional_physical_mapping(function_iteration_oid);
CREATE INDEX IF NOT EXISTS idx_fpm_part      ON ck_functional_physical_mapping(part_iteration_oid);
```

### 3.5 ck_functional_baseline（功能基线）

```sql
CREATE TABLE IF NOT EXISTS ck_functional_baseline (
    oid              CHAR(36)    PRIMARY KEY,
    name             VARCHAR(255) NOT NULL,
    code             VARCHAR(64)  NOT NULL,
    description      TEXT,
    baseline_type    VARCHAR(32)  NOT NULL,        -- FUNCTIONAL / ALLOCATED
    product_oid      CHAR(36),
    product_type     VARCHAR(64),
    view_code        VARCHAR(50),
    snapshot_data    JSONB        NOT NULL,         -- 完整快照数据
    status           VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',  -- DRAFT/APPROVED/FROZEN/SUPERSEDED
    baseline_date    TIMESTAMP,
    approved_by      CHAR(36),
    approved_at      TIMESTAMP,
    tenant_oid       CHAR(36),
    creator          VARCHAR(100),
    created_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater          VARCHAR(100),
    updated_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_fb_product     ON ck_functional_baseline(product_oid);
CREATE INDEX IF NOT EXISTS idx_fb_type        ON ck_functional_baseline(baseline_type);
CREATE INDEX IF NOT EXISTS idx_fb_code        ON ck_functional_baseline(code);
```

### 3.6 snapshotData JSONB 结构说明

```json
{
  "baselineVersion": "1.0",
  "createdAt": "2026-07-23T10:00:00Z",
  "functionalUnits": [
    {
      "unitOid": "uuid-xxx",
      "iterationOid": "uuid-yyy",
      "name": "动力系统",
      "number": "FUNC-001",
      "revision": "A",
      "iteration": 3,
      "typeDefinitionCode": "FUNC_SYS",
      "functionalCategory": "核心功能",
      "allocationStatus": "FULLY_ALLOCATED",
      "statusCode": "RELEASED"
    }
  ],
  "structureLinks": [
    {
      "parentIterationOid": "uuid-xxx",
      "childIterationOid": "uuid-zzz",
      "quantity": 1,
      "linkType": "DECOMPOSITION"
    }
  ],
  "physicalMappings": [
    {
      "functionIterationOid": "uuid-yyy",
      "partIterationOid": "uuid-aaa",
      "mappingType": "SATISFIES",
      "allocationRatio": 100
    }
  ]
}
```

---

## 4. API接口设计

### 4.1 FunctionalUnit CRUD

遵循CK-PLM现有Part的RESTful风格，路径为 `/api/v1/functional-units`。

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/v1/functional-units` | 创建功能单元（含初始iteration） |
| `GET` | `/api/v1/functional-units/{oid}` | 获取功能单元详情（latest iteration） |
| `PUT` | `/api/v1/functional-units/{oid}` | 更新功能单元Master属性 |
| `DELETE` | `/api/v1/functional-units/{oid}` | 软删除功能单元 |
| `GET` | `/api/v1/functional-units` | 分页查询功能单元列表 |

#### 创建功能单元

```
POST /api/v1/functional-units
Content-Type: application/json

{
  "name": "动力输出功能",
  "number": "FUNC-001",
  "description": "提供整车动力输出，满足最大功率≥150kW",
  "containerOid": "product-line-oid-xxx",
  "containerType": "ProductLine",
  "typeDefinitionCode": "FUNC_SYS",
  "iteration": {
    "functionalCategory": "核心功能",
    "requirementSource": "客户SOR第3.1节",
    "allocationStatus": "UNALLOCATED"
  }
}
```

### 4.2 功能结构树查询

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/v1/functional-units/{oid}/structure` | 获取功能单元的子节点（一层展开） |
| `GET` | `/api/v1/functional-units/{oid}/structure/tree` | 获取功能单元完整功能树（递归） |
| `POST` | `/api/v1/functional-units/{oid}/structure` | 添加子功能节点 |
| `DELETE` | `/api/v1/functional-structure-links/{linkOid}` | 删除链接 |
| `PUT` | `/api/v1/functional-structure-links/{linkOid}` | 修改链接属性（用量、排序等） |

### 4.3 功能-物理映射

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/v1/functional-units/{oid}/mappings` | 查看功能单元到部件的映射列表 |
| `POST` | `/api/v1/functional-units/{oid}/mappings` | 添加功能→部件映射 |
| `DELETE` | `/api/v1/functional-mappings/{mappingOid}` | 删除映射 |
| `GET` | `/api/v1/parts/{partOid}/function-mappings` | 反向查询：部件被哪些功能引用 |
| `GET` | `/api/v1/functional-units/{oid}/mappings/matrix` | 导出功能-物理追溯矩阵 |

### 4.4 基线管理

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/v1/functional-baselines` | 创建功能基线快照 |
| `GET` | `/api/v1/functional-baselines` | 查询基线列表（按产品筛选） |
| `GET` | `/api/v1/functional-baselines/{oid}` | 查看基线详情（含快照数据） |
| `GET` | `/api/v1/functional-baselines/{oid}/compare` | 基线对比（与指定基线对比） |
| `PUT` | `/api/v1/functional-baselines/{oid}/approve` | 批准基线 |
| `PUT` | `/api/v1/functional-baselines/{oid}/freeze` | 冻结基线 |
| `PUT` | `/api/v1/functional-baselines/{oid}/supersede` | 废止基线 |

---

## 5. 服务层设计

### 5.1 核心Service接口

```
cn.ck.plm.functional.service
├── api/
│   ├── FunctionalUnitService          // 功能单元CRUD + 结构树
│   ├── FunctionalUnitIterationService // 功能单元Iteration管理
│   ├── FunctionalStructureLinkService // 功能结构链接
│   ├── FunctionalPhysicalMappingService // 功能-物理映射
│   └── FunctionalBaselineService      // 功能基线管理
└── impl/
    ├── FunctionalUnitServiceImpl
    ├── FunctionalUnitIterationServiceImpl
    ├── FunctionalStructureLinkServiceImpl
    ├── FunctionalPhysicalMappingServiceImpl
    └── FunctionalBaselineServiceImpl
```

### 5.2 复用现有基础设施

| 现有Service | 复用方式 |
|-------------|---------|
| `MasterService` / `MasterServiceImpl` | FunctionalUnit 直接使用 `createInitialIteration()` / `createDerivedIteration()` |
| `IterationService` / `IterationServiceImpl` | FunctionalUnitIteration 使用 `checkOut()` / `checkIn()` / `promoteLifecycle()` |
| `IBADataService` | 直接用 `save()` / `restore()` / `deleteByEntity()` |
| `NumberRuleService` | 功能单元编号按编码规则自动生成 |

### 5.3 关键业务逻辑

#### 创建功能基线（FunctionalBaselineService.createBaseline）

```java
/**
 * 创建功能基线 —— 将当前功能架构状态序列化为快照。
 *
 * 流程：
 * 1. 查询指定产品上下文下的所有功能单元（含latest iteration）
 * 2. 查询所有功能结构链接
 * 3. 查询所有功能-物理映射
 * 4. 序列化为基线快照JSON
 * 5. 插入 ck_functional_baseline 记录
 *
 * @param productOid  产品线/型号OID
 * @param baselineType FUNCTIONAL / ALLOCATED
 * @param viewCode     视图编码
 */
FunctionalBaseline createBaseline(String productOid, String baselineType,
                                   String viewCode, String name, String description);
```

#### 基线对比（FunctionalBaselineService.compare）

```java
/**
 * 基线对比 —— 比较两个功能基线之间的差异。
 *
 * 返回：
 * - addedUnits:      新增的功能单元
 * - removedUnits:    移除的功能单元
 * - changedUnits:    变更的功能单元（版本/状态变化）
 * - addedMappings:   新增的物理映射
 * - removedMappings: 移除的物理映射
 */
BaselineDiff compare(String baselineOid1, String baselineOid2);
```

---

## 6. 与现有架构的整合

### 6.1 与TypeDefinition（软类型）的整合

FunctionalUnit通过 `typeDefinitionCode` 关联软类型，可以利用现有软类型体系的全部能力：

- **分类扩展**：军工功能单元类型 → `FUNC_MIL`，汽车功能单元类型 → `FUNC_SYS` / `FUNC_SUB` / `FUNC_CTQ`
- **IBA动态属性**：不同功能类型挂不同IBA属性
  - `FUNC_MIL` → 战术技术指标、GJB引用章节、密级等
  - `FUNC_SAFETY` → ASIL等级、安全目标ID、FTTI等
- **生命周期模板**：不同类型绑定不同生命周期
  - 一般功能单元：DRAFT → IN_WORK → RELEASED
  - 安全功能：DRAFT → REVIEW → APPROVED → RELEASED（需多级审批）
- **版本规则 + 编码规则**：同TypeDefinition统一管理

数据库初始化配置示例：

```sql
-- 注册功能单元预置软类型
INSERT INTO ck_type_definition (oid, code, name, source, type_kind, root_type_code, description, sort_order, enabled)
VALUES
  (uuid_generate_v4(), 'FUNC_SYS',    '系统级功能', 'OOTB', 'SOFT_TYPE', 'FUNC_SYS',    '产品顶层功能定义', 1, TRUE),
  (uuid_generate_v4(), 'FUNC_SUB',    '子系统功能', 'OOTB', 'SOFT_TYPE', 'FUNC_SYS',    '二级功能分解',      2, TRUE),
  (uuid_generate_v4(), 'FUNC_CTQ',    '关键功能特性', 'OOTB', 'SOFT_TYPE', 'FUNC_SYS',   '需独立追溯的功能',  3, TRUE),
  (uuid_generate_v4(), 'FUNC_SAFETY', '安全功能',    'OOTB', 'SOFT_TYPE', 'FUNC_CTQ',   'ISO 26262安全功能', 4, TRUE),
  (uuid_generate_v4(), 'FUNC_PERF',   '性能指标功能', 'OOTB', 'SOFT_TYPE', 'FUNC_CTQ',   '量化性能指标',      5, TRUE),
  (uuid_generate_v4(), 'FUNC_MIL',    '战术技术指标', 'OOTB', 'SOFT_TYPE', 'FUNC_SYS',   '军工六性等指标',    6, TRUE);
```

### 6.2 与View（视图）机制的整合

FunctionalUnitIteration 继承 IterationEntity，天然支持View机制：

```
同一功能单元的同一revision下，可在不同View下存在不同iteration
  - Design View → 显示设计中的功能架构
  - Manufacturing View → 显示制造视角的功能架构

View过渡规则（ViewTransition）同样适用：
  功能基线冻结后，Design View切换 → 触发新的iteration创建
```

### 6.3 与产品线的Container关系

FunctionalUnit 通过 `containerOid + containerType` 关联产品上下文：

- 军工场景：`containerOid = 装备型号OID, containerType = ProductModel`
- 汽车场景：`containerOid = 车型OID, containerType = ProductModel`

### 6.4 包结构建议

```
cn.ck.plm.functional
├── config/         // 功能模块配置（如Spring Bean）
├── controller/     // REST API Controller
├── dto/            // 请求/响应DTO
│   ├── FunctionalUnitCreateDTO
│   ├── FunctionalUnitResponseDTO
│   ├── StructureTreeNodeDTO
│   ├── MappingCreateDTO
│   ├── MappingResponseDTO
│   ├── BaselineCreateDTO
│   ├── BaselineDiffDTO
│   └── ...
├── entity/         // 数据实体
│   ├── FunctionalUnit
│   ├── FunctionalUnitIteration
│   ├── FunctionalStructureLink
│   ├── FunctionalPhysicalMapping
│   └── FunctionalBaseline
├── mapper/         // MyBatis Mapper
│   ├── FunctionalUnitMapper
│   ├── FunctionalUnitIterationMapper
│   ├── FunctionalStructureLinkMapper
│   ├── FunctionalPhysicalMappingMapper
│   └── FunctionalBaselineMapper
└── service/
    ├── api/        // 服务接口
    │   ├── FunctionalUnitService
    │   ├── FunctionalUnitIterationService
    │   ├── FunctionalStructureLinkService
    │   ├── FunctionalPhysicalMappingService
    │   └── FunctionalBaselineService
    └── impl/       // 服务实现
        ├── FunctionalUnitServiceImpl
        ├── FunctionalUnitIterationServiceImpl
        ├── FunctionalStructureLinkServiceImpl
        ├── FunctionalPhysicalMappingServiceImpl
        └── FunctionalBaselineServiceImpl
```

---

## 7. 业务场景覆盖分析

### 7.1 军工构型管理场景

| 业务操作 | 系统实现 |
|---------|---------|
| 方案阶段建立功能架构 | 创建FunctionalUnit树形结构（FUNC_MIL类型） |
| 定义功能单元的技术指标 | 通过IBA属性记录（如"最大射程≥300km"） |
| 建立功能基线 | 调用 `POST /api/v1/functional-baselines` ，type=FUNCTIONAL |
| 功能基线审批后冻结 | 调用 `PUT .../freeze`，snapshotData不可变 |
| 将功能需求分配到子系统 | 创建FunctionalPhysicalMapping（type=ALLOCATED_TO），allocationRatio记录分配比例 |
| 建立分配基线 | 创建第二个Baseline，type=ALLOCATED |
| 后续设计变更对比 | 调用 `compare` 接口对比新老基线差异 |
| 构型状态记录 | 基线status流转：DRAFT → APPROVED → FROZEN → SUPERSEDED |

### 7.2 汽车规划BOM场景

| 业务操作 | 系统实现 |
|---------|---------|
| 概念阶段定义F-BOM | 创建FunctionalUnit树（FUNC_SYS → FUNC_SUB → FUNC_CTQ） |
| 建立功能-物理追溯矩阵 | 批量创建FunctionalPhysicalMapping（type=SATISFIES） |
| 查询"还有哪些功能未分配" | 查询 allocationStatus IN ('UNALLOCATED', 'PARTIAL') 的功能单元 |
| F-BOM到E-BOM的转化 | 基于PhysicalMapping，可以在前端展示功能→部件的对应视图 |
| 安全功能独立管理 | FUNC_SAFETY类型，IBA属性记录ASIL等级和FTTI |
| 多车型功能复用 | 不同ProductModel下的FunctionalUnit可通过derivedFrom复制 |

### 7.3 追溯矩阵查询示例

```sql
-- 查询"动力系统"功能下的完整追溯链（功能→部件）
SELECT
  fu.name AS function_name,
  fu.number AS function_number,
  fui.allocation_status,
  p.name AS part_name,
  p.number AS part_number,
  fpm.mapping_type,
  fpm.allocation_ratio
FROM ck_functional_unit fu
JOIN ck_functional_unit_iteration fui ON fui.master_oid = fu.oid AND fui.latest = TRUE
JOIN ck_functional_physical_mapping fpm ON fpm.function_iteration_oid = fui.oid
JOIN ck_part_iteration pi ON pi.oid = fpm.part_iteration_oid AND pi.latest = TRUE
JOIN ck_part p ON p.oid = pi.master_oid
WHERE fu.container_oid = '{productOid}'
ORDER BY fu.name, p.name;
```

---

## 8. 实施路线图

### Phase 1 — 核心模型落地（本次）

- [ ] 创建 `FunctionalUnit` + `FunctionalUnitIteration` 实体及MyBatis Mapper
- [ ] 创建4张数据库表（DDL追加到schema.sql）
- [ ] 实现FunctionalUnit CRUD Service（复用MasterService/IterationService）
- [ ] 预置功能单元软类型（TypeDefinition初始化数据）
- [ ] FunctionalUnitController暴露基础CRUD API

### Phase 2 — 功能结构与映射（下次）

- [ ] 创建 `FunctionalStructureLink` + `FunctionalPhysicalMapping` 实体及Mapper
- [ ] 实现功能结构树的CRUD API
- [ ] 实现功能-物理映射的CRUD API
- [ ] 前端：功能结构树组件（参考现有产品线树形组件）

### Phase 3 — 基线管理

- [ ] 创建 `FunctionalBaseline` 实体及Mapper
- [ ] 实现基线创建/审批/冻结/对比API
- [ ] 前端：基线管理页面

### Phase 4 — 高级特性

- [ ] 功能架构与需求管理模块集成（需求→功能→部件全链路追溯）
- [ ] 功能BOM与工程BOM的可视化对比视图
- [ ] 基于基线的变更影响分析

---

## 附录

### A. 与Windchill/Windchill MPMLink的对标

| Windchill 概念 | CK-PLM 对应 |
|---------------|-------------|
| WTPart (with type=Functional) | FunctionalUnit + FunctionalUnitIteration |
| WTPartUsageLink (functional decomposition) | FunctionalStructureLink |
| MPMLink Allocation | FunctionalPhysicalMapping |
| Baseline | FunctionalBaseline |
| SoftType (ModelClass) | TypeDefinition |
| IBA | IBA + IBAExtensible |
| View | View（复用） |

### B. 与团队现有Part模型的差异总结

| 维度 | Part | FunctionalUnit |
|------|------|---------------|
| 语义 | 物理实体（"是什么"） | 功能描述（"做什么"） |
| 文件关联 | 可以有主文件+附件 | 通常无文件（或关联需求文档） |
| 层级关系 | PartUsageLink（待实现） | FunctionalStructureLink |
| 可装配性 | 是（构成物理BOM） | 否（构成功能F-BOM） |
| 软类型 | 硬件/软件/服务等 | 系统功能/子系统功能/安全功能等 |
| 国际对标 | WTPartMaster/WTPart | WTPart（Functional类型）/ 需求管理工具 |
