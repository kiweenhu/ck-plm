# CK-PLM × Cadence OrCAD 集成

路线 A（客户端插件）首批实现：Capture Tcl 菜单 + 桥接 CLI + CK-PLM REST 三段链路。

## 架构

```
OrCAD Capture (Tcl 菜单: 检出/检入/上传/BOM同步)
        │ exec 调用
        ▼
ckplm_cli.py (本地桥, 零依赖, 纯标准库)
        │ HTTP Bearer token
        ▼
CK-PLM /api/ecad/** (EcadSchematicController)
        │
        ├── ck_ecad_schematic / ck_ecad_schematic_iteration  版本与检出锁
        ├── ck_design_instance                               BOM 落地
        ├── ck_file (FileCategory.CAD)                       .DSN 物理文件
        └── EcadSchematicCheckoutProvider → 统一检出体系（"我的检出"可见）
```

## 对象模型（两级）

| 对象 | 表 | 文件 | 版本 |
|---|---|---|---|
| 电子设计项目（.opj 容器） | `ck_ecad_project`（已有） | opj 文件暂随项目附件管理 | 项目阶段 |
| 原理图对象（.DSN） | `ck_ecad_schematic` + `ck_ecad_schematic_iteration`（新增） | ckfile_oid → CAD 分类 | A.1 / A.2 / B.1 … |

原理图版本流转与文档一致：检出派生新迭代（iteration+1）→ 上传 .DSN → 检入解锁；
新建大版本 revision+1、iteration 归 1（v1 实现为字母递增，未接 VersionRuleService）。

## 安装与使用

### 1. CK-PLM 侧

schema.sql 已含三张新表（`ck_ecad_schematic` / `ck_ecad_schematic_iteration` / `ck_ecad_bom_sync_log`），
服务重启自动建表（CREATE TABLE IF NOT EXISTS）。无需额外配置。

可选：在编码规则（ck_number）里配置 `ECAD_SCHEMATIC-NUM` 规则，原理图编号自动生成；
未配置时回退 `SCH-<时间戳>`。

### 2. 工程师机器（装了 Capture）

```bash
# 一次性配置
python ckplm_cli.py config --server http://<plm-host>:<port> --username <账号>
python ckplm_cli.py config --password <密码>     # 密码仅存本机 ~/.ckplm/cli.json
python ckplm_cli.py login                        # 验证连通性
```

Capture 里 `Tools → Tcl/Tk Scripts → Run Script...` 运行 `capture-tcl/ckplm_menu.tcl`，
菜单栏出现 **CK-PLM** 菜单。

### 3. 日常操作流

```
CK-PLM 菜单: 检出          → CLI checkout（若未配置设计名映射会弹窗要编号）
CK-PLM 菜单: 上传设计...    → 选 .DSN 上传（CLI upload-dsn）
CK-PLM 菜单: BOM 同步...    → 选 Capture 导出的 BOM（Tools → Bills of Materials 导出）
                             CLI 解析后 POST bom-sync，返回 新增/移除/变化/未匹配
CK-PLM 菜单: 检入          → CLI checkin，版本生效
```

不用 Capture 的场景（例如 BOM 工程师、评审人）直接用 CLI：

```bash
python ckplm_cli.py bom-sync --schematic SCH-2026-0001 --bom design.bom --map reference=0,value=1,quantity=2,footprint=3,partnumber=4
```

## REST 接口清单

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/ecad/projects` / `/by-code/{code}` / `/{oid}` / `/{oid}/schematics` | 项目读取 |
| POST | `/api/ecad/schematics?projectId=...` | 创建原理图对象 |
| GET | `/api/ecad/schematics/{oid}` · `/by-number/{number}` | 详情 |
| GET | `/{oid}/iterations` · `/{oid}/iterations/latest` | 版本历史/最新 |
| POST | `/{oid}/checkout` · `/{oid}/checkin` · `/{oid}/undo-checkout` · `/{oid}/revisions` | 版本流转 |
| POST | `/{oid}/dsn` (multipart) | 上传 .DSN（须检出） |
| POST | `/{oid}/bom-sync` (BomSyncItem[]) | BOM 同步，返回 BomSyncResult |
| GET | `/{oid}/instances` | 设计实例 |
| GET | `/api/ckfiles/{ckfileOid}/download` | 下载 .DSN（已有通用接口） |

鉴权：`POST /api/auth/login` 拿 token → `Authorization: Bearer <token>`。

## BOM 匹配规则

- `partNumber` 列非空 → 精确匹配 `ck_part.number`
- 否则 `value` 列 → 精确匹配 `ck_part.number`（Capture BOM 常把 Value 填成物料编号）
- 匹配不到的行进 `unmatched`，前端可引导建新部件
- 差异（新增/移除/变化）写 `ck_ecad_bom_sync_log.diff_json`，可对接"一键发起 ECR"

## 已知限制（v1）

1. **Tcl 菜单 API 未在真实 Capture 上验证**——适配点集中在 `ckplm::register_menu`
   与 `ckplm::current_design` 两个过程，不同 17.x 版本命令名可能有出入，跑了不对就改这两处
2. 大版本号走字母递增，未接 VersionRuleService 编号规则
3. BOM 同步不写 `ck_ecad_project_design_link`（原理图与项目走直连 FK，link 表留给 PCB 等多设计场景）
4. .opj 文件本身暂未纳入版本管理（项目容器对象的文件管理排下一批）
5. 差异 → ECR 的自动联动未做（数据已就位，缺一个前端按钮调 change 模块）
