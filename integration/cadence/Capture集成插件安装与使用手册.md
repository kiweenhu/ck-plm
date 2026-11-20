# CK-PLM × OrCAD Capture 集成插件 安装与使用手册

**适用版本**：CK-PLM v0.3.0+ / 插件 v1（路线 A：客户端插件）
**适用对象**：PLM 管理员（服务端准备）、电子研发工程师（客户端安装与日常使用）
**配套文件**：`ckplm_cli.py`（桥接 CLI）、`capture-tcl/ckplm_menu.tcl`（Capture 菜单插件）

---

## 1. 概述

本插件在 OrCAD Capture 菜单栏注册 **CK-PLM** 菜单，工程师不离开原理图环境即可完成
设计文件的检出 / 检入 / 版本管理，并把 Capture 导出的 BOM 同步到 PLM 与部件库对账。

```
OrCAD Capture（CK-PLM 菜单）
        │  exec 调用
        ▼
ckplm_cli.py（本机桥接，纯 Python 标准库，零第三方依赖）
        │  HTTPS + Bearer token
        ▼
CK-PLM 服务端 /api/ecad/**（ECAD 模块）
        ├── ck_ecad_schematic / _iteration   原理图对象与版本迭代
        ├── ck_design_instance               BOM 实例落地
        ├── ck_ecad_bom_sync_log             同步差异留痕
        └── 统一检出体系（检出后在 PLM"我的检出"中可见）
```

**对象模型（两级）**

| 对象 | 说明 | 文件 | 版本模型 |
|---|---|---|---|
| 电子设计项目 | .opj 工程容器 | 随项目附件管理 | 项目阶段 |
| 原理图对象 | .DSN 设计文件 | ck_file（CAD 分类） | 大版本 A/B/C × 小版本 .1/.2/.3 |

版本流转规则：**检出** = 派生新迭代（小版本 +1，锁定）；**检入** = 提交文件、解锁；
**新建大版本** = revision +1、小版本归 1。检出期间其他人无法修改，全部检出可在 PLM
"我的检出"页面统一查看与释放。

---

## 2. 环境要求

| 端 | 要求 |
|---|---|
| CK-PLM 服务端 | v0.3.0 及以上（含 ecad 模块），PostgreSQL |
| 工程师机器 | OrCAD Capture 17.x（原理图编辑环境） |
| 工程师机器 | Python 3.8+（仅标准库，无需 pip install 任何包） |
| 网络 | 工程师机器可访问 CK-PLM 服务地址 |

---

## 3. 安装

### 3.1 服务端（PLM 管理员，一次性）

1. **部署后端**。三张新表（`ck_ecad_schematic`、`ck_ecad_schematic_iteration`、
   `ck_ecad_bom_sync_log`）已在 schema.sql 中，服务启动时自动建表
   （CREATE TABLE IF NOT EXISTS），无需手工执行 SQL。

2. **配置原理图编号规则（可选但建议）**。
   在编码规则中添加规则键 **`ECAD_SCHEMATIC-NUM`**（例如 `SCH-{YYYY}-{0001}`）。
   未配置时原理图编号回退为 `SCH-<时间戳>`，不影响功能，但正式使用建议配置，
   便于人工识别。

3. **创建设计项目**（可选）。原理图对象可以独立存在，也可以挂在
   "电子设计项目"（ck_ecad_project）下。若需要项目级组织，先在 PLM 页面创建项目。

### 3.2 工程师机器（每人一次）

**第 1 步：获取插件文件**

将 `integration/cadence/` 整个目录复制到本机任意固定路径，例如：

```
D:\ckplm-integration\
├── ckplm_cli.py
└── capture-tcl\ckplm_menu.tcl
```

**第 2 步：配置桥接 CLI**

```bash
# 服务器地址与账号
python ckplm_cli.py config --server http://<plm-host>:<port> --username <你的PLM账号>

# 密码单独配置（仅存本机 ~/.ckplm/cli.json，注意保管）
python ckplm_cli.py config --password <你的密码>

# 验证连通性（成功会缓存 token 到 ~/.ckplm/token.json）
python ckplm_cli.py login
python ckplm_cli.py whoami
```

`whoami` 能返回原理图列表即链路打通。token 过期后 CLI 会自动重新登录，无需手工干预。

**第 3 步：修改 Tcl 脚本中的 CLI 路径**

用文本编辑器打开 `ckplm_menu.tcl`，修改头部配置区两行：

```tcl
variable cliPath    "D:/ckplm-integration/ckplm_cli.py"   ;# 改成第 1 步的实际路径
variable pythonBin  "python"                              ;# python 不在 PATH 时改成完整路径
```

> 注意路径分隔符用正斜杠 `/`，不要用反斜杠。

