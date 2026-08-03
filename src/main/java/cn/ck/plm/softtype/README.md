# 业务配置中心 (SoftType Module)

## 概述

业务配置中心是 CK-PLM 系统的核心模块，负责管理软类型（SoftType）元数据，包括：
- **类型定义 (TypeDefinition)** - 业务对象类型的元数据
- **属性定义 (AttributeDefinition)** - 业务对象字段的元数据
- **页面布局 (PageLayout)** - 业务对象的 UI 页面配置
- **IBA (Instance-Based Attributes)** - 实例级动态属性扩展

## 核心特性

### 1. 软类型系统 (SoftType)
借鉴 Windchill PDM 的软类型架构，将业务对象（如产品系列、文档等）的元数据与代码实体分离存储在数据库中，支持运行时配置而无需修改代码。

### 2. 自动初始化
应用启动时自动执行初始化器，按顺序完成：
```
@Order(1) PageLayoutMigration  → 数据库迁移
@Order(2) TypeDefinitionInitializer  → 注册类型定义
@Order(3) AttributeInitializer  → 扫描字段并注册属性
@Order(4) PageLayoutInitializer  → 创建默认页面布局
```

### 3. 字段类型映射
系统自动根据 Java 字段类型映射 UI 组件：

| Java 类型 | 数据库类型 | UI 组件 |
|-----------|-----------|---------|
| Boolean | BOOLEAN | switch |
| Integer/Long | INTEGER | input-number |
| Float/Double/BigDecimal | FLOAT | input |
| LocalDate | DATE | date-picker |
| LocalDateTime | DATETIME | date-picker |
| String (description) | STRING | textarea |
| String (*Oid) | REFERENCE | select |
| String (parentOid) | STRING | tree-select |
| 其他 String | STRING | input |

## 模块结构

```
cn.ck.plm.softtype/
├── config/                      # 初始化配置
│   ├── TypeDefinitionInitializer.java   # 类型定义初始化
│   ├── AttributeInitializer.java        # 属性定义初始化
│   ├── PageLayoutInitializer.java       # 页面布局初始化
│   └── PageLayoutMigration.java         # 页面布局迁移
├── entity/                      # 实体类
│   ├── TypeDefinition.java              # 类型定义实体
│   ├── AttributeDefinition.java         # 属性定义实体
│   ├── PageLayout.java                  # 页面布局实体
│   ├── IBA.java                        # IBA 属性实体
│   └── TypeIBA.java                    # 类型 IBA 配置
├── mapper/                      # 数据访问层
│   ├── TypeDefinitionMapper.java
│   ├── AttributeDefinitionMapper.java
│   ├── PageLayoutMapper.java
│   ├── IBAMapper.java
│   └── IBADataMapper.java
├── service/                    # 业务服务层
│   ├── api/                    # 服务接口
│   └── impl/                   # 服务实现
└── controller/                 # REST 控制器
    ├── TypeDefinitionController.java
    ├── AttributeDefinitionController.java
    ├── PageLayoutController.java
    └── IBAController.java
```

## 核心数据表

| 表名 | 说明 |
|------|------|
| `ck_type_definition` | 类型定义表，存储业务对象类型元数据 |
| `ck_attribute_definition` | 属性定义表，存储字段元数据 |
| `ck_type_page_layout` | 页面布局表，存储各实体的页面配置 |
| `ck_iba` | IBA 定义表，存储动态属性定义 |
| `ck_iba_data` | IBA 数据表，存储实例的动态属性值 |
| `ck_type_iba` | 类型-IBA 关联表 |

## REST API

### 类型定义
- `GET /api/type-definitions` - 获取所有类型定义
- `GET /api/type-definitions/{code}` - 按代码获取类型
- `POST /api/type-definitions` - 创建类型定义
- `PUT /api/type-definitions/{code}` - 更新类型定义

### 属性定义
- `GET /api/attribute-definitions` - 获取所有属性定义
- `GET /api/attribute-definitions/{entityCode}` - 按实体获取属性
- `POST /api/attribute-definitions` - 创建属性定义
- `PUT /api/attribute-definitions/{id}` - 更新属性定义

### 页面布局
- `GET /api/page-layouts` - 获取所有布局
- `GET /api/page-layouts/{entityCode}/{operation}` - 获取特定布局
- `PUT /api/page-layouts/{id}` - 更新布局

### IBA
- `GET /api/ibas` - 获取所有 IBA 定义
- `POST /api/ibas` - 创建 IBA
- `GET /api/iba-data/{entityOid}` - 获取实例 IBA 数据
- `PUT /api/iba-data/{entityOid}` - 更新实例 IBA 数据

## 扩展方式

### 新增业务实体
1. 在 `TypeDefinitionInitializer.ENTITY_META` 中添加实体映射
2. 在 `AttributeInitializer.ENTITY_CLASSES` 中添加实体类
3. 在 `PageLayoutInitializer` 中添加默认页面布局

### 新增 IBA 属性
1. 在 `IBAExtensible` 接口实现类中添加 IBA 定义
2. 或通过后台管理界面动态创建

## OOTB 预置类型

| 类型 Code | 显示名 | 说明 |
|-----------|--------|------|
| CK_PRODUCT_LINE | 产品系列 | 产品系列管理，关联产品、团队与缩略图 |
| CK_PRODUCT_MODEL | 产品型号 | 隶属于产品系列，拥有独立团队和研发阶段 |
| CK_DOCUMENT | 文档 | 文档复合对象，支持版本控制、文件存储与阶段关联 |

## 权限说明

所有 API 均需登录认证，超级管理员可在后台管理界面进行配置操作。