**第 4 步：在 Capture 中加载菜单**

启动 OrCAD Capture，执行：

> **Tools → Tcl/Tk Scripts → Run Script...** → 选择 `ckplm_menu.tcl`

菜单栏出现 **CK-PLM** 菜单（含 检出 / 检入 / 取消检出 / 上传设计 / 下载最新设计 / BOM 同步）即为成功。

**第 5 步（可选）：随 Capture 自动加载**

确认手动加载一切正常后，可将脚本放入 Capture 的 Tcl 脚本目录随启动自动加载：

```
<CDS安装目录>\capture\tclscripts\ckplm_menu.tcl
```

建议所有工程师先手动方式跑通再启用自动加载，避免脚本问题阻塞 Capture 启动。

---

## 4. 使用

### 4.1 首次使用：创建原理图对象

第一次接入某块设计的原理图时，先在 PLM 中创建对应的原理图对象：

```bash
# 独立创建
python ckplm_cli.py create --name "主控板原理图"

# 或挂在某个设计项目下（--project 传项目 oid，可在 PLM 页面复制）
python ckplm_cli.py create --name "主控板原理图" --project <项目oid>

# 指定编号（缺省按编码规则自动生成）
python ckplm_cli.py create --name "主控板原理图" --number SCH-2026-0001
```

返回 JSON 中的 `number` 就是后续所有操作的 `--schematic` 参数，建议记入团队约定
（或直接在 Capture 菜单弹窗中输入编号）。

### 4.2 日常操作流（推荐顺序）

一次典型的"修改 → 提交"闭环：

```
① CK-PLM 菜单：检出           派生新小版本并锁定，他人此时不可修改
② （在 Capture 中修改原理图）
③ CK-PLM 菜单：上传设计...     选择 .DSN 文件上传到当前迭代
④ CK-PLM 菜单：BOM 同步...     选择 Capture 导出的 BOM，与 PLM 部件库对账
⑤ CK-PLM 菜单：检入           提交文件、解除锁定，版本生效
```

要点：

- **必须先检出再上传**。未检出时上传 .DSN 会被服务端拒绝。
- **上传后不检入，文件不生效**。检入动作把当前迭代转正并释放检出锁。
- 检出后反悔了不想提交：用 **取消检出**，派生的迭代作废，不影响任何人。
- 想拉取最新设计：**下载最新设计**，CLI 会保存到临时目录并提示路径。

### 4.3 BOM 同步

**导出 BOM**：在 Capture 中执行 **Tools → Bills of Materials**，导出 CSV/BSV 文件，
保证包含位号（Reference Designator）列，其余列（数量 / 值 / 封装 / 物料编号）有则带上。

**执行同步**：CK-PLM 菜单 → BOM 同步... → 选择刚导出的文件。
CLI 自动按表头识别列（中英文表头均可识别），解析后推送 PLM 对账。

**列识别不了时手动映射**（在命令行执行）：

```bash
python ckplm_cli.py bom-sync --schematic SCH-2026-0001 --bom design.bom ^
    --map reference=0,value=1,quantity=2,footprint=3,partnumber=4
```

`--map` 的值是列序号（从 0 开始）。五个键：

| 键 | 含义 | 必需 |
|---|---|---|
| reference | 位号 | 是 |
| quantity | 数量 | 否（缺省 1） |
| value | 值/型号 | 否 |
| partnumber | 物料编号 | 否 |
| footprint | PCB 封装 | 否 |

**匹配规则与返回结果**：

- BOM 行的物料编号非空 → 按 `ck_part.number` 精确匹配
- 物料编号为空 → 用值（value）列匹配（Capture BOM 常把 Value 填成物料编号）
- 同步结果分四类返回：**新增**（PLM 中没有的位号）、**移除**（原理图删掉但 PLM 还有）、
  **变化**（数量或匹配部件不同）、**未匹配**（匹配不到部件，需建新件或标替代料）
- 差异明细写入同步日志（`ck_ecad_bom_sync_log`），在 PLM 侧可追溯，后续可对接
  "差异一键发起 ECR"

### 4.4 版本查看

```bash
# 版本历史（全部迭代）
python ckplm_cli.py iterations --schematic SCH-2026-0001

# 当前设计实例（BOM 落地结果）
python ckplm_cli.py instances --schematic SCH-2026-0001
```

### 4.5 不用 Capture 的场景

BOM 工程师、评审人等无需打开 Capture，直接用 CLI 即可完成对账与查看，
命令同 4.3 / 4.4。所有 REST 接口亦可供其他系统集成（见附录 A）。

---

## 5. 常见问题（FAQ）

**Q1：Capture 里菜单没出现？**
不同 Capture 17.x 小版本的菜单注册命令名有差异。适配点集中在脚本中的
`ckplm::register_menu` 与 `ckplm::current_design` 两个过程：前者负责菜单注册
（`RegisterAction` / `AddMenu` / `AddMenuItem`），后者负责取当前设计名。
菜单没出来改前者，取不到设计名改后者，其余逻辑不用动。可在 Capture 的
Tcl 控制台里直接执行 `ckplm::register_menu` 看报错信息定位。

**Q2：点菜单提示"找不到 python"？**
`pythonBin` 默认是 `python`，依赖 PATH。改脚本头部的 `variable pythonBin`
为完整路径，例如 `"C:/Python312/python.exe"`。

**Q3：CLI 报"未配置服务器地址"？**
先执行 3.2 第 2 步的 `config` 命令。配置文件在 `~/.ckplm/cli.json`，
可直接查看/手工编辑。

**Q4：登录失败 / token 过期？**
执行 `python ckplm_cli.py login` 强制重新登录。若仍失败，核对服务器地址、
账号密码，以及服务端 `/api/auth/login` 是否可达。

**Q5：上传 .DSN 被拒绝？**
几乎都是未检出或锁被他人持有。先执行检出；若提示已被他人检出，
到 PLM"我的检出"页面协调释放。

**Q6：编号变成了 SCH-1737xxxx 这种时间戳？**
服务端未配置 `ECAD_SCHEMATIC-NUM` 编码规则（见 3.1 第 2 步）。已有对象不受影响，
新对象配置规则后自动走规则编号。

**Q7：BOM 同步大量"未匹配"？**
检查 Capture BOM 的物料编号列是否真的填了 PLM 部件编号。若表头不在 CLI 识别
范围内（如自定义导出模板），用 `--map` 显式指定列位。匹配不到的行不会写入
PLM，属预期行为——先补部件库再同步。

**Q8：脚本重跑会不会出现两个 CK-PLM 菜单？**
不会。`register_menu` 会先移除旧菜单再注册，重复执行安全。

---

## 6. 已知限制（v1）

1. Tcl 菜单 API 未在真实 Capture 环境验证过，适配点集中（见 Q1），首装建议
   管理员陪同调通一次
2. 大版本号当前为字母递增，未接入编码规则服务（VersionRuleService）
3. .opj 工程文件本身暂未纳入版本管理（项目容器对象的文件管理排下一批）
4. BOM 差异 → ECR 的自动发起未做（差异与日志数据已就位，缺前端入口）
5. 密码明文存于本机 `~/.ckplm/cli.json`，请勿在公共终端配置后遗留

---

## 附录 A：REST 接口清单（供其他系统集成）

鉴权：`POST /api/auth/login` 获取 token，后续请求带 `Authorization: Bearer <token>`。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/ecad/projects` · `/by-code/{code}` · `/{oid}` · `/{oid}/schematics` | 设计项目读取 |
| POST | `/api/ecad/schematics?projectId=...` | 创建原理图对象 |
| GET | `/api/ecad/schematics/{oid}` · `/by-number/{number}` | 原理图详情 |
| GET | `/{oid}/iterations` · `/{oid}/iterations/latest` | 版本历史 / 最新迭代 |
| POST | `/{oid}/checkout` · `/{oid}/checkin` · `/{oid}/undo-checkout` · `/{oid}/revisions` | 版本流转 |
| POST | `/{oid}/dsn`（multipart，字段名 `file`） | 上传 .DSN（须处于检出态） |
| POST | `/{oid}/bom-sync`（BomSyncItem 数组） | BOM 同步，返回差异结果 |
| GET | `/{oid}/instances` | 设计实例（BOM 落地结果） |
| GET | `/api/ckfiles/{ckfileOid}/download` | 下载 .DSN 物理文件 |

## 附录 B：CLI 命令速查

| 命令 | 关键参数 | 说明 |
|---|---|---|
| `config` | `--server --username --password` | 配置服务器与账号 |
| `login` | — | 登录并缓存 token |
| `whoami` | — | 连通性检查 |
| `create` | `--name [--project --number]` | 创建原理图对象 |
| `checkout` | `--schematic [--comment]` | 检出 |
| `upload-dsn` | `--schematic --file [--sheets --components]` | 上传设计文件 |
| `bom-sync` | `--schematic --bom [--map]` | BOM 同步对账 |
| `checkin` | `--schematic` | 检入 |
| `undo` | `--schematic` | 取消检出 |
| `download` | `--schematic [--file]` | 下载最新 .DSN |
| `instances` | `--schematic` | 查看设计实例 |
| `iterations` | `--schematic` | 查看版本历史 |

`--schematic` 统一接受原理图编号（如 `SCH-2026-0001`）或 oid。
