# 更新日志

## [未发布]

### 本次更新摘要

方法论内容的**实例层**（P2）：创建产品系列 / 型号时，自动把本租户的**六类**方法论模板**快照**成该归属单元的实例 —— **交付件**（`ck_deliverable`）、**阶段门**（`ck_gate`）、**评审**（`ck_review`）、**检查规则**（`ck_rule`）、**指标**（`ck_metric`）、**驾驶舱**（`ck_dashboard`）。这与研发阶段（`ck_stage` ← `ck_stage_template`）是同一套路数 —— 原来只做了阶段，现在六类齐了。"快照"的含义：实例化即定型，模板以后改了**不回灌**、实例上的裁剪也**不会被模板覆盖**；企业已**停用**的模板条目**不发给新项目**。创建流程**幂等**（以"该类是否已有实例"为守卫，重复调用不会翻倍）。

方法论内容的**模板层**（七批，内容层到此完整）：**交付件（交付物）**、**阶段门（评审门）**、**评审模板**、**检查规则**、**问题类型**、**驾驶舱指标**与**驾驶舱布局**。行业包里的清单（智能硬件 IPD：46 项交付件 + 10 道门 + 8 份评审 + 11 条检查规则 + 7 类问题类型 + 11 项指标 + 3 个驾驶舱视角）现在真的会随包装进**企业一份**，并可在「业务配置 → 模板管理」里裁剪：改强制项、改完成条件、改决策选项、改参与者、改检查项、改条件与严重度、改要不要根因分析或复测、改指标的分层与取数依据、改驾驶舱的受众与分组件，或**停用**。停用而不删除 —— 重装行业包只会 SKIP，既不会把企业改过的值冲掉，也不会把停用的条目改回启用。

> 此前这些内容只有包里那些 JSON：平台上既没有模板表；实例表 `ck_deliverable` 也**没有 `tenant_oid`**，且不在租户拦截器的共享名单里 —— 任何 MyBatis 语句都会因注入 `tenant_oid` 而报错，等于**物理不可访问**；门更是连表都没有。本轮补上两层的**模板**；**实例化（交付件实例 / 门实例）、完成与放行的自动判定、与流程节点产出的联动**留在后续版本（P2/P3/P4）。

### 表单布局（Form Layout）

#### 布局行按字段类型自动分宽：富文本 / 文本域 / 上传 / 表格不再被塞进半宽
- 用户反馈（2026-11-01，截图框住「提出变更请求」里被压扁的变更原因/建议方案）：*"如果是一行两列，dynamicForm 的样式是否要调整，即 dynamicForm 需要根据页面配置的内容自行判断对应的样式"*
- 根因：布局行只按 `columns` 等分（`Math.floor(24 / columns)`），控件类型完全不参与计算 —— 2 列布局里富文本拿到 317px，工具条换行、内容区出横向滚动条；文本域只剩三行高度的一半宽。设计器画布又是另一套 `layoutColWidth(columns)` 映射，两边各算各的
- 改动：新增 `frontend/src/widgets/layout-span.js`，运行态（`RenderFields`）与设计器画布（`PageDesigner`）**共用一份**判定 —— `textarea / rich-text / file-upload / data-table`（以及任何 `dataType=LONGTEXT` 的控件）占满整行，其余等分；`field.span`（24 栅格）/ `field.colSpan`（按格）/ `field.fullWidth` 可显式覆盖
- 顺带修掉布局行的横向滚动条：原来的 `Row gutter=16` 用负边距布列，在弹窗里比容器宽 16px，白白多出一条横向滚动条（内容并不需要滚）；改为 CSS Grid（`grid-template-columns: repeat(N, minmax(0,1fr))` + `columnGap`），单元格用 `gridColumn: span N`
- 数据侧：ECR 页面布局（4 条，create/update × 2 租户）的 `columns` 原为 1（整表单列），按截图恢复为 2 —— 一行两列由布局行的「每行列数」决定，不是代码里写死的
- 验证（实页，Chrome + CDP）：弹窗内 12 个普通字段实测 301px、3 个宽控件 617px；`.ant-modal-body` 的 `scrollWidth === clientWidth`（横向滚动条消失，改前 625 vs 617）；富文本编辑器 `scrollWidth - clientWidth = 0`；截图 `target/layout-width-form.png`

#### ECR 页面布局：包内 `softtypes.json` 与系统脱节，按系统拉齐
- 用户反馈（2026-11-01）：*"变更请求在当前系统的 pagelayout 是否与 pack 包中的不一致，以系统中的为准拉齐 pack 包"*
- 差异（`pageLayouts.byHost.CHANGE_REQUEST`，pack 两条 entry 都停在旧版）：新增的 4 个字段（`changeLevel / certImpact / customerImpact / inventoryImpact`）包里没有；`productOid` 的 label 系统已改为「变更系列/型号」且 `required: true`，包里还是「所属容器」非必填；`columns` 系统 2、包 1；「变更原因 / 建议方案 / 补充说明」系统是 `rich-text`，包里还是 `textarea`
- 改动：以系统为准重写包内两条 `layoutJson`（create 取当前租户那份 —— 正是用户在设计器里调过的顺序与控件；update 取系统 update 行），排版保持不变（只替换两行 `layoutJson`，避免整包重排产生无意义 diff）
- 校验：`JSON.parse` 后与库里逐字段深比较 `=== true`（create 15 字段 / update 15 字段，列数均为 2）；脚本留档 `target/align-pack-layout.mjs`，以后系统再改可直接重跑
- 遗留（待确认）：系统内 create 与 update 仍不一致 —— create 顺序为「标题 / 变更类型 / 责任人 / 变更系列型号 / 分级 / 三项影响 / 受影响对象 / 关联问题 / 优先级 / 计划完成 / 三个富文本」，update 仍是旧的「所属容器在前 + 三个文本域 + productOid 非必填」。pack 按各自现状对齐（不擅自替用户决定）；建议把 update 也拉齐到 create，否则「新建」和「编辑」弹窗长得不一样

#### 变更管理（Change）

#### 「设置状态」报 500：终止流程失败把整个事务标成了 rollback-only
- 用户反馈（2026-11-04）：「设置状态」弹窗确定后提示 500（`Request failed with status code 500`）
- 日志两条连着看才看得出来：`UnexpectedRollbackException: Transaction rolled back because it has been marked as rollback-only`，而它前面一行是被吞掉的 `终止变更通告流程跳过: … 运行中的流程实例不存在或不属于当前租户`
- 根因：`ProcessService#terminateInstance` 带 `@Transactional`（REQUIRED），加入的是**当前**事务；实例不存在时抛 `IllegalArgumentException` 会把当前事务标成 rollback-only。`terminateProcessQuietly` 虽然 catch 住了，提交时 Spring 仍然抛 `UnexpectedRollbackException`
- 触发条件很常见：单据早已驳回 / 审批早已办完，流程实例自然不在了 —— 任何"把单子从结束态拨回来"的操作都会踩到
- 修复（`ChangeNoticeServiceImpl#terminateProcessQuietly`）：改成 `TransactionSynchronization#afterCommit` 执行（没有活动事务时直接执行）。终止流程本就是"单据状态已经定下来"之后的副作用，主事务回滚时它不该发生；afterCommit 里没有活动事务，`terminateInstance` 会自己开新事务，失败只记 INFO
- 顺带：同一条路径也在「取消」分支上（`CANCEL` 之后终止流程），同样的输入同样会 500 —— 一并修好
- 验证：`mvn -o -q compile` 0 错；后端重启 `Started CkPlmApplication in 14.1s`；端到端点击待用户重试确认（缺租户管理员凭据）

#### 租户管理员可直接「设置状态」（运维纠错通道，前后端各一处）
- 用户要求（2026-11-04，截图框住变更通告列表操作列）：*"面向租户管理员增加设置状态功能"*
- 场景：单据被卡住而正常流程推不动 —— 审批在外部系统挂了、流程实例丢了、审批结论回写失败
- 后端：新增 `POST /api/plm/change-notices/{oid}/status` `{ status, reason }` → `ChangeNoticeService#setStatusByAdmin`
  · 与 `/transition` **分开**：那条走状态机（责任人按规则推），这条跳过规则与完成门槛（受影响对象是否执行完、子任务是否关闭）。混在一个端点上，"有权限"和"能流转"就分不清了；`ADMIN_SET_STATUS` 也刻意不写进 `ChangeNoticeFlow.RULES`，否则谁都能顺着 `/transition` 调
  · 硬约束：仅租户管理员（服务端用 JdbcTemplate 查角色，403 兜底）；原因必填；**不能设成 APPROVING**（那需要真实流程实例，硬设会让单据等一个永远不会来的回调）
  · 副作用按目标状态补齐：离开"进行中"时 `releaseByNotice` 释放对象占用；流程实例还在跑就先 `terminateProcessQuietly`（否则回调把状态改回审批结论）；`DRAFT` / `REJECTED` 清 `processInstanceId`（否则重新提交会被"已发起过审批"拦住）；`APPROVED` / `COMPLETED` / `CLOSED` 补对应时间戳
  · 每次操作写一条 `ADMIN_SET_STATUS` 处理记录（前端时间线显示「管理员设置状态」，能区分人为干预与正常流转）
- 前端：列表操作列对 `isTenantAdmin` 多一个「设置状态」；弹窗内目标状态排除当前状态与 `APPROVING`，原因必填，顶部黄条写明"跳过门槛、会记入处理记录"
- 暂未开放：变更请求（ECR）与变更任务（ECT）的同款入口 —— 需要时按同一套补
- 验证：`mvn -o -q compile` 0 错；`npm run build` 通过（45.0s）；后端重启 `Started CkPlmApplication in 8.9s`
- 未实测：真机点一遍（缺租户管理员凭据）

#### F5 不再重复弹出变更单据详情（一次性参数用完即删）
- 用户反馈（2026-11-03）：停在「变更请求」页签按 F5，ECR-0004 的详情抽屉又自己弹出来（截图即该画面）
- 根因：`?requestOid=` / `?noticeOid=` 是"打开哪张单"的**一次性指令**，但一直留在地址栏；面板的 `openFromRoute()` 每次挂载都会执行一遍，刷新等于重放这条指令
- 改动（`ChangeRequestPanel` / `ChangeNoticePanel` 各一处）：读到 oid、打开抽屉之后立刻 `router.replace` 把该参数从地址栏删掉（保留 `tab` 等其它参数）
- 语义变化：**抽屉是否打开不再由 URL 表示**。再搜同一张单仍然能跳 —— push 相同的 query 会重新触发 watcher，所以不影响"搜到了却打不开"
- 与上一条配合：切页签也会丢掉这些一次性参数，两条路径都不会把它们留在地址栏里
- 验证：`npm run build` 通过（37.1s）
- 未实测：浏览器里跳单据 → F5（缺租户账号凭据）—— 预期刷新后只剩列表，地址栏只剩 `?tab=request`

#### 变更控制的页签记到地址栏，F5 不再被扔回「变更通告」（纯前端）
- 用户反馈（2026-11-03）：*"在变更控制导航栏，F5刷新就是这个页面"* —— 停在「变更请求」或「我的任务」时按 F5，总被送回默认的「变更通告」
- 根因：`activeKey` 只在初始化时读一次 `route.query.tab`，用户点页签从不写回 URL，刷新等于重新打开 `/change`
- 改动（`ChangeControl.vue`）：`a-tabs` 加 `@change="onTabChange"` → `router.replace({ query: { tab: key } })`
- 两个容易踩的点：
  · 用 `@change` 而不是 `watch(activeKey)`：路由 watcher 会先改 `activeKey`，若在那里回写 URL，会把刚跳进来的 `?noticeOid=…` 一并洗掉 —— 详情抽屉就永远打不开了（已核对 antdv 实现：`change` 只在用户点击 tab 时触发，程序化改值不发）
  · 回写时**只保留 tab**，故意丢掉 `noticeOid` / `requestOid`：它们是一次性指令（"打开这张单"），留在地址栏里会让下次刷新在「我的任务」页签上莫名弹出一个抽屉
- 顺带修掉一半状态：地址栏没有 `tab`（点左侧「变更控制」菜单回来）时回到默认页签，不再出现"URL 干净、界面还停在任务页签"
- 验证：`npm run build` 通过（42.1s）
- 未实测：浏览器里切换页签看地址栏、再 F5（缺租户账号凭据）—— 预期 `?tab=task` 刷新后仍在「我的任务」

#### 需要审批时「提交」改为「提交审批」，并直接拉起审批流程（前后端各一处）
- 用户要求（2026-11-03，截图框住列表操作列的「提交」）：*"如果配置了变更需要审批则应该是提交审批"*
- 现状的两个问题：① 按钮叫「提交」，需要审批时可它只是把单子推到「已提交」，还要再点一次「发起审批」才真起流程；② 那个「已提交」中间态没有任何下一步提示，单据很容易停在那儿没人管
- 后端（`ChangeNoticeServiceImpl`）：把原来的 `START_APPROVAL` 分支抽成 `startApproval(notice, action, comment)`，两条路径共用（差异只留在时间线的动作名）；需要审批时 `SUBMIT` 直接 `DRAFT → APPROVING` 并记下流程实例，时间线一条「提交审批」。`SUBMITTED` / `START_APPROVAL` 保留，只为兼容存量单据与"流程 key 后来才配好"的租户
- 前端（`utils/changeFlow.js` + `ChangeNoticePanel`）：新增 `noticeActionLabel(action, approvalRequired)` —— `SUBMIT` 在需要审批时显示「提交审批」，免审仍是「提交」；列表主按钮 / 抽屉按钮 / 流转弹窗标题 / 成功提示统一用它。时间线历史仍用静态文案，不随配置漂移
- 顺带修掉过时提示：提交弹窗原写「审批流程接入在 M2，当前会停在「已提交」」，改为「提交后立即发起审批流程，审批环节在任务中心办理，结论自动回写」
- 失败模式变化（已知，与 ECR「发起评审」同口径）：需要审批但流程未部署 / key 为空时，提交会失败并提示去「变更控制 → 配置」配 key 或开免审 —— 不再出现"提交成功但永远停在已提交"
- 验证：`mvn -o -q compile` 0 错；`npm run build` 通过（48.4s）；后端重启 `Started CkPlmApplication in 9.7s`
- 未实测：真实走一遍「提交审批」（需租户账号 + 已部署 `change-notice` 流程）

#### 变更事项去掉「派发」操作（纯前端）
- 用户要求（2026-11-03，截图框住变更事项操作列的「派发」）：*"不需要派发操作"*
- 为什么能直接去掉：派发这一步本就是重复劳动 —— 通告审批通过（含免审）时，流程回调 `ChangeNoticeApprovalOutcomeHandler` 调 `ChangeTaskService#dispatchByNotice` 把该通告下的草稿任务批量置为 OPEN；审批通过之后再新建的任务，后端建的时候就是 OPEN。人工点「派发」只剩"漏项兜底"一种用途，而且在 DRAFT 状态的通告上点它还会被后端 403 挡回（`assertDispatchAllowed`）
- 改动：`utils/changeFlow.js` 的 `TASK_TRANSITIONS` / `CHANGE_TASK_ACTION_LABEL` 去掉 `DISPATCH` —— DRAFT 任务在操作列不再有任何可点动作；`ChangeNoticePanel` 删掉「派发清单（N）」按钮与 `handleDispatchTasks` / `draftTaskCount` / `noticeDispatchable`
- 顺带改文案：任务状态 `DRAFT`「待派发」→「待生效」，受影响对象表格的任务下拉后缀同步为「（待生效）」—— 按钮都没了还写"待派发"，用户会去找那个不存在的入口
- 保留：后端 `POST /api/plm/change-tasks/by-notice/{oid}/dispatch` 与前端 `dispatchChangeTasks` 包装（运维 / 脚本兜底），注释里标明新代码不要在前端调
- 验证：`npm run build` 通过（53.9s）；`mvn -o -q compile` 0 错（后端只改注释，行为不变）
- 未实测：抽屉里的实际渲染（缺租户账号凭据）—— 操作列现在应只剩「开始处理」

#### 新建变更任务挑对象：处在变更中的、本通告已挑过的都置灰（一条 SQL + 一个控件）
- 用户要求（2026-11-03，截图框住「新建变更任务 → 包含的变更对象」的下拉）：*"处在变更中对象不能选，本通告已经选择过的对象不能选"*
- 两条规则掌握事实的一方不同，所以分两处落：
  · **处在变更中** → 候选接口带出 `changeCode`：`PostgreSqlChangeObjectLookupMapper` 三类候选各加一个 `LEFT JOIN LATERAL`，取该对象所在**在途**通告的编号。口径与 `ChangeNoticeFlow.ACTIVE`（DRAFT/SUBMITTED/APPROVING/APPROVED/IN_WORK）一致 —— **不算 ECR**：变更占用（`ck_change_affected_object.locked`）本来就只建在通告上，ECR 阶段的预估对象只是"影响面"，两张请求盯同一对象是正常评审过程
  · **本通告已选过** → 前端拼 `excludeOids`：抽屉里取服务端受影响对象；弹窗里通告可能还没创建，只能取「编辑详情带回的对象 + 其余草稿任务的范围」。漏掉任一边，同一对象就能被同单的两张任务各挑一次（服务端 `addAffectedByEntity` 是幂等的，不报错，只会让"这个对象归哪张任务"变成最后一句说了算）
- 前端 `ChangeAffectedObjectSelect` 增 `excludeOids`：命中即 `disabled` 并在文案后缀「（已在本变更中）」/「（已在 ECN-0004 中）」；**当前已选的那条不置灰**（同 `ChangeRequestSelect` 的处理 —— 编辑既有任务时它自己的对象本就在"已挑过"集合里，一律禁掉会让人连自己选过的都点不回来）；`options-change` 抛出的仍是**未加注**的文案（它被当成任务范围下拉的标签，带上「已在本变更中」会跑到无关的地方）
- 「收集关联对象」不再把本通告已收录的对象收进来 —— 收进来只会显示成置灰项，白让人以为收集成功
- 验证（本地库，psql 直跑候选 SQL 原文）：`PART-202610-0006 → ECN-0004`、`PART-202610-0002 → ECN-0007`，其余候选 `changeCode` 为空 ✓
- 验证（构建）：`mvn -o -q compile` 0 错；后端重启 `Started CkPlmApplication in 8.8s`（`<script>` 与 LATERAL 在 Mapper 注册期即解析）；`npm run build` 通过（58.4s）
- 未实测：浏览器里下拉的置灰与后缀（缺租户账号凭据）—— 请用一个租户账号打开「变更通告 → 新建 → 变更事项 → 新建任务」，搜 `PART-202610-0006` 应显示「已在 ECN-0004 中」且点不动

#### 变更请求（ECR）按"六段变更流程"改造前三段：提出分级 → 影响分析 → 评审决策
- 起因（2026-11-01）：*"安装典型六段流程优化变更请求和变更请求流程"* —— 收敛到 ECR 一侧（①提出初筛 ②跨职能影响分析 ③CCB 决策），范围经确认：按变更等级分流审签路径、会签参与人沿用「设置评审人」表单、允许改本地库
- **新增字段**（`schema.sql` 幂等 ALTER + 建表语句同步）：`change_level`（QUICK/STANDARD/MAJOR，空 = STANDARD）、`cert_impact` / `customer_impact` / `inventory_impact`（各三档，空 = 未评估 —— 不默认成"无影响"，那等于替人下结论）
- **新增状态与动作**：`ANALYZING 影响分析中` + `ANALYZE 开始影响分析`（DRAFT → ANALYZING，非必经；`SUBMIT` 允许从 DRAFT / SUBMITTED / ANALYZING 收口）。前后端状态机镜像（`ChangeRequestFlow` / `utils/changeFlow.js`）同步，列表加「分级」列、详情抽屉加「变更分级」与「影响分析」三行
- **流程模板 `change-request` 重写**：`开始 → 按变更分级分流`，快速 = 单人「快速审批」；标准/重大 = 「影响分析会签」（100% 全票）→ 重大再进「CCB 决策会签」；两个 `SET_ASSIGNEE` 分别放在两条分支上，各自只覆盖自己下游的审批/会签活动，避免"快速变更也得先给三道会签挑人"
- **服务端门禁**：重大变更发起评审前必须填认证与客户两项影响（`assertMajorImpactAssessed`），否则 400；已发起评审后 `change_level` 锁死 —— 否则在途实例走旧路径、单据写新分级，事后从图上完全看不出
- **流程变量**：`startReviewProcess` 带上 `changeLevel / certImpact / customerImpact / inventoryImpact`，网关条件 `${changeLevel == 'QUICK'}` / `${changeLevel == 'MAJOR'}` 才有值可判
- 验证（本地，两个用户真跑）：ECR-0004 重大变更缺影响评估 → 400「认证/合规影响、客户/合同影响 未填写」；ECR-0005 重大 → ANALYZING → REVIEWING → 设置会签人 → 影响分析会签 2/2 → CCB 会签 2/2 → 状态回写 `ACCEPTED`；ECR-0006 快速 → 设置审批人 → 快速审批 → `ACCEPTED`（全程 `target/e2e-six-stage.mjs`，UI 侧 Chrome+CDP 截图 `six-stage-list/form/detail.png`）
- 部署：模板重建为 v2 并部署（`processDefinitionId=change-request:2:…`）；行业包内 `change-request.dsl.json` / `.bpmn20.xml` 同步更新，新环境安装即生效
- 未做（明确不在本轮）：ECN 的「生效方式 / 断点批次」、验证放行强制校验 —— 属于六段里的④⑤

### 文档（Docs）

#### 新增「CK-CADForge 安装操作指南」，并把两处 README 的事实修正到与代码一致（无代码改动）

- 新增 `docs/CK-CADForge-安装指南.md`：最短路径 → 部署前准备（内存口径 / 双向网络 / 共享令牌 / 共享卷）→ Docker（复用 compose 内置的 `cadforge` 服务 / 自建镜像 / 等价 `docker run`）→ 裸机（conda + `pythonocc-core=7.7.2`、Windows DLL、systemd、gltfpack）→ 环境变量总表 → 并发与分道 → 与 CK-PLM 对接（页面字段填法给出 Docker / 跨机两套值）→ 五步验收 → 故障排查（9 类症状 + 崩溃码）→ 令牌轮换 / 升级 / 日志 → API 速查
- CK-PLM `README.md`：「项目文档」表加入该指南入口
- CK-CADForge `README.md` 按代码修订 7 处：
  · 修正 `CADFORGE_MAX_CONCURRENCY` 默认值（`2` → `max(2, min(6, 核数/2))`），并注明**镜像内仍固化为 2**、部署时必须覆盖
  · 补齐 `CADFORGE_HEAVY_CONCURRENCY` / `HEAVY_SIZE_MB` / `PROGRESS_INTERVAL` / `LOG_TAIL` / `PORT` 五个变量
  · 「快速开始」改为"先确认服务活着"：演示管线**默认关闭**，无 OCCT 时任务直接失败，本地联调才用 `CADFORGE_ALLOW_MOCK=1`
  · 对接章节改为「首选页面配置（`ck_cad_forge_config`，改完即时生效）／备选 yml 种子兜底」，补 8090 无入站鉴权、`sourcePath` 两侧路径必须一致、`allowed-hosts` 优于 `allow-private-hosts`
  · 新增「并发与分道」小节：车道槽位算法、上限 6 的理由（单进程峰值 1~3 GB）、`HEAVY < MAX` 硬约束、改完看 `healthz.lanes`
  · 修正 `/healthz` 与产物 kind 描述（去掉代码里并不存在的 `lod`）
  · 顺手修 typo「崩渍」→「崩溃」
- 为什么值得写：这三处偏差都只有读代码才看得出来 —— 按旧 README 部署会只开 2 个槽位、以为没装 OCCT 会自动降级、以为 8090 可以对外
- 验证：`GET /healthz` 实采 `{engine: occt, maxConcurrency: 6, lanes:{fast:5, heavy:1, heavyThresholdMb:20}}`，与指南示例逐字段一致；Docker / 裸机命令与 `docker-compose.yml`、`app/main.py`、`app/worker.py`、`Dockerfile` 逐条对照；纯文档改动，`mvn -o compile` / `npm run build` 不受影响

### 缺陷修复（Fixes）

#### 生命周期模板列表「描述」列定宽 + 省略号 + hover 提示（纯前端）
- 用户反馈（2026-11-04，截图红框圈住描述列）：*"描述太长，将长度固定，如果超出长度的文字使用...号，同时鼠标moveon的时候有提示"*
- 根因：列上虽然写了 `ellipsis: true`，但**没给 width**；表格用的是 `scroll-x="max-content"`，列宽由内容决定 —— 永远撑得下，省略就永远不会触发，于是整张表被最长的那句描述撑宽、其余列被挤成一团
- 改动（`views/system/LifecycleConfig.vue`）：描述列 `width: 300`；`#bodyCell` 加一个 `a-tooltip + .lc-cell-ellipsis` 的单行省略渲染
- 一个 CSS 细节：`.lc-cell-ellipsis` 必须 `display: block` —— a-tooltip 会在目标元素外面再包一层，行内元素量不到可用宽度，`text-overflow` 不生效
- 验证：`npm run build` 通过（42.3s）

#### 物料清单缩略图在「工程数据被检出」后消失，还会串成别的文档的图（一条 SQL）
- 现象：工程数据（EPMDocument）检出后，物料清单里这一行零件的缩略图没了 —— 明明文件没变、几何没变
- 根因（两处各缺一半）：
  · 检出会新建迭代 +1 并**继承同一个 `ckfile_oid`**（与「产品数模」页签那次同一个坑），而 `ck_cad_visualization` 记录仍挂在被检出的那一版上
  · `selectThumbnailsByPartIterations` 只做 `JOIN ck_cad_visualization cv ON cv.eng_doc_iteration_oid = ei.oid` —— 新迭代名下没有记录，整行被 inner join 吃掉
- 顺带暴露的第二个问题：同一零件在别的版本上还有一条关联（`l.part_iteration_oid IN (同主对象的其它迭代)`）时，这行**不是空的**，而是显示**那份别的工程数据的缩略图** —— 图张冠李戴比没图更难发现（实测旧 SQL 在检出副本场景下返回的是另一份文档的 `8e983c57…`）
- 改动仅 `PostgreSqlCadVisualizationMapper#selectThumbnailsByPartIterations`：内连接换成 `LEFT JOIN LATERAL`，先取本迭代自己的转换记录，没有才沿 `derived_from_oid` 复用一次，且要求两版 `ckfile_oid` 相同（换了文件必须重新转换，不能拿旧图冒充）；排序 `(c.eng_doc_iteration_oid = ei.oid) DESC, c.created_at DESC` 保证本迭代的记录永远优先
- 验证（本地库，事务内造检出副本后 `ROLLBACK`，业务数据未被改动）：
  · 基线 `0f700f0a…` → `cafcb635…`（本迭代记录直取）✓
  · 造 A.1 → A.2 检出副本（同 `ckfile_oid`、`derived_from_oid` 指向源）：新 SQL 仍给 `cafcb635…`；旧 SQL 给的是另一份文档的 `8e983c57…` ✓
  · 副本换成不同 `ckfile_oid`：新 SQL 0 行（不复用）✓
  · 副本自己也有转换记录时取自己那条，不会被源版本顶掉 ✓
- 验证（运行时）：`mvn -o -q compile` 0 错；后端重启后 `Started CkPlmApplication in 11.1s`（`<script>` 与 LATERAL 在 Mapper 注册时即解析，能起来即语法通过）

#### DynamicForm 字段上下间距统一 12px（改动前实际是 24px）
- 用户反馈（2026-11-01，截图框住「提出变更请求」弹窗里两处字段间隙）：*"dynamicForm的样式，字段的前后间隙为16px"*，同日再改：*"改成12px吧"* —— **最终 12px**
- 根因：`RenderFields` 把 `style: { marginBottom: '16px' }` 传给 `Form.Item`，antd-vue 把它落到**内层** `.ant-form-item-row` 上，与外层 `.ant-form-item` 自带的 24px 折叠后仍是 24px —— 那句 16px 一直在空转，实测 `margin-bottom: 24px`、控件底边到下一字段标签顶边也是 24px
- 改动：`DynamicForm.vue` 的 scoped 样式 `.dynamic-form :deep(.ant-form-item) { margin-bottom: 12px }`（带 `.dynamic-form[data-v-*]` 前缀，优先级高过 antd 默认值，覆盖所有用 DynamicForm 的弹窗与页面，含「分类属性」分区）；同时删掉 `RenderFields` 里那句落错位置的 `marginBottom: 16px` —— 留着会与 12px 折叠成 16px，改不动
- 验证（实页，Chrome + CDP）：提出变更请求弹窗 11 个字段逐项实测 `margin-bottom: 12px`、`控件底边 → 下一字段标签顶边 = 12px`（改前 24px）；截图 `target/form-gap-before.png`

#### 「不适宜」筛选是灰的、点不动（本阶段没有裁剪行时）
- 用户反馈（2026-11-01）：*"不适宜是灰色的，不能选择"*
- 根因：`a-radio-button` 上挂了 `:disabled="!hasTailoredRows"`，而默认落在 `IPD_PLAN`（0 条裁剪）→ 整个档位置灰；即使能点，`watch` 也会立刻把它弹回「适宜」，两处叠加给人"这功能坏了"的感觉
- 改动仅 `MethodologyInstancePanel.vue`：去掉置灰与该 watch 分支（只保留"失去查看权限时收回「适宜」"）；「不适宜」永远可选，结果为空时由空态文案说明「本阶段没有「本项目不适用」的交付件」，口径行照旧补「（当前只显示不适宜的 0 项）」—— 一个点不动的选项比一个空列表更像坏了
- 验证（实页，Chrome + CDP）：`计划阶段`点「不适宜」→ 选中态切换、0 行 + 空态文案；再切到`概念阶段`→ 选择保持「不适宜」并列出 6 行（`target/not-applicable-plan-empty.png`）；lint 0

#### 「适宜 / 不适宜 / 所有」过滤在页面上不出现（切到没有裁剪行的阶段就整块消失）
- 用户反馈（2026-11-01）：*"前端页面没有出现过滤的操作"*
- 根因：`showApplicabilityFilter` 除了要求「能看见裁剪行」，还要求「**本阶段**确实有裁剪行」；页面默认落在 `IPD_PLAN`（8 条交付件、0 条裁剪），控件被整块藏起 —— 裁剪行只集中在 `IPD_CONCEPT`（6 条），而工具栏不该随阶段忽隐忽现
- 改动仅 `MethodologyInstancePanel.vue`：出现条件改为**只看人**（`canSeeTailored`，与列表是否带出裁剪行同一口径）；本阶段无裁剪行时只把「不适宜」置灰，`watch([showApplicabilityFilter, hasTailoredRows])` 此时把选择收回「所有」，避免停在「不适宜」看空表
- 验证（真实页面，Chrome + CDP，租户账号 + 临时 token，测完已删）：`IPD_PLAN` → 三枚按钮出现、「不适宜」置灰、默认「所有」；切 `IPD_CONCEPT`（6 条裁剪）→「不适宜」可点，点后 6 行且全部带「本项目不适用」，计数补上「（当前只显示不适宜的 6 项）」；点「适宜」→ 2 行、无裁剪标签

#### 「数据指标」列表取消分页（纯前端，1 行）
- 用户要求（2026-10-30，截图框住「共 11 条 · 1 · 2 · 10/page」）：*"数据指标取消分页"*
- 改动仅 `MethodologyInstancePanel.vue` 的 `DataTable`：`:pagination="kind === 'metric' ? false : undefined"` —— 11 项指标一屏放得下，切成两页反而和上方「共 11 项，已评估 5 项」对不上账
- 交付件 / 门 / 评审仍保留分页（条数可能几十条，且依赖搜索 + 前端分页配合）
- 验证：`npm run build` 通过（35.7s）、lint 0；`DataTable` 里 `pagination === false` 的分支本就不切页、不渲染分页器（`computedPagination` 直接返回 false）

#### 阶段分布：交付件进度条 / 阶段门 的悬停提示补齐，并统一成同一个浮层（纯前端，1 个文件）
- 用户反馈 1（2026-10-30，截图框住「阶段分布」的绿/橙条）：*"鼠标move到交付件进度条上没有提示"*
- 用户反馈 2（同日，两张截图对比）：*"交付件进度条和门的提示字号与截图中保持一致"* —— 首版用原生 `title`，字体是系统字号、配色是系统配色，跟「阶段门」页签里的 antd 浮层并排放着像两个产品
- 改动仅 `SeriesBriefing.vue`：进度条与「门 x/y」都改成 `<a-tooltip>` + `.sb-tip`（12px / line-height 1.6 / 标题 600，✓ 绿 ✗ 红），逐项对齐 `MethodologyInstancePanel.vue` 的 `.mi-tip`
- 内容口径：交付件 = `阶段名：交付件 3/5 已完成（60%）` + 未完成清单（最多 10 条，超出只报数量）；`total = 0` 说明「不适用 / NA 不计入」，不显示 `0/0` 的假进度；门 = `阶段门 x/y（决策门 + 技术评审）` + 逐门 `✓/✗ 编码 名称 —— 依据`
- 顺手删掉右侧 `x/y` 的原生 `title`：内容已被进度条浮层完全覆盖，且两套字号挨在一起更刺眼
- 验证：`npm run build` 通过（42s）、该文件 lint 0；`.sb-tip` 与参考的 `.mi-tip` 同属组件内 `scoped` 样式 + `a-tooltip` 标题插槽，参考浮层的实际渲染见用户截图，故字号链路一致（未再开浏览器实页复验）

#### 物料清单里创建零组件报 `ck_part.stage_oid` 非空约束（1 条 DDL + schema.sql）
- 用户反馈（2026-10-30，创建零组件弹窗报错截图）：*"在物料清单中创建 Part 对象时，创建零组件失败：null value in column \"stage_oid\" of relation \"ck_part\" violates not-null constraint …… 最新的方案是 Part 对象直接挂到系列和型号下，与阶段和文件夹没有关系"*
- 根因：前端自 2026-10-22 起就按新方案建件（`ProductLineDashboard#openPartModal` 明确不传 `stageOid / folderOid`，只挂容器），但 `ck_part.stage_oid` 还是 `NOT NULL` —— 代码与库约束不同步，插入必失败
- 改动：
  · **DB（本机已执行）**：`ALTER TABLE ck_part ALTER COLUMN stage_oid DROP NOT NULL;`
  · `schema.sql`：`ck_part.stage_oid` 去掉 `NOT NULL` 并写明原因（列与索引保留，老数据仍有值，只是不再必填）
- 影响面：只放开"可空"，已有行不变；`selectByContainerAndStage`（按阶段查）仍只命中带阶段的旧数据，`selectByContainerAndKeyword`（物料清单列表）不受影响
- 验证（真实接口，用与弹窗相同的载荷：只给 `containerOid / containerType / typeDefinitionCode`）：`POST /api/parts` → 200，`PART-202610-0012`，`stageOid = null`、`folderOid = null` ✓；验证后已 `DELETE /api/parts/{oid}` 清理，库里剩余 0 行 ✓

#### 阶段分布的门「鼠标移上去没提示」：补逐门明细（纯前端，1 个文件）
- 用户反馈（2026-10-30，截图框住「门 x/y」那一列）：*"鼠标移动到门上的时候没有提示，比如那些门已完成，那些门未完成"*
- 改动仅 `SeriesBriefing.vue`：把「这门过没过 + 为什么」抽成 `gateDetail()`（计数与提示同源，避免"提示说过了、数字说没过"），行上带 `gateTitle` 走原生 `title` 悬停；`cursor: help` 提示"可以问"；顺手给交付件 `x/y` 也加了提示
- 文案口径：决策门给状态（`已通过 / 进行中 / 未开始 / 已否决 / 已走完（未登记决策）`）；技术评审给名下评审进度（`名下评审完成 1/2`），与门面板、GateProcessEvaluator 同一判据
- 验证（本机 8080 实页 + CDP 读 `title`）：概念 `门 2/2` → `✓ TR1 … 名下评审完成 1/1` / `✓ CHARTER … 已通过`；计划 `门 0/2` → `✗ TR2 … 0/1` / `✗ PDCP … 未开始`；开发 `门 0/1`（TR3 名下 3 场评审）✓；`npm run build` 通过（51.5s）、lint 0

#### 办理任务页顶部留白收到 18px，顺带修掉左栏被 sticky 顶下去 10px（纯前端，1 个文件）
- 用户要求（2026-10-30，截图框住办理页顶部整条空白）：*"红色框的间隙调整到 18px"*
- 改在 `TaskHandle.vue` 两处：
  · `.task-handle` 的 `padding: 16px 20px 24px` → `2px 20px 24px` —— 外层 `layout-content` 本来就有 16px，叠出 32px；现在合计 **18px**；
  · `.th-side` 的 sticky `top: 12px` → `0`：sticky 的下界按滚动容器的**内容盒**算（`layout-content` 上内边距 16px），`top:12` 会把左栏从静止位置**往下推 10px**，于是补完 18px 后左边「流程进度」卡片比右边「返回」行低一截（量出来的 `delta = 12 - paddingTop`）
- 验证（本机 8080 真实办理页 + CDP，任务 `84aa7e3b…`）：静止时 `gapToCard = 18 / gapToHead = 18`（两列对齐）；把 `.layout-content` 滚到 300px 后卡片仍停在视口 72（= 内容盒顶，吸顶未失效）✓；`npm run build` 通过（44.8s）、lint 0

#### 只读「决策申请」里的决策材料不可点：补上超链接（纯前端，1 个文件）
- 用户反馈（2026-10-30，办理页「材料齐备性核对」截图框住决策材料）：*"决策材料没有超级链接，参考申请单"*
- 根因：`GateDecisionRequestSummary`（材料核对 / 决策会两个节点共用的只读块）只回查了交付件**名字**，渲染成纯文本；申请单 `GateDecisionApplyForm` 里那串是可点的，走 `DeliverableArtifactModal`（产出物关联，只读）
- 改动仅 `GateDecisionRequestSummary.vue`：`deliverableNames`（code→name）换成 `deliverableRows`（code→整行），决策材料渲染成链接、点击打开同一个只读弹窗；**查不到对应交付件时退回纯文本**（已删除 / 裁剪 / 没装方法论包 —— 不给点了没反应的链接）；样式与申请单一致（蓝色 + 悬停实线）
- 验证（本机 8080 真实办理页 + CDP，任务 `84aa7e3b…`「材料齐备性核对」）：`.gdrs-material-link` 命中 1 条；点击后弹窗出现，标题「产出物关联 —— 项目任务书（Charter）」，内容为 `DOC-202610-0004 智能手表立项书 · 本型号` ✓；`npm run build` 通过（47.6s）、lint 0

#### 阶段分布：条长不再编码"有几件事"，门把技术评审也算进来（纯前端，1 个文件）
- 用户反馈（2026-10-30，截图框住概念阶段那行）：*"阶段分布是当前容器的内容吗？为什么概念阶段的绿色长度不是拉到最后面，两个门也已经完成了"*
- 两个独立问题，都改在 `SeriesBriefing.vue`：
  · **绿条**：原来 `widthPercent`（该阶段交付件数 / 最大阶段条数）既是轨道长度、又被绿色填满，概念 2/2 看起来只完成 ~22%（8 条里 6 条标了「本项目不适用」，有效只剩 2 条）。改为**轨道一律等宽、绿色 = 完成率**（概念满绿），数量差异由右侧 `x/y` 表达；
  · **门**：原来只算决策门（概念 0/1）。按用户口径改为「关卡 = 决策门 + 技术评审」：决策门看 `status === PASSED`（现算），技术门（2026-10-29 取消该层后只剩老容器里的遗留行，永远不会有决策）改按**它名下那几场评审**判定 —— 全部 `DONE` 才算过，一场都没挂的不算过（`gatePassed()`，评审行优先认 `gate` 列，缺列时按评审码前缀 `TR1_*` 认亲）
  · 型号对比表**保持只算决策门**（整容器口径），两处 hint 都写明差别
- 验证（本机 8080 实页 + CDP 读 DOM）：概念 `2/2 · 门 1/2`、计划 `0/8 · 门 0/2`、开发 `1/9 · 门 0/1`、验证 `0/9 · 门 0/2`、发布 `0/7 · 门 0/2`、生命周期 `0/5 · 门 0/1`（合计 10 道门 = 4 决策 + 6 技术）；所有轨道同为 913px，概念绿段 913px（满）、开发 100px（1/9）✓；`npm run build` 通过（68s）

#### 申请说明在办理节点是空的：发起时只写进了 comment，而发起侧根本不传 comment（纯前端）
- 用户反馈（2026-10-30，任务中心「决策申请与自检结果」办理页截图）：*"申请说明没有从提交流程的节点带过来"*
- 根因：`collect()` 把「申请说明」只放进 `payload.comment`。但**发起侧**（`StartProcessModal#submitApply` → `doStart`）只把 `payload.variables` 交给 `startProcessInstance`，`comment` 被直接丢弃 —— 于是那句话在发起那一刻就没了，办理节点的 `restoreFromVariables()` 自然无从回填（同一张表的门码 / 完成率 / 材料能回填，是因为它们本来就写了变量）
- 改动（2 张同类表单，一处口径）：
  · `GateDecisionApplyForm`：`collect()` 增 `ckplmGateDecision_remark`；`restoreFromVariables()` 补回填（只填空字段）
  · `TechnicalReviewApplyForm`：同一处缺陷，增 `ckplmTechReview_remark` + 回填（在 `restoreSelects()` 里）
  · `comment` 照旧保留 —— 办理节点提交时仍把申请说明记进本节点办理意见
- 影响面：**新发起**的实例生效；已经填过的那条实例里这句话确实找不回来（从未落库），需人工补填一次
- 验证：`npm run build` 通过（56.4s）；`npx vitest run src/components/task` **跑不起来**（`ReferenceError: window is not defined` ← `@vue-office/docx` 在 node 环境下 import 即失败，与本次改动无关，本次未新增任何 import）；办理页回填链路与已有的门码 / 完成率回填同一出口（`form.variables` → `restoreFromVariables`），**未做**浏览器点击级回归

#### 表单显示名「决策会表决单」改为「决策评审表决单」（仅数据，无代码改动）
- 用户要求（2026-10-30，设计器「决策会」节点的「节点表单」下拉截图）：*"决策会表决单命名为决策评审表决单"*
- 改动：`ck_process_form_template` 中 `code = 'gate-decision-meeting'` 的两行（平台租户 + CKAI-TECH 各一份副本）`name` 由「决策会表决单」改为「决策评审表决单」，`updated_at/updater` 同步
- **不动** `code`（`gate-decision-meeting`）与 `component`（`GateDecisionMeetingForm`）—— 它们是 DSL `formRef`、编译出的 `flowable:formKey` 与前端渲染注册表的同一契约，code 改名会让在途实例与已部署定义全断
- 已核：`ck_process_template` / `ck_process_template_version` 的 `dsl_json` 与 `bpmn_xml` 中**不含**表单显示名（只存 code），故无需重新部署流程
- 验证：改后查表两行为「决策评审表决单」✓；前端 `GateDecisionMeetingForm.vue` 注释同步 ✓；设计器下拉刷新即见新名

#### 整机视图顶部留白比阶段页多 12px（纯前端，1 处 padding）
- 用户反馈（2026-10-30，两张截图对照）：*"整机视图红色框的间隙太大，参考概念阶段页的间隙"*
- 根因：整机视图 `.po-page` 自带 `padding: 12px 16px`，而阶段页 `.pl-dashboard` 是 `0` —— 外层 `layout-content` 已经有 `16px 24px`，于是整机视图的页头离顶栏 28px（比阶段页多 12px）、左右各多 16px
- 改动仅 `ProductOverview.vue` 一行：`padding: 12px 16px` → `0 0 12px`（底部 12px 保留，内层滚动到底不贴边）
- 验证（本机 8080 实页 + CDP 量 `getBoundingClientRect`）：改前 `gapTop=28 / gapLeft=40`，改后 `gapTop=16 / gapLeft=24`，与阶段页（`16 / 24`）逐像素一致 ✓

#### 整机视图页头的「返回」挪到「整机视图」标题右侧（纯前端）
- 用户口径（2026-10-29，截图箭头从标题指向按钮）：*"将返回移动到整机视图的右侧"*
- 原来它挤在标题行里、夹在编码标签与描述之间 —— 既不好找，也把中间那列撑长；它是"离开本页"的动作，跟页头标题待在一起才对
- 改动仅 `ProductOverview.vue`：按钮移入 `.po-summary-current`（标题右侧），`flex-shrink: 0` 防折行；`goBack` 逻辑（有历史回上一页、否则回列表）未变
- 验证：`npm run build` 通过（34.4s）；**未做**页面截图回归

#### 整机视图的「阶段门 x/y」分母里混着已废除的技术门：通过率永远 0（口径修正，5 处）
- 用户反馈（2026-10-29，截图在 `系列战况简报`）：*"红色框中的统计是不是都存在问题"* —— 逐格核对了库内真实数据
- 核对结论（拿型号 `i-watch-X7` 与系列 `b6bba136…` 实算）：
  · **阶段门 0/10 是错的**：10 道门里有 6 道是 2026-10-29 扁平化前的**技术门**遗留行（TR1–TR6），它们没有决策、永远不会 `PASSED`，分母凭空多出 6 道 → 真实的门只有 4 道决策门。副作用不止难看：健康度的「阶段门通过率」被钉死在 0%，阶段自动推断也永远停在最早那个阶段；
  · **评审 0/8 是对的**：8 场评审都在型号名下，没有任何一场跑过流程（TR1_CONCEPT 的流程挂在系列上，不在型号上）；
  · **未闭环问题 0 是对的**：该产品下 3 条问题全部 `CLOSED`，型号名下没有登记问题（口径提醒：登记在系列上的问题不会出现在型号行）；
  · **阶段分布 2/2 与型号对比 0/46 并不矛盾**：前者是**当前容器**（系列）自己的数据，后者逐行是**各型号**的数据 —— 页面原来没写清，容易被读成自相矛盾
- 修复：新增 `isDecisionGate()`（`utils/gateDefs.js`，与 `ProductLineDashboard#stageGates` 同一判据：`type !== 'TECHNICAL_REVIEW'`），凡"阶段门 x/y"的统计一律只算决策门：
  · `SeriesBriefing`：型号对比表 + 阶段分布的门数/通过数；两块的标题说明补上"这里是谁的数据"；
  · `ExecutiveBoard`：健康度的阶段门维度 + 型号排名的门数；
  · `ProductOverview`：页头「门未通过 x/y」；
  · `ProductLineDashboard#gatesByStageKey`：阶段卡片标签与阶段自动推断（开发 / 生命周期没有决策门 → 显示「无门」，不再被技术门判成"未通过"）；
  · `ModelBattleMap`：阶段就绪度的门状态圆点
- 验证：`npm run build` 通过（34.9s），6 个改动文件 0 lint；口径与库内数据逐条对齐（型号 46 交付件/10 门（4 决策）/8 评审/0 问题；系列 40 有效交付件（3 完成）/10 门（2-2-1-2-2-1）/8 评审）；**未做**页面截图回归

#### 「决策会」有了专属会签表单：表决时看得见在决策什么（新表单 `gate-decision-meeting`）
- 用户反馈（2026-10-29，截图在流程设计器「决策会」节点右侧「节点表单」）：*"决策评审决策会节点表单模板没有"*
- 根因：`gate-decision` 的 `n_meeting`（会签节点）在 DSL 与 BPMN 里都没写 `formRef` / `formKey`，`ck_process_form_template` 里也没有一张 `COUNTERSIGN_APPROVAL` 的门决策表单 → 设计器下拉里没有可选项，运行期落内置「会签」（只有规则与各人意见，看不到在决策什么）
- 新表单 `GateDecisionMeetingForm.vue`（code `gate-decision-meeting`，「决策会表决单」）= 内置会签的全部内容（直接复用 `buildCountersignModel` / `validateCountersign`：表决规则、逐人结论与意见、一票否决口径一处不改）**+ 只读的决策申请**
- 顺带把上一轮写在核对单里的只读块抽成 `GateDecisionRequestSummary.vue`（与技术评审的 `TechnicalReviewRequestSummary`、元器件认证的 `QualificationRequestSummary` 同一种拆法）：两个节点共用一份，避免"核对单说 A、表决单说 B"
- 接线：`FORM_COMPONENT_VIEWS` 增 `GateDecisionMeetingForm`；包内 `forms.json` 增一条（nodeTypes=`COUNTERSIGN_APPROVAL`，sortOrder 147）；`gate-decision.dsl.json` 的 `n_meeting` 写 `formRef`，BPMN 同步写 `flowable:formKey` / `ckplm:formRef`
- **数据侧（本机租户 `1a2a2379…`）**：`ck_process_form_template` 补一行 `gate-decision-meeting`（按 code 幂等）—— 不补的话设计器下拉里依旧没有它，运行期也仍落兜底
- **还差一步（必须人工）**：设计器里给「决策会」选中「决策会表决单」并重新部署；在途实例仍跑旧定义
- 验证：`npm run build` 通过（35.9s）；包内 `forms.json`（13 条）/ DSL / BPMN 解析通过，`n_material → gate-decision-material`、`n_meeting → gate-decision-meeting` 两条绑定与 BPMN 的 `flowable:formKey` 逐条一致；库内实查两张表单行 `enabled=t`、节点类型分别是 `APPROVAL` / `COUNTERSIGN_APPROVAL`；**未做**设计器下拉与办理页的点击回归

#### 阶段门状态接上流程：决策流程发起后不再停在「未开始」（对齐技术评审的现算与提示）
- 用户反馈（2026-10-29，截图在系列页「决策门（DCP）」）：*"决策评审已发起，但状态还是未开始，参考技术评审的状态更新与提示"*
- 根因：`ck_gate.status` 是实例化时的快照（恒为 `PENDING`），决策流程跑起来后**没人写它** —— 与技术评审在 2026-10-26 遇到的完全同一个病；评审当时靠 `ReviewProcessEvaluator` 现算解决了，门没有对应的那一层
- 新增 `GateProcessEvaluator`（与 `ReviewProcessEvaluator` 同一套结构：`ObjectProvider` 延迟取 `ProcessService` 解循环依赖、按业务对象反查实例、按流程 key 粗筛、按流程变量认领、查询失败一律吞掉只留快照）：
  · 认领依据 = 决策申请节点写的 `ckplmGateDecision_gate`（门码）精确匹配门行 `code`。**不做"本阶段只有一道门就认它"的兜底** —— 猜错会把 CHARTER 的进度挂到 PDCP 上，比显示"未开始"糟得多；
  · 状态口径：running / suspended → `IN_PROGRESS`（带当前节点与办理人）；正常走完 → 按 `ckplmGateRecord_decision` 落 `PASSED`（GO / GO_WITH_CONDITION）或 `REJECTED`（HOLD / TERMINATE / RECYCLE），没登记决策就落 `DONE`（不臆造通过）；被终止 → 退回 `PENDING`（可重新发起）；
  · 顺带把「决策」列点亮：结论一登记就带出（哪怕实例还在收尾）
- `Gate` 补 7 个**传输字段**（processInstanceId / processStatus / currentActivityName / currentAssignees / processStartUserName / processStartedAt / processEndedAt），与 `Review` 同名同义，不落库
- 接线：`MethodologyInstanceServiceImpl#findByOwner` 返回前 `enrich(gates)`；裁剪闸门 `blockTailorIfProgressed` 的门分支改为按现算结果判定（原来读库内 `PENDING` 恒放行，等于白留的口子）
- 前端 `MethodologyInstancePanel`：状态列悬停给依据（当前节点 / 办理人 / 发起人 / 起止时间，走完再带「决策 X」）；门在跑时「发起」按钮不再给（后端本来也会拦）；「查看流程」对门也开放（宿主 `openProcessInstance` 本就按 `processInstanceId` 走）；「决策」列在跑时显示「决策中」而不是「未决策」
- 验证：`mvn -DskipTests compile` 0 错、`npm run build` 通过（39.4s）；库内实查该系列那条在跑的实例 `ckplmGateDecision_gate=CHARTER`、四道决策门 `process_key=gate-decision` 且 `status=PENDING` —— 认领与判定的输入全部对得上；**未做**重启后的接口/页面回归（后端在 IDE 里跑，重启会打断会话）

#### 「材料齐备性核对」有了专属节点表单：核对人看得见决策申请内容（新表单 `gate-decision-material`）
- 用户反馈（2026-10-29，截图在办理页左栏「材料齐备性核对 / 表单: 审批意见」）：*"决策评审材料齐备性核对没有独立的节点表单，表单中需要体现决策申请内容，参考技术评审"*
- 根因：`gate-decision` 的 `n_material` 在 DSL 与 BPMN 里都没写 `formRef` / `formKey` → 运行期按节点类型（APPROVAL）落到内置「审批意见」，核对人只看到一张空表，等于凭记忆签字（与技术评审核对节点此前是同一个病）
- 新表单 `GateDecisionMaterialForm.vue`（表单 code `gate-decision-material`）：
  · **只读的决策申请**：读流程变量 `ckplmGateDecision_*`（阶段门 / 交付物完成率 / 未关闭阻断问题数 / 认证齐套 / 供应已确认 / 决策材料），门的名称与交付件名称按 `GET /api/instances` 回查，查不到就显示 code；专项两行只在变量有值（= 该门声明了对应检查）时出现；
  · **核对结论与意见**：直接复用 `buildApprovalOpinionModel` / `validateApprovalOpinion`，允许驳回、驳回目标、意见必填全随节点声明；本流程没画「驳回」出边时**不摆**驳回选项（否则点了会顺默认出边继续往前）；
  · 刻意**不现算**：核对的对象是「申请时提交的那份」，实时自检在门的「自动检查」列看，两个口径不混（这一点写进了表单页脚）
- 接线：`FORM_COMPONENT_VIEWS` 增 `GateDecisionMaterialForm`；行业包 `processes/forms/forms.json` 增一条（nodeTypes=APPROVAL）；`gate-decision.dsl.json` 的 `n_material` 写 `formRef`，`gate-decision.bpmn20.xml` 同步写 `flowable:formKey` / `ckplm:formRef`
- **数据侧（本机租户 `1a2a2379…`）**：`ck_process_form_template` 补了一行 `gate-decision-material`（与安装器写入同构、按 code 幂等），否则运行期 `findFormTemplate` 查不到 → 仍落兜底表单
- **还差一步（必须人工）**：该租户的 `阶段决策流程` 已部署到 v3，包内改动不会回灌既有模板 —— 需在流程设计器里给「材料齐备性核对」选中「决策材料齐备性核对单」并重新部署；**在途实例仍跑旧定义**，要新发起一条才能看到新表单
- 验证：`npm run build` 通过（40.3s）；包内 `forms.json`（12 条）/ `gate-decision.dsl.json` / `gate-decision.bpmn20.xml` 逐个解析通过（UTF-8），`n_material.formRef` 已落；库内实查新表单行 `APPROVAL | GateDecisionMaterialForm | enabled=t`；**未做**真实办理页点击回归

#### 门决策申请单（`gate-decision-apply`）：门会自己选、两项自检按本门现算、专项只在该出现的门出现、材料改选交付件（纯前端）
- 用户反馈（2026-10-29，截图在「阶段门与自检」）：*"阶段门没有自动选择，交付件完成率和未关闭问题数没有自动计算，专项只在对应的决策门中出现，决策材料应该与技术评审一样，选择交付件"*
- **门自动选择**：从门的行点「发起」时，宿主早就算好 `form.presets.gate`（`ProductLineDashboard#presetFromGateRow`），但本表从来没读过 —— 现在按 code 预填（`applyPresets`，三条约束与技术评审申请单一致：只填空字段、值必须在下拉里、等下拉加载完）。行上的门不在可选列表时给一句中性说明，不静默留空
- **两项自检按本门现算**：
  · 「未关闭阻断问题数」原来取产品级 `blockOpen`，与"这道门要不要放行"不是一回事 —— 改为取 `gate-check` 返回里**本门** ISSUE_OPEN 检查的 `blockCount`，说明行同时给出本门未闭环总数 / 阻塞 / 提示与 `maxCount`；
  · 「交付物完成率」改为选了门就把该门阶段的完成率带出（`stageRates`，DELIVERABLE_RATE 同口径）；未选门时不再拿产品级数字冒充，字段保持可填并明说要先选门
- **专项确认按声明显隐**：`认证齐套` / `供应已确认` 分别只在所选门的 `auto_checks` 声明了 `CERT_COMPLETE` / `SUPPLY_CONFIRMED` 时出现（本地库实测：CHARTER / PDCP 两项都没有、ADCP 两项都有、LDCP 只有认证齐套）。没声明的门在提交时写 `null` 而不是 `false` —— 后者会被读成"明确未齐套"
- **决策材料改选交付件**：多选本系列「交付件」面板里的交付项（`GET /api/instances`，剔除「本项目不适用」），默认按所选门的阶段收窄，已选项可点名称看交付项详情（复用交付件面板的产出物弹窗，只读）；值仍是交付件 code 逗号串，写回同一个变量 `ckplmGateDecision_decisionMaterial`，老数据回填时不认识的一律不显示成裸 code
- **门下拉改取本系列的门实例**：原来列的是本租户**阶段门模板**（本地库里 TR1–TR6 还在，等于让人给决策流程挑一道技术门）；现在走 `GET /api/instances` 的 `gates`（与门面板同一份，只留 `DECISION_REVIEW`），专项开关也按实例上的 `auto_checks` 判 —— 模板改过、实例没回灌时不会出现"表单要认证、面板没这项"；实例取不到（没装方法论包）才退回模板并保留"决策门优先、否则全部"的兜底
- 验证：`npm run build` 通过（33.8s，仅既有 chunk 体积告警）；库内实查 20 条门模板 + 10 条门实例，`type` / `stage_key` / `auto_checks` 与判断逐条对得上；**未做**真实登录态的页面点击回归

#### 项目内的阶段门表格去掉「决策选项」列（纯前端）
- 用户口径（2026-10-29）：*"决策选型不用在前端展示"* —— 那一列铺的是这道门**可以**给出哪些决策（Go 通过 / Hold 暂缓 / Terminate 终止），属于配置，不是这道门的结果
- 改动：`MethodologyInstancePanel` 删掉 `decisionOptions` 列定义与对应渲染模板，`parseGateJsonArray` 随之从 import 里去掉；「决策」列（实际达成哪个）保持不动
- 数据与规则不变：`ck_gate.decision_options` 仍在库里、仍由「业务配置 → 模板管理 → 阶段门」维护，发起流程时也照旧按它出选项 —— 只是不在项目门表格里露面
- 验证：`npm run build` 通过（1m07s，仅有既有的 chunk 体积告警）；未做页面截图回归

#### 阶段门的「未闭环问题」不再替人做决定：只数挂在这道门上的问题
- 现象：产品下任意一条未闭环问题都会被算进**每一道**声明了 ISSUE_OPEN 的门 —— 登记时没选阶段门的问题（`ck_issue.gate_oid` 为空）也会把某道门判成不通过
- 用户口径（2026-10-29）：*"那道门并没有被这个问题选中，算到它头上等于替人做了决定"* → 自动检查只统计 `issue.gateOid == gate.oid` 的问题
- 后端 `IssueServiceImpl#gateCheck`：产品级未闭环仍一次查全（`selectOpenByProduct` + `decorateAll`），但每道门先筛出 `gateOpen` 再统计 `gateBlock` / `gateWarn`；检查项的 `actual` / `blockCount` / `warnCount` 与文案改为「本门无未闭环问题（要求 ≤ N）」/「本门还有 N 个未闭环问题（含 M 个阻塞），要求 ≤ N」
- 响应里的 `openTotal` / `blockOpen` / `warnOpen` 保持**产品级汇总**（系列页对比列、门决策申请单带出用），与逐门判定不是同一个口径 —— 接口与前端 API 注释都写明了这点，免得下次又被当成一回事
- 影响面：`selectOpenByProduct` 全局只有这一处业务调用（`IssueServiceImpl:364`），Mapper 接口与 PostgreSQL 实现未改；门面板 / 发起前提示读的是逐门结果，文案自动跟着变
- 验证：`mvn -DskipTests compile` 0 错；**未做**真实数据下的接口回归（改动是纯统计口径，SQL 与返回结构都没动）

#### 评审进行中不允许裁剪（前端置灰 + 后端拦截，无表结构变更）
- 现象：评审行变成「进行中」（已接流程状态）后，「标记本项目不适用」的按钮仍可点 —— 一点就把在跑的评审从清单里抹掉，办到一半的人回头找不到自己那条（2026-10-26 用户口径："已经在进行中的不可以裁剪"）
- 前端：`MethodologyInstancePanel` 的裁剪按钮 `:disabled="tailorBlocked(record)"`，禁用时 tooltip 写明原因「评审进行中，不能标记「本项目不适用」；请先完成或终止该流程」——**禁用要说明为什么**，不能只变灰
- 后端：`MethodologyInstanceService#tailor` 在权限校验之后加闸门 —— `disabled=true` 且 `kind=review` 时，用 `ReviewProcessEvaluator#isRunning` 判定（running / suspended）；命中则 400「…正在评审中，不能标记「本项目不适用」；请先完成或终止该流程」。前端置灰只是别让人白点，真正拦得住的是这里
- `ReviewProcessEvaluator` 顺带抽出 `claimsOf(...)`（认领逻辑）供 `enrich` 与 `isRunning` 共用，避免两套口径漂移
- 验证：
  · 读侧跑通了**运行中**分支：`GET /api/instances`（owner `b6bba136…`）返回 `TR1_CONCEPT status=IN_PROGRESS / processStatus=running / 当前节点=设置审批人 / 办理人=Roney.Liu` ✓
  · 写侧护栏：管理员对这条评审 `PATCH /api/instances/review/{oid} {disabled:true}` → **400**「「概念与需求评审」正在评审中，不能标记「本项目不适用」…」，库内 `ck_review.disabled` 仍为 `f`（未改动）✓
  · `mvn -o compile` 0 错、`npm run build` 通过（41s）
- 未做：阶段门（`gate-decision`）同样可以在流程中裁剪 —— 它还没有流程状态现算，等补上后一并加闸门

#### 「设置流程参与者」把候选角色显示成 code 而不是名称（无后端改动）
- 现象：办理「设置审批人」时每行写着「候选：角色 QUALITY_ENGINEER 的成员」，会签那行是「角色: QUALITY_ENGINEER, SYSTEM_ENGINEER, HARDWARE_LEAD 的成员」（2026-10-26 用户截图反馈"显示的是角色的 code，不是显示名称"）
- 根因：DSL 的参与人策略存的是角色 **code**（稳定标识），表单直接 `slot.roleCodes.join('、')` 拼进文案，从来没换成名称 —— 与"交付件责任人显示 code"是同一类问题（后者在 2026-10-26 已由后端补 `ownerRoleName`）
- 改动：`SetupAssigneeForm` 与槽位加载**并行**拉设计器同一份角色选项（`loadOptions('ROLES')`，`options.ts` 模块级缓存，同一会话只请求一次），建 code→中文名表；文案改为「候选：角色 质量工程师、系统工程师、硬件主管 的成员」，**code 移进悬停提示**（与角色定义核对时用）；查不到名字回落 code，绝不显示空白（空白会让人以为没有候选范围）
- 验证：`GET /api/roles`（CKAI-TECH 租户）四个 code 全部有名字：QUALITY_ENGINEER→质量工程师、PROJECT_MANAGER→项目经理、SYSTEM_ENGINEER→系统工程师、HARDWARE_LEAD→硬件主管；`npm run build` 通过（35s）
- 备注：截图顶部「以下活动未指定角色，候选人为全部用户：「整改执行」」是**流程侧**的既有副作用（设置审批人接管下游全部办理节点，而 `n_fix` 是发起人策略、没声明角色），不是显示问题 —— 见上一条的"副作用"说明

#### 评审清单不随流程变化：发起后仍是「未开始 / 未评审」（无表结构变更）
- 现象：评审页签点「发起」→ 流程都跑到「检查项逐条核对」了，行上还是「未开始」「未评审」，而且还能再点一次「发起」（2026-10-26 用户反馈"流程发起后，评审清单上没有变化，用户体验不好"）
- 根因（三处叠加，缺一条都不成立）：
  · `ck_review.status` 是快照时的 `PENDING`，**全仓没有任何代码**在流程发起 / 结束时写它（`technical-review` 也没有 `ProcessOutcomeHandler`）；
  · 交付件早就有"看的时候算"的 `DeliverableStatusEvaluator`，评审没有对应实现；
  · 前端 `StartProcessModal @success` 只调了 `refreshStageLists()`（刷零件 / 文档），**没有重拉 `/api/instances`** —— 后端就算算出了状态也刷不出来
- 后端：新增 `ReviewProcessEvaluator`（`product/service/impl`）—— 按 `ck_process_entity_set`（`findInstancesByEntity`，与对象详情页「关联流程」同一来源）取该归属单元的实例，按**流程 key** 粗筛（`TR*` → `technical-review`，变更 / 问题等流程排除），再用发起表单写的 **`ckplmTechReview_reviewTemplate`（评审码）** 精确认领；变量缺失时退回 `ckplmTechReview_gate`，且只在该门下**仅一条评审**时采用（宁可不认领，也不把 A 的进度挂到 B 上）
  · 状态口径：`running|suspended` → `IN_PROGRESS`（带当前节点 + 办理人）；`completed` → `DONE`（`reviewedAt` = 结束时间，`verdict` 取 `ckplmTechReview_verdict`，取不到留空）；`terminated` → 退回 `PENDING`（这次没评成，必须还能重新发起）
  · `Review` 加 7 个**传输字段**（`processInstanceId / processStatus / currentActivityName / currentAssignees / processStartUserName / processStartedAt / processEndedAt`，不落库 —— 与 `Deliverable.ownerRoleName` 同一口径）；`MethodologyInstanceServiceImpl#findByOwner` 返回前调用
  · 依赖用 `ObjectProvider<ProcessService>` 延迟取：直连会形成 `MethodologyInstanceServiceImpl → … → ProcessEntitySetServiceImpl → ProcessServiceImpl` 的启动期环（实测 `Requested bean is currently in creation`，启动直接失败）
- 前端：
  · `MethodologyInstancePanel`（评审）状态列加浮层：进行中给「当前节点 + 办理人」，已结束给结束时间，页脚给发起人 / 开始时间；结论列在"流程已结束但无结构化结论"时显示 **未评定** 并说明去哪看（不臆造 PASS/FAIL）；已发起的行给 **「查看流程」**（emit `open-process`）；`canStart` 排除 `running/suspended`（后端本来也会以"该版本已有流程在执行中"拦住，先一步不给点，免得点了才报错）
  · `ProductLineDashboard`：`@success` 改为 `onProcessStarted()`（刷列表 **+ 重拉 instances**）；`openProcessInstance()` 推到 `/workflow/instance/:id`
- 验证：`GET /api/instances?ownerOid=d6651cfa…`（X系列智能手表）返回 TR1_CONCEPT 行 `processStatus=terminated`、发起人「刘杨」、起止时间齐全、`status` 仍 `PENDING`（终止后可重新发起）；同单元另外 7 条无流程的评审保持原样（`processInstanceId=null`）；`mvn -o compile` 0 错、`npm run build` 通过（1m8s）
- 未做（下一步）：① 结论仍是自由意见 —— 「结论登记」节点没有结构化 verdict 表单，所以完成态只能显示「未评定」；② 阶段门（`gate-decision`）同样既没有状态现算也没有回写，点「发起」后仍显示「未决策」，与评审是同一个问题

#### technical-review / gate-decision 补上「设置审批人」节点（CK-PLM 各出新版本 + 行业包对齐）
- 现象：两条流程都有**会签**节点（technical-review 的「复评（评审组）」、gate-decision 的「决策会」），却都**没有「设置审批人」节点**；前面几个审批/办理节点也只能靠角色候选 —— 用户实测「检查项逐条核对」建出来 `assignee_` 为空（该实例 `449cce4b` 08:41 被终止）
- 根因：编译器对**未被设置审批人覆盖**的会签输出 `flowable:collection="${ckplmApprovers}"`（`compile.ts` 的 `emitMultiInstance`），而**全仓没有任何地方写 `ckplmApprovers`** —— `builtin-variables.ts` 原话"后端不写它们，登记进来只会让人配出一个永远取不到值的流程"，`act_ru_variable` 里也从来没有这个变量。走到会签节点要么表达式取不到变量，要么 0 个实例被跳过
- 设计：设置节点放在**发起人自己的填报节点之后** —— technical-review：材料准备与确认 → 设置审批人 → 检查项逐条核对；gate-decision：决策申请与自检结果 → 设置审批人 → 材料齐备性核对。这样"发起人办的这一步"不会变成要指派的槽位（覆盖规则是**类型 + 可达性**：设置节点会接管下游所有审批/会签/办理）
- 改动：
  · 两条 DSL 各加 `SET_ASSIGNEE` 节点（`assignee: INITIATOR`、`formRef: CKPLM_SETUP_ASSIGNEE`）+ 一条边，画布整体下移 120
  · gate-decision 顺带清掉两处遗留 `reject`（`stripLegacyReject` 本来就会清，写了等于没写）
  · BPMN 一律由前端编译器 `compileToBpmnXml` 产出（不手写 XML）并装回包内；两份 DSL 过 `check-dsl.mjs`：**0 ERROR / 0 WARNING**
  · CK-PLM（CKAI-TECH 租户）：technical-review **v4→v5**、gate-decision **v1→v2**，保存后立即部署
- 人员绑定随之换源（编译产物）：
  · `n_check` / `n_material` → `flowable:assignee="${ckplmSetupAssignees_n_*}"`（原为 `candidateGroups=QUALITY_ENGINEER`）
  · `n_issue` / `n_fix` / `n_verdict` / `n_record` → `candidateUsers="${ckplmSetupAssignees_n_*}"`
  · `n_reverify` / `n_meeting` → `collection="${ckplmSetupAssignees_n_*}"`（不再是没人写的 `ckplmApprovers`）
- 验证：库内 `dsl_json` 与包内 DSL **逐字一致** ✓；`act_re_procdef` 出现 `technical-review:5` / `gate-decision:2`、模板 `deployed_version` 同步 ✓；部署产物里 `set_assignee` 与上述绑定齐全 ✓
- 副作用（如实记）：覆盖规则不分策略，**「整改执行」`n_fix`（发起人策略）也会进表单成为一格**，候选退化为全体用户 —— 想让它固定回发起人，得把设置节点挪到它之后或改覆盖规则，本轮按用户选择的位置未做
- 注意：**在途实例仍按旧版本跑**（`449cce4b` 已终止在 `technical-review:4`），新发起的实例才走 v5 / v2；平台租户与 `ce50ab19…` 租户的同名模板仍是旧版（v1），本轮未动

#### 行业包 `electronics-core` 的 technical-review 流程对齐到库内在用的第 4 版（无表结构变更）
- 现象：包内 `processes/technical-review.dsl.json` 还是 10-08 的初版 —— 开始节点没挂申请表单、办理节点叫「发起与材料准备」且没有交付件上传、两个审批节点还带着 `reject` 字段（解析时被 `stripLegacyReject` 清掉，写了等于没写）；而 CKAI-TECH 在跑的是第 4 版（在设计器里改过的）。新租户装包会拿到一份旧流程
- 对齐（`dsl` 逐字等于库内 `dsl_json`）：
  · 开始节点加 `formRef: technical-review-apply` —— 发起即填申请，与「材料准备与确认」同一张表（正是上面那条变量回填要用的场景）
  · `n_apply` 更名「材料准备与确认」，并加 `needDeliverables: true` / `deliverableObjectTypes: ["DOCUMENT"]`
  · `n_check` / `n_reverify` 去掉遗留 `reject`（驳回要靠 `route: "REJECT"` 的出边）；`n_verdict` 去掉 `commentRequired`
  · `layout` 起点与终点 x=296（与设计器里的画布位置一致）
- `technical-review.bpmn20.xml` 同步换成库内第 4 版的编译产物：`ProcessInstaller` 的目录形态是「DSL 建模板 + 自带 BPMN 直接部署」，两者不一致会装出"模板是新的、实际跑的是旧的"
- 未动：外层 `key / name / displayName / category / description / enabled`（与库内模板主档一致）、`processes/forms/forms.json`（`technical-review-apply` / `technical-review-checklist` 两张表与组件名照旧）
- 验证：`dsl` 与库内 `dsl_json` 深比较逐字相等 ✓；`bpmn20.xml` 与 `act_ge_bytearray` 里的 v4 逐字相等（去 BOM / CRLF 后）✓；`check-dsl.mjs` **0 ERROR**（1 WARNING `SETUP_MISSING` —— 本流程按角色/发起人指派，与库内一致）；两个文件的行尾 / BOM 风格保持包内原样
- 说明：**已装租户不受影响**（`ProcessInstaller` 模板已存在即 SKIP，不覆盖企业改过的内容）；装包流程的 `PROCESS` 明细仍是 SKIP 3 条
- 顺带查实（**既有缺陷，本次未改**）：**流程市场清单对所有包都为空** —— `ProcessTemplateMarket.itemOf` 按"裸 DSL"读 `meta.key`，而包内 `.dsl.json` 是 `{key, …, dsl:{…}}` 包装形态（`ProcessInstaller` 认的正是这个形态），启动后第一次访问市场的日志里 7 条全是「包内流程 DSL 缺少 meta.key，已跳过」

#### 发起时填过的申请信息不带入同模板的办理节点（无表结构变更）
- 现象：技术评审流程发起时在「填写申请信息」里填了评审模板 / 所处阶段 / 关联阶段门 / 送审材料，进到「材料准备与确认」（`n_apply`）——**同一个模板** `technical-review-apply` —— 表单却是空白，得重填一遍
- 根因：发起时 `collect()` 确实把字段写成了流程变量（实测该实例 `act_ru_variable`：`ckplmTechReview_gate=TR1`、`stage=IPD_CONCEPT`、`reviewTemplate=TR1_CONCEPT`、`materialRefs=DLV-CONCEPT-PROP`），但**办理侧没有任何途径拿到它们**：`ProcessTaskFormVO` 只带 `formKey / taskDefinitionKey / dslJson / businessOid`，表单组件也就无从回填（此前只有元器件认证申请自己做了一次"取实例详情 → 读 variables"的补丁）
- 后端：`ProcessTaskFormVO` 新增 `variables`（**用户表单变量**，按 `INTERNAL_VARIABLES` 剔除 `businessObjectSet / initiator / tenantOid / lifecycleStatus / approved / lastAction / lastComment / ckplmDecision` 等平台内部项）；`ProcessServiceImpl#taskForm` 由 `runtimeService.getVariables` 填充，实例已结束时退回历史变量，读失败只记 WARN 不挡办理 —— 一次请求给齐"该节点表单 + 发起时填过的内容"，表单不必再各自串两个接口
  · 逐项做一次 JSON 可序列化探测：Flowable 允许变量存任意 `Serializable`，某个实例存了序列化 POJO 时若直接塞进响应，会让**整个办理页** 500；序列化不了的项跳过（前端本就渲染不了它）
- 前端：新增 `taskForms/variables.js`（`variableOf` 兼容 `{name:value}` / `[{name,value}]`、`asArray`、`asCodeList` 兼容数组 / JSON / 逗号串）
  · `TechnicalReviewApplyForm`：「材料准备与确认」这类同模板办理节点按 `ckplmTechReview_*` 回填评审模板 / 阶段 / 门 / 送审材料（值必须在当前下拉与交付件候选里，否则宁可不填 —— 填了只会显示裸 code）；顺序放在 `applyPresets` **之前**，行上预设只补空、不盖住用户真填过的值
  · `GateDecisionApplyForm`：同类缺陷一并补上（阶段门决策申请单也挂 START/TASK）；**自动带出的两项（阻断问题数 / 交付物完成率）优先** —— `autoFilled` 为主时不用旧变量覆盖
  · 两者都只填空字段、只跑"有任务"的场景（发起侧没有"之前"），失败静默
- 验证：`mvn -o compile` 0 错；真接口 `GET /api/workflow/task/{id}/context`（用户在跑的实例 `449cce4b…` 的 `n_apply`，formKey=`technical-review-apply`）返回
  `form.variables = {ckplmTechReview_reviewTemplate:TR1_CONCEPT, ckplmTechReview_stage:IPD_CONCEPT, ckplmTechReview_materialRefs:DLV-CONCEPT-PROP, ckplmTechReview_gate:TR1, ckplmTechReview_targetObjects:i-watch-X7}`，
  `businessObjectSet / initiator / tenantOid / lifecycleStatus` 已剔除 ✓；`npm run build` 通过。**未实测**：办理页在浏览器里的实际回显（无该账号凭据）—— 请打开该任务的办理页确认四项已带出

#### STEP 转换补上缩略图，并在「产品数模」列表最前展示（无表结构变更）
- 现象：产品数模页签里每份数模只有状态标签和面片数，没有图，扫列表时认不出是哪个零件
- 根因：`ck_cad_visualization.thumbnail_ckfile_oid`、后端 `applyArtifacts` 的 `kind=thumbnail` 落库通道都在，但**转换器从来没产出过缩略图** —— CK-CADForge 只返回 glb / structure，请求里那个 `generateThumbnail=true` 一直是空头承诺
- 转换器（CK-CADForge）：新增 `app/thumbnail.py`，在**最终 GLB**（压缩之后）上渲染 512×512 透明底 PNG，作为 `kind=thumbnail` 产物回传
  · 纯 numpy 光栅化 + z-buffer + Gouraud 着色（顶点法线取 GLB 自带的 NORMAL）；不用 OCCT 的 `V3d_View` —— 后台子进程没有桌面会话，建窗口要么失败要么黑图
  · 视角按包围盒最长轴横躺屏幕、侧上方 60° 斜视；`right` 必须与视向**严格正交**，否则退化成斜投影，零件会被拉歪成一块方片（实测踩到两次）
  · 矩阵乘法用逐元素展开而非 `a @ b`：pythonocc 环境的 numpy 调 BLAS 会去延迟加载 MKL 线程库，缺库直接崩进程（`0xC06D007E`，无任何 Python 栈）
  · 出图失败只记日志、不判任务失败 —— 少一张小图的代价远小于丢掉一份已成功的 GLB
- 后端：无需改动 —— `registerArtifact` 按扩展名解析出 `image/png`，落 `thumbnail_ckfile_oid`
- 前端：`ProductLineDashboard.vue` 的「产品数模」表格最前置一列「缩略图」（`a-image`，点击可放大）；没有图时给**同尺寸虚线占位**，行高与列宽不跳动
- 存量：已转换成功的记录没有缩略图，点「重新转换」即补齐（本轮把 `c5818cf0…` 重转验证）
- 验证：真实 STEP `E-SCE22-D15-L20-M4-S0.stp` 重转 → `status=SUCCESS`、`thumbnailCkfileOid=947a5162…`；`ck_file.mime_type=image/png`；`GET /api/ckfiles/{oid}/stream` 取回的 PNG 与转换器产物字节一致；571 个三角形出图约 0.35s；前端 Vite HMR 无报错

#### 「产品数模」列表增加检出状态列，与物料清单同口径（无表结构变更）
- 现象：数模列表看不出这份数模是否被检出，与物料清单（编码/名称/类型/版本/单位/生命周期/**检出**/操作）不一致 —— 用户会在别人已检出的数模上点编辑，那个动作注定失败
- 后端：`ContainerCadModelVO` 增 `checkedOut / checkedOutBy / checkedOutComment`；`selectModelsByContainer` 从**已 join** 的 `ck_eng_document_iteration` 直接带出 `ei.checked_out / checked_out_by / checked_out_comment`，不新增表或列（检出状态挂在**迭代**上，与零件迭代同一套语义）
- 前端：`ProductLineDashboard.vue` 的列定义在「版本」后插入「检出」列，渲染与物料清单一致 —— 已检出橙底 `已检出: 用户`（tooltip 里带检出人与检出备注），未检出绿底 `已检入`
- 验证：`mvn -o compile` 0 错；真实检出 `DOC-202610-0002` → `GET /api/cad-visualizations/models` 返回 `checkedOut=true / checkedOutBy=Roney.Liu`；取消检出后回到 `false`（**验证后已还原**，不留脏数据）；前端 HMR 无报错

#### 检出 / 检入 / 取消检出后，物料清单与产品数模的「检出」列即时刷新
- 现象：在物料清单里检出或撤销检出，该行的「检出」列不变（还挂着"已检出: 某某"或"已检入"），要切页签或刷新整页才更新
- 根因：三个动作成功后只刷了「研发资料」当前文件夹的列表（`loadParts / loadDocuments`）；物料清单（`bomLoaded`）与产品数模（`cadModelsLoaded`）各自缓存了数据，且两个 loader 默认撞上缓存标志就早退 —— 请求根本没发出去
- 修复：抽出 `refreshAfterCheckoutChange()`，由 `confirmCheckout`（检出 / 检出并编辑）、`checkinRecord`（检入）、`undoCheckoutRecord`（取消检出）成功后统一调用：文件夹内容重取；**当前就在**物料清单 / 产品数模页签则带 `force` 重取，不在该页签只作废缓存（切过去时 watch 会自动重拉），不为看不见的列表白发请求
- 顺带：`undoCheckoutRecord` 原先失败时没有任何提示（只有成功分支），补上 `message.error`；两个函数的缩进一并整理
- 验证：`ProductLineDashboard.vue` HMR 无报错、lint 0；接口侧复测检出 → 取消检出，`checkedOut` 字段翻转正常（验证后已还原）

#### 「产品数模」操作列改为下拉菜单，补齐三维浏览 / 下载数模 / 下载装配数模 / 检出 / 撤销检出 / 检入 / 设置生命周期状态（新增打包下载接口，未改表）
- 前端（`ProductLineDashboard.vue`）：
  · 操作列由平铺的 link 按钮改为 `a-dropdown + a-menu`，由 `handleCadModelAction` 分发；「三维浏览」在还没生成时**置灰而不是消失** —— 平铺按钮只能靠"按钮不见"表达前置条件，用户看不懂自己少了什么
  · 检出 / 检入 / 撤销检出按 **ENG_DOCUMENT** 分派（`checkoutEngineeringDocument / checkinEngineeringDocument / undoCheckoutEngineeringDocument`）：数模的载体是工程数据，而行的 `oid` 是**源文件 oid**（`row-key=sourceCkfileOid`），必须换用 `engDocOid`，否则接口会报对象不存在
  · 「设置生命周期状态」按 `typeDefinitionCode=ENG_DOCUMENT` 打开共享弹框；已实测 `GET /softtype-instances/{engDocOid}/lifecycle-states` 正常返回候选（草稿 → 工作中可达）
  · 「下载装配数模」= 本产品下**全部**数模源文件打包，不是逐个 `window.open`（浏览器会把连续弹窗当广告拦掉，用户拿到几个算几个）
- 后端（新增二进制接口）：
  · `CadConvertService.packageModels(containerOid)`：按容器取数模行 → 逐个读源文件 → 写 zip；条目名 `工程数据编码-原文件名`（不同工程数据下常有同名 `body.stp`，不加前缀解压就互相覆盖），撞名补序号，绝不静默丢文件
  · `GET /api/cad-visualizations/models/package`：`application/octet-stream` + `Content-Disposition`；没有任何可下载文件时返回 **404**（不发空包）；整包在内存拼装后再写出，保证"要么完整、要么 404"，不会给用户半截 zip
  · 前端只能用 axios 取 blob 再触发保存 —— `window.open` 带不上 Authorization，会 401
- 验证：`mvn -o compile` 0 错、前端 lint 0 / HMR 无报错；`GET /api/cad-visualizations/models/package?containerOid=b6bba136…` 返回 4,380 字节 zip，内含 `DOC-202610-0002-E-SCE22-D15-L20-M4-S0.stp`（17,258 字节）；ENG_DOCUMENT 的检出 / 撤销检出均 200（验证后已还原）

#### 检出产生的副本迭代复用源版本的轻量化产物；缩略图加检出锁角标
- 现象：检出后那一行变成"未转换 / 模型信息 `—` / 缩略图没了"，看起来像检出把转换结果弄坏了
- 根因：`EngineeringDocumentCheckoutProvider#checkout` 检出时创建 iteration+1 的副本迭代，并**继承同一个 `ckfile_oid`**（文件根本没变）；而 `selectModelsByContainer` 的 LATERAL 只按 `c.eng_doc_iteration_oid = ei.oid` 取记录 —— 新迭代名下自然没有记录，于是显示未转换、缩略图为空
- 后端：LATERAL 增加"沿检出副本 → 源版本复用一次"的分支，条件是 `ei.derived_from_oid = c.eng_doc_iteration_oid` **且源版本与当前迭代的 `ckfile_oid` 相同**（文件没换才复用；换了文件必须重新转换，不能拿旧模型冒充）；排序改成 `(c.eng_doc_iteration_oid = ei.oid) DESC, c.created_at DESC`，保证本迭代自己的记录永远优先，不会被旧版产物顶掉
- 前端：缩略图右下角加检出锁角标（橙色圆底 + 白色描边，tooltip 显示检出人与检出注释）—— 与「检出」列的标签互补，扫列表时先看形状再看锁
- 验证：检出 `DOC-202610-0002`（A.1 → A.2）后 `GET /api/cad-visualizations/models` 返回 A.2 行 `status=SUCCESS / triangleCount=571 / partCount=1 / thumbnailCkfileOid=947a5162… / glbCkfileOid=a494a164…`（全部复用 A.1 的产物）；前端 lint 0、HMR 无报错

#### 缩略图即三维浏览入口（点图直接进预览）
- 现象：列表里缩略图只是一张图，想看三维得先展开「操作 → 三维浏览」；用户直觉就是点那张图
- 前端（`ProductLineDashboard.vue`）：缩略图外层绑定点击 → `openCadView(record)`；`a-image` 关掉自带的放大预览（否则点一下弹出大图，与"进三维"的预期冲突）
- 可点条件抽成 `canOpenCadView(record)`（`status=SUCCESS` 且有 `glbCkfileOid`），与操作菜单里「三维浏览」的置灰判断**共用同一处** —— 两处各写一遍条件，早晚出现"图能点、菜单却置灰"
- 交互：可点时鼠标手型、hover 描边变主色并上浮 1px、原生提示「三维浏览」；检出锁角标加 `@click.stop`，点锁不会误进预览
- 验证：前端 lint 0、HMR 无报错；当前 A.2 行（`SUCCESS` + `glbCkfileOid`）走可点分支，未转换 / 转换失败的行点击无反应

#### 修掉打开三维预览时的 `TypeError: Cannot use 'in' operator to search for 'type' in null`
- 现象：点缩略图进三维浏览，模型能正常显示，但页面顶部弹出 Vue 运行时错误（栈指向 ant-design-vue `Empty`）
- 根因：`CadStructureTree.vue` 的空态写成 `<a-empty :image="null">`（意图是"不要占位图"）。ant-design-vue 的 Empty 里用 `typeof image === 'object' && 'type' in image` 判断是否为简单图 —— `typeof null` 同样是 `'object'`，于是 `'type' in null` 直接抛 TypeError
- 为什么只在打开瞬间闪一下：结构树数据是异步拉的，弹窗刚打开时 `structureNodes` 为空、正好走空分支；数据到位后换成 `a-tree`，控制台只留下一条红
- 修复：改成 `:image="false"`（ant 里 `false` 才表示不渲染图片，与 `StartProcessModal` / `AdminManager` 的既有写法一致），并在注释里写明 null 的坑
- 验证：前端 lint 0、HMR 无报错；全仓已无 `:image="null"` / `:image="undefined"`

#### 三维浏览改为独立整页（弹窗装不下复杂装配体）
- 现象：三维预览开在 960px 弹窗里，装配体一复杂，画布可视区域和右侧结构树都被挤得很窄，还会与页面滚动条互相干扰
- 新页面 `views/product/CadViewerPage.vue`，路由 `product/cad-view/:iterationOid`（name `CadViewer`）：
  · 顶部只放「返回 / 工程数据编码 / 文件名 / 版本 / 下载数模」，其余高度全部让给画布；`.cvp` 撑满内容区（`.main-layout` 为 100vh，`.layout-content` 是确定高度的 flex 项）
  · 页面自己按 `iterationOid` 调 `GET /api/cad-visualizations/latest` 取产物 —— 刷新、直接粘贴 URL 都能用，不依赖上一页内存里的对象
  · 没有 GLB 时按转换状态给出人话（进行中 / 失败原因 / 格式不支持），不是一片空白
- `ProductLineDashboard.vue`：`openCadView` 改为 `router.push`（带 `code / title / version / fileOid` 查询参数）；删除 `a-modal`、`cadViewVisible / cadViewRecord / cadViewGlbUrl / cadViewStructureUrl / cadViewStats` 与 `.pl-cad-viewer` 样式，`CadViewer` 的 `defineAsyncComponent` 一并移除（three.js 不再进本页 chunk）
- 缩略图与「操作 → 三维浏览」两个入口都走同一个 `openCadView`，行为一致
- 验证：`CadViewerPage.vue` 经 Vite 转译 HTTP 200（34 KB，无编译错误）；前端 lint 0；`ProductLineDashboard.vue` 已无弹窗残留引用

#### 页签状态记到 URL（`?tab=`），整页重载后不再被扔回「物料清单」
- 现象：在产品数模页签里撤销检出，随后页面整体重载，页签跳回物料清单，看起来像"撤销检出把页面刷了"
- 根因：`stageView` 只是 `ref('bom')`，任何一次整页重载（F5 / 前端热更新 / 外链跳回）都会把页签打回默认值。撤销检出本身只做数据刷新（`refreshAfterCheckoutChange`：重取文件夹内容 + 当前页签的 BOM / 数模列表），全仓没有任何 `location.reload` —— 刷新不是这个操作发起的，但页签丢失让它看起来像
- 修复：`stageView` 初值读 `route.query.tab`（按白名单校验，非法值回落 `bom`），变化时用 `router.replace` 写回 URL；再加 `watch(() => route.query.tab)` 处理浏览器前进/后退
- 验证：前端 lint 0、HMR 无报错；`/product/{oid}?tab=cad` 可直接打开产品数模页签，撤销检出后停在原页签

#### 三维浏览：装配结构树移到画布左侧
- 与 CATIA / SolidWorks 的模型树同侧 —— "左边选零件、右边看模型"是从 CAD 带过来的肌肉记忆，树放右边每次都要多找一眼
- `CadViewer.vue`：`.cv-body` 内 DOM 顺序改为「结构树 → 画布」；`.cv-structure` 的 `border-left` 改成 `border-right`（分隔线要落在树与画布之间那一侧，否则线画到屏幕最外缘）
- 共享组件：文档详情的 CAD 页签同步生效；画布宽度变化由既有 `ResizeObserver` 自适应，无需额外处理
- 验证：前端 lint 0、HMR 无报错

#### 三维浏览：操作按钮改为画布右侧浮动竖排
- 顶部横排按钮吃掉了整整一条高度（装配体越复杂越缺竖向空间），改为浮在画布右上角的竖排按钮组（半透明白底 + 细边框 + 阴影），不再参与布局
- 剖切滑块挪到画布左下、测量提示挪到画布左上，避免与右侧按钮列叠在一起；滑块加 `flex:1; margin:0`，否则在浮层里会塌陷成 0 宽 / 撑高一行
- 加载与错误遮罩的顶部留白（原先为避开工具栏而设的 56px）收掉，改为 `inset: 0 0 32px 0`
- 按钮仍保留文字（只加 tooltip），避免"只剩图标认不出"
- 验证：前端 lint 0、HMR 无报错

#### 三维浏览：左下角增加 XYZ 方位坐标罗盘（可开关）
- 为什么不在世界原点画 `AxesHelper`：它按模型尺寸画三根长线，装配体一加载就穿进零件内部，既看不清又挡视线
- 实现：独立小场景 + 独立 viewport 叠在画布左下角（92px）；小相机只继承主相机**朝向**、位置固定 —— 罗盘永远正对屏幕，转的是"当前视角下 XYZ 指向哪边"；白圆底 + 红/绿/蓝箭头 + X/Y/Z 字母（canvas 纹理）
- 开关：右侧按钮列新增「坐标」，默认开启
- 细节：渲染罗盘时 `autoClear=false` 且只 `clearDepth()` —— 否则要么盖出一块黑底，要么被模型挡住；剖切滑块从画布左下改到底部居中，给罗盘让位；卸载时逐个释放 sprite 贴图（`material.dispose()` 不会释放 `texture`）
- 验证：前端 lint 0、HMR 无报错

#### 三维浏览：测量结果改为画布顶部大卡片，测量标注加粗
- 现象：测完只有一个读数塞在底部状态栏，和"三角面 / 零件 / 单位"同字号混排，几乎找不到；标注线是 1px 细线，深色模型上根本看不出量的是哪一段
- 读数：顶部居中弹出结果卡片（20px 加粗等宽数字 + 「测量距离」标签 + 清除按钮）；底部状态栏不再重复显示，避免两个数字打架
- 标注：`THREE.Line` 换成圆柱模拟的粗线（WebGL 的 `linewidth` 在多数驱动上被忽略、恒为 1px），两端加小球；`depthTest:false` + `renderOrder` 让标注始终浮在模型上方，不会被零件挡住
- 粗细随模型包围盒对角线走（`size.length() * 0.006`）—— 小零件上不糊成柱子，大装配上也看得见
- 交互：点第一个点先落一个红球（确认点中了），重新开始测量时先撤掉上次读数
- 验证：前端 lint 0、HMR 无报错

#### 新建工程数据改用 ENG_DOCUMENT 自己的布局（原先是 DOCUMENT 的副本）
- 现象：产品数模页签的「新建工程数据」弹窗里全是文档字段（文档编号 / 文档名称 / 主文档 / 文档类型），看不到 CAD 名称 / CAD 类型 / 来源 CAD 工具 —— 前端确实按 `ENG_DOCUMENT/create` 取布局，但**布局内容**当初是照抄 DOCUMENT 的（pack note 原话"ENG_DOCUMENT 库里当时无布局，其 4 条复制自 DOCUMENT"）
- pack：`platform-base/softtypes.json` 的 `pageLayouts.byHost.ENG_DOCUMENT` 重写 create / update
  · create：工程数据编号 / 名称 / 类型（工程数据类型选择器）/ 描述 / **数模主文件** / **CAD 名称** / **CAD 类型** / **来源 CAD 工具** / 研发阶段 / 文件夹 / 所属产品 / 附件
  · update：同上（去掉编号，名称与 CAD 属性可改）
- 控件：`DocumentTypeSelect` 增加 `rootCode`（默认 `DOCUMENT`）；新增控件码 `eng-document-type-select`（`RenderFields` + `widgets/catalog`）—— 否则类型下拉只列文档类型，选不到 FOOTPRINT / PCB_LAYOUT / SCHEMATIC / SYMBOL
- 库：4 个租户 × create/update 共 8 行换新布局（装包对已有布局只追加字段、不改 label，故直接改）；改前备份 `target/eng-doc-layouts-backup.csv`
- 后端无需改：`EngineeringDocumentServiceImpl#createInstance` 本就读取 `cadName / cadType / cadTool / ckfileOid`
- 验证：`GET /api/page-layouts/by-code?entityCode=ENG_DOCUMENT&operationCode=create` → `hasCad=true hasEngType=true`；库中字段 = number / name / typeDefinitionCode / description / ckfileOid / cadName / cadType / cadTool / stageOid / folderOid / ownerOid / attachmentOid；pack `ConvertFrom-Json` 通过；`mvn -o compile` 0 错、前端 lint 0
- 边界：ENG_DOCUMENT 的 `detail` / `list` 布局本轮未动（仍是早期文档样式）

#### 新建零组件不再看「阶段管理对象」，阶段模板里也去掉 PART（无表结构变更）
- 背景：零件已无「所属阶段」属性（2026-10-22 起独立于阶段与文件夹），但入口仍按阶段模板的 `managedObjectTypes` 判显隐 —— 切到"概念阶段"（只声明 FUNCTIONAL/DOCUMENT）时，型号页的「新建零组件」会凭空消失
- 前端：`ProductLineDashboard.vue` 4 处「新建零组件」（物料清单工具栏 + 研发资料的自有 / 继承 / 功能架构列表）去掉 `v-if="stageManagedObjects.has('PART')"`；DOCUMENT / FUNCTIONAL 的判断**保留**（这两类仍按阶段管理）
- pack：4 个包的 `stages.json` 从 `managedObjectTypes` 移除 `"PART"`（smart-hardware-ipd 六阶段、automotive-apqp、military-gjb、traditional-stage-gate）
- 库（装包对已存在的阶段模板只 SKIP、不更新，故直接改）：`ck_stage_template` 23 行、`ck_stage` 17 行去掉 PART；改前备份到 `target/stage-template-managed-backup.csv` / `target/stage-managed-backup.csv`
- 验证：`select distinct managed_object_types` → 只剩 `["DOCUMENT"]` / `["FUNCTIONAL","DOCUMENT"]`；四个 pack 文件 `ConvertFrom-Json` 通过；`target/classes` 内 stages.json 已无 PART；`mvn -o compile` 0 错、前端 lint 0

#### 系列 / 型号的同名 Part 带上默认单位 `ea`（无表结构变更）
- 现象：新建产品系列 / 型号时自动建的容器同名 Part，「单位」是空的 —— 详情页空白，拿它当父件搭 BOM 时用量也没单位
- 根因：`ContainerPartProvisioner` 调 `partService.create(part, null, null, null, null)`，第 4 个参数正是迭代的 `unit`，一直传 null
- 修复：传 `ea`（单位字典里的"个"）—— 与 BOM 行"未指定单位就用 ea"同一口径（`BomLinksServiceImpl#create`）
- 存量：`PART-202610-0006 X7智能手表`（型号同名件）单位为空 → 补 `ea`；判定只认 `container_type ∈ {PRODUCT_LINE, PRODUCT_MODEL}` 且与容器同名的件，别的零件不动
- 验证：真实建 `UNIT-TEST-SERIES` → 同名件 `PART-202610-0001 / A.1 / ea` ✓；验完按 oid 逐表清理（系列 1 / 阶段 6 / 文件夹 22 / 规则 7 / 指标 6 / 团队 1 / 同名 Part 1，复查全 0）；`mvn -o compile` 0 错

#### Part 类对象详情去掉「研发阶段 / 所属文件夹」：pack 与库一起清（无表结构变更）
- 现象：零部件（PART）及其 7 个软类型（STRUCTURAL / ELECTRONIC / ELECTRICAL / PCBA / SOFTWARE / STD_PART / GEN_PART）的**详情页**仍显示「研发阶段」「所属文件夹」两个只读字段 —— 零件管理早已独立于阶段与文件夹（2026-10-22）
- 根因：`folderOid` / `stageOid` 是 2026-10-22 之前导出的布局基准里的字段；装包对**已有布局只补缺失字段、不删多余字段**（`SoftTypeInstaller#appendMissingFormFields`），所以改 pack 也删不掉库里的存量
- 改动（三处）：
  · **pack 布局**：`platform-base/softtypes.json` 的 `pageLayouts.byHost.PART` 中 `detail` 去掉 `fld-folder` / `fld-stage`（create / update / list 本就干净）
  · **pack 基本属性**：`attributes.json` 核对后 `PART_BASE` 本就无这两条（库中 PART 属性 29 条、IBA 90 条均无 `stageOid`/`folderOid`），只把注里的口径按库现状校正为 `PART 29 / 共 122 条`，并注明已退役
  · **库存量**：26 行 `detail` 布局（PART + 7 个软类型 × 各租户）用 `jsonb` 数组过滤只删这两个字段（其余字段与顺序不动），改前已备份到 `target/part-family-detail-layouts-backup.csv`
- **重新导入**：`POST /api/industry-packs/platform-base/1.0.0/install` → `INSTALLED`，报告「已为 8 个类型（PART 宿主）补齐 0 条页面布局；跳过已有 32 条（不覆盖）」，`contentHash` 更新为 `fcb6238…`
- 验证：`ck_type_page_layout` 中 `root_type_code='PART'` 的全部布局 `position('stageOid'/'folderOid') = 0`（remaining = 0）；接口 `GET /api/page-layouts/by-code?entityCode=PART&operationCode=detail` 返回 `stage=False folder=False`；`mvn -o compile` 0 错；重启后 `OOTB 默认页面布局初始化：新增 0 / 已存在 16`（不会被初始化器改回）
- 边界：`DOCUMENT` / `ENG_DOCUMENT` 的这两个字段**保持不动** —— 本轮只针对 Part 类对象

#### 「物料清单」只认当前容器：型号页不再显示挂在父系列上的零件（无表结构变更）
- 现象：X7 智能手表（型号页）「物料清单」出现两条 —— `I-WATCH-X7`（`container_oid` = 父系列）与型号自己的同名 Part；上方提示还写着"本型号及所属系列下所有零件"
- 口径（用户 2026-10-22）：只按 `p.container_oid = 当前容器 oid` 过滤，父系列的 Part 对象不展示；BOM 用量 / 使用于也只统计当前容器零件上的 BOM 行
- 配套（否则新件会"消失"）：型号页「新建零组件」的 `containerOid` 由 `parentOid` 改为型号**自身** oid（`containerType` 仍按 nodeType 取 `PRODUCT_MODEL`）—— 原先建在父系列上，只查当前容器后这些件就看不见了
- 文案/注释：`本型号及所属系列下所有零件` → `本型号下所有零件`；mapper / service / controller 的过时 javadoc 一并校正
- 验证：改后的完整 SQL 在库上执行 → 型号页 1 行（`PART-202610-0006 X7智能手表 A.1`），父系列的 `PART-202610-0002 I-WATCH-X7` 不再出现；`mvn -o compile` 0 错、前端 lint 0

#### 「终止同层分支」的校验改按并行作用域判定，不再只看源节点类型（无 SQL 变更）
- 上一版规则要求"源节点必须是并行 / 包容网关"，会在两种常见画法上误报：分支**内部**的审批节点出边（"这一支批完就收口"）、并行分支各自走到结束（没有汇聚网关）
- 现在先算一次**并行作用域**（从每个分叉网关向下标记，到同层汇聚网关为止），再判断这条边的目标是否落在块内：块内放行、块外 WARNING
- 汇聚网关之后的边**不再算块内** —— 否则作用域一路蔓延到整条主干，"配了没效果"就再也提示不出来
- 作用域只算一次：每条边重算是 O(E×N)，模板一大就白花时间
- 验证：`dsl-core` 新增 3 例（块内审批节点出边放行 / 无汇聚网关仍算块内 / 汇聚之后仍报），前端 **34 个测试文件全绿**

#### 「终止同层分支」不再是死配置：声明写进产物、导入不丢、配错有提示
- 现象：设计器里勾了「终止同层分支」，编译告警说"本层只输出声明"，但产物里**一个属性都没写** —— 声明既没进 BPMN，导入时也无处可读，配置纯属摆设
- 修复（三处）：
  - **编译**：连线写 `ckplm:terminateSiblings="true"`，同时在**源节点**上汇总 `ckplm:terminateOn="flow_a,flow_b"`
  - **导入**：两处任一可读即还原；连线属性丢失时用节点汇总兜底（sequenceFlow 上的自定义属性在部分解析器上不保留，后端 `ckplm:route` 就踩过这个坑）
  - **校验**：新增 `TERMINATE_OUTSIDE_BRANCH`（WARNING）—— 勾在非并行 / 包容分支的出边上时提示"没有同层分支可终止，开关不会生效"
- 为什么节点上要再写一份：运行期判定读的是节点属性（`ckplm:assignee` / `ckplm:passRule` 已验证可读），连线属性不保证保留；后续接运行期时不必再改产物格式
- 运行期收口见下方「局部终止落地」：本条目只覆盖声明与导入两件事
- 验证：新增编译 3 例（声明写入 / 连线属性被剥掉仍能还原 / 告警文案）、校验 2 例（分支内不报 / 分支外报 WARNING 且不拦保存）；前端 **33 个测试文件全绿**

#### 任务"无人可办"不再无声无息：落一条 WARN 到节点执行日志（无 SQL 变更）
- 现象：活动配的角色当前没有成员（或参与人表达式取到空集合）时，任务建出来既没有 `assignee`、也没有候选人和候选组 —— 不报错、不进任何人的待办，流程就停在那儿；业务方来问"怎么没人审"，服务端日志里也什么都没有
- 根因：`autoAssignSingleCandidate` 只处理"恰好一个候选人"的自动指派，对"零身份链接"直接 `continue`，不留任何痕迹
- 修复：零身份链接的任务按「实例 + 节点」落一条 `WARN` 到节点执行日志 —— 流程详情点开该节点就能看到原因与处理建议（补角色成员 / 管理员转办），不必等业务方来问
- 去重：同一个卡住的任务在后续每一步都会被扫到，按「实例 + 节点 + 标记」判重，不把日志刷成一屏
- 边界：写日志失败只记 debug —— 可观测性问题不该升级成流程故障
- 验证：`mvn -o test` **63 例全绿**，改动文件 lint 无告警

#### 「部门主管 / 发起人上级」审批人策略：先加"配了必炸"护栏，运行期补齐后清单已清空（无 SQL 变更）
- 现象：设计器里选「部门主管」或「发起人上级」，流程能存能部署，但走到那个节点时**任务建不出来** —— 上一步的「同意」跟着回滚，界面只丢一个内部错误，看起来就是"流程卡死"
- 根因：编译层按契约输出 `flowable:assignee="${ckplmDeptLeader}"` / `"${ckplmInitiatorLeader}"`，但后端**没有任何组件写入这两个变量**，平台组织架构（`ck_organization` / `ck_user`）里也没有"主管 / 上级"这条关系；Flowable 对未知标识符是**抛异常**而不是给 null，所以这不是"指派不到人"的软问题
- 修复（三处一起收，判定清单只有一份 `UNSUPPORTED_ASSIGNEE_STRATEGIES`）：
  - **校验**：新增 `ASSIGNEE_STRATEGY_UNSUPPORTED`，记为**错误**、部署前拦下，并给出可操作建议（换策略，或把活动放到「设置审批人」下游）
  - **编译**：不再输出那个会炸的 assignee（任务至少还能被转办/认领，不至于把流程打断），改为醒目的告警
  - **面板**：下拉项标注「（运行期未实现）」，但**保留选项** —— 存量模板里已经存了这个值，删掉选项会让下拉框显示空白
- 例外：**被「设置审批人」覆盖**的活动不拦 —— 那里的人员由发起人在设置页指定（`ckplmSetupAssignees_*`），策略只作候选范围声明，编译层根本不会输出该表达式；拦它会把完全跑得通的流程判死
- 收口：运行期两条解析链都已落地（见下方「新增功能」），`UNSUPPORTED_ASSIGNEE_STRATEGIES` 已清空 —— 拦截 / 编译告警 / 面板标注三处的护栏代码**保留**，下一个"运行期没跟上"的策略把 code 加回去即可生效
- 验证：`dsl-core` 改为断言"两个由组织解析的策略都不拦、缺部门字段仍按 `MISSING_ASSIGNEE` 拦"，`compile.test.ts` 断言"产物只留 `ckplm:assignee` 声明、不含 `${ckplmDeptLeader}`"；前端 **32 个测试文件全绿**

#### 会签结论改为按本轮所有票汇总（一票否决不再是"最后一个人说了算"；无 SQL 变更）
- 现象：3 人评审里 1 人驳回、2 人同意，只要**最后办的是同意**，流程照样走「通过 → 发布」—— 设计器里勾的「一票否决」形同虚设
- 根因：`approved` 是**实例级**变量，并行会签的每个实例 `complete` 都写一次，后写必盖前写；`passRule` 当时只影响"什么时候离开会签"，不影响"最终结论"，`PERCENT` / `COUNT` 同样被最后一票代表
- 修复：`ProcessServiceImpl#completeTask` 新增 `tallyCountersign` —— 每一票记在**多实例根执行**的局部变量（`ckplmVoteApprove` / `ckplmVoteReject`）上，按 BPMN 的 `ckplm:passRule` 重算本轮结论后才写 `approved`；判定口径抽成 `process/support/CountersignRule`（`PERCENT` / `COUNT` / `VETO` / 未配置＝全部同意），与编译层写进 `completionCondition` 的口径逐条对齐
- 按轮清零：票数挂在根执行的作用域上，本轮活动一结束即销毁 —— 「驳回退回 → 重新提交」再走一遍会签时不会带着上一轮的票
- 提前收口：本轮已不可能通过时置 `ckplmVetoed`（同一作用域），编译层在 `completionCondition` 里以 `|| ckplmVetoed` 短路 —— 一票驳回后多实例立即结束、剩余评审人的任务随之取消，不必把注定失败的票再办一遍；未重新部署的老流程没有这段短路，只是会白办，**结论不受影响**
- 兜底不阻断办理，也不凭空多出否决：非多实例 / 找不到根执行 / 应到人数取不到（比例口径算不出来）/ 汇总过程异常，一律退回"这一票就是结论"的老口径；但 `ckplmVetoed` **任何时候都写** —— 它已经被编译层拼进 `completionCondition`，变量缺失会让条件求值直接报错（任务办不掉比结论不准严重得多）
- 根执行按**结构**定位（沿父链取"同活动的最上层执行"，再往上第一个活动 id 不同的执行即边界），不依赖引擎内部的 `nrOfInstances` 局部变量 —— 后者只用来取"应到人数"，名字或存放方式一变也不会让整段逻辑静默失效
- 兼容：既有会签流程**无需重新部署**（判据取自 BPMN 里已有的 `ckplm:passRule`）；包内 `doc-technical-review` 的 `completionCondition` 已按新编译产物同步补上短路，新装租户装完即生效
- 同步更新前端契约文案：一票否决的编译告警不再指向"需运行期置 `ckplmVetoed` 后中断"的占位说明；`builtin-variables.ts` 的 `approved` 说明补上"会签取本轮汇总结论，不是最后一票"
- 验证：新增 `CountersignRuleTest` 12 例（比例 / 票数 / 否决 / 否决压比例 / 坏值 / 应到人数未知 / 四种提前收口口径），`mvn -o test` **63 例全绿**；前端 `npm test` **32 个测试文件全绿**（`compile.test.ts` 三条断言随产物更新）

### 新增功能（Features）

#### 变更请求 / 变更任务补上页头状态统计（原来只有变更通告有）
- 用户反馈（2026-11-01）：*"变更请求没有统计信息"* —— 页头状态概览此前只在通告页签出现，因为只有 `/change-notices/stats` 一条接口
- 后端：新增 `GET /api/plm/change-requests/stats`、`GET /api/plm/change-tasks/stats?assignee=`（`assignee` 为空 = 全部），
  统一返回 `{ total, counts }`；`ChangeRequestMapper` / `ChangeTaskMapper` 各加一条 `countByStatus`（任务按 `assignee` 可选过滤），
  服务层先把状态机里的每个状态铺成 0 再填数 —— 前端要的是"每一项都在"，不是"有单的才出现"
- 前端：`ChangeControl` 的页头概览改为**按页签取数**（`STAT_PILLS` 由 `utils/changeFlow` 的状态标签表生成，以后加状态不用改这里），
  三套计数各自缓存、互不干扰；状态筛选经 `v-model:status` 下发给面板，「只看我的」也上提到父级（`v-model:only-mine`）——
  页头计数必须按同一口径取，否则勾选后数字对不上。两个面板相应去掉工具栏里的状态下拉（通告此前已去），列表一变回 `changed` 触发重取计数
- 顺带补全：通告页签原来只列 6 个状态（漏了审批中 / 已关闭 / 已驳回 / 已取消），现在按状态机全量显示
- 验证：接口逐一核对（`change-requests/stats` → `total 1 / ACCEPTED 1`；`change-notices/stats` → `DRAFT 1`；`change-tasks/stats` 全部与按人各一组 `total 0`）；
  实页三页签截图（`target/change-stats-request|notice|task.png`）：请求页 8 枚标签 `1 全部* / 0 草稿 / 0 已提交 / 0 评审中 / 1 已受理 / …`，点「已受理」选中态转移、列表按 `ACCEPTED` 重取；通告页 10 枚；任务页 5 枚 + 工具栏「只看我的」；页头仍是 65px 未换行；控制台无异常；lint 0

#### 变更请求 / 变更通告 / 变更任务 三个列表的版式对齐「物料清单」（纯前端）
- 用户要求（2026-11-01，两张截图对比）：*"变更请求和变更通告、任务的页面样式参照 [物料清单] 优化"*
- 表格密度：三个 `DataTable` 由 `size="middle"` 改 `size="small"`，表头 / 行高 / 字号与物料清单一致（实测表头 39px、行高 41px；物料清单行 56px 是因为多了缩略图列）
- 表格上方加统一「口径条」（`.ck-list-bar`，与物料清单 `.pl-bom-materials-bar` 同一版式）：左 = 这段列表是什么（ECR/ECN/ECT 的流转口径），右 = 共 N 条
- 工具栏归位：状态筛选（+「只看我的」）移到 `#toolbar-left`，主按钮（提变更请求 / 新建变更通告）留在右侧与搜索、视图同排 —— 与物料清单 / 交付件面板同一位置
- 变更通告：原来的 6 张统计卡（全部/草稿/已提交/已批准/实施中/已完成）压成**页头右侧**的一行可点标签，数字即条数、点一下按该状态过滤、再点取消（「全部」= 清空）—— 一排 KPI 曾把表格压下 ~70px，还和下面的状态下拉筛同一件事
- 页头做成一条**带边框的横条**（用户 2026-11-01：*"上面没有边框，统计信息没有移动到顶部"*）：标题在左，状态概览 + 变更设置在右；没边框时这一块与页签、表格黏在一起
- 页头视觉与产品页 `.pl-header` **逐项对齐**（用户同日第二张截图：*"红色框的样式对齐"*）：白底 + `1px solid #f0f0f0` + `8px` 圆角 + `0 1px 4px rgba(0,0,0,.04)` 轻阴影 + 顶部 `3px` 品牌渐变条（`::before`，故容器 `position:relative; overflow:hidden`），内边距 `12px 20px 10px`、下外边距 `12px`，标题 `22px/700`（`letter-spacing:-.3px`、`#1a1a2e`）
- 状态概览改用产品页 `.pl-stat-pill` 那种胶囊（20px 圆角、`#fafbfc` 浅底、细边、条数在前状态在后），选中态 `#e6f4ff` 底 / `#91caff` 边 / `#1677ff` 字 —— 与页头其它元素同一套形状语言
- 页头距顶栏 **18px**（用户 2026-11-01 指定）：`.chg-page` 原先写 `padding: 16px 20px`，叠上外层布局已有的 16px 后实测 32px（比产品页的 16px 远一倍）；改为 `padding: 2px 0 16px`，合计 18px
- 左右宽度对齐产品页（用户同日：*"红色框的宽度对齐后面的截图"*）：原来 `.chg-page` 又加了 20px 左右内边距，叠上布局的 24px 后整页比产品页窄 40px；去掉后两侧间隙 24px、页头卡片宽 1180px，与产品页 `.pl-header` 完全一致（同视口实测：两页 `cardLeft` 均 174、`gapLeft/gapRight` 均 24）
- 状态与计数归父组件（`ChangeControl`）：页头是父级 DOM，子组件 `Teleport` 上去在首屏会报 `Failed to locate Teleport target`（整棵树一次性挂载，目标节点还没上文档）并连带把面板渲染打断；改为父级取 `/change-notices/stats`、经 `v-model:status` 下发筛选、面板列表一变回 `changed` 重取计数
- 细节：`a-tag` 统一 `size="small"`（编码/优先级/状态/免审），三个列表补 `empty-text`（暂无变更请求 / 通告 / 任务）
- 验证（真实页面，Chrome + CDP，租户账号 + 临时 token，测完已删）：三个页签逐页截图核对（`target/change-head-notice|change-head-request|change-request|change-notice|change-task.png`）；页头实测 `1px rgb(240,240,240)` 边框 / `0 1px 4px rgba(0,0,0,.04)` 阴影 / `3px` 渐变条 / 内边距 `12px 20px` / 高 65px（与产品页页头同一套取值）；通告页标签 `1 全部(选中) / 1 草稿 / 0 已提交 / 0 已批准 / 0 实施中 / 0 已完成`，切到变更请求页签标签整行消失（该页签用工具栏左侧状态下拉）；点「草稿」→ 选中态转移且列表按 DRAFT 重取，再点一次回到「全部」；控制台无 Teleport / 渲染报错；lint 0

#### 「适宜 / 不适宜 / 所有」过滤的可见范围：加上项目经理（新增权限位 `canViewTailored`）
- 用户要求（2026-11-01）：*"产品经理和项目经理，以及租户管理员"*
- 后端新增**只看不改**的权限位：`ProductTeamRoleResolver.canViewTailoredUnder`（团队角色 = 产品经理 / 项目经理，管理员豁免）、`InstancePermissions.canViewTailored`、`MethodologyInstanceServiceImpl` 两处装配；裁剪（标记不适用 / 恢复 / 改强制）仍限产品经理
- 前端 `canSeeTailored` 改读该权限位（旧版后端没有该字段时退回"能裁剪即可见"，不炸）
- **顺手修掉一个存量 bug**：`ck_role` 里同名「项目经理」有两条编码（`PM` 与 `PROJECT_MANAGER`），`name → code` 映射实测取到 `PM`，而 `START_ROLES` 只写了 `PROJECT_MANAGER` —— 项目经理一直被判成"没有发起流程权"。`START_ROLES` 与 `TAILORED_VIEW_ROLES` 现已同时收录两个码，并在注释里写明原因
- 验证（真实接口，临时把 `Kiween.Hu` 在本系列的团队角色改成「项目经理」、造 1 小时临时会话，测完两样都已回滚、临时 token 已删）：项目经理 → `{admin:false, canTailor:false, canStartProcess:true, canViewTailored:true, myRoleCodes:["PM"]}`；产品经理（管理员）→ 四项全 true；系统工程师 → `canViewTailored:false`
- `mvn -o compile` 通过、`npm run build` 通过（32s）、lint 0

#### 阶段内容面板增加「适宜 / 不适宜 / 所有」过滤（产品经理视角）
- 用户要求（2026-11-01，截图框住交付件工具栏左侧空白）：*"产品经理页面增加过滤，适宜/不适宜/所有"*
- 改动仅 `MethodologyInstancePanel.vue`：`DataTable` 的 `#toolbar-left` 槽里放 `a-radio-group`；`适宜` = 未裁剪、`不适宜` = `disabled`（本项目不适用）、`所有` = 全量，与原有「本项目不适用」标签同一语义
- **默认选中「适宜」**（2026-11-01 用户：*"默认到适宜"*）：裁剪过的条目是"本项目不做"，打开阶段内容先看要做的那些，想看全貌点「所有」
- 只在**能看见裁剪行的人**（`canSeeTailored` = 产品经理 / 项目经理 / 管理员）出现，档位永远可选（本阶段没有裁剪行时点「不适宜」得到空态说明，见「缺陷修复」）
- 统计口径不变（仍按 `displayRows`），过滤**真的藏掉行**时才在「共 N 项，其中强制…」后补一句「，已隐藏 6 项（见「所有」）」；本阶段无裁剪行或已选「所有」时不写，免得每块面板都挂一句噪音（此时原有「已裁剪 N 项（本项目不适用）」也不会重复报同一个数）
- 验证（实页，Chrome + CDP，临时 token 测完已删）：默认选中「适宜」；`计划阶段`（8 条 / 0 条裁剪）8 行、不写附加说明；`概念阶段`（8 条 / 6 条裁剪）2 行 + 「，已隐藏 6 项（见「所有」）」；点「所有」→ 8 行，其中 6 行带删除线并显示「已裁剪 6 项（本项目不适用）」；lint 0

#### 上市时间偏差可算：目标上市日期（IBA）+ LDCP 通过日
- 用户要求（2026-10-30，截图框住「上市时间偏差」行）：*"同目标成本一样，构建上市时间"*
- 包：`electronics-core/attributes.json` 新增第 8 组 `LAUNCH_TARGET`，属性 `TARGET_LAUNCH_DATE`（`DATE`，非必填），`bindTo: ["PRODUCT_LINE","PRODUCT_MODEL"]`；`pack.json` 的 `ATTRIBUTE.count` 27 → 28
- 接口：`ProductLineController` 新增 `GET / PUT /api/product-lines/{oid}/target-launch-date`（与目标成本同一套路由 / 权限 / 清除语义）；只收 `yyyy-MM-dd` —— 这是用来相减的日期，存成 `2027/3/31` 之后算不出天数，格式错直接 400
- 指标：`TIME_TO_MARKET_DEV` = LDCP（上市决策评审）通过日 − 目标上市日期，单位天，正数延期 / 负数提前 / 0 按期；只认 `decision ∈ {GO, GO_WITH_CONDITION}` 的门 —— Hold / Terminate / 退回重做都不算"上市这件事发生了"。实际日期不取状态字段、不取创建时间
- 前端：`ExecutiveBoard` 新增「上市时间」卡片（目标日期就地编辑 `a-date-picker`，偏差显示 `+10 天`），与「成本达成」共用 `canManageTarget` 权限判断
- 层级与取数依据：`smart-hardware-ipd/metrics.json` 的 `TIME_TO_MARKET_DEV` 由 `C / -` 改为 `B / TARGET_LAUNCH_DATE / LDCP`，同步库里 `ck_metric_template` + `ck_metric`（2 + 4 行）；`MetricEvaluator` 的文档从「三项不碰」改为「两项不碰」（只剩 `REUSE_GAIN` / `QUALITY_LOSS_COST`）
- 验证（真实接口，租户 CKAI-TECH，系列 `i-watch-x`）：GET 初始 `null` → PUT `2026-11-30` → 读回 `2026-11-30`；PUT `2026/11/30` → 400「格式应为 yyyy-MM-dd」；LDCP 未过 →「上市决策评审（LDCP）还没通过…」；临时把 LDCP 置为 `GO / 2026-12-10` → `value=10`、`valueText="计划 2026-11-30 / 实际 2026-12-10（延期 10 天）"`；改成 `HOLD` → 回到「还没通过」；门与目标日期均已回滚。`mvn -o compile` 通过、`npm run build` 通过（55s）、lint 0

#### 器件准入率 / 优选率改口径：读 AML 的认证状态（`approved_status`）
- 用户反馈（2026-10-30，截图框住「器件准入率」行）：*"器件的准入率可以计算吗？"* → 拍板：*"用 AML 的认证状态"*
- 原口径读 IBA（`QUALIFICATION_STATUS` / `PREFERRED_LEVEL`），但这两个属性只分配给 ELECTRONIC，且没有任何表单入口，全库 `ck_type_iba_data` 里 PART 实体 **0 行** —— 指标永远「暂未评估」。更硬的一层：`i-watch-x` 里 5 个件是 PART / PCBA / STRUCTURAL×2 / ELECTRICAL，**一个 ELECTRONIC 都没有**，分母恒为 0
- 新口径：`QUALIFIED_RATE` = 有 AML（`ck_part_aml.approved_status ∈ {PREFERRED,APPROVED}`）的可认证件 / 可认证件（`type_definition_code ∈ {ELECTRONIC,PCBA}`）× 100；`PREFERRED_RATE` = 有 `PREFERRED` 料号的可认证件 / 可认证件 × 100。同一器件挂多个 MPN 只算一件（`COUNT(DISTINCT p.oid)`）
- 为什么这样切：AML 是「器件 → 厂商料号」面板（`PartMpnPanel.vue`）和元器件认证申请流程**一直在写**的表，属于既有业务事实，不必让人再抄一遍到 IBA；"没有 AML"就是"未准入"，照常出 0%（与 ALT_COVERAGE 同类，都属于表里的事实），只有容器内一个可认证件都没有时才显示「本容器无可认证器件」
- 结构化：`MetricFactsMapper` 新增 `countCertifiableParts` / `countCertifiablePartsWithAmlStatus`；`MetricEvaluator` 删掉 IBA 侧的 `qualFilled/qualQualified/prefFilled/prefPreferred`，改为 `certifiableParts/qualifiedParts/preferredParts`；`selectPartIbaValues` 的取数收敛到只剩 `PART_LIFE_CYCLE`（停产风险仍是 IBA 口径，未动）
- 同步文案：`metrics.json` 的 `source` / `note`，以及库里 `ck_metric_template.source` / `ck_metric.source`（3 + 5 行 × 2 项，仅文案）
- 验证（真实接口，租户 CKAI-TECH，系列 `i-watch-x`，可认证件 = 控制器 PCBA 1 个）：无 AML → `0%` / `0/1`；临时插一条 `PREFERRED` → `100%` / `100%`；改为 `APPROVED` → `100%` / `0%`（优选不算）；改为 `DISALLOWED` → `0%` / `0%`；删除临时行后回到 `0%`（测试数据已清理）。`mvn -o compile` 通过、lint 0
- 未动：`EOL_RISK_COUNT` 仍读 IBA `PART_LIFE_CYCLE`（同样没人填）—— 若也改用 AML 的 `lifecycle_state`，需再拍一次口径

#### 成本达成率接通取数：`TARGET_COST` / BOM 卷积（口径 target / actual）
- 用户反馈（2026-10-30，截图「成本达成」已填 `¥ 1,000.00`）：*"当前已经有目标成本了，可以进行评估了"*
- 取数：`MetricEvaluator.collect()` 新增两侧事实 —— 目标 = `MetricFactsMapper.selectTargetCost(ownerOid, 'TARGET_COST')`（**只认本容器**那一行 IBA，型号没填不回溯父系列）；实际 = 新方法 `BomLinksService.containerCurrentCost(ownerOid)`
- `containerCurrentCost` 与 `containerCostTrend` 同口径（顶层件 = 容器内无父件的零件，逐个 `BomCostCalculator` 卷积后求和；系列并入旗下型号），但**不回放 BOM 快照** —— `GET /api/instances` 每个容器都会调一次，而趋势接口会为每个顶层件逐份重算历史点
- 口径定案：**达成率 = 目标 / 实际 × 100，≥100% 为达成**（原先注释写 `actual / target`，会把超支读成 >100%，与"达成率"相拗）。同步改了 `ExecutiveBoard` 的卡片说明、`metrics.json` 的 note，以及库里的 `ck_metric_template.source` / `ck_metric.source`（3 + 5 行，仅文案）
- 不出数的情况全部写进 `value_text`：没填目标 →「缺目标成本：在系列 / 型号上填…」；容器没有可计价顶层件 →「缺实际成本…」；实际 ≤ 0 →「实际成本为 0…」—— 不估算、不拿实际冒充
- 验证（真实接口，租户 CKAI-TECH）：系列 `i-watch-x`（目标 1000）→ `value=338.98`、`valueText="目标 ¥1,000.00 / 实际 ¥295.00"`，实际值与 `cost-trend` 的 `currentTotalCost=295` **一致**；型号 `i-watch-X7` 未设目标 →「缺目标成本…」，临时设 300 后 →「实际成本为 0…」（该型号容器无 BOM 顶层件，趋势接口同样为 0），测试值已回滚
- `mvn -o compile` 通过、`npm run build` 通过（35s）、相关文件 lint 0

#### 经营战报的「成本达成」可就地设置目标成本（1 个新接口 + 3 个文件）
- 用户要求（2026-10-30，截图框住「成本达成」卡片右侧）：*"GM可以在这里设置系列和型号的目标成本"*
- 后端：`ProductLineController` 新增 `GET / PUT /api/product-lines/{oid}/target-cost`（系列与型号共用一条路由，oid 落在哪张表由 `resolveIbaHost` 判）—— 写的是 IBA `TARGET_COST`，不新增列；`targetCost` 传 null = 清除（`mergeValues` 里 null 项在逐条插入时被跳过，等价于只删这一条属性，其它 IBA 不受影响）；负数 / 超上限（1e12 元，防手滑多打 0）/ 非数字一律 400，不静默截断
- 权限：沿用「编辑系列 / 型号」同一口径（团队角色 = 产品经理，管理员豁免）；前端靠 `/api/product-lines/my-manage-permissions` 决定显不显示「设置」链接，无权时显示一行灰字说明而不是给个点了报 403 的按钮
- 前端：`ExecutiveBoard.vue` 的「目标成本」一行改为就地编辑（`a-input-number` + 保存 / 取消，回车提交，留空保存 = 清除），保存后回显接口返回值；卡片头与底部提示同步改口径（不再说「成本目标交付物上登记目标价」）
- 验证（真实接口，租户 CKAI-TECH，系列 `i-watch-x` / 型号 `i-watch-X7`）：GET 首次 `{"ownerType":"LINE","targetCost":null}` → PUT `1200000` → 读回 `1200000.0`；型号 PUT `450000` → `{"ownerType":"MODEL","targetCost":450000.0}`；PUT `-1` → 400「目标成本不能为负数」；传 null 清除后 `ck_type_iba_data` 无残留行（测试数据已回滚）。`mvn -o compile` 通过、`npm run build` 通过（41s）、相关文件 lint 0
- 未接：达成率**数值**不会因此出现 —— `MetricEvaluator` 的 `COST_ACHIEVE` 仍写死「缺目标成本」，取数与系列实际成本聚合另开一项

#### 目标成本进包：`TARGET_COST` 绑到产品系列 / 型号（3 个包内 JSON，无代码改动）
- 背景：`COST_ACHIEVE`（成本达成率）一直不出数 —— 实际价能算（BOM 卷积），目标价**全库没有登记处**；原注释指向「成本目标交付物（`DLV-COST-TARGET`）」，而那份交付物只按 `BOUND + LIFECYCLE` 挂文档，取不到结构化数值
- 改动：`electronics-core/attributes.json` 新增第 7 组 `COST_TARGET`，属性 `TARGET_COST`（`FLOAT`，元，非必填），`bindTo: ["PRODUCT_LINE","PRODUCT_MODEL"]` —— 本包唯一一组**非器件**属性；`pack.json` 的 `ATTRIBUTE.count` 26 → 27、显示名「器件属性」→「属性定义」；`metrics.json` 的 `COST_ACHIEVE` 取数依据改为 `BOM 卷积 / TARGET_COST`
- 口径写进属性 note（装包后即 IBA `description`）：系列上填 = 该产品线目标成本（预算池，系列层达成率基准），型号上填 = 该型号目标价；**两层各算各的，运行期不做隐式继承** —— 型号没填就显示「暂未评估」，不拿系列值顶替；币种与含税口径必须与 BOM 行 `unit_cost` 一致；留空 = 未设定，不写默认值
- 未做（有意）：型号「从系列带入」按钮、表单里拖入该字段（页面设计器或 `PageLayoutInitializer`）、`MetricEvaluator` 的取数与系列实际成本聚合 —— 均属代码改动，另开一项
- 验证：装包预演 `GET /api/industry-packs/electronics-core/1.0.0/plan`（租户 CKAI-TECH 的只读预演）返回 **27 条** ATTRIBUTE，其中 `INSERT | TARGET_COST | 预演：将新增属性「目标成本(元)」（FLOAT，组「目标成本（产品线 / 型号）」，绑定 PRODUCT_LINE/PRODUCT_MODEL）`；三个 JSON 可解析且 `Σattributes = 27` 与 `pack.json` 声明一致。注意运行时读的是 `target/classes` 下的包文件，改 `src/main/resources` 后需重新构建（或同步到 `target/classes`）才生效

#### 技术评审扁平化：取消「技术门」这一层（评审直接挂阶段）
- 背景：上一轮把评审内嵌进技术门看起来收了口，但界面上仍然是「一行技术门 + 展开一行评审」两层 —— 技术评审是**一场会**，不是**一道门**，为它单独造一层壳，看的人还得先理解"门下面还有会"。本轮把这层壳彻底去掉
- 行业包：`technical-gates.json` 删除，新增 `technical-reviews.json`（8 份技术评审，每条带 `stage` 直接挂阶段）；`decision-gates.json` 保留 4 道决策门（包里唯一的一类门）；`pack.json` 改为 `GATE=decision-gates.json(4) + REVIEW=technical-reviews.json(8)`
- 数据模型：`ck_review_template` / `ck_review` 新增可空 `stage_key`，`gate` 放宽为可空 —— 同一行只有一列有值（新的挂阶段、老的挂门）。老库不迁移：读的时候按 `stage_key → gate→门阶段` 回落，语义与界面都对
- 安装器：`ReviewTemplateInstaller` 支持 `stage`（预演时校验阶段码是否在包链 stages 清单里）；`GateTemplateInstaller` 退回只装门，删掉内嵌 `reviews[]` 展开与 `reviewMapper` 依赖（一条从属关系只留一种写法）
- 前端：阶段门页签内分两块 —— 「决策门（DCP）」+「技术评审（TR）」；技术评审行平铺，去掉「门下评审」列与展开行、去掉「服务于门」列；作战地图的评审状态改为按评审自身 `stageKey` 归位（老数据回落门阶段），无门时显示「技术评审」
- 老数据：库里 TR1–TR6 门行仍在，但界面不再展示（`stageGates` 过滤 `TECHNICAL_REVIEW`）；下一步若要彻底清掉，可单独跑一条 UPDATE（不在本次范围）
- 验证：`TechnicalReviewInstallTest` 3 例全绿（8 份评审全部挂阶段且 gate 为空、开发阶段 3 场会、决策门 4 道 0 评审、预演不写）；`mvn compile` 通过、`npm run build` 通过；真实账号离屏截图确认页签为「决策门 1 + 技术评审 1」两层且无技术门行
- 本地库已手工执行：`ALTER TABLE ck_review_template/ck_review ADD COLUMN stage_key VARCHAR(50)`、`ALTER TABLE ck_review_template ALTER COLUMN gate DROP NOT NULL`（`schema.sql` 同步）

#### 阶段门与评审合并为一个页签（门文档拆两份 + 评审内嵌，数据 0 迁移）
- 背景：阶段内原本有「阶段门」「评审」两个页签，而评审的归属语义只有"挂在哪道门"（`ck_review_template.gate` → `ck_gate_template.code`）——拆成两页等于把一段从属关系拆开看，用户要在两个页签间来回对"这条评审属于哪道门"；包里同理，`reviews.json` 与 `gates.json` 分家还带来安装顺序依赖（门没装出来时评审只能 SKIP 等重装）
- 行业包（`smart-hardware-ipd/v1.0.0`）：
  · `gates.json` 拆成 `technical-gates.json`（TR1–TR6）+ `decision-gates.json`（CHARTER/PDCP/ADCP/LDCP）；8 份评审**内嵌**进技术门的 `reviews[]`，原 `reviews.json` 删除；`pack.json` 用两条 `type=GATE` 声明两份文件、去掉 REVIEW 资源（引擎按资源逐条分派，拆文件不需要改引擎）
  · **门名规范化**：技术门统一「XX评审」、决策门统一「XX决策评审」，名字里不再带编号与括号补注（界面上「编码」「名称」是两列，编号重复一遍只是噪声；「投板门 / 量产门 / EOL 迭代」等补注并入 `industryNote`）
  · `sortOrder` 显式写死（技术门 1–6、决策门 7–10）—— 两份文件各自从 1 数会让同阶段内 TR 与 DCP 的先后变成不确定
- 安装器 `GateTemplateInstaller`：读门清单后**展开** `reviews[]` 落 `ck_review_template`（归属门取外层门码；内嵌条目若写了 `gate` 且不一致记 FAILED，不静默纠偏）；**门已存在（SKIP）时仍然展开评审** —— 这是老租户补装缺失评审的唯一入口，跟着门一起 `continue` 就永远补不上
- 前端：`ProductLineDashboard` 的「阶段门」「评审」两个页签合并为一个「阶段门」页签；`MethodologyInstancePanel` 内按类型分组（技术门 / 决策门），技术门行可展开看名下评审（编码 / 名称 / 检查项 / 状态 / 结论 / 发起 / 查看流程 / 裁剪），主表新增「门下评审」列显示份数；摘要改为「共 N 道门（技术门 a / 决策门 b），挂接评审 M 份」，否则合并后"共 2 道门"看不出下面还挂着几场会
- 兼容：`?tab=review` 旧链接一律归一到 `?tab=gate`（页签 key 记在 URL 上，直接删白名单会让书签静默退回物料清单）；作战地图「评审状态」下钻目标同步改为阶段门页签
- 顺带修掉 `DataTable` 的一个哑弹：展开行此前绑的是 `:expandable="{...}"` 对象，而 ant-design-vue v3 的 `rc-table` 只认**扁平 props**（`expandedRowRender` / `rowExpandable` / `expandedRowKeys`），该绑定从未生效（当时也没有调用方用）。改为扁平写法并补 `rowExpandable` / 受控 `expandedRowKeys` / `@expand`
- 数据迁移：**0 条**。库表与字段一个没动，评审仍落 `ck_review_template` 原表，`code` 与 `gate` 均未改；已装的库不受影响，重装只按 `code + 租户` 幂等 SKIP
- 验证：新增 `GateTemplateInstallerEmbeddedReviewTest`（5 例全绿）—— 技术门 6 道 + 内嵌评审 8 份（TR3 下 3 份、`TR4_HW_SCHEMATIC` 保留历史码）、决策门 4 道 0 评审、门名规范化、门已存在仍补装评审、预演一行不写但报出 14 条；`mvn compile` 0 错、`npm run build` 通过（45s）
- 已知取舍：合并后门表不再有"列显隐"开关（两张分组表各带一个会互相打架），搜索提到面板头部统一过滤 → 本次没解决，后续若需要可把列显隐状态提到面板级再用

#### 转换器并发与分道调度（大件不再堵住小件）
- 现象：默认并发 2，且只有一个 FIFO 线程池 —— 一个 31MB 整机装配跑 80 秒，期间别人提交的 50 个零件图全部干等（哪怕每个只要 1 秒）
- 转换器（CK-CADForge，独立仓库）：
  · 并发默认值改为 `max(2, min(6, 核数/2))`（本机 16 核 → 6）。上限 6 不是拍脑袋：每个 OCCT 子进程峰值 1~3GB（实测 31MB 装配体约 1.4GB），再高不是更快而是换页
  · **双车道**：按源文件大小分道（默认 ≥20MB 走 heavy），`heavy = 1` 个槽、`fast = 总并发 - heavy`。大件跑多久都只占它自己那一个槽，小件的等待时间与它无关
  · 并发口径（池大小 / 槽位数）收敛到 `worker` 一处计算 —— 两处各读一遍环境变量，迟早出现"池里能跑 3 个、槽位只放 1 个"的假并发
  · `GET /healthz` 增加 `queued / running / lanes{fast,heavy,heavyThresholdMb}`；`GET /jobs/{id}` 返回 `lane`，排查"为什么排这么久"时第一眼就能看到它走的是哪条道
  · 新增环境变量：`CADFORGE_HEAVY_CONCURRENCY`（默认 1）、`CADFORGE_HEAVY_SIZE_MB`（默认 20）、`CADFORGE_MAX_CONCURRENCY`（默认按核数）
- CK-PLM：提交线程池 2 → 4。它只负责"把任务 POST 出去"、不参与转换，但批量 200 份时若转换器不可达，2 个线程要排几分钟才能把所有记录标成 FAILED
- 验证（16 核，fast=5 / heavy=1，3 个大件 31MB + 8 个小件 17KB 同时提交）：
  ```
  t=4s   running=6  heavy_RUNNING=1 heavy_QUEUED=2 small_RUNNING=5 small_QUEUED=3
  t=8s   running=4  heavy_RUNNING=1 heavy_QUEUED=2 small_RUNNING=3 small_SUCCESS=5
  t=12s  running=1  heavy_RUNNING=1 heavy_QUEUED=2 small_SUCCESS=8
  ```
  → 8 个小件 12 秒内全部完成，期间 3 个大件始终只有 1 个在跑、2 个排队 ✓（改前：3 个大件会直接吃掉 3 个槽）
- 提醒：heavy 车道只有 1 个槽，连续提交多个大装配会串行（每个仍受 `CADFORGE_JOB_TIMEOUT` 限制）。机器内存充足时可用 `CADFORGE_HEAVY_CONCURRENCY=2` 放宽，但要同步确认内存余量

#### 数模批量提交 + 取消转换
- 背景：与 MCAD 集成后一次会新增几十份数模，逐个点操作菜单再点"生成预览"是纯体力活；而误提交之后没有任何止损手段
- 后端：
  · `POST /api/cad-visualizations/convert-batch` —— 请求体是数组 `[{iterationOid, ckfileOid}]`，**逐条独立处理**（一条坏数据不影响整批），返回分类计数 + 逐条明细：`ACCEPTED`（新入队）/ `CACHED`（内容指纹命中，复用产物）/ `SKIPPED`（转换器判定不该转）/ `FAILED`（可重试）。上限 200 条 —— 不设限会让请求线程跑十几分钟（每条都要读文件算 SHA1），网关早超时了
  · `POST /api/cad-visualizations/{oid}/cancel` —— 仅 QUEUED / RUNNING 可取消（其余返回 409）；先尽力调转换器 `DELETE /api/v1/jobs/{id}` 释放 worker（失败也继续，状态以本地为准），再落 `CANCELLED`
  · 新增状态 `CANCELLED`：不复用 `SKIPPED` —— 后者是"转换器判定不该转、重试无意义"，前者是"人主动叫停、重新转换完全合理"，混在一起用户分不清"我的点击生效了吗"
- 前端：
  · 「产品数模」列表加多选（缺迭代/源文件的行禁选）+ 工具栏「批量生成预览 (N)」；结果弹窗只列**没进去的**（跳过/失败）——一次勾 30 份，把 28 条成功也逐条列出来，用户反而找不到要处理的那 2 条
  · 「数模转换」页面：进行中的行加「取消」（二次确认，转换中会说明会终止进程）；已取消行可「重新转换」；统计卡「已取消 / 跳过」合并展示
- 修复（联调中发现）：终态保护原先只挡 `RUNNING`，取消 RUNNING 任务后转换器杀掉子进程立刻回一条 `FAILED`，把刚点的"取消"改成"失败"。现改为**终态一旦落定，任何回调都不再改写**（含迟到的 SUCCESS / FAILED）
- 验证（2026-10-26 实测）：
  · 批量：真实 STEP 31MB + 一个不存在的 ckfileOid → `accepted=1, failed=1`，失败条目带「源文件不存在：no-such-file」✓
  · 取消：提交后进入 RUNNING → `cancel` 返回 `CANCELLED`，15 秒后复查仍为 `CANCELLED | progress 5 | errorCode CANCELLED`（迟到 FAILED 已被挡住）✓
  · 重复取消同一记录 → 409「只有排队中 / 转换中的任务可以取消」✓
  · 页面实测：已取消行显示「已取消」标签 +「用户已取消」+「重新转换」；`mvn -o compile` 0 错、`npm run build` 通过（44.27s）
- 说明：批量提交后多数记录先落 QUEUED（真正的 HTTP 提交在线程池里异步做），所以"提交成功"只代表已入队 —— 进度与失败原因统一到「数模转换」页面看

#### 新增「数模转换」转换中心（全局队列 + 排队位次）
- 背景：批量提交图纸后，"我提交的那批现在到哪了"在产品页签里没法回答 —— 转换记录是按容器查的，一次只能看一个产品；批量提交缺一个统一的观察面
- 后端 `GET /api/cad-visualizations/jobs?filter=&keyword=&page=&size=`：
  · 跨容器、跨人查 `ck_cad_visualization`，LEFT JOIN 迭代 / 工程数据 / 产品系列 / 产品型号，带出"哪份图纸、挂在哪个产品、谁提交的"
  · **全部 LEFT JOIN**：`eng_doc_iteration_oid` 是逻辑外键、历史上有脏值；内连接会让"转不动的任务"从队列里消失，而这类恰恰最需要被看见
  · **排队位次在 SQL 里算**（同租户 + 更早提交的 QUEUED 条数），不问转换器 —— 否则"看一眼队列"就绑定了 CADForge 的版本，而 `submitted_at` 本来就在 CK-PLM 手里
  · 排序：进行中优先（否则一页 20 条全被历史成功记录占满，看不见队列），进行中内部按提交时间正序，与转换器 FIFO 一致
  · 统计与列表同一次返回（一次 `FILTER` 聚合出 5 个数）：分两次请求会出现"顶部写转换中 3 条、表格里一条没有"的自相矛盾
  · 租户隔离由既有拦截器负责，因此统计 SQL 必须写 `WHERE 1 = 1` —— 拦截器是把 `AND tenant_oid = ?` 追加到语句末尾的，无 WHERE 的句子会被拼成语法错误（本次踩过）
- 前端新增 `/cad/center` 页面与侧边菜单「数模转换」（放在「系列/型号」旁边，不藏进产品页签）：
  · 顶部 5 个计数卡（排队中 / 转换中 / 今日成功 / 失败·超时 / 已跳过），点卡片即切筛选
  · 列表：状态、进度条 + 阶段、排队位次、提交人、耗时、失败原因；**进行中才轮询**（4s），队列空了自动停
  · 失败/超时可直接「重新转换」；成功可跳「三维浏览」（复用 `CadViewerPage`）；`SKIPPED` 明确标注"不可重试"（转换器判定无需转换，点了也不会变）
  · 表格 `scroll.x = 1180` + 操作列 `fixed: right`：窄屏下把"重新转换"裁掉，等于看着失败记录却点不到按钮
- 验证（2026-10-26 浏览器实测）：
  · 造 5 条覆盖 `QUEUED×2 / RUNNING / FAILED / SUCCESS` 的数据 → 接口 `total=5`、统计 `queued=2 running=1 failed=1`、位次 `0 / 1`、排序"排队 → 转换中 → 历史"✓
  · 筛选：`active` 3 条、`failed` 1 条、`success` 1 条；`keyword=bracket` 命中 2 条 ✓
  · 页面实测：菜单高亮、计数卡、进度条与阶段文案（`网格化零件 42/118 · 42%`）、失败原因、操作按钮均在位 ✓
  · `npm run build` 通过（33.18s）、`mvn -o compile` 0 错

#### CAD 转换过程可见进度（转换器边转边报 + 三处入口显示进度条）
- 现象（2026-10-26 用户反馈）：*"CADForge 在做转换的过程中，看不到进度"* —— 只有一个不停转的圈，不知道在干什么、还要等多久
- 根因（两端各缺一半）：
  · **转换器只在结束时回调一次**（`main._callback` 在 `_run_job` 末尾），过程进度只存在内存里；对 CK-PLM 可见的 `progress` 实际只有 0 和 100，中间那个 10 从未发出
  · 进度只能由**子进程**算出（`pipeline.py` 的读入 / 网格化 / 写 GLB / 压缩 / 缩略图都跑在 `worker.run_isolated` 派生的子进程里），而父子之间只有「结束才写」的 `.result.json` 和一条被 `communicate()` 独占的 stdout —— **没有增量回传通道**
  · 前端三处入口里只有「产品数模」列表读了 `progress`（一行小字）；文档预览弹窗与三维浏览页只读 `status`，三维浏览页甚至连轮询都没有，只提示"请稍后刷新本页"
- 转换器（CK-CADForge，独立仓库）：
  · 新增 `app/progress.py`：子进程覆盖写 `out_dir/.progress.json`（临时文件 + `os.replace` 原子替换，Windows 上 `rename` 不能覆盖），父进程定时读
  · `worker.run_isolated` 增 `on_progress` 回调 + 伴随线程（0.5s 一轮，**只在子进程存活时**读，迟到的过程进度会污染终态）
  · `pipeline` 按阶段上报：读入 STEP 10 → 网格化 22~62（**逐零件**，文案 `网格化零件 i/N`）→ 写 GLB 65/75 → 结构树 78 → 压缩 85 → 缩略图 92 → 收尾 99；网格化是流水线里最久的一段，也是唯一天然带"第几个 / 共几个"的地方
  · `main` 收敛出唯一回调出口 `_post_status`（终态与过程进度都走它，token / 超时 / 顺序只维护一处），RUNNING 上报按 `CADFORGE_PROGRESS_INTERVAL`（默认 1s）节流；job 增 `stage`，`GET /jobs/{id}` 一并返回
- CK-PLM 后端：
  · `ck_cad_visualization` 增 `stage VARCHAR(64)`（`CREATE TABLE` + `ALTER ... ADD COLUMN IF NOT EXISTS` 平滑升级）；`ForgeCallbackPayload` / 实体 / `ContainerCadModelVO` / Mapper 全线带 `stage`
  · `handleCallback` 写 `stage`，并加**终态保护**：记录已是 SUCCESS / FAILED / SKIPPED 时忽略迟到的 RUNNING —— 过程回调与终态回调是两次请求，网络不保证顺序，否则已成功的记录会被打回"转换中"（模型能用、状态一直转圈，比没有进度更糟）
  · 成功收口清空 `stage`（留着最后一句会让完成态看起来还没结束），失败**保留**（卡在哪一步有诊断价值）；`retry` 清空上一轮进度与阶段；看门狗补偿同步 `stage`
- 前端：
  · 新增 `components/cad/CadConvertProgress.vue` 统一口径：**百分比未知不等于 0%**（QUEUED / 刚进网格化时转换器还没报），走不定长动画条；进度超过 45s 不动就提示"系统会自动补查，不必重复提交"
  · 文档预览弹窗（`DocumentViewer`）转换中改用进度条 + 阶段文案；三维浏览页（`CadViewerPage`）补 2s 轮询（原来要用户自己 F5）；「产品数模」列表状态列由 `xx%` 小字改为进度条 + `网格化零件 42/118 · 42%`
- 验证（真实 STEP 31 MB / `GXA1000000-000-000__GCO.stp`，occt-7.7.2）：
  · 转换器与库内同步推进：`10 → 22 → 27 → 31 → 58 → 92 → SUCCESS/100`，阶段依次「读入 STEP 装配 → 网格化零件 42/118 → 写出 GLB → 压缩模型 → 渲染缩略图」✓
  · 终态：`SUCCESS | 100 | stage 空 | 478720 面片 | 24 零件 | occt-7.7.2 | GLB 已登记` ✓
  · 终态保护实测：SUCCESS 之后补发 `RUNNING / 42` 回调 → 库内仍为 `SUCCESS | 100 | stage 空` ✓
  · `mvn -o compile` 0 错、`npm run build` 通过（57.73s）
- 说明：过程进度是**尽力而为**的 —— 回调丢一次就停在那一格，靠"45s 未更新"提示与看门狗兜底；全链路吞异常，报进度失败绝不影响转换本身

#### 任务办理改为当前窗口跳转，办理页补「返回」
- 任务中心「办理」原来是 `window.open('/workflow/task/{id}', '_blank')`（2026-10-26 用户要求改掉）：现在走 `router.push`，同一窗口内跳转
- 换掉新窗口的三个实际麻烦：弹窗被拦截时"点了没反应"（原来只弹一条 warning 兜底）、办完找不到列表、多条任务开一堆窗口
- 办理页页头：「返回」（`history.back()`，回来来源 —— 任务中心 / 工作台待办 / 消息通知；无历史时兜底回任务中心）+ 次要出口「任务中心」小字链接
- 办理完成结果页：主按钮改「返回任务中心」，次按钮「返回上一页」；原来那句「关闭本窗口」随 `window.close()` 一起删除（当前窗口里它无效）
- 验证：`npm run build` 通过（46.62s）

#### 「数据指标」并入驾驶舱页签，排在作战图之后
- 原来指标是驾驶舱下方独立一段（`<section>` + 标题「指标」），现在改为页签的最后一页（用户 2026-10-26）：经营战报 → 系列战况简报 / 型号作战地图 → **数据指标**
- 页签 key 用固定值 `__metrics__`（它不对应任何 `ck_dashboard` 实例，只是页签位）；切换容器时若当前停在数据指标，会回到本容器的作战图 —— 换对象先看主视图
- 能力开关沿用 `METRIC_MANAGEMENT`：未启用的企业连页签都不出现；只有指标能力（没开驾驶舱）时，页签条也能正常渲染
- 页签自带标题，原来的 section 级标题「指标」与 `.po-section-title` 样式一并撤掉（标题重复）
- 验证：`npm run build` 通过（35.05s）

#### 驾驶舱视角按容器归属：系列挂系列作战图、型号挂型号作战图
- 现象（用户 2026-10-26 截图）：系列页上出现了「型号作战地图」并被标成 ★ 默认页签 —— 视角实例是六类快照的一部分，**每个 owner 各存三份**，之前只按 `sortOrder` 全量列出，没管归属
- 归属规则：`PRODUCT_LINE`（系列战况简报）只在系列上出现、`PRODUCT_MODEL`（型号作战地图）只在型号上出现；`ENTERPRISE`（经营战报）两边都在（企业级视角，任何容器都能看）
- 容器类型判断：优先 `line.nodeType`；主数据未回来时用视角实例自带的 `ownerType` 兜底（同一 owner 的快照，不会打架），避免先渲染一版再跳动
- ★ 与默认页签改为「**本容器的作战图**」而不是模板里的 `primaryScope` —— 模板的 primaryScope 标在型号作战地图上，直接套用会让系列页默认停在一个不属于它的视角
- 验证：`npm run build` 通过（50.02s）

#### 11 项指标接入真实计算（系列 / 型号各算一份），算不出的写明缺什么
- 新增 `MetricEvaluator`（`product/service/impl`）+ `MetricFactsMapper`（`product/mapper`），在 `GET /api/instances` 返回前重算 —— 与交付件状态判定同一处、同一时机（"看的时候算"，算的永远是打开面板那一刻的数据）
- 接线 8 项：`BOM_TOTAL_COST` / `COST_GAP_RATE`（BOM 卷积总成本、缺单位成本行数）、`ISSUE_CLOSE_RATE`（CLOSED ÷ 非取消，与 `IssueService.stats` 同口径）、`QUALIFIED_RATE` / `PREFERRED_RATE` / `EOL_RISK_COUNT`（器件 IBA `QUALIFICATION_STATUS` / `PREFERRED_LEVEL` / `PART_LIFE_CYCLE`）、`ALT_COVERAGE`（`ck_part_alternate_link` 且 `enabled=TRUE`）、`COST_ACHIEVE`（缺目标价 → 不出数，写明缺口）
- 三项不碰：`TIME_TO_MARKET_DEV` / `REUSE_GAIN` / `QUALITY_LOSS_COST` —— 行业包里 source 就是 `-`，给它们编算法正是文档第 16 节禁止的事
- 口径纪律：器件三项的分母取「**填了该属性的器件数**」而非容器器件总数 —— 没人填过时显示 0% 会被读成"一个都没通过准入"，事实是"没人填"；`ALT_COVERAGE` 例外（替代关系是表里的事实，没填 = 没有替代，分母用器件总数）
- 新增 `MetricMapper.updateEvaluation`：只写 `value / value_text / readiness / evaluated_at`，绝不碰口径字段（layer / level / source）—— 口径若被"算数的人"顺手改了，这个数就没人能复核
- 指标面板「数值」列：有值显示值 + 单位（比率补 %、计数补「个」；成本不补币种，币种该由业务定），明细进悬停；没值仍显示「暂未评估」，下面一行灰字写明缺什么
- 本地库实测口径（CKAI-TECH / X系列智能手表）：问题 1 CLOSED + 1 VERIFYING → 闭环率 **50.00%**；器件 1 个、0 条 IBA、0 条替代 → 替代料覆盖率 **0.00%**；BOM 0 行、无目标价 → 其余各项按缺口说明显示
- 验证：`mvn -o compile` 通过；后端重启正常（:8082 返回 401 = 在跑）；评估用到的 SQL 逐条在库上验证（JSONB `#>> '{}'` 取标量、BOM 根解析、问题状态分布）

#### 系列战况简报三块：型号对比表 / 阶段分布 / 成本趋势（如实呈现缺口）
- 新组件 `frontend/src/views/product/SeriesBriefing.vue`；整机视图按视角类型分流的三条分支至此齐全（`ENTERPRISE` / `PRODUCT_LINE` / `PRODUCT_MODEL`）
- 型号对比表：`GET /api/product-models?productLineOid=` 取清单，每个型号**并行**取 `/api/instances` 与 `/api/issues/gate-check`（串行要等两个往返），列出交付件 / 阶段门 / 评审 / 未闭环问题四项；当前查看的型号置顶并标「当前」
- 阶段分布：直接用父页面已取的实例按 `stageKey` 分组，每阶段一行条形 —— 条长 = 该阶段交付件数占最大阶段的比例（分布本身），条内绿色 = 已完成占比；右侧标该阶段门通过数
- 成本趋势：**不画线**。BOM 卷积成本需要「结构 + 单位成本」，而 `ck_bom_snapshot` 记录数为 0、`ck_bom_links` 也为 0 —— 画平线等于告诉 GM"成本很稳定"，是最坏的一种谎。组件改为列出三条缺口（BOM 结构 / 单位成本 / 历史快照）与补数路径
- 字典新增 `SERIES_BRIEFING_WIDGETS`，`RENDERED_WIDGETS` 现为三个渲染器的并集
- 数据现状（本地库核对）：X系列智能手表下 1 个型号（46 项交付件、0 项 DONE）、系列自身 2/46 —— 对比表只有 1 行，属数据稀疏
- 验证：`npm run build` 通过（1m 6s）

#### 经营战报四个分组件落地：健康度（当期）/ 成本达成 / 风险 Top5 / 型号排名
- 新组件 `frontend/src/views/product/ExecutiveBoard.vue`；整机视图改为按**视角类型**（`ck_dashboard.code`，即稳定身份）分流渲染：`PRODUCT_MODEL` → 作战地图三块、`ENTERPRISE` → 经营战报四块、其余视角仍列分组件清单
- 健康度：四项等权平均（交付件完成率 / 阶段门通过率 / 评审完成率 / 未闭环阻塞问题，一条扣 20 分），**只给当期分数、不画趋势线** —— 平台没有健康度快照表，一个点连不成线；四项构成逐项可见，扣分规则写在代码注释里，便于反驳
- 成本达成：前端不自行计算，只如实呈现 `ck_metric` 里 `COST_ACHIEVE` 的 `value / readiness / source`；没有目标价就写「暂未评估」，并写明补数路径（成本目标交付物登记目标价 + BOM 各层填全单位成本），不用实际成本冒充达成率
- 风险 Top5：`GET /api/issues/gate-check?ownerOid=` 的 `items`（与阶段门 `ISSUE_OPEN` 检查同一份数据），排序 = 阻断在前 → 逾期在前 → 创建时间倒序，取前 5，逾期日期标红
- 型号排名：`GET /api/product-models?productLineOid=` + 逐型号 `GET /api/instances` 算交付件完成率排序（型号数量是个位数，逐个取比新造聚合接口省事）；分母口径与作战地图逐字一致（`!disabled && status !== 'NA'`，只有 `DONE` 算完成）
- 字典 `utils/dashboardWidgets.js` 拆成 `BATTLE_MAP_WIDGETS` / `EXECUTIVE_WIDGETS` / `RENDERED_WIDGETS`（两者合并）—— 作战地图底部的"待接入"清单只排除自己那三块，不会把经营战报的四个码也算成已实现
- 数据现状（本地库核对）：X系列智能手表下 1 个型号、2 条问题（1 条未闭环且已逾期）、46 项交付件（2 项 DONE）、指标数值全空、无成本目标字段 —— 排名与风险各只有 1 行属数据稀疏，不是实现缺口
- 未做：健康度历史快照（趋势线的前提）、成本目标价的录入入口
- 验证：`npm run build` 通过（39.7s）

#### 整机视图的 ★主视角开始真渲染：阶段就绪度 / 交付件矩阵 / 评审状态（无后端改动）
- 现象：整机视图的「驾驶舱」只有一张表，「分组件」列是三个蓝色标签（阶段就绪度 / 交付件矩阵 / 评审状态 +2）—— `ck_dashboard.widgets` 只是布局快照里的码数组，没有任何渲染件把它变成视图，用户等于在看"目录"
- 新组件 `frontend/src/views/product/ModelBattleMap.vue`：主视角（`primaryScope`）的三件套按**问题链**纵向堆叠 —— 我在哪（阶段条：每段完成率 + 门状态圆点）→ 差什么（专业 × 阶段热力矩阵，空白 ≠ 0%）→ 下一步开什么会（评审紧凑表，默认只露当前阶段及之后 2 段）；三块共享"当前阶段"（默认 = 第一个未完成的阶段）
- 驾驶舱改成**三个页签**（经营战报 / 系列战况简报 / 型号作战地图，用户 2026-10-26 要求）：按 `sortOrder` 排、停用的视角不出现、★ 标在页签上，默认落在主视角；有渲染件的视角渲染视图，没有渲染件的如实列出它声明的分组件码（`中文名 + 码 + 暂无渲染实现`），不假装有内容。原来那行「★主视角 · 型号作战地图 · 受众 RD/PM」标题随列表一起撤掉（页签本身就是标题）
- 分组件码字典抽成 `utils/dashboardWidgets.js`（`WIDGET_LABEL` / `widgetLabel` / `parseWidgets` / `hasRenderedWidget`）—— 原来 `MethodologyInstancePanel`（列表列）与 `DashboardTemplateConfig`（模板编辑）各存一份，整机视图页签也要用，三处各抄必然漂移；顺带把"哪些码已有渲染件"（`RENDERED_WIDGETS`）也集中到一处
- 口径不另立：完成率分母与后端 `DELIVERABLE_RATE` **逐字一致**（`!disabled && status !== 'NA'`，只有 `DONE` 算完成）；某阶段无应交付项显示「—」而不是 100% —— 后端"没有要交的东西按 100% 判门"是判门逻辑，不能被界面借去当结论（与指标区"未评估就写暂未评估"同一条原则）
- 交互：矩阵格子 → 详情页 `?stage=<code>&tab=deliverable`；评审行 → `?stage=<code>&tab=review`；`ProductLineDashboard` 的 `loadStages` 新增 `?stage=` 落点（按 code 匹配，命中后标记"用户已选定"，免得被"第一个门未通过的阶段"自动推断抢走；未命中则退回原推断）
- 零新增接口：`GET /api/instances` 已一次返回 deliverables / gates / reviews（含流程进展），只补一次 `getStages(oid)` 拿阶段顺序与名称
- 配置驱动：`widgets` 里没声明的块不渲染（三块都没声明 → 空态说明），不假装有工作面；主视角被停用时退化为原来的列表
- 页头组织对齐系列/型号详情页（用户 2026-10-26 截图比对要求）：**左摘要 · 中面包屑+标题 · 右统计+描述**，同一套设计变量与顶部渐变条。左摘要卡站详情页「当前阶段」的位置 —— 本页不分阶段，那里放整体进度（`门未通过 x/y` + `交付件 done/total`）；标题行补回节点类型标签（产品型号紫 / 子系列绿 / 根系列蓝）与编码 tag；右侧三个统计 pill = 阶段 / 评审 / 检查规则，下面一行是描述 + 创建时间；原来独占一行的副标题改为「整机视图」的 title 悬停（页头寸土寸金，与详情页"说明文字进悬停"同一取舍）
- 验证：`npm run build` 通过（3918 模块 / 52.8s）；页面视觉待登录后目视确认

#### 变更请求可关联多个问题：一张 ECR 由多条问题驱动
- 「提出变更请求」弹窗在**责任人下方**新增「关联问题」多选：服务端搜索（输入编号/标题即时查，空关键字给最近 20 条兜底），支持多选与清空
- 新表 `ck_change_request_issue`（`request_oid` + `issue_oid` 唯一，两个外键都 `ON DELETE CASCADE`）—— 问题被删时关联自动清掉，不会留下指向空洞的引用
- **为什么不存逗号串**：要能反查"某个问题引出了几张变更单"，逗号串既建不了索引，也做不了外键与级联
- `source_issue_oid` 保留不废：仍写入**第一个**关联问题（既有索引与反查继续可用）；启动时把存量的单值补录进关联表（`NOT EXISTS` 守卫，幂等）
- 编辑沿用既有 patch 语义：字段缺席（`null`）= 本次不改，空列表 = 清空；详情接口新增 `issues`（编号/标题/状态），列表接口不动（避免 N+1）
- 验证：建带 2 个问题的 ECR → 详情 `issues=2`、`sourceIssueOid` = 第一个；改成 1 个 → `issues=1` 且 source 跟随；清空 → `issues=0`、source 置空；删除 → 关联行级联清零（`leftover=0`）。后端 **80 例**、前端 **34 文件 557 用例**、`npm run build` 全绿

#### 启动时自动补齐表结构：不会再出现「代码发了、表没建」
- `application.yml` 打开 `spring.sql.init.mode=always` + `schema-locations: classpath:schema.sql`，`continue-on-error=false`（任何一条失败就让启动失败，不静默跳过 —— "表建了一半"的应用比"起不来"更难查）
- 为什么需要：本工程没有迁移框架，`schema.sql` 从不自动执行。变更管理模块 6 张表（`ck_change_request` / `ck_change_task` / `ck_change_activity` / `ck_change_affected_object` / `ck_change_notice` / `ck_change_config`）代码早就写完，表却一直没建，接口一调就是 `relation does not exist`
- 为什么安全：脚本全幂等（`CREATE TABLE IF NOT EXISTS` / `ADD COLUMN IF NOT EXISTS` / `CREATE INDEX IF NOT EXISTS`）—— 387 条语句 / 117 张表，重跑无副作用；且不含 `$$` 函数体或 `DO` 块（Spring 按分号切句，函数体会被切碎）。将来要写函数/触发器，需改用 Flyway
- 实测：`DROP TABLE ck_change_config`（空表）→ 重启 → 自动重建（`after_drop_exists=0` → `after_restart_exists=1`）；`/api/plm/change-requests`、`/api/product-lines`、`/api/bom-links/materials/{oid}` 全 200；业务表 117 张
- `application-template.yml` 同步打开，并注明「由 DBA 统一管 DDL 的环境可改回 `mode: never` 手工执行」

#### 建系列/型号时自动生成同名 Part：BOM 的父件不用再手工建一个
- 建产品系列 → 自动建 `container_type=PRODUCT_LINE` 的 Part；建产品型号 → `container_type=PRODUCT_MODEL`。归属指向容器**自身** oid，一一对应
- `name` 与容器同名；`type_definition_code=PART`（根类型，不替用户钉成结构件/电气件）；`number` 交给编号服务（实测生成 `PART-202610-0004` / `0005`）
- `folder_oid` 故意留空：这是容器级主数据，不是某个阶段文件夹里的交付物 —— 塞进「市场评估」只会让研发资料列表凭空多一条
- `stage_oid` 落概念阶段（阶段初始化后的第一个），阶段视图里也能找到它
- 失败只记 ERROR、**不阻断**容器创建：Part 是附属物，编号 / 版本规则偶发异常时不该让用户连产品都建不出来（代价：这条容器暂时没有同名件，补建一次即可）
- 物料清单口径跟着改：型号页现在同时统计"型号自己那份 Part"和"挂在父系列上的零件"（`container_oid = 型号 oid OR = (SELECT parent_oid FROM ck_product_model WHERE oid = 型号)`）；产品系列跑同一个子查询（恒为 NULL）自然退化为只查自身，一条 SQL 覆盖两种容器
- 验证：真实接口建型号 → Part 自动出现（`PART` / `PART-202610-0004` / `A.1` / `DRAFT` / `PRODUCT_MODEL` / stage 有值 / folder 为空）；建系列 → 同样（`PART-202610-0005` / `PRODUCT_LINE`）。验完按 oid 逐表清理，`leftover=0`；后端 **80 例全绿**，前端 34 文件 557 用例全绿，`npm run build` 通过
- 已知边界：①**存量**系列/型号不会补建 Part（本次只覆盖新建）；②删除容器不级联删除 Part —— 软删后件仍在库里，BOM 结构不会丢，需要时手工处理

#### 系列/型号新增「物料清单」页签：这个产品到底用了哪些料，一眼看完
- 位置在「研发资料」之前：一行 = **一颗被用到的物料**（不是一条 BOM 行），列含编码 / 名称 / 类型 / 版本 / 单位 / **用量合计** / 使用于（父件编码）
- 口径：容器 → 零件最新迭代 → 它的 BOM 行 → 子件，按物料去重聚合。一条 join 就够，不必递归 —— 容器下的零件**既是子件也可能是父件**，这条查询已经把结构里每条父子边都扫到了
- `pi.latest = TRUE` 是必须的：历史迭代上的 BOM 行还在表里，不隔离就会把改版前的料也算进用量（实测：旧迭代上挂一行 99 个，不加这个条件总量会变成 101）
- `unit` 参与分组：同一颗料可能在不同父件上用不同单位（个 / 米），跨单位 SUM 是没有意义的数字，宁可拆两行
- **不按阶段过滤**：一颗料往往同时装在几个阶段的件上，按阶段切只会让同一颗料在每个页签里各出现一次，"总量"反而算不清
- 型号页回溯到所属系列：零件只挂在产品系列下（型号页建件也落父系列，见 `openCreatePart`），直接按型号 oid 查会得到一个恒为空的页签 —— 而旁边「研发资料」里明明有继承来的零件
- 懒加载：切到该页签才查（BOM 行动辄上百条，不该跟研发资料一起查）；换产品时标记作废重取，避免显示上一个产品的料
- 接口：`GET /api/bom-links/materials/{containerOid}`
- 验证：真实库上**事务内造数 + ROLLBACK**（3 个零件 / 4 个迭代 / 3 条 BOM 行，其中一条挂在旧迭代上）—— 旧的 99 个被正确排除，用量合计 2 / 4、父件编码 P-001 均符合预期，库未被改动；后端 **80 例全绿**，前端 34 文件 557 用例全绿，`npm run build` 通过
- 端到端：重启后端后带真实登录态请求 `GET /api/bom-links/materials/{oid}` → **HTTP 200**，返回用量 2 / 4、父件编码 `ZZBOM-001`、旧迭代的 99 被排除；顺带修掉 `ck_part.oid` 为 `CHAR(36)` 导致的 oid 尾随空格（加 `TRIM`），验完按 oid 前缀清理，库内 0 残留
- 注意：`404` 只说明**服务没重启**（本地后端进程比编译产物早 2.5 小时），不是路由写错 —— 路径 `/api/bom-links/materials/{containerOid}` 与既有的 `/{oid}` 段数不同，不存在抢占

#### 会签式加签：并行会签上再拉一个人，所有人都办完才过（M4）
- 加签按节点类型自动分派：<b>并行会签</b>走扩容（每人都必须审），<b>单实例 / 串行会签</b>仍走 M3 的或签（谁先办谁算过）
- 引擎依据：`RuntimeService#addMultiInstanceExecution(activityId, parentExecutionId, vars)` —— 多实例的 `nrOfInstances` 随之 +1，新执行立刻建出任务，`completionCondition` 与运行期计票的分母一起变
- **必须传多实例根的父执行**：Flowable 在"这个父执行的子执行"里找多实例根，把根自己传进去会报 `No multi instance execution found`（实测，见 `CountersignAddSignerEngineTest`）
- 为什么不是 ad-hoc 子流程 / 插入节点：Flowable 7 的 `ChangeActivityStateBuilder` 只有"移动"、没有"插入"；而多实例扩容是引擎自带的正当入口，不必在编译期预置壳子
- 为什么串行会签不扩容：串行多实例只有一个活动执行在跑，追加执行会打乱 `loopCounter` 驱动的队列语义，本期只做并行
- 候选组产物要补一次指派：节点策略是角色时，任务落在 `candidateGroups` 上，元素变量（`ckplmApprover`）压根不参与 assignee —— 不补 `setAssignee` 的话，被加签人得去「可认领」里自己抢，与"指定你也必须审"不是一回事
- 计票口径自动对齐：`CountersignRule` 的应到人数取 `nrOfInstances`，扩容后 `PERCENT` / `COUNT` 的门槛跟着提高，不会出现"新人这一票算白投"
- 语义降级不静默：接口返回 `{ mode, participantCount }`，前端对会签提示"本节点共 N 人，全部办理完才结束"，对或签仍提示"任务已转为候选，您可在「可认领」里继续办理" —— 两种后果差别很大，不能共用一句话
- 接口：`POST /api/workflow/task/{id}/add-signer`（body `targetAssignee`，响应新增 `mode` / `participantCount`）
- 验证：新增 3 例引擎测试（扩容后人数 +1 且新人直接得待办 / 少一票节点不放行 / 候选组产物必须补指派），后端 **80 例全绿**；前端 34 文件 557 用例全绿，`npm run build` 通过

#### 加签（或签）：审批中临时拉人一起办，谁先办谁算过（M3）
- 任务中心「待办」行新增「加签」：追加一名候选人，**原办理人一并转为候选人**，任务变为候选任务
- 为什么必须释放 assignee（实测，见 `AddSignerEngineTest`）：Flowable 的候选查询带 `taskUnassigned`、`claim` 也拒绝"已有办理人"的任务 —— 只要 assignee 还在，被加签的人<b>既看不到也接不了</b>，加签等于没加
- 当时为什么只能或签：会签（多实例）在进入活动时实例数就定死，这条路径无法扩容 —— "每人都必须审"的加签在 M4 用 `RuntimeService#addMultiInstanceExecution` 补齐（见上方条目），单实例 / 串行会签仍走或签
- 与委派 / 转办的区别：那两个是**换人**（委派办完回到我、转办彻底交出去），加签是**加人**
- 交互上说清代价：加签成功后任务从「待办」移到「可认领」，前端成功提示里直接写明，避免用户以为任务丢了
- 接口：`POST /api/workflow/task/{id}/add-signer`（body `targetAssignee`）
- 验证：新增 2 例引擎测试（加签后双方都可认领且互不干扰 / 认领即归属、办完流程继续），后端 **77 例全绿**；前端 34 文件全绿，构建通过

#### 局部终止落地：并行分支真的能收口了（M3，引擎级 + 测试基建）
- 引擎测试基建：手装 H2 + `StandaloneInMemProcessEngineConfiguration` 起<b>真引擎</b>（`FlowableEngineSmokeTest`），不再只能"看着代码猜"；并行汇聚、会签多实例这类行为从此可以真跑一遍
- 运行期：`BranchTerminator` 在任务办完后按节点上的 `ckplm:terminateOn` 判定，把兄弟支路**移动到最近的并行 / 包容汇聚网关**，流程从汇聚点继续
- **没有用"删除执行"**：同一份流程上实测 `deleteExecutionAndRelatedData` —— 兄弟执行消失后并行汇聚永远等不齐（按到达次数计数），实例直接卡死。移动是一次正常到达，计数对得上，语义也更贴切（"这一支到这儿为止"，不是"从未存在过"）
- 判据位置有实测依据：引擎只保留**节点**上的自定义命名空间属性，`sequenceFlow` 上的会被丢掉（`ckplm:route` 的老问题）—— 所以 `terminateOn` 写在源节点，连线那份只服务前端导入
- 找不到汇聚点、引擎拒绝迁移都只留 WARN，不回滚已完成的办理：用户点了提交，流程至少要走下去
- 校验同步收紧：并行块之外的开关仍报 `TERMINATE_OUTSIDE_BRANCH`（配了也不会生效）
- 验证：新增 5 例引擎测试（属性可读性 / 移动方案 / 正式实现收口 / 未声明则不动 / 基线对照），后端 **75 例全绿**；前端 **34 个测试文件全绿**

#### 往返回归：DSL → BPMN → DSL 有了护栏，并修掉 4 处真实丢失（M3）
- 新增跨模块回归 `dsl-roundtrip.test.ts`：四份覆盖人工 / 自动化 / 分支 / 路由的流程，走 `compile → parse → diffDsl`，断言往返无损、**再次编译产物逐字节相同**
- 这条链路第一次跑就红了，暴露四处真的会丢：
  - **流程说明**：编译写了 `<documentation>`，解析却没读回来（`documentation` 一直在忽略名单里）→ 现在读回 `meta.description`
  - **流程变量**：BPMN 没有"变量声明"的位置，之前一个都没写 → 编译在 `<process>` 上写 `ckplm:variables`（JSON），解析读回；否则导入 BPMN 等于把"任务页显示哪些变量、默认值是什么"全丢了
  - **条件结构**：字段选择器（对象类型 / 字段 / 操作符 / 取值）编译成 UEL 后不可逆，导入只能退化成「表达式模式」，用户看到一段看不懂的表达式 → 条件边额外写 `ckplm:condition` 结构化副本，解析优先用它（路由边不写：那份条件由编译层按路由生成，本来就没有用户填的结构）
  - **通过 / 驳回路由边**：编译出的 `${approved}` / `${!approved}` 被当成用户条件读回，往返一次就从「普通流转 + 路由」变成「条件流转 + 一段表达式」，面板上多出改不动的条件编辑器 → 认出路由后丢掉这条生成的条件
- 「设置审批人」的 `formRef` 是**设计上的补全**（内置表单 `CKPLM_SETUP_ASSIGNEE`），不算丢失：回归用例在比较前把原稿按同一口径标准化，避免假差异
- 为什么不是浏览器 e2e：环境装不了 Playwright；而设计器真正的破绽在三个独立模块（compile / parse / validate）的约定对不上，跨模块往返比点击流更能守住它
- 兼容：新增属性对 Flowable 是未知命名空间属性（忽略）；旧产物没有这两个属性，解析退回原逻辑
- 验证：前端 **34 个测试文件全绿**（新增 7 例：人工 / 自动化 / 分支往返、编译确定性、往返幂等、diff 能报出缺失字段）

#### 预置流程模板市场：包里的流程能单个取用，不必装整包（M3，无 SQL 变更）
- 模板库新增「预置模板」入口：列出所有行业包里 `processes/*.dsl.json` 的流程，标注所属包 / 版本 / 节点数
- **取用**即在本租户「预置流程」分组下生成一份可编辑模板（第 1 版，不自动部署）：部署仍由设计器手动触发 —— DSL→BPMN 的编译器在前端，后端没有第二份
- 取用时可改 key / 名称，且 **DSL 的 `meta.key` 会同步改写**：部署时后端强校验 `<process id>` 等于模板 key，只改主档会得到"存得住、部署不了"的模板
- 依赖如实标出：流程引用的 `formRef` 在本租户不存在时，列表直接提示"缺少表单 X，建议先装包" —— 流程能部署，但那些节点的任务页渲染不出表单
- 只列有 DSL 的流程：platform-base 内置的技术审批只有 BPMN、没有 DSL，取用后无法在设计器里编辑，故不进市场（仍由装包直接部署）
- 数据源是包目录而不是模板表：租户没取用前不该在 `ck_process_template` 里留下痕迹；取用复用 `ProcessTemplateService.create`，与装包走同一套落库逻辑
- 接口：`GET /api/plm/process-templates/market`、`POST /api/plm/process-templates/market/adopt`
- 验证：后端新增 `ProcessTemplateMarketTest` 5 例（缺 key 跳过 / 缺表单标出 / 改写 meta.key / key 冲突拒绝 / 未知包拒绝），`mvn -o test` **68 例全绿**；前端 **33 个测试文件全绿**，`npm run build` 通过

#### 版本 diff：两个版本之间到底改了什么，不用再把 JSON 对眼睛（M3）
- 设计器「版本历史」抽屉新增对比：顶部选任意两版，或行内直接点「对比上一版」；默认「上一版 → 最新版」
- 对比按**语义**比、不比画布：节点按 `id`、连线按 `id`、流程变量按 `name` 配对 —— 改名报一次「修改」，不会变成"删一个 + 新建一个"；坐标、连线折点与锚点**不参与**（挪一下布局不算改动）
- 嵌套配置展开到字段级：`审批人·策略：指定角色 → 部门主管`、`会签规则·通过比例：60 → 75`，而不是甩一坨 JSON；枚举取值翻成中文，且按**路径**选词表 —— 同一个 `mode` 在会签规则与定时等待里是两个意思
- 分组展示：流程属性 / 流程变量 / 节点 / 连线，各带新增·删除·修改计数；DSL 结构代际（`dslVersion`）变化单独列出
- 两版语义完全一致时明确提示"只调了布局"，避免用户以为对比坏了
- 实现：`dsl-core/diff.ts`（纯函数、零依赖）+ `app/VersionDiffModal.vue`（只负责取数与排版，差异判定不重复实现）
- 验证：前端 `npm test` **33 个测试文件全绿**（新增 `diff.test.ts` 11 例），`npm run build` 通过

#### 「发起人上级 / 部门主管」审批人策略落地：声明式解析，不再是"配了就炸"（新增 1 列，存量库需重跑 schema.sql）
- 设计器里的「发起人上级」「部门主管」都不再是"运行期未实现"：编译层只留 `ckplm:assignee` 里的 strategy 声明（**不写** `${ckplmInitiatorLeader}` / `${ckplmDeptLeader}`），运行期在任务创建那一步解析并直接指派 —— 发起人上级走「`initiator` → 用户 → 所属组织 → `ck_organization.leader_oid`」，部门主管走「业务对象上的部门字段（`deptFieldKey`）→ 组织 → `leader_oid`」
- 为什么用声明而不是变量：变量式只能"发起时快照"，跨周流程里主管换人、人员调部门都不生效，最后派给一个已离任的人；声明式按任务创建时的数据解析，而且**存量部署不用重新部署**（判定只读 BPMN 上已有的 `ckplm:assignee`，与驳回路由、会签规则同一套做法）
- 组织维护：「组织 → 部门架构」新增「部门主管」字段（选人）；留空时节点日志会明确写出"部门「X」还没有设置主管"
- 解析失败**不阻断**流程：留一条 WARN 到节点执行日志，具体到"发起人未归属部门 / 部门没有主管 / 主管已停用 / 主管账号不存在"，以及部门主管那条链上的"流程没有关联业务对象 / 对象上的部门字段为空 / 部门值与任何组织都对不上"；任务仍在 —— 管理员补数据后转办即可，不必重跑流程
- 自审可追溯：解析结果就是发起人本人时（一个人部门 / 发起人自己就是主管）**照常指派**，不擅自跳过审批 —— 但记一条 WARN「存在自审」，审计要看的是这件事，不该由系统替业务决定
- 部门主管的「部门字段」按「组织 oid」或「组织编码」两种写法都认（页面选人存的是 oid，导入的历史数据里也有编码），取值还兼容「软类型自身」与「能力宿主」两种 entityType 约定，按顺序取第一个非空值
- 部门主管**没配「部门字段」**是校验错误（`MISSING_ASSIGNEE`）：运行期没有可读的部门，任务注定无人办理；而"字段配了、值与任何组织都对不上"属于数据问题，只留 WARN、任务仍在
- 数据库：`ck_organization` 新增 `leader_oid CHAR(36)`。本工程没有迁移框架（`sql.init.mode=never`），`schema.sql` 里补了 `ALTER TABLE ck_organization ADD COLUMN IF NOT EXISTS leader_oid CHAR(36);` —— **存量库重跑一次 schema.sql 即可**，新装天然带上
- 验证：后端 `mvn -o test` **63 例全绿**；前端 **32 个测试文件全绿**（新增"两个策略都只留声明、不写变量"的编译用例，以及"两个策略都不拦 / 缺部门字段仍拦"的校验用例）

#### 交付件模板（`DELIVERABLE` 资源）
- 新表 `ck_deliverable_template`：22 列，含 `stage_key`（↔ `ck_stage.code`）/ `gate` / `completion_rule` / `owner_role` / `due_offset_days` / `disabled`，唯一键 `UNIQUE (code, tenant_oid)`
- 新安装器 `DeliverableInstaller`：读包内 `deliverables.json` 的 `items[]`；**行业取包清单的 `devMethod`**（与同一包装出的 `ck_stage_template.industry` 逐字一致，交付件的 `stage_key` / `gate` 才与阶段对得上，不靠"有没有 TRADITIONAL 模板"去猜）；幂等键 `code + 目标租户`，**永不 UPDATE 已有行**；预演只报不写
- 新接口 `/api/deliverable-templates`：列表（可按 industry 过滤）/ 取一条 / 新建 / 更新 / 删除 / **停用开关** / 克隆平台模板 / 平台方法论列表 / 平台模板
- 新页面「交付件模板」，挂在「业务配置 → 模板管理」的第二个页签：按阶段筛选、完成条件摘要、停用开关、克隆平台模板；**阶段名取自研发阶段模板同一份数据**（单一来源，不硬编码）
- 写接口语义（与前端约定，见 `DeliverableTemplateServiceImpl` 类注释）：**字段缺席（null）= 本次不改；空串（""）= 显式清空**

#### 阶段门模板（`GATE` 资源）
- 新表 `ck_gate_template`：21 列，含 `stage_key` / `type`（`TECHNICAL_REVIEW` / `DECISION_REVIEW`）/ `process_key` / `decision_options` / `auto_checks` / `required_participants` / `record_fields` / `evidence_required` / `disabled`，唯一键 `UNIQUE (code, tenant_oid)`
- 新安装器 `GateTemplateInstaller`：读包内 `gates.json` 的 `items[]`；行业同样取包清单的 `devMethod`；幂等键 `code + 目标租户`；预演只报不写
- **流程引用跨包校验**（包自己定的规矩"不允许悬空引用"）：`process` 必须能在**包链**（本包 → basePack → …）里找到 `processes/<key>.bpmn20.xml`，找不到记 FAILED 并写明查过哪些包 —— 悬空引用会让门发起时才发现流程不存在，那时代价已经在用户那儿了
- 新接口 `/api/gate-templates`（与交付件同款 9 个端点）
- 新页面「阶段门模板」（「模板管理」的第三个页签）：阶段 / 类型双筛选、决策选项与自动检查摘要（BLOCK 红 / WARN 橙）、参与者、流程、停用开关、克隆平台模板
- 交叉校验（可随时用 SQL 复核）：门的 `(code, stage)` 与交付件的 `(gate, stage)` 一一对应 —— 实测 10 道门引用的交付件数与各阶段实际条数完全相同（8/8/9/9/7/5），三条不变量全为 0

#### 评审模板（`REVIEW` 资源）
- 新表 `ck_review_template`：19 列，含 `gate`（挂在哪道门）/ `type` / `process_key` / `check_items`（逐条带 BLOCK/WARN）/ `output_types` / `verdict_options` / `disabled`，唯一键 `UNIQUE (code, tenant_oid)`
- 新安装器 `ReviewTemplateInstaller`：读包内 `reviews.json` 的 `items[]`；行业同样取包清单的 `devMethod`；幂等键 `code + 目标租户`；预演只报不写
- 两条引用校验各按其性质处理：**流程**引用悬空记 **FAILED**（包自己的硬规矩"不允许悬空引用"）；**归属门**尚不存在记 **SKIP + 原因**（那是依赖顺序问题，不是数据缺陷 —— 等门模板就绪后重装即可补齐，不该让整包变成 PARTIAL）
- 新接口 `/api/review-templates`（与前面同款 9 个端点）
- 新页面「评审模板」（「模板管理」的第四个页签）：按门筛选、检查项悬停可见全文（BLOCK 红 / WARN 橙）、结论选项、流程、停用开关、克隆平台模板
- 交叉校验（可随时用 SQL 复核）：10 道门 → TR1 1 份 / TR2 1 / **TR3 3 份（原理图 + 结构 + 软件，20 项检查）** / TR4 1 / TR5 1 / TR6 1，4 道决策门 0 份；共 8 份 / 51 项检查。反向引用（交付件完成条件里的 `REVIEW_PASSED.reviewTemplate`）实测 `DLV-SCHEMATIC → TR4_HW_SCHEMATIC` 存在；三条不变量全为 0

#### 检查规则模板（`RULE` 资源）
- 新表 `ck_rule_template`：23 列，含 `trigger_type` / `scope` / `condition` / `severity` / `category` / `stage_key` / `gate` / `default_override`（企业画像联动）/ `fix_action`（一键修复）/ `disabled`，唯一键 `UNIQUE (code, tenant_oid)`。列名用 `trigger_type` 而非 `trigger` —— 后者是 SQL 保留字
- 新安装器 `RuleTemplateInstaller`：读包内 `rules.json` 的 `items[]`；基础包（electronics-core）不声明方法论，其共用规则落成**通用条目**（`industry` 为空，任何方法论都适用）—— 这正是"共用侧 7 条 + 变体侧 4 条 = 11 条"里那 7 条的语义
- **四组枚举逐条校验**（时机 / 对象 / 条件类型 / 严重度，与包内 note 同口径）：越界即记 FAILED 并把允许取值写进原因 —— 越界值平台根本解释不了，装进去只会变成静默失效
- **三处引用校验**：`stage` → 阶段模板、`gate` → 门模板、`fixAction.code` → 交付件模板；引用对象尚未就绪时记 SKIP + 原因（重装即可补齐）
- 新接口 `/api/rule-templates`（同款 9 个端点）
- 新页面「检查规则模板」（「模板管理」的第五个页签）：时机 / 严重度双筛选、条件与修复动作的人话摘要（字段判定 / 字段为空 / 未关闭问题 / 对象缺失）、关联阶段与门、停用开关、克隆平台模板
- 交叉校验（可随时用 SQL 复核）：ZZTEST 侧 **通用 7 + IPD 4 = 11 条**（平台侧另存 7 条通用供克隆）；四组枚举越界 0；三处引用缺失 0
- 运行期说明：规则的**运行引擎**（在保存零件 / 保存 BOM / 进阶段 / 过门 / 发布时真正执行判定）尚未实现，属后续版本；本版交付的是"规则内容本身"可装、可审、可裁

#### 问题类型模板（`ISSUE_TYPE` 资源）
- 新表 `ck_issue_type_template`：16 列，含 `default_severity` / `require_root_cause` / `require_retest` / `industry_note` / `disabled`，唯一键 `UNIQUE (code, tenant_oid)`
- 新安装器 `IssueTypeInstaller`：读包内 `issueTypes.json` 的 `items[]`；行业同样取包清单的 `devMethod`；严重度枚举把关（`BLOCK` / `WARN`，与检查规则、阶段门同一口径）；两处字段名映射：包内的 `note` → 库里的 `industry_note`，两个键都认
- 新接口 `/api/issue-type-templates`（同款 9 个端点）
- 新页面「问题类型」（「模板管理」的第六个页签）：严重度筛选、需根因 / 需复测标记、行业注解、停用开关、克隆平台模板
- 交叉校验（可随时用 SQL 复核）：企业侧 7 类（默认 BLOCK 3 / WARN 4；需根因仅 `DESIGN_DEFECT`、需复测仅 `TEST_FAIL`，与包内逐条一致）；**反向引用**（检查规则的 `condition.issueType`，实测 `R-SH-03 → TEST_FAIL`）缺失 0
- 与其它模板的关系：问题类型是"问题被结构化"的地基 —— 检查规则的 `issueType`、门的 `ISSUE_OPEN` 检查、健康度报告的问题闭环率都按这里的编码统计。问题实体与闭环流程（`ck_issue` 一类）尚未实现，属后续版本

#### 指标（驾驶舱）模板（`METRIC` 资源）
- 新表 `ck_metric_template`：16 列，含 `layer`（分层）/ `level`（**数据就绪度 A/B/C**）/ `source`（取数依据）/ `disabled`，唯一键 `UNIQUE (code, tenant_oid)`
- 新安装器 `MetricInstaller`：**指标清单的形状与前六类不同** —— 它按分层写成 `businessLayer[]` + `executiveLayer[]` 两个数组（而非 `items[]`），安装器认这两种数组（并兼容 `items[]` + 条目自带 `layer`），把数组名落成 `layer` 字段
- `level` 是硬约束：`A/B/C` 三级<strong>数据就绪度</strong>，文档第 16 节原文"C 级指标第一版显示『暂未评估』，**禁止编造数值** —— 一个编出来的数字被客户当场问穿，整个健康度报告的可信度就归零了"。安装报告里对 C 级条目也明写这句，界面上的就绪度标签同样带此提示
- 新接口 `/api/metric-templates`（同款 9 个端点）
- 新页面「指标模板」（「模板管理」的第七个页签）：分层 / 就绪度双筛选、取数依据、A/B/C 三色标签（C 级橙、悬停给出硬规则原文）、停用开关、克隆平台模板
- 交叉校验（可随时用 SQL 复核）：企业侧 **11 项 = 业务层 8 + 经营层 3**，就绪度 **A 3 / B 5 / C 3** —— 与文档第 16 节示例**逐项一致**；枚举越界 0、缺分层/缺就绪度 0
- 就绪度与来源的互相校验（本次实测）：A 级来源 `BomCostCalculator` / `missingCostLines` / `PartAlternate` 平台均已有实现；B 级来源 `qualificationStatus` / `preferredLevel` / `partLifeCycle` 等器件属性已在 `attributes.json` 定义并随包落库为 IBA（**库里按大写存储** `PREFERREDLEVEL`，查找按大小写不敏感处理，见 `AttributeInstaller` 类头）；C 级来源 `ReviewIssue`（需评审引擎）与"需项目实体 / 现场质量数据"两类**尚未落地** —— 与 C 级标记相符
- 说明：本版交付的是**指标的定义与就绪度标记**；**取值引擎（真正算数）与驾驶舱渲染尚未实现** —— 这正是 `level` 必须进模板表的原因：它是"这个数能不能显示"的开关

#### 驾驶舱（布局）模板（`DASHBOARD` 资源）
- 新表 `ck_dashboard_template`：17 列，含 `audience` / `page_count` / `widgets` / `generate_policy` / `primary_scope` / `disabled`，唯一键 `UNIQUE (code, tenant_oid)`
- 新安装器 `DashboardInstaller`：**形状最特殊** —— 包内清单是 `scopes[]` + 两个<strong>包级键</strong>，安装器负责把它们摊平到行上：`primaryScopeType` → 命中那条的 `primary_scope` 标记（**必须恰有一条命中**，命不中记 FAILED）；`generatePolicy`（`{视角 → 生成节奏}` 映射）→ 逐行摊到 `generate_policy`（映射里每个 key 都必须是真实存在的视角，悬空 key 记 FAILED —— 与阶段门/评审的"不允许悬空引用"同一纪律）
- `widgets` 只校验非空：分组件码里有一部分<b>就是指标码</b>（`COST_ACHIEVE` 在 `metrics.json` 里有），其余（`MODEL_RANKING` / `STAGE_READINESS`…）是"渲染件"而非数据项 —— **没有定义表，安装器不假装能校验其存在性**
- 新接口 `/api/dashboard-templates`（同款 9 个端点）
- 新页面「驾驶舱模板」（「模板管理」的第八个页签）：视角 / 受众 / 页数 / 分组件（中文标签 + 悬停看全码）/ 生成节奏 / ★主视角标记 / 停用开关 / 克隆平台模板
- 交叉校验（可随时用 SQL 复核）：企业侧 **3 个视角**；**主视角唯一性实测恰 1 条**且为 `PRODUCT_MODEL` ✓；**必选件实测齐备**（`MODEL_RANKING` 在经营战报、`MODEL_COMPARE_TABLE` 在系列战况）；分组件去重 **12 个**、其中 `COST_ACHIEVE` 与指标表**对账一致**；每个视角都有生成节奏（缺失 0）
- 与既有页面的关系：`views/product/ProductLineDashboard.vue` 是**既有手写的产品线页面**，本版**未改动它** —— 本轮交付的是"驾驶舱的布局定义"（谁是受众、由哪些分组件组成、多久出一次），渲染引擎待后续版本

#### 界面用词：方法论模块去掉「模板」二字，并移除「克隆平台模板」（无 SQL 变更）
- **导航项**：原「模板管理」→「**方法论配置**」，并按企业**实际导入的方法论**显名（IPD 体系的企业看到「**IPD 方法论**」）。口径：`GET /api/onboarding/state` 的 `devMethodCode`（引导页选定、平台权威；与八类内容的 `industry` **同源**，故菜单名与内容永远一致）；**未引导 / 取不到 / 无权限 → 静默退回「方法论配置」**，导航不因取数失败破相
- **八个页签去掉「模板」二字**：研发阶段 / 交付件 / 阶段门 / 评审 / 检查规则 / 问题类型 / 指标 / 驾驶舱
- **各页内文案同步**：页头标题、副标题、统计标签（"评审模板总数" → "评审总数"）、删除确认（"确定删除该阶段模板？" → "确定删除该阶段？"）、编辑弹窗标题（"编辑评审模板" → "编辑评审"）、占位提示（"来自研发阶段模板" → "来自研发阶段"），以及空态提示里对克隆的指引
- **移除「克隆平台模板」功能**：8 个页面的克隆按钮 + 克隆弹窗 + 相关脚本与 import 一并删除（内容现在由行业包随包装入企业一份，界面上不再需要"从平台克隆"）。后端接口 `POST /api/*-templates/clone-from-platform` 与 `api/index.js` 的导出**保留** —— 实施/排障仍可用，只是不再占界面
- **只改界面字样**：源码注释与代码标识符（`*Template`、`listXxxTemplates` 等）**未动** —— 它们不显示给用户，且"模板 → 实例"正是这套机制的真实表述
- 实测（浏览器）：页签栏 8 项无「模板」✓ 工具栏只剩「+ 新增阶段」✓「交付件」页签 46 条正常渲染 ✓ 8 个页面 `vite` 逐页编译全部 200 ✓ 残留检查（`克隆` / `openCloneModal` / `CloudDownloadOutlined` …）全 0 ✓

#### 缺陷修复：方法论页宽表没有横向滚动条，右侧列被裁掉（无 SQL 变更）
- 现象：「IPD 方法论 → 交付件」列宽合计远超内容区宽度，右侧的 **工期偏移 / 状态 / 操作** 三列**完全看不见**，且**没有横向滚动条**，内容无从触及
- 根因：`DataTable` 本就支持 `scrollX`，但**类型限定为 `Number`** —— 想传 `'max-content'` 会被判类型不符；而 8 个方法论页面**一个都没传**，于是 antd 既不做横向滚动、也未压缩列宽，超出部分被容器直接裁掉
- 修复：`DataTable.scrollX` 放开为 `Number | String`（并写明"宽表请用 `'max-content'`"）；**8 个方法论页面统一补上 `scroll-x="max-content"`** —— 列宽未超容器时不出现滚动条，行为不变
- 与平台既有做法一致：`views/system/OperationLogPage.vue` 早就在用 `:scroll="{ x: 'max-content' }"`
- 实测（浏览器，守护进程以 `--hide-scrollbars false` 启动以显示滚动条）：把最右列 `scrollintoview` 滚进来后，**工期偏移 / 状态 / 操作 三列完整可见、开关与按钮可用**（左侧 `编码/名称` 随之滚出视野，证明容器确实在横向滚动）

#### 缺陷修复：先翻页再筛选会切出空表（分页写"共 5 条"，表体却 No data）
- 现象：「方法论 → 交付件」翻到第 2 页以后，再选阶段筛选（或输入搜索词）—— 表体显示 "No data"，分页器却写着"共 5 条"、且只画得出 1 页
- 根因：`DataTable` 的前端分页用 `slice((current-1)*pageSize, …)` 取当前页，但**数据变少时从不收敛 `current`**。46 条（5 页）翻到第 2 页后总数掉到 5 条，`current` 仍是 2 → `slice(10,20)` 切一个只有 5 条的数组 → 空表。三个现象（空表 / 共 5 条 / 只画 1 页）由此一次解释
- 修复：`DataTable` 增加页码收敛 —— 监听「前端总数 + 每页条数」，`current` 超过最大有效页时收敛到最后一页（数据没变少时是 no-op；后端分页模式不干预）
- 影响面：**共用该组件的所有前端筛选 / 搜索 / 列表**一并治好（8 个方法论页面，以及任何用 `DataTable` + 前端分页的页面）—— 修的是组件，不是只修交付件一页
- 实测（浏览器，走用户原路径）：清空搜索 → 翻到第 2 页 → 选「生命周期管理（IPD_LIFECYCLE）」→ **5 条正常显示**（退市计划（EOL）、变更通知（ECN / PCN）…）；另用搜索词 `IPD_LIFECYCLE` 复测同样通过

#### 界面用词：元器件库的「元器件申请」改为「IPN申请」（无 SQL 变更）
- 「企业资源 → 元器件库」的入库按钮与弹窗标题由 `libLabel + libAction` 拼出：把 `COMPONENT` 那一项从「元器件」改为「IPN」，两处**同时**变为 **「IPN申请」**；标准件 / 通用件的「标准件入库」「通用件入库」不受影响
- 连带对齐三处同名文案：创建成功提示（"IPN申请已提交：…"）、**后端**拒绝越库创建电子元器件时的报错（`PartServiceImpl`，路径指引里的按钮名应一致）、以及三处代码注释
- 实测：`javap` 反查编译产物，常量池里已出现 `IPN` 字面量 ✓；浏览器 `get text` 读到按钮 = **IPN申请** ✓
- 说明：只改"这个动作叫什么" —— **编号规则 / 对象类型（ELECTRONIC）/ 库名（元器件库）都没动**，本次改名不涉及数据与流程

#### 方法论实例层（六类，P2）
- **六张实例表**：`ck_deliverable`（旧残表**就地重定义**）/ `ck_gate` / `ck_review` / `ck_rule` / `ck_metric` / `ck_dashboard` —— 全部与 `ck_stage` 同构：`owner_oid + owner_type`（LINE=系列 / MODEL=型号）定位归属，内容列由模板**快照**而来，`UNIQUE (owner_oid, owner_type, code)` 保幂等，`template_code` 记录溯源
- **一个服务**：`MethodologyInstanceService#initFor(ownerOid, ownerType)` —— 六类各一道守卫（该类已有实例即跳过）、各自取模板（本租户优先、平台兜底）、逐条快照插入；**行业一次解析、六类共用**，取自 `ck_tenant.dev_method_code`（引导页选定、平台权威），不再用"有 TRADITIONAL 就用它"那种猜法
- **两个钩子**：`ProductLineServiceImpl`（系列创建）与 `ProductModelServiceImpl`（型号创建）在初始化阶段之后各追加一行调用 —— 与阶段同一时刻、同一事务
- **实例上两套状态别混**：`disabled` = 本项目裁剪（这一条不参与）；`status` / `decision` / `verdict` / `value` = 执行态（各自表达"做到哪了"）
- **指标不编造数值**：实例只快照计算口径（layer / level / source），`value` / `readiness` 留待运行期评估，此刻为空 = "尚未评估"
- 实测（真建一个系列 + 一个型号）：六类条数 **6 / 46 / 10 / 8 / 11 / 11 / 3**（阶段 / 交付件 / 阶段门 / 评审 / 检查规则 / 指标 / 驾驶舱）与模板逐项吻合；快照内容抽验通过（交付件的阶段 / 强制 / 门 / 完成条件 / 溯源齐备；驾驶舱 ★主视角只落在 `PRODUCT_MODEL`）；`value` 全为空 ✓
- **尚未包含**：问题类型（本次未要求）与**界面**（实例已落库，但还没有"系列 / 型号 → 六类实例"的查看页 —— 属下一步）

#### 能力开关读侧（③）+ 系列/型号页六个方法论面板

**能力读侧** —— 补上 `TenantCapability` 类注释里自陈"尚未做"的那一步：
- 后端 `GET /api/capabilities`（读 `ck_tenant_capability` 账本，只返回**已启用**的）+ 服务 `CapabilityService`
- 前端 `useCapabilities()`：模块级单例、一次请求、`has(code)` 供模板直接调用
- 口径：账本里只有"包里声明且平台已实现"的能力；`enabled=false`（管理员手动关闭）不算启用；**取不到按"未启用"处理** —— 宁可不显示入口，也不要点了发现什么都没有

**补码与登记**（把账本更新到事实）：
- `smart-hardware-ipd/capabilities.json` 新增三个码：`RULE_ENGINE` / `METRIC_MANAGEMENT` / `DASHBOARD`
- `PlatformCapabilities` 订正两条**已过时**的依据（交付件的"实例化待 P2"当天已落地；阶段门"尚无列与入口"已不成立），并新登记 `GATE_REVIEW` / `RULE_ENGINE` / `METRIC_MANAGEMENT` / `DASHBOARD`，依据里如实保留"自动判定 / 求值待 P3"

**实例读接口**：`GET /api/instances?ownerOid=` 一次取回六类（一套 IPD 仅 46+10+8+11+11+3 条，阶段过滤放前端做 —— 切阶段不发请求）

**页面（系列 / 型号）**：
- 阶段页签内：**过程资料（原有）/ 交付件 / 阶段门 / 评审 / 检查规则** —— 后四类按当前阶段过滤（评审按"服务于本阶段的门"匹配），页签带条数
- 页面级「**整机视角**」：**指标 / 驾驶舱**（与阶段无关）
- **每个面板都由能力开关控制**：交付件→`DELIVERABLE_MANAGEMENT`，阶段门与评审→`GATE_REVIEW`，检查规则→`RULE_ENGINE`，指标→`METRIC_MANAGEMENT`，驾驶舱→`DASHBOARD`；未启用即不出现
- 新增通用组件 `MethodologyInstancePanel.vue`（六类共用：列定义、状态色、完成条件摘要、分组件中文标签）

**顺手修正**：既有错别字「产品线管**业**」→「产品线管**理**」（路由标题 + `ProductSeries.vue` 悬停提示）

**实测**：
```
重装 IPD 包    CAPABILITY INSERT 4 / SKIP 5 / FAILED 0
              未实现三项如实写「本包声明「认证管理」但平台当前未实现，未启用（平台补齐后重装即生效）」
/api/capabilities  10 项（6 原有 + 4 新）
/api/instances     交付件 46 / 阶段门 10 / 评审 8 / 规则 11 / 指标 11 / 驾驶舱 3
浏览器            指标面板 11 项，「数值=暂未评估」；阶段内四类页签可点、带条数
```
（无 SQL 变更 —— 本次只加接口、登记与页面；`ck_tenant_capability` 的 4 行由重装包自动写入。）

#### 系列/型号页：阶段区改为「当前阶段卡片 + 下拉切换」（无 SQL 变更）
- **当前阶段 = 第一个门未通过的阶段**（P3 口径；判定逻辑就一处 `inferredStageOid` computed，P3 落地后不用再改判据）：
  · 某阶段没配门 → 跳过，不把它算作"未通过"；一个阶段有多道门 → 任一道未 `PASSED` 即算本阶段未通过；
  · 所有配了门的阶段都过了 → 停在最后一道门所在阶段；压根没有门实例（存量对象 / 未装方法论）→ 退回第一阶段
- **展示形态**：六个阶段页签 → **单张「当前阶段」卡片**（阶段图标 + 名称 + 描述 + 门状态标签）+ 右上角**下拉切换其他阶段**；用户手动切换后不再自动抢选中
- **实测**（浏览器）：当前阶段=概念阶段 ✓ 门未通过 0/2 ✓（概念阶段 TR1 + CHARTER 两道门都 PENDING）。今天门全是 PENDING，所以效果就是停在第一阶段 —— **预期行为**，P3 做完自动判定后自然变准
- **改版中修掉两个自己引入的缺陷**（记在这里免得重犯）：
  1. **暂时性死区**：自动选中的 `watch` 最初写在 `stageDefs` 声明**之前** —— `watch` 创建时会立刻读一次来源，触发 `Cannot access 'stageDefs' before initialization`，setup 中断并连带报 `$setup.hasCapability is not a function`。已移到 `stageDefs` 之后，并把原因写进注释。
  2. **滚动链断裂**：原外层 `.pl-stage-tabs` 承担着 `flex:1` 与 `.ant-tabs-content-holder{flex:1;overflow-y:auto}` 的内部滚动；换成单卡片后这层没了，阶段区被 `.pl-pipeline{overflow:hidden}` **裁掉且滚不到**（三种滚动方式都够不着）。已把 flex + 滚动链搬到新的 `.pl-stage-view`，并给「整机视角」加 `flex-shrink:0 + max-height:46vh`（短屏自己滚，不挤阶段区）。

#### 缺陷修复：阶段与交付件"行业"不同源 —— 阶段页签下过滤不出交付件（无 SQL 变更）
- **现象**：某租户（10-02 用传统模板引导、之后又装了 IPD 包）的「交付件」面板恒为 `0`，**尽管该对象确有 46 条交付件实例**（查库为证）
- **根因**：**阶段**的行业取自 `StageServiceImpl.resolveDefaultIndustry()` 的旧猜法（"本租户有 TRADITIONAL 模板就用 TRADITIONAL，否则取第一条"），而**六类实例**读的是 `ck_tenant.dev_method_code`。该租户两套阶段模板都在（IPD 6 + TRADITIONAL 6）→ 阶段落成传统六码（`MARKET_VALIDATION`…），交付件落成 IPD 码（`IPD_CONCEPT`…）→ 页面的 `stageKey === 当前阶段码` 永远为假 → 0。
  这正是 P1 设计里点名过的隐患（"行业一次解析、两者共用"），当时只改了新六类，**漏了阶段**。
- **修复**：
  1. `StageServiceImpl` 改用 `ck_tenant.dev_method_code`（与六类实例**同源**），读不到（未引导 / 老数据 / 异常）才退回旧口径 → 新建对象两边一致
  2. `MethodologyInstancePanel` 增加**如实提示**：本阶段 0 条**但本对象有**时，明确写出"本对象共有 N 条，它们不在当前阶段下 —— 常见于阶段与内容方法学不一致的存量对象"，不再让人误以为"没建"
- **存量对象**（阶段=传统 + 内容=IPD）**按约定不动**：面板会如实说明原因；新建对象不再有此问题
- **实测**：
  · 数据链：该租户 `dev_method_code=IPD` ✓ 阶段模板 IPD 6 + TRADITIONAL 6 ✓ 已有阶段实例=传统六码 ✓（三项合起来足可定案）
  · 活体：新建系列 → 阶段码 `IPD_CONCEPT…` ✓ 交付件 46 条**全部**落在该对象的阶段码下 ✓（即页面过滤全部命中）
  · 后端重启 ERROR 0 ✓ 无编译错误 ✓ 三方静态诊断 0 ✓

#### 面板不再"把已有数据藏起来"：本阶段无匹配时改列全集（无 SQL 变更）
- **缘起**：上一轮修了根因（阶段与交付件不同源），但**存量对象**（阶段=传统六码、交付件=`IPD_*`）按约定不动 → 其「交付件」面板仍显示 0，用户两次反馈"还是没展示出来"
- **关键认识**：问题**不在比较哪个字段** —— 页面比的正是 `ck_deliverable.stage_key` ↔ 当前阶段的 `code`；卡住的是**两边的值不是同一套**（该对象的 46 条 `IPD_*` 在其阶段（`MARKET_VALIDATION…`）里一个都不存在）
- **改法**（`MethodologyInstancePanel`）：本阶段 0 条**但本对象有**时 ——
  · **改列本对象全集**（有数据就一定显示出来，不再对着 0 发呆）
  · 顶部橙色横幅如实说明："本阶段无匹配，已改列本对象全部 N 条；它们的「所属阶段」不在当前阶段下 —— 常见于阶段与内容方法学不一致的存量对象"
  · **强制带出「所属阶段」列**，一眼看出它们属于哪套码
  · 新增 `allRows` 属性（四个阶段内面板传入各自全集；页面级指标/驾驶舱本就与阶段无关，不受影响）
- **顺带修掉一个短屏缺陷**：新增「整机视角」后，页面仍是"整页不滚、只让内层滚" + 阶段区 `flex:1` 会被压缩 → 短屏（约 500px 高）下阶段内容区被压成 **0 高**（元素在 DOM 里、点得到，但什么都看不见）。改为：`.pl-dashboard` 允许**整页纵向滚动** + `.pl-pipeline { flex:1 0 auto; min-height:60vh }` 保底 + `.pl-overview` 不再自限高度
- **实测**（浏览器，用可逆手段制造错位：给测试系列的 46 条 `stage_key` 临时加 `_X` 后缀，验证后**已改回**）：
  · 面板列出**全部 46 条** ✓ 横幅文案正确 ✓ 「所属阶段」列带出 `IPD_CONCEPT_X` ✓
  · 同一屏里 阶段门 (2) / 评审 (1) / 检查规则 (9) 仍**正常命中**当前阶段（概念阶段）✓ —— 说明"该匹配的匹配、该兜底的兜底"两条路都通
  · 撤销后复查：46 条、`_X` 残留 **0** ✓

#### 系列/型号页：阶段头并入页头的标题行（无 SQL 变更）
- 用户反馈"红框里占的篇幅太多"：阶段头（当前阶段 + 下拉切换）原为**独立卡片、独吞一整行**，与页头加起来占三四行
- 改法：把阶段头**并入页头 `.pl-header-main`**（标题与统计之间），去掉卡片外观（边框/背景/下外边距），只留一条左侧浅色分隔线
- 为在同一行放下它，累计压缩宽度（共约 180px）：删掉「当前阶段」标签（下拉框就在标题旁、自带箭头，含义自明）、删掉"切换阶段"四字、下拉宽度 190→130、阶段说明改为 `title` 悬停显示、页头组间距 24→12；容器加 `flex-wrap` 兜住窄屏
- 效果：页头的「标题」与「阶段头」**同处一行** ✓，原来给阶段卡片的那一整行**还给内容区**（阶段内面板可用高度 +约 50px）
- 实测（浏览器，窗口 1092px、可用约 910px）：标题与阶段头同排可见 ✓（`子系列 根因修复验证-系列 IPN-FIX-VERIFY` ｜ `🎯 概念阶段 [门未通过 0/2] [概念阶段 ▾]`）；**统计 pill 与"创建于"自动换到下一行** —— 这是 `flex-wrap` 的预期降级（宁可换行也不挤扁 pill），页头整体仍从"三四行"降到三行
- 仍可再收紧（本次未做，等你确认）：把统计 pill 与"描述 · 创建于"挪到**面包屑那一行的右侧**（那行有约 700px 空余），页头即可稳定为**两行**

#### 系列/型号页：页头左右对调 + 阶段头分两行（无 SQL 变更）
- 用户要求：① 阶段头与「产品系列/型号」标题块**左右对调**，并让标题块**尽量靠右**；② **阶段与阶段汇总信息分两行**
- 次序改动：`.pl-header-main > *` 上用三行 `order` 集中声明（阶段头 `order:1` 最左；标题块 `order:2` + `margin-left:auto` —— 吃满中间空档、贴住右侧统计列；统计列 `order:3` 最右）。**不搬动 DOM**：这块模板已改过多次，集中一处可读，也不必再切一次结构
- 阶段头改两行竖排（`.pl-stage-head` 转 `column`）：上行 `🎯 概念阶段`；下行 `[门未通过 0/2]` + 切换下拉（新 `.pl-stage-summary`）。竖分隔线由左侧改到**右侧**（它现在站在页头最左）；删除已无引用的 `.pl-stage-switch`
- 顺带治好上一轮的遗留：三块现在都是两行高，统计 pill **不再**被挤到第三行
- 实测（浏览器）：`🎯 概念阶段` / `[门未通过 0/2] [概念阶段 ▾]` 在最左 ✓；`产品系列 / 根因修复验证-系列` + `[根系列] … IPN-FIX-VERIFY` 紧贴右侧 `0子系列 0型号 6阶段` ✓；整页头恰好两行 ✓

#### 说明：方法论能力（阶段门 / 评审 / 检查规则 / 指标 / 驾驶舱）在已装企业里怎么补齐（无代码变更）
- **现象**：某企业明明装了行业包，却看不到「阶段门 / 评审 / 检查规则 / 指标 / 驾驶舱」页签（前端入口是 `v-if="hasCapability('GATE_REVIEW')"` 这类条件）
- **原因**：这些能力是在 `PlatformCapabilities` 里**后来才登记**的，而该企业的包是**登记之前**装的 —— 装包时按纪律跳过（安装报告原文："本包声明…但平台当前未实现，未启用"），`ck_tenant_capability` 账本里于是没有它们；界面按账本决定入口显隐，故入口不出现。**数据是齐的**（门实例、评审实例都在库里），纯粹是"入口许可"没开
- **补齐方式**：**重装一次该行业包**（幂等，只补不覆盖；管理员手动关掉的能力不会被重新打开）。实测：乘恺科技 `GATE_REVIEW` 由缺 → 有，页签随即出现 ✓
- **注意**：平台启动只自动装 `BUILT_IN_PACKS`（`platform-base` / `electronics-core`，且**只对平台租户**），**不含** `smart-hardware-ipd` —— 所以业务企业的新能力**不会**随重启自动补，必须重装该包
- **现状缺口**：界面上没有"重装行业包"按钮（`installIndustryPack` 接口目前只有实施/排障手动调用，`IndustryPackPage` 仅平台管理员可见且只读）；若要给租户管理员一个自助入口，可以补

#### MPN 管理（参考 Windchill 的 AML / AVL）：四张表 + 一套接口 + 资源库页签（含增量 SQL，见下）
- **四张表**：`ck_manufacturer`（制造商）/ `ck_supplier`（供应商）两张主数据 + `ck_part_aml`（AML：内部件 ↔ 制造商料号）+ `ck_part_avl`（AVL：AML 行 ↔ 供应商，含供应商料号 / 价格 / 币种 / 交期 / MOQ / SPQ / 首选货源）
- **三层语义**：内部件（`ck_part`，三套资源库共用的主表）→ AML → AVL。**AVL 挂在 AML 行上而不是内部件上** —— "从哪家买"是某个制造商料号的性质，换料号就换渠道；把两层压平（内部件直接连供应商）会丢掉"哪家供应商供的是哪个原厂料"这层事实
- **与 Windchill 的对应**：WTPart → `ck_part`；ManufacturerPart（制造商 + mpn 即其身份）→ `ck_part_aml`；ApprovedVendorList → `ck_part_avl`；`approvedStatus` 三态（PREFERRED / APPROVED / DISALLOWED，**DISALLOWED 保留行与原因、不作删除**，供采购排除追溯）；`lifecycleState`（ACTIVE / NRND / EOL）与 approvedStatus **正交**（认可但已 NRND 正是采购要看到的）
- ★ **补上行业包声明里的一个空洞**：`electronics-core/attributes.json` 声明 `supplierCount.derivedBy = count(distinct supplierRefs)`，而 `supplierRefs` 一直没有落地的表 —— 现在它就是 `ck_part_avl`。派生口径：`supplierCount = count(distinct ck_part_avl.supplier_oid)`（该内部件全部 AML 行合计），**值为 1 即命中** `smart-hardware-ipd/rules.json` 的 R-SH-11「单一来源器件风险提示」（该规则此前永远无法求值）
- **接口** `/api/mpn/*`：制造商 / 供应商 CRUD、选件器（按关键字搜内部件）、`/parts/{oid}/aml`（AML 树，行内嵌 avls）、`/parts/{oid}/summary`（汇总）、AML / AVL CRUD。**删除被引用的主数据会被拦**并提示改用「停用」（历史引用不能悬空）；AML 重复「制造商 + 料号」也会被拦
- **能力码** `MPN_MANAGEMENT`：`electronics-core` 包内声明 + `PlatformCapabilities` 登记。实测**启动装包即自动落账到平台租户**，不需要手动重装
- **前端**：资源库页（企业资源）新增「**MPN 管理**」页签（受 `MPN_MANAGEMENT` 能力控制）—— 制造商 / 供应商 / AML·AVL 三个页签；AML 行可展开看它的供货渠道；顶部实时显示 `amlCount / supplierCount / 单一来源风险`标签
- **实测（端到端，全走真接口）**：建件 → 建 TI → 建两家供应商 → 建 AML（重复料号被拦 ✓）→ 挂两家 AVL（首选 + 价 + 交期 + MOQ/SPQ ✓）→ 汇总 `supplierCount=2` ✓ → **删一家变 1 且 `singleSource=true`** ✓ → 删被引用的制造商被拦 ✓ → 删 AML 级联清掉其下 AVL ✓
- **遗留（如实记）**：① 属性自动派生（把 supplierCount 回填到 IBA、让 R-SH-11 由规则引擎自动触发）尚未做，目前由 `/summary` 现算；② 内部件不是接口创建的 —— 项目有业务规则「电子元器件必须归档在企业资源库的元器件库（走 IPN 申请）」，验收用的是直插的演示件，已删

#### MPN 主数据（制造商 / 供应商）移入导航「主数据」，与 MPN 同一能力开关（无 SQL 变更，仅前端）
- **用户决策**：制造商/供应商名录要有独立入口放导航栏 —— 但它是 MPN 管理的一部分，**必须同样受 `MPN_MANAGEMENT` 能力控制**
- 新增一级导航项「**主数据**」（位于「分类管理」与「业务配置」之间，配置类聚在一起 ✓）+ 路由 `/master-data` + 面包屑 `主数据（制造商 / 供应商）`
- 新页面 `MasterDataPage.vue`（制造商 / 供应商两个页签）；两个表格与表单抽成**可复用面板** `ManufacturerPanel.vue` / `SupplierPanel.vue` ——
  「企业资源 → MPN 管理」里的同名页签**用同一对组件**，是**两处入口、一份实现**（不会出现两套表格两套表单各自演进）
- 保留「企业资源 → MPN 管理」作为**物料视角**入口：AML·AVL 仍按件挂；从物料进来时可就近维护名录，不必跳走
- 显隐口径与项目其它能力一致：**取不到能力按"未启用"处理**（宁可不显示入口，也不给"点进去什么都没有"的空诺）
- **实测（浏览器，含开关真验）**：
  · 启用时：侧边栏出现「主数据」✓；页面标题 / 两页签 / 空态引导（"如 TI · 德州仪器"）正常 ✓
  · **停用（`enabled=false`）后刷新：「主数据」菜单项立即消失** ✓✓
  · 恢复（`enabled=true`）后：菜单项与「MPN 管理」页签**一同回归** ✓✓
- 注：接口层仍不校验能力（与项目既有口径一致 —— 能力控**入口显隐**，不控接口）。若将来要做硬管控，再统一加注解拦截

#### 能力开关补齐：5 个旧功能的界面入口全部接上（无 SQL 变更，仅前端）
- **背景**：能力的"读侧"（界面按账本 `ck_tenant_capability` 决定入口显隐）是**后补**的 —— 此前只接了 6 个能力（系列/型号页的指标/驾驶舱/交付件/阶段门/评审/检查规则 + MPN），其余能力"账本里有行、界面没人读"，开关形同虚设。用户追问「BOM_COST 有没有补上能力开关」→ 本次补齐
- **本次接入的入口**：
  · `BOM_COST` → 零件详情页 BOM 工具栏的「成本报告」按钮（`PartDetail.vue`）
  · `COMPONENT_MANAGEMENT` / `ECAD_MANAGEMENT` / `STD_PART_LIBRARY` / `GEN_PART_QUALIFY` → 企业资源的**四个资源子库页签**（`EnterpriseResource.vue`，按新增的 `LIB_CAPABILITY` 映射过滤），以及「资源库配置」里的「通用件阈值配置」「标准件入库流程」两个页签（`ResourceLibraryCategoryConfig.vue`）
- **口径**：容器（`ck_resource_container`）决定"**有哪些库**"，能力账本决定"**这个租户能不能用**"—— 两者都对才出现；未登记映射的子库（技术文档 / 产品图册 / 其他）不受能力控制，照旧显示
- **一个易踩点**：能力是**异步**到的，若当前选中的页签恰好被过滤掉，必须自动切到第一个可见的 —— 否则 `activeTab` 停在一个不存在的页签上，页面看起来是空的
- **写法统一**：`ProductLineDashboard` 原用别名 `has: hasCapability`，统一为 composable 原生名 `has`（7 处）；现全仓只有一个判定入口 `useCapabilities().has(...)`
- **实测（浏览器，真开真关）**：
  · 停用 5 个能力 → 刷新：**四个子库页签消失** ✓、选中**自动落到「技术文档知识库」** ✓、无错误浮层 ✓✓
  · 恢复 → 刷新：**八个页签全回归** ✓、「元器件库」重新成为默认选中 ✓✓
- ✗ **一处只做了代码级验证**：`BOM_COST`（库里当前没有零件，进不到 BOM 工具栏）—— 机制与已验证的四处完全相同，但如实标注未做页面级验证
- ✗ **修掉一个只在运行时暴露的自造 bug**：`visibleLibraries` 的 `watch` 被写在 `resourceChildren` 定义**之前** —— `watch` 创建时要立即读一次源 → 撞上 `const` 的 TDZ → **页面白屏**（`Cannot access 'resourceChildren' before initialization`）。**编译 200、静态诊断 0 都发现不了**，只有真开页面才暴露；已重排定义顺序并把教训写进注释
- **未做**：接口层能力硬校验（与"能力只控入口显隐"的既有口径不同，需先定口径再动）

#### platform-base 补注册：BOM 行对象（BOM_LINK）+ BOM 行属性（位号 / 贴装面）
- **由来**：用户指出「platform-base 基础包缺少 BOM 行对象（BomLinks）的注册，以及 BOM 行属性的注册」—— 两处都属实（该包此前**连 `attributes.json` 文件都没有**）
- **为什么必须先注册对象**：属性组的 `bindTo` 填的是**类型码**（`AttributeInstaller` 用 `typeDefinitionService.findByCode(typeCode)` 查类型），类型不存在时只记 `missing` 而**不报错** → 属性**静默绑不上**。所以顺序是「先对象、后属性」，且 `pack.json` 里 `ATTRIBUTE` 必须排在 `SOFT_TYPE` **之后**（已在 pack 的 note 里写明这条约束）
- **① `softtypes.json`**：新增 **`BOM_LINK`**（BOM 行，第 7 个 OOTB 根类型；`hostCode=BOM_LINK`、`sortOrder=13`、域 `PRODUCT_DATA_DOMAIN`）—— 它是**唯一不绑**编码规则 / 版本规则 / 生命周期模板的根类型（关系对象：行号由 `ck_bom_links.line_number` 表达、随父件迭代受控、无独立生命周期），故该条目不写 `numberRule/versionRule/lifecycleTemplate`（安装器对这三个字段的 null 是安全的）
- **② 新建 `attributes.json` + `pack.json` 声明 `ATTRIBUTE`（`reusable:false`）**：
  · **`refDes` 位号**（STRING）—— 器件在 PCBA 上的位置标识（Reference Designator）
  · **`placementSide` 贴装面**（ENUM: TOP / BOTTOM / BOTH）
  `reusable:false` 的原因（照 `AttributeInstaller` 的注释）：**IBA 按租户独立**，`IBAService.findByCode` 只查当前租户 → 平台建的属性别的租户看不见，故每个租户安装时各建一份
- **为什么位号在「行」不在「件」**：同一个器件在 A 板是 R1/R2、在 B 板是 R15 —— 挂到器件上会让不同父件的位号互相污染。Windchill 同口径：Reference Designator 挂在 `WTPartUsageLink` 上
- **③ `pack.json`**：`SOFT_TYPE` count 11 → 12；`softtypes.json` 与 pack 的说明文字同步（原写"6 个 OOTB 根类型（各带编码规则…）"，现为 7 个且 BOM_LINK 不带这三项）
- **实测**（**重启后端即自动补装**，无需手动重装）：`ck_type_definition` 有 `BOM_LINK`（`root_type_code=BOM_LINK` ✓ `sort_order=13` ✓ `type_kind=OOTB` ✓ 归平台租户）；`ck_iba` 有 `REFDES`（STRING）/ `PLACEMENTSIDE`（ENUM，`constraints_json={"enumValues":["TOP","BOTTOM","BOTH"]}`）；`ck_type_iba` 两条映射均在 ✓✓
- 顺带核实（**更正一条此前的错误说法**）：`ck_iba` 共 **97** 条、`ck_type_iba` 映射 **32** 条 —— `electronics-core` 的 24 个器件属性（`MANUFACTURER/MPN/SUPPLIERCOUNT/PREFERREDLEVEL/ROHSCOMPLIANT/…`）**早已落地**。此前"平台尚无 ATTRIBUTE 安装器"的说法是**照抄 `attributes.json` 里过期的自述**（那是该文件诞生时的状态，安装器后来已实现，注释没跟着更新）—— **代码里 `AttributeInstaller`（11 KB）一直在**

#### 修正上一条：位号/贴装面**移出** platform-base，改由 electronics-core 声明（行业属性不进平台包）
- **用户意见**（三条，都采纳）：① `attributes.json` 应与 `softtypes.json` 合并 —— 业务对象与它的描述同处一文件才完整；② 合并时要能同时表达**两类属性**（基本属性 / IBA 属性）；③ **位号是电子行业特征，platform-base 不该体现**
- **本次已做（第三条）**：
  · 删除 `platform-base/v1.0.0/attributes.json`，撤回其 `ATTRIBUTE` 资源声明 —— 平台包只剩**通用**内容；`BOM_LINK` 类型**留在**平台包（它是通用关系对象，参照 Windchill WTPartUsageLink）
  · `electronics-core/v1.0.0/attributes.json` 新增第 6 组 **`BOM_USAGE`「BOM 行信息（电子）」**，`bindTo: ["BOM_LINK"]`（**跨包引用**平台包的类型），装 **`refDes` 位号** 与 **`placementSide` 贴装面**；pack 的 `ATTRIBUTE` count 24 → 26
  · 文件头说明同步（5 组 24 个 → 6 组 26 个）
- **实测**（先清掉上一轮以平台包名义在库里的两条 IBA，重启后端让它按新声明重装）：
  · 库里两条回来了，且 **`sort_order` 由 201/202 变为 225/226** —— 即电子包属性文件的第 25、26 条（`SORT_BASE=200`）✓，证明确实换了来源
  · 装包报告明细：`SKIP | refDes | 属性已存在，复用「位号」（STRING）；**已绑定过 BOM_LINK**` ✓（两条同）—— 电子包确实在管这两条 ✓
  · `ck_type_iba` 上 BOM_LINK 的映射仍为 2 ✓，编译 0 错误 / ERROR 0 ✓
- **顺带查实的一条硬约束**（与第①②条合并方案直接相关，先记下以免误判）：
  **"基本属性"无法由包创建** —— `ck_attribute_definition` 由 `AttributeInitializer`（`@Order(3)` 启动任务）**反射 Java 实体类字段**登记，且对 DOCUMENT/PART/FUNCTIONAL/ENG_DOCUMENT 走的是「**先清后建**，确保与实体类字段完全一致」；其真源是**实体类 + schema.sql**，不是包文件。故包里的"基本属性声明"只能做**声明 + 安装时校验对齐**（声明了实体没有的字段就如实报出），**不能建列**，更不能写 `ck_attribute_definition`（会被反射链的清建覆盖）。
  「IBA 属性」则相反：真源就是包文件（`AttributeInstaller` → `ck_iba` + `ck_type_iba`）✓
- **未做（等确认）**：①②的**文件合并**（把属性段并入 `softtypes.json`）。方案已成型：类型条目内 `attributes`（基本属性，声明+校验）+ `ibaAttributes`（本类型 IBA）+ 顶层 `attributeGroups`（跨包类型的 IBA，如电子包给 BOM_LINK 加位号）；需同步改 `SoftTypeInstaller`（读新段）与 `AttributeInstaller`（改读位置），并把 `electronics-core/attributes.json` 的 26 条搬入其 `softtypes.json`

#### platform-base 补「基本属性声明」（attributes.json · 124 条）+ AttributeInstaller 支持两类属性
- **由来**：用户要求把 `ck_attribute_definition` 里 PART / DOCUMENT / ENG_DOCUMENT / PRODUCT_LINE / PRODUCT_MODEL 的基本属性注册，组织进 `platform-base` 的 `attributes.json`，**数据参照库表现状**
- **生成方式**：**由 SQL 直接吐 JSON**（`jsonb_pretty` + `\o` 落盘到包目录）—— 数据以库为准，124 条不手抄：PART 31 + DOCUMENT 29 + ENG_DOCUMENT 40 + PRODUCT_LINE 12 + PRODUCT_MODEL 12
- **文件结构**：顶层 `baseGroups[]`（每组 `code` / `name` / `bindTo` / `attributes[]`），属性项含 `code` / `name` / `dataType` / `required` / `searchable` / `listable` / `editable` / `uiComponent` / `sortOrder`
- **`AttributeInstaller` 扩展为同时处理两类属性**（格式互不干扰）：
  · `groups[]` → **IBA 扩展属性**（原有职责：写 `ck_iba` + `ck_type_iba`）
  · `baseGroups[]` → **基本属性声明**：只与 `ck_attribute_definition` **对齐校验，绝不写库**
    理由（已写进类注释）：基本属性的真源是 **Java 实体类 + schema.sql** —— `AttributeInitializer`（`@Order(3)`）反射实体字段登记、对 PART / DOCUMENT / FUNCTIONAL / ENG_DOCUMENT 走「**先清后建**」；本安装器若写这张表，写进去的会被随后的清建覆盖，还会造成"包与实体谁是真源"两套并存
- `pack.json`：新增 `ATTRIBUTE` 资源（`count: 124`，`reusable: true`）
- **实测**：重启后端 → 装包报告 5 条「基本属性声明与实体一致」（DOCUMENT 29 / ENG_DOCUMENT 40 / PART 31 / PRODUCT_LINE 12 / PRODUCT_MODEL 12），**124 条全对齐** ✓；编译 0 错误 / ERROR 0 / 静态诊断 0 ✓
- ✗ **途中踩了两个"只有真跑才暴露"的坑**（都已修，并把原因写进注释）：
  ① 校验调用最初写在 `install()` **末尾** → 被「没有 `groups[]` 数组」的**早退分支**跳过，报告里只剩那一句（platform-base 的属性文件只有 `baseGroups`，正好每次命中）→ 已移到早退**之前**
  ② 首版用 `AttributeDefinitionService.findByEntityName` 取"库里已有字段" → 它的 SQL 带 **`AND editable = true`**（那是给界面表单用的），**系统只读字段**（number / masterOid / revision / checkedOut / createdAt / creator …）不返回 → **68 条被误报成"库里没有"** → 已改为 `JdbcTemplate` **直查全量**（并**显式带租户** —— 直查不走 MyBatis 拦截器），同时纠正了该服务接口那句"获取某实体的**所有**属性定义"的失真注释
- **未做（等确认）**：`FUNCTIONAL` 同类属性还有 **27 条**，本次未纳入（用户只列了 5 个类型）—— 若要一并声明，说一句即可

#### FUNCTIONAL（功能架构）归位：platform-base → military-gjb / automotive-apqp（无 SQL 变更）
- **用户判断**：FUNCTIONAL 应在 APQP 与 GJB 包中注册 —— 依据是它自己的描述「**装备级功能系统（军工）/ 车型功能域（汽车）**」：它本身就是**行业概念**，不是平台基础设施（与"位号不该在 platform-base"同一道理）
- 改法（6 处 / 5 个包声明文件）：
  · `platform-base/softtypes.json`：**移除 FUNCTIONAL**（类型 12→11）；`pack.json` 的 `SOFT_TYPE` count 12→11；文件 note 同步（"7 个 OOTB 根类型"→6、"功能架构三件套"从平台子类型清单移除）
  · `military-gjb/softtypes.json`：**新增 FUNCTIONAL**（军工措辞：型号功能架构顶层分解，武器系统/火控/导航/电子对抗…；`kind` 由 OOTB 改为 `SOFT_TYPE`、`source=ootb`，与同包 SYSTEM/SUBSYSTEM/CI 同风格）；count 3→4；**note 改写**（原文写着"父类型 FUNCTIONAL 仍在 platform-base…全行业共用"—— 已被本次改动推翻）
  · `automotive-apqp/softtypes.json`：同上（汽车措辞：整车功能顶层分解，动力域/底盘域/车身域/智能座舱域…）
- **编码规则 `FUNCTIONAL-NUM` 刻意留在 platform-base**（`numberRules.json` 未动）：它被两个行业的 `SYSTEM/SUBSYSTEM/CI` 共同引用，放共用层比在两处各复制一份稳；两个包的 note 都写明了这一点
- **同码类型在两个包各声明一份**：都装时后装的一方按 code 判重跳过（`SoftTypeInstaller` 幂等）；两个包 note 都加了"两份定义必须保持一致，改一处记得同步另一处"
- **实测**（重启后端 + 真调装包接口取报告）：
  · platform-base 报告 `SOFT_TYPE` 明细 **11 条**，**已无 FUNCTIONAL** ✓
  · military-gjb 与 automotive-apqp 各 **4 条：FUNCTIONAL / SYSTEM / SUBSYSTEM / CI** ✓✓（两个包 `enabled:false`，但显式调 install 接口可装 —— 事实如此，一并记录）
  · 编译 0 错误 / ERROR 0 ✓
- ✗ **途中我自己犯了个低级错，被校验脚本当场抓住**：在 `platform-base/softtypes.json` 的 `types[]` 里插了 `<!-- -->` **HTML 注释** —— **JSON 不支持注释**，整份类型定义会读不出来（现有库因类型都已存在而未受影响，但**新建租户会装不上**）。已删除。教训：**JSON 合法性的唯一可靠校验是 `JSON.parse`**，肉眼看不出"多了注释/少个逗号"
- **遗留（待定）**：库里**平台租户**仍留着旧的那行 FUNCTIONAL 类型（platform-base 早先装的），装包时按判重 SKIP 未重建 —— "平台不再提供它"的语义要彻底生效需删掉这行；该类型当前**无实例数据**（清库时已删尽），故删除是安全的，但属破坏性操作，等确认

#### 功能架构（FUNCTIONAL）前端补能力开关 `FUNCTIONAL_ARCHITECTURE`（无 SQL 变更）
- 承接上一节"FUNCTIONAL 归位行业包"：类型既然只在 military-gjb / automotive-apqp 里，**没装这两个包的企业就不该看到功能架构入口** —— 补上开关
- **为什么用能力账本、而非"类型是否存在"**：`SoftTypeInstaller` 判重查「本租户 **or** 平台级」，类型是**平台共享**的；加上库里平台租户还留着旧的那行 FUNCTIONAL（见上一节"遗留"）→ **类型树里现在仍有 FUNCTIONAL**，按类型判断的开关**当下根本不生效**。能力账本（`ck_tenant_capability`）是本租户粒度、支持管理员手动关闭，且与全站入口口径一致
- 改动（5 个文件）：
  · 新建 `military-gjb/v1.0.0/capabilities.json` 与 `automotive-apqp/v1.0.0/capabilities.json`，各声明 `FUNCTIONAL_ARCHITECTURE`（两包同码 → 落账幂等，装第二个时 SKIP）。注意 `capabilities.json` 是**固定文件名**：`IndustryPackLoader.CAPABILITIES_FILE` 按包 basePath 直读，**不需要**在 `pack.json` 的 resources 里登记
  · `PlatformCapabilities` 登记 `FUNCTIONAL_ARCHITECTURE`（依据：`ck_functional` + `ck_functional_iteration` + `FunctionalController /api/functionals` + 系列/型号页入口；架构分解视图与类型专用表单如实标 P3）
  · `ProductLineDashboard.vue`：3 个「新建功能架构」按钮的 `v-if` 由 `stageManagedObjects.has('FUNCTIONAL')` 改为 **`&& has('FUNCTIONAL_ARCHITECTURE')`**（一次 `replace_all` —— 三处文本完全一致）
  · `ProductSeries.vue` / `StageTemplateConfig.vue`：「管理对象类型」下拉改为 `computed`，按能力过滤 FUNCTIONAL；**标签字典保留全量** —— 否则历史阶段模板里已配 FUNCTIONAL 的行会显示成裸 code。⚠ `ProductSeries.vue` 的 `managedObjectOptions` **同时兼任标签查找源**（列表里 `find(o => o.value === t)?.label`），这一点是搜索时才发现的，差点改出退化 —— 已拆成「全量字典 `MANAGED_OBJECT_LABELS`」+「过滤后的下拉选项」两件东西，查找处也一并改用字典
- **刻意不动**：`api/index.js` 的 `ENTITY_API_PATH.FUNCTIONAL` 与 `DynamicForm.vue` 的 `PART_SOFT_TYPE_IBA` 集合 —— 它们是「实体码 → 端点/宿主」的映射表，**不是入口**，多一个成员无害；动它们属于重构，不在本次范围
- 账本由 `MainLayout` 顶层 `loadCapabilities()` 统一加载（**已核实 `MainLayout.vue:307`**，非注释臆测），故那两个页面**只读**，不必再调 load
- **实测**：
  · 装 `military-gjb` → 报告 `CAPABILITY/FUNCTIONAL_ARCHITECTURE = **INSERT**` ✓（若未在 PlatformCapabilities 登记，这里会是"本包声明但平台当前未实现"的 SKIP —— **登记与否，装包报告上一眼可辨**）
  · 装 `automotive-apqp` → 同名能力 `= SKIP` ✓（幂等，不重复落行）
  · `GET /api/capabilities` 含该码 ✓（本租户总能力数 7）
  · **反向验证（关得掉才算开关）**：临时把账本该行 `enabled` 置 false → API 仍返回该行，但 `useCapabilities` 的 `enabled !== false` 过滤使 `has()` 为 **False** ✓ → 已改回 `true`（现 `enabled=t, source_pack=military-gjb@1.0.0`）
  · 前端三文件 lint 0 ✓ vite 编译 HTTP 200 ✓
- ✗ **两处如实交代**：① 无头浏览器（agent-browser）本轮**环境性崩溃**（`Chrome exited early (exit code: 3)`），**未能出截图** —— 以 lint + vite 编译替代证据，**没有**为它去杀你正在用的 Chrome；② 我重启后端时脚本里的 `Stop-Process -Id $parent` **误杀了前端 dev server**（前后端父进程相同），已用 `npm run dev` 恢复（8080 → 200）。该重启脚本这一行有副作用，后续应改为**只杀占用 8082 的进程本身**

#### 命名全面归一：库内属性 code 一律 UPPER_SNAKE（库改动 + 声明同步）
- **起因**：用户指认「`attributes.json` 里 BOM 行的 code 是 `BOM_USAGE`，而 `softtypes.json` 里是 `BOM_LINE`」。排查结果：**`BOM_LINE` 全仓 0 命中、全库 0 命中**（`BOM_USAGE` 是**属性组** code、`BOM_LINK` 是**类型** code，两层本就不该同名 ✓）。但"命名不统一"确实存在，且不止一处：
  · ① `ck_iba.code`：声明/引用/示例全是 camelCase（`supplierCount` / `refDes`）→ 库里却是 UPPER 连写（`SUPPLIERCOUNT` / `REFDES`）；而**分类树那批**（`PIN_COUNT` / `B_VALUE`）反而是标准的 UPPER_SNAKE —— **同一张表两种风格**
  · ② `ck_type_definition.source`：`OOTB` 7 条 vs `ootb` 15 条（`TypeDefinition.java` 的注释记着它**曾致 OOTB 判定失效**："不可删除保护失效、也无法作为根类型被 findRootCode 识别"）
  · ③ 三处**过时注释**：`attributes.json` 称"没有包级 ATTRIBUTE 安装器"、`rules.json` 称"平台无规则表、无规则引擎"、`R-SH-07` 的 note 称"qualificationStatus 属性不存在"—— 全都与实现不符
- **口径定为 UPPER_SNAKE** ✓（依据：类型码 `BOM_LINK` / `ENG_DOCUMENT`、表名 `ck_*`、以及已存在的 `PIN_COUNT`）
- **库侧**（`BEGIN` 事务 + 全库备份 `ck_plm_backup_before_naming_unify.sql`）：
  · `ck_iba.code` **改 20 条**（`ALTERNATECOUNT→ALTERNATE_COUNT` … `REFDES→REF_DES`、`SUPPLIERCOUNT→SUPPLIER_COUNT`、`QUALIFICATIONSTATUS→QUALIFICATION_STATUS`、`ULCERTNO→UL_CERT_NO` 等）
  · `ck_type_iba_data.attr_code` 与 `ck_cls_iba_data.attr_code` **同步**（这两张按 code 存值、**无外键兜底，漏改就丢值**；实测均为 **0 行** → 此刻零数据风险；语句幂等保留）
  · `ck_type_definition.source` **15 条** `ootb` → `OOTB`
  · **单字/缩写 33 条不动**（`MOQ` / `MPN` / `TYPE` / `SERIES` / `PACKAGE` / `CAPACITANCE` … —— 本来就不需要下划线）
  · **绑定未受影响**：`ck_type_iba` / `ck_cls_iba` 按 `iba_oid` 关联（有外键）—— 实测 `BOM_LINK` 的 2 条绑定仍在 ✓
- **声明侧**（**12 个文件 / 106 处**）：`electronics-core/attributes.json` 20 条 code、`rules.json` 7 处 `"field"`、`smart-hardware-ipd/deliverables.json` 6 处 `"field"`、`rules.json` 1 处、两个 `sample/*.json`（`attributes` 键与 `refDes`）、`electronics-core/metrics.json`（`"source"` 引用 + note 裸词）、`processes/forms/forms.json`、四个 `softtypes.json` 的 `"source": "ootb"` → `"OOTB"` 共 20 处
- ★ **两处漏改是"校验脚本"抓出来的，不是我以为的对**：① 初版脚本**手写文件白名单**，漏了 `smart-hardware-ipd/deliverables.json`（它的 `completionRule` 里有一堆 `"field"`）；② `metrics.json` 的引用键是 **`"source"`** 而非 `"field"`，且扫描只到两层、漏了 `processes/forms/**`。→ 已改为**递归扫全部 .json + 按文件角色定键名**，并保留 `tmp-json-check.js` 式校验（48 个 JSON 逐个 `JSON.parse` + 老写法残留计数）
- **实测**：48 个 JSON 全部可解析 ✓ 老写法残留 **0** ✓ 老 source 残留 **0** ✓；装 `electronics-core` 报告 `SUPPLIER_COUNT=SKIP` / `REF_DES=SKIP` / `PLACEMENT_SIDE=SKIP` ✓✓（**声明与库对得上** —— 若两边分叉，这里会是 `INSERT`）；`ck_iba` 仍 97 条 ✓；compile 0 / ERROR 0 ✓
- ✗ **未做（另一个命名空间，等你定）**：`forms.json` 的表单**字段码** `needAlternate` 与属性同名 —— 那是"表单字段"不是 IBA（note 自称"与 attributes.json 同源"）；本次连声明一起改了 ✓，但库侧 `ck_process_form_template` 用 `node_types`/`component` 存字段（**无独立字段列**）→ 该表若有数据需单独核对
- ✗ **列出但未改**：上述三处过时注释（属"文档与实现不符"，与命名归一是两件事）—— 建议单独一轮处理

#### 新增项目级 skill：`type-attribute-operations`（固化 type 属性三层模型；无产品代码变更）
- **起因**：排查属性时反复在 `ck_attribute_definition` / `ck_iba` / `ck_type_iba` / `ck_cls_iba` / 两张值表之间绕；用户要求把"type 属性操作"的知识固化成 skill，避免每次理解任务时认错层
- **内容**：三层来源与各自落库顺序（**① SYSTEM**＝OOTB 实体字段，`AttributeInitializer` 反射登记、软类型**按 `root_type_code` 回落**；**② IBA**＝类型扩展，`ck_iba` → `ck_type_iba` → `ck_type_iba_data`；**③ 分类 IBA**＝实例挂分类，`ck_cls_iba` → `ck_cls_iba_data`，定义仍在 `ck_iba`）、**改动连带关系**、**已知缺口**、**14 条踩坑**
- **核实并更正三处**（都写进 skill）：
  · 表名是 **`ck_attribute_definition`**（全库没有 `ck_attribute`）
  · IBA 的**值**在 `ck_type_iba_data` / `ck_cls_iba_data`，键是 `attr_code` ↔ `ck_iba.code`，**两张表都没有外键**（改 code 漏改就丢值）；`ck_cls_iba_data.entity_oid=''` 表示"分类节点默认值"
  · 分类绑定是**实例级**（`ck_part.cls_oid → ck_classification.oid`）—— `ck_classification` 里**没有"类型"列**，全库也没有"类型↔分类"绑定表，故"某类型的分类属性"只有给定具体对象才能答
- **附两个已实测脚本**：`scripts/inspect-type-attributes.sql`（按类型码查三层属性，system 层自动回落，可选 `-v entity=` 带出分类 IBA）、`scripts/verify-attribute-integrity.sql`（悬挂引用 / **改名漏改探测器** / 风格漂移 / 注册表缺口 / 规模基线）
- **打包校验**：`✅ Skill is valid!`；位置 `.codebuddy/skills/type-attribute-operations/`（项目级，随仓库共享）
- ✗ 如实交代：写脚本时我**两处凭记忆写错**（`ck_type_definition.lifecycle_template_code` 不存在、体检脚本用正则猜"多词连写"误报 `SPECIFICATION`）—— 都已按 `\d` 实测修正；后者刻意改成"只报确定异常 + 列清单供人工判"，因为**任何按大写拆词的算法都会猜错**（`SPECIFICATION` 是单字、`REFDES` 实为 `REF_DES`）
- **用户补充（已核实并写入 skill）**：type 实体的 IBA 值在 `ck_type_iba_data`、分类属性值在 `ck_cls_iba_data`。顺带查实两处**代码级细节**（比补充更细一层）：
  · `ck_type_iba_data.entity_type` 填 **宿主码大写**（`PartServiceImpl.IBA_ENTITY_TYPE = "PART"`）—— 而 `schema.sql` 注释写的是小写 `product_line, team, document`、`IBADataService` javadoc 写的是表名 `"ck_product_line"`：**同一列三处说法不同，代码才是真源**（新增实体照抄同类常量，别按注释填）
  · **两张值表粒度不同**：实体 IBA 挂**主对象**（`entity_oid` = 主对象 oid），分类 IBA 挂**迭代**（`entity_oid` = `ck_part_iteration.oid`）→ `PartController.saveAs` 的"另存为"要把分类值从**源最新迭代**复制到**新最新迭代**，实体 IBA 则按主对象复制一份 —— **版本迭代 / 另存为时两者行为不同**，排查"改了版本属性没了"要先分清哪一类
  · 写入入口也是两套：`IbaDataSupport`（`softtype/service/impl`）+ `IBADataService` ↔ `ClsIbaDataSupport`（`cls/service/impl`）+ `ClsIbaDataService`
  · 据此又修了一处脚本：`inspect-type-attributes.sql` 第 3 段原来只按 `attr_code` 过滤，现补上 `entity_type`（用 `root_type_code` 推），避免同名 IBA 挂不同宿主时串行

#### attributes.json 键名正名：`baseGroups`→`entityFields`、`groups`→`ibaGroups`（+ 两道防呆校验）
- **起因**：用户提出"`baseGroups` 语义不明，是否改成 `IBAGroups`" —— ✗ **方向正好相反**：`baseGroups` 装的是**实体字段**（SYSTEM 层，恰恰**不是** IBA），`groups` 才是 IBA。但**命名确实不对称**：`groups` 是**裸名**（看不出是 IBA），`baseGroups` 要靠"相对什么的基础"才读得懂
- **采纳方案 A**（用户选定）：
  · `platform-base/attributes.json`：`baseGroups` → **`entityFields`**（5 组 124 条）
  · `electronics-core/attributes.json`：`groups` → **`ibaGroups`**（6 组 26 条）
  · `AttributeInstaller`：**新键优先、旧键兼容读取**（读到旧键记一条 SKIP 提示迁移）；类注释同步正名
- **两道防呆校验**（比原提的 `kind` 字段更轻 —— 用**事实互相校验**，零新字段）：
  · `ibaGroups[]` 的组**缺 `bindTo[]`** → 记 **FAILED** 并跳过整组（原先 `installOne` 会静默创建一批"没有任何类型能用到"的孤儿属性 —— 报告里 `bound`/`missing` 都是空，**看不出问题**）
  · `entityFields[]` 里出现"其实是 IBA"的字段 → 校验文案补上 *"★ 若这些其实是 IBA 扩展属性，请移入 `ibaGroups[]` —— 放在 `entityFields[]` 里永远不会落库"*
- **实测（含反向测试）**：先人为埋两个错 → 报告如实报出（`DOCUMENT_BASE` **指名** `SUPPLIER_COUNT` 并给出迁移提示 ✓；`BOM_USAGE` FAILED 且**只跳过该组**、其余 24 条 IBA 照常 ✓）→ 撤掉埋错重启复验**全绿**（platform-base 6 条全 SKIP / FAILED 0；electronics-core 26 条全 SKIP / FAILED 0 —— **既有绑定零影响** ✓）；compile 0 / ERROR 0 ✓
- **影响面排查（改前做的）**：这两个键的读取点**只有 `AttributeInstaller` 的两处 `json.get`**；前端 `frontend/src` 对它们 **0 命中**（命中的 8 处都是无关的 `groups`：流程设计器 `layout.groups` / `ports.groups`、BOM 成组替代）→ **改名不影响前端** ✓
- 同步更新 `type-attribute-operations` skill（三层说明 / 数据流图 / 代码索引 / `pitfalls ⑧` 补新防线 + 新增 `⑰` 键名正名）
- 兼容期：全仓已无 `"baseGroups"` 与顶层 `"groups"` 的使用，兼容分支可择机删除

#### 核实 `AttributeInitializer` 的完整职责（回答"entity fields 的初始化类还需要吗"；无代码变更）
- **结论：需要且不可替代**。三条硬理由：
  · ① **它是唯一的"写库方"** —— `entityFields[]` 只做校验、绝不写库，删掉它，新部署的库 `ck_attribute_definition` 就是空的（表单 / 列表 / 属性页全没字段）
  · ② **只有它能跟随代码** —— 实体字段的物理真源是 Java 实体 + `schema.sql`（**包无法建列**）；而包的 `entityFields[]` 自述是"按 2026-10-10 的库现状**抄录**"，是**快照**不是真源
  · ③ **UI 元数据只有它会推** —— `dataType` / `uiComponent`（textarea / select / tree-select / switch / input-number）/ `sortOrder`（1–103）；而 `verifyBaseGroups` **只比对 `field_name` 是否存在**，在包里改这些**不会生效**
- **范围更正**（此前资料不准确）：它扫 `ENTITY_CODE_MAP` 的 **6 个** OOTB 实体 —— `ProductLine` / `ProductModel` / `Document` / `Part` / `FunctionalEntity` / `EngineeringDocument`（递归扫继承链；复合实体做 Master + Iteration 合并）；**6 个全部走「先清后建」**（含 `PRODUCT_LINE` / `PRODUCT_MODEL`），且只清 `source='SYSTEM'`
- ★ **单向校验真空已补齐**（用户确认"补"）：`verifyBaseGroups` 现在做**双向校验** ——
  · **正向**（原有）："声明有、库里没有" → 报出缺失字段，并提示"若这些其实是 IBA 扩展属性，请移入 `ibaGroups[]`"
  · ★ **反向**（新增）："实体侧有、声明里没有" → 报出未声明字段，并提示"实体类字段有变更时请同步更新本文件的 `entityFields[]`" —— **实体类新增字段后，声明不再悄悄落后**
  · 两者都记 **SKIP**（只提醒、不判失败，避免把包状态打成 PARTIAL）；取库侧清单的 SQL 补上 `UPPER(source) = 'SYSTEM'`，防止将来 `source='IBA'` 的行混入、被误报成"实体字段未声明"
  · **实测（一改两验）**：把 `DOCUMENT_BASE` 的一条 `name` 临时改名 → 报告**同时给出两条**（正向点名 `nameX` ✓ 反向点名 `name` ✓）→ 撤掉后复验全绿（platform-base 6 条 / electronics-core 26 条，**FAILED 0、异常文案 0**）✓
  · ✗ 途中如实交代：第一次替换**只改了 SQL 与声明清单收集、漏掉了反向校验代码段** → 复测时只出现正向一条，才发现并补上（"先埋错再验证"这一步再次立功）
- 已同步更正 `type-attribute-operations` skill（`SKILL.md` 的 ① 节重写 + 双向校验说明、`attribute-model.md` 数据流图注）

#### 创建产品系列/型号时，创建者自动成为「产品经理」（团队角色）
- **缺口**：`ProductLineServiceImpl.create` 与 `ProductModelServiceImpl.create` 都**自动建了团队**（`TEAM_<code>`、"<名称>研发团队"），但**建完不提任何人** —— 创建者在团队里是空的，`ck_team_member` 一条都不写
- **改法**（两处各 +14 行，插在 `teamMapper.insert(team)` 之后）：把 `UserContext.get()` 的当前用户写入 `ck_team_member`，`role_name = "产品经理"`
  · `user_id` 存 **username** —— 与前端手动添加成员、`buildMemberVOs` 的 `userMapper.selectByUsername` 同一口径
  · ★ 「产品经理」是 `ck_team_member.role_name` 的**自由文本**，**不是** `ck_role` 里那 16 个系统权限角色（`PLATFORM_ADMIN`/`TENANT_ADMIN`/…）—— 两套东西，别混
  · 团队是**刚建**的、必无成员，故无需 `existsByTeamAndUser` 兜底（`UNIQUE(team_oid,user_id)` 也不会冲突）
  · `UserContext` 用**全限定名**调用，不动 import 块（项目既有惯例，见 `TypeDefinitionServiceImpl` 的注释原话）
  · 只改 `create`，**不改 `ensureTeam`** —— 后者会在 `getTeam` 这类**读操作**里补建团队，在那里加成员等于让 GET 产生业务写入
- **实测**：真调 `POST /api/product-lines` + `POST /api/product-models` → 两个团队各得一条 `sysadmin | 产品经理` ✓✓；compile 0 / ERROR 0 ✓
- **顺带查实两件事**（都是设计、非 bug，但值得记）：
  · **`DELETE /api/product-lines/{oid}` 是软删**（进回收站，`delete_mark=t`，注释原话"团队/子节点保留，恢复后仍完整"）；**全仓没有 purge / hardDelete 接口** → 要彻底清测试数据只能写 SQL 白名单（★ 平台租户的数据**不能**按 `tenant_oid` 扫，会误伤平台数据）
  · **PowerShell `Invoke-RestMethod` 传中文字符串 body 不是 UTF-8** → 测试数据的中文名落库成了 `???????`（对照组极清晰：Java 里写死的 `role_name="产品经理"` 完全正常）。这是本项目 shell 坑的**第 4 个**（前三个：`%` 被 shell 吞、括号、多条 `& psql`）
- 测试数据已**物理清干净**（精确 oid 白名单 + 事务 + 干跑 + 自验证：删 86 行、残留 0、按 code 复查 0）

#### type 页面布局：包内声明 `pageLayouts.byHost` + 装包按宿主套用（补齐"从父类型克隆"）
- **起因**：用户指出"softtype 的 pageLayout 在 `softtypes.json` 中没有定义，应该给一份标准的定义，后面将克隆平台的功能去掉"
- **改前现状**（实测）：布局只有一条来路 —— 新建软类型时从父类型**克隆**（`TypeDefinitionServiceImpl.inheritPageLayouts`；父链上都没有则新类型**没有任何页面**，且该方法曾因自递归把"新建类型"打成 500）；`softtypes.json` 里**没有任何布局声明**
- **改法**：
  · **基准从库里导出、不手写**（`jsonb_pretty`，与 124 条 `entityFields` 同一手法）：`PART` / `DOCUMENT` / `PRODUCT_LINE` / `PRODUCT_MODEL` 各 4 个操作；`ENG_DOCUMENT` 库里当时无布局 → 其 4 条**复制自 DOCUMENT**（字段集同族：Master + Iteration）
  · `platform-base/softtypes.json` 新增顶层 **`pageLayouts.byHost`**（5 宿主 × 4 操作，约 47 KB；文本插入、未重排原文件）
  · `SoftTypeInstaller` 新增 `installPageLayouts`：按 `root_type_code` 把该宿主下的**所有类型**（含软类型子孙）补齐布局
    · ★ **必须在 types 装完之后调用** —— 布局挂在类型 oid 上，本包这次新建的类型（如 `STRUCTURAL`/`STD_PART`）也要轮到；首版写在早退之前，属设计错误，已修
    · **幂等**：按 (类型 oid, 操作码) 判存在，已有即跳过 —— **不覆盖**管理员在低代码设计器里改过的
    · 类型总量只有几十条 → 一次 `selectAll` 在内存按宿主分组，**不为它新增 Mapper 方法**
- **实测**（三轮，含反向制造缺失）：
  · 装 platform-base → 5 个宿主全部出报告明细；**跳过已有 64 条**（幂等生效，既有布局零改动）
  · ★ **删掉 `STRUCTURAL` 的 4 条布局**再装 → `PART 宿主：补齐 4 条；跳过已有 28 条` ✓✓（**补齐路径实证**）；再装一遍 → 补齐 0（不重复补）
  · ★ **新建软类型** `ZZTEST_SOFT` → 立刻有 4 条布局（**克隆链路仍工作** ✓）；删除该类型 → 布局级联清空 ✓；`layout_total` 由 64 → **68** ✓ 自洽
  · compile 0 / ERROR 0 ✓；测试数据与临时脚本已清净（`tmp files left: 0`）
- ✗ **如实交代两处**：
  · ① 注入脚本**先写盘后复解**，而 block 里 `"note"` 行**漏了一个逗号** → `softtypes.json` 一度非法（已定位并补上逗号；**没有走"恢复备份"** —— 那份备份是上一轮命名归一时的原件，恢复会把已移除的 `FUNCTIONAL` 带回来）
  · ② 我**两次**从被 `LIMIT` **截断**的查询输出里数数（先说"14 个 entity_code"、又说"那些类型没有布局"），实际都不成立 —— **教训：截断的输出不能当全集用**
- 同步更新 `type-attribute-operations` skill（`SKILL.md` 新增 §2.5 页面布局；`attribute-model.md` 补 `SoftTypeInstaller` / `PageLayoutServiceImpl` / 克隆链路的代码索引）

#### 登录提示纠偏：待审核企业的管理员不再只看到「用户已被禁用」（无 SQL 变更）
- **触发**：注册租户 `CKAI-TECH`（乘恺科技有限公司，2026-10-09）后，管理员 `Roney.Liu` 登录只看到「**用户已被禁用**」，而真实原因是**该企业尚未通过平台审核**
- **根因**（两侧都是刻意的设计，只是提示顺序把因果说反了）：
  · `TenantServiceImpl.register` 建管理员账号时就是 `enabled=false + locked=true`（注释原话"审核通过前不可登录"），由 `approve` 在审核通过时打开 —— 所以这两面旗子常常只是"企业还没过审"的**表象**
  · `AuthController.login` **先判用户、后判租户** → 待审核租户的管理员三个条件同时命中，永远停在第一句「用户已被禁用」，把用户引去查自己的账号
- 改动（`AuthController.java` 两处 + 两个私有方法，纯提示语义，**不改任何登录判定逻辑与状态**）：
  · 用户被禁用/锁定时**先问租户**：`describeTenantBlock()` —— 租户非 ACTIVE 时按真实状态给文案；租户正常（或平台级账号无租户）返回 null，仍走原文案「用户已被禁用 / 已被锁定」（真·用户级禁用场景原样保留）
  · `tenantStatusMessage()` 统一"租户状态 → 文案"：`PENDING`＝「您的企业申请正在平台审核中，审核通过后即可登录」／`REJECTED`＝「…未通过审核：<驳回原因>」／`SUSPENDED`＝「已被暂停使用」／`DISABLED`＝「已被停用」
  · 密码校验后那处租户校验也改用同一文案（原先一律说"租户未激活，请等待管理员审核"，对已驳回/已暂停/已停用的租户都不成立）
- **实测**（重启后端，真调 `/api/auth/login` 三例）：
  · `Roney.Liu`（任意密码）→ 403「您的企业申请正在平台审核中，审核通过后即可登录」✓
  · `sysadmin` → 200 success ✓（未回归）
  · 不存在的用户名 → 401「用户名或密码错误」✓（未回归）
  · lint 0 / compile 0 / ERROR 0 ✓
- **刻意不动**：前端 `frontend/src` 对 `用户已被禁用|租户未激活|…` **0 命中**（无按文案做分支的代码，改文案安全）；判断仍发生在密码校验之前（与改前一致，未改变安全语义）；**未激活 `CKAI-TECH`** —— 审核是平台管理员的管理决策，走产品既有入口 `/system/tenants`（`TenantReview.vue`）
- **提醒（下一步的门）**：该租户 `onboarding_state` 仍是 `PENDING`，审核通过后管理员登录会被**引导页**接住（选行业 + 研发方法论，一旦选定不可改）—— 与 `status` 正交，见 `OnboardingController` javadoc
- **已知未修 · 用户决策（2026-10-10：暂不修改）** —— 复核租户审核链路时查出两处小问题，明确**本轮不改代码**：
  · `TenantReview.vue:135` 审核成功弹「租户「X」审核通过，**管理员账号已创建**」—— 文案已过时（注册时即建号，`approve` 只负责**启用**），留着不影响运行
  · 审核通过 / 驳回**均不通知租户**（注册时有 `sendToAdmins` 通知平台管理员，反方向没有）—— 驳回场景现由登录文案透出 `reject_reason` 兜底
  · **未来方向（用户已定）**：以**验证码 + 短信**的方式通知 —— 届时一并覆盖注册受理、审核通过、驳回等节点
  · 届时可复用的现成条件：`NotificationService`（现成通知抽象点）、`ck_user.phone`（管理员手机号已有字段）；✗ **注意缺口**：`ck_tenant` 只有 `contact_name / contact_email`、**没有 `contact_phone`** —— 若要"通知租户联系人"而非管理员本人，需先补这一列

#### 计量单位（ck_unit）由「全租户共享」改为「按租户隔离」（**本次有增量 SQL**）
- **用户决策**：「修改成租户隔离，因为租户在 onboarding 的时候会根据 units.json 文件 install」—— 查证后确认原先口径有三处冲突：
  · 引擎按 `pack.json` 的 `resources[].reusable` 判定共享与否：UNIT 原为 `reusable:true` → **租户安装时整条 SKIP**（`IndustryPackEngine:674` 记"系统级共享"）→ `units.json` 实际上**从未装进任何租户**
  · 但 `ck_unit` 的 `UNIQUE (name)` 是**全局唯一**，而「单位管理」页（`UnitConfig.vue` / `POST /api/units`）**允许任何租户自建单位** → 一个租户新增后所有租户都看得见，且别的租户装包时会被同名 SKIP 掉
  · 「`UnitInitializer` 时代无租户列」的口径，与"单位随包按租户各装一份"的目标不符
- 改动（9 个文件；照 `ck_classification` 的先例 —— **显式带 tenant_oid，不靠拦截器单点兜底**）：
  · `schema.sql`：`ck_unit` 加 `tenant_oid CHAR(36)`；`UNIQUE (name)` → `UNIQUE (name, tenant_oid)`；加 `idx_unit_tenant`
  · `Unit.java`：加 `tenantOid` 字段 + 访问器
  · `UnitMapper` / `PostgreSqlUnitMapper`：`COLS` 与 `INSERT` 带 `tenant_oid`；`selectByName / selectByQuantityType / selectAll / existsByName / selectByBaseUnitName` **全部显式 `AND tenant_oid = #{tenantOid}`**（与 `ClassificationMapper` 同款：拦截器在上下文为空时不注入，显式写才与上下文无关）；`update` 不写归属列
  · `UnitServiceImpl`：加 `tenantOid()`（取自 `TenantContext`，为空即抛错）；`create` 落 `setTenantOid`，其余读写一律传参。★ 实测 `AuthInterceptor:77` 每个请求都 `TenantContext.set(...)`，HTTP 链路必然有值
  · `UnitInstaller`：目标租户取 `context.getTenantOid()`（引擎调用期已切上下文），取不到记 FAILED；判重改按 `(name, 租户)`；javadoc「租户归属」节改写
  · `TenantStatementInterceptor`：`ck_unit` **移出**「完全共享表」名单（落到默认业务表类）—— 否则租户过滤失效
  · `platform-base/pack.json`：UNIT `reusable: true` → **false**（照同包 `CLASSIFICATION` 的写法，`scope` 不动）
  · `platform-base/units.json`：note 同步（幂等键 = (name, 租户)）
  · `OnboardingController` javadoc：「计量单位」从"平台共享类资源"移入"租户级隔离资源"（原文"直接复用不重复写"已不成立）
- **升级 SQL（现有库必跑；新库由 `schema.sql` 直接建对）**：
  ```sql
  ALTER TABLE ck_unit ADD COLUMN IF NOT EXISTS tenant_oid CHAR(36);
  UPDATE ck_unit SET tenant_oid = '00000000-0000-0000-0000-000000000000' WHERE tenant_oid IS NULL;
  ALTER TABLE ck_unit DROP CONSTRAINT IF EXISTS ck_unit_name_key;
  ALTER TABLE ck_unit DROP CONSTRAINT IF EXISTS uk_unit_name_tenant;
  ALTER TABLE ck_unit ADD CONSTRAINT uk_unit_name_tenant UNIQUE (name, tenant_oid);
  CREATE INDEX IF NOT EXISTS idx_unit_tenant ON ck_unit(tenant_oid);
  ```
  → 现有 42 条**全部归平台租户**（母版）；业务租户在 onboarding 装包时各得一份
- **实测**（重启后端，真调接口 + 真查库；改动前已全库 pg_dump 备份）：
  · 装 `platform-base` → UNIT **42 条全 SKIP**，message = **「本租户已有该单位」** ✓ —— 若仍是 `reusable:true`，这里会显示"平台已配置，系统级共享" —— **报告上一眼可辨**
  · `POST /api/units` 新建 → 返回体与库内 `tenant_oid` 均为平台租户 ✓（写入路径带租户）
  · `DELETE /api/units/{oid}` → 200 ✓
  · **隔离硬验证**：手工给 `CKAI-TECH` 插一条同名 `kg` → 两租户**各存一条**（复合唯一键生效 ✓）；`sysadmin` 视角仍为 **42 条**（看不到对方那条 ✓）；验完即删 ✓
  · 编译 0 / ERROR 0 ✓
- ✗ **一处如实交代**：首轮编译失败 2 处（`Unit.setTenantOid` 找不到符号）—— 给实体补字段时**漏了访问器**，而 IDE 诊断报 0 没抓住（跨文件的方法缺失它看不见）—— **是编译器抓出来的**。教训：改实体字段后，跨文件调用点要靠编译或显式搜索验证，别信 lint 的 0

#### 「模型定义」与「方法论」两条链改为按租户隔离（**本次有增量 SQL**）
- **用户决策**：「业务配置中的模型定义也需要租户隔离」→ 追加确认为 **A+B 一起做**（A＝模型定义链，B＝方法论链）
- **为什么不能照计量单位那样"只移拦截器名单"** ✗：`TenantStatementInterceptor` 有条规则 —— *SQL 里已出现 `tenant_oid =|IN` 就整体跳过注入*；而这两条链的 Mapper **SQL 本身就带** `tenant_oid IN (#{tenantOid}, #{platformOid})`（那正是"平台一份 + 租户可覆写一份"的实现，配 `DISTINCT ON (code)` + "本租户优先"排序）→ **只移名单一点作用都没有**，必须连 SQL 一起改。这也是本页此前"半隔离"的根因：`ck_iba`/`ck_type_iba` 早已按租户隔离（注释原话"每个租户拥有独立的 IBA 属性定义"），而 `ck_type_definition` 还是共享的。
- 改动（10 个 Mapper + 拦截器 + 4 个包声明 + 2 处文档）：
  · **SQL 改"只查本租户"**（6 个模板族各 2-3 处，共 30+ 处）：`PostgreSqlTypeDefinitionMapper`(7) · `PostgreSqlPageLayoutMapper`(3) · `PostgreSqlStageTemplateMapper`(2) · `PostgreSqlRuleTemplateMapper`(3) · `PostgreSqlReviewTemplateMapper`(3) · `PostgreSqlMetricTemplateMapper`(3) · `PostgreSqlIssueTypeTemplateMapper`(3) · `PostgreSqlGateTemplateMapper`(3) · `PostgreSqlDeliverableTemplateMapper`(3) · `PostgreSqlDashboardTemplateMapper`(3) —— 同步去掉 `DISTINCT ON` 与 "本租户优先" 排序（隔离后不存在重复 code；`selectAll` 改回各自的自然排序）
  · **参数 `platformOid` 保留但废弃**（写进 Mapper 注释）：删参数要改 Mapper 接口 + 服务层几十处调用，收益为零
  · **拦截器**：`ck_type_definition` / `ck_attribute_definition` / `ck_type_page_layout` / `ck_type_version_rule_link` / `ck_type_number_rule_link` / `ck_type_lifecycle_template_link` / `ck_type_lifecycle_state_process_link` **移出 `PLATFORM_SHARED_TABLES`**（落到默认业务表类）；**不动** `ck_lifecycle_*` / `ck_number` / `ck_version_rule`（平台预置字典，被绑定引用而非"各装一份"）、`ck_business_domain` / `ck_role*` / `ck_view*` / `ck_cls_page_layout` / `ck_process_form_template`（本轮未要求）
  · **包声明 `reusable: true → false`**：platform-base 的 `SOFT_TYPE`（其 `softtypes.json` 11 条）与 `ATTRIBUTE`、electronics-core / automotive-apqp / military-gjb 的 `SOFT_TYPE`（electronics-core 的 ATTRIBUTE 早已是 false）—— 否则租户安装时**整条 SKIP**，类型永远只有平台那份
  · **`SoftTypeInstaller`**：判重文案 `"类型已存在（平台级或本租户）"` → `"本租户已有该类型"`；那段"判重与父类型解析**必须**查租户+平台两段"的注释改为隔离口径 —— ★ 它记着的正是本次的风险（*"只查本租户会找不到父类型…曾把一个租户包的 3 个类型全装丢"*），缓解手段是 `installChain` **父包在前**（javadoc 原文）保证基础包先落本租户
  · `OnboardingController` javadoc：「类型定义与其属性·页面布局·规则绑定」从"平台共享类资源"移入"租户级隔离资源"
- **无 SQL 迁移**：`ck_type_definition` 等表**本就有** `tenant_oid` 列；库里 22 条类型（8 OOTB 根 + 14 SOFT_TYPE）**全在平台租户**，作母版；业务租户在 onboarding 装包时各得一份
- **前端零改**：`TypePage.vue` 的 `canEdit`（`tenantOid === 平台` → 仅平台管理员可编辑）与「平台级」只读标签，隔离后语义天然成立
- **语义变化（已与用户确认接受）**：① 平台管理员改类型**不再自动影响**租户（原"平台维护 + 租户覆写"→ 各管各的）；② 行业包升级新增的类型**不会自动**出现在已装租户（引擎按 code 判重 SKIP）；③ 原先那句 *"平台级类型不可编辑，如需自定义请先**克隆到本租户**"* 指向的功能**全仓不存在** —— 隔离后该问题自然消失（每租户都有一份可编辑的）
- **实测**（重启后端，真调接口）：
  · 编译 0 / ERROR 0 ✓
  · `GET /api/type-definitions/tree`（平台租户视角）**22 节点** ✓（与库中一致）
  · 反复装 `platform-base` → `SOFT_TYPE` **16 条全 SKIP** ✓ + `ATTRIBUTE` 6 条 SKIP ✓ —— 样例 message = **「本租户已有该类型」**（改动前是"类型已存在（平台级或本租户）"，**报告上一眼可辨**）
  · ★ 核实"16 条"：= `softtypes.json` 的 **11 条类型** + `pageLayouts` 段的 **5 条布局条目**（布局也以 `SOFT_TYPE` 记明细）—— 不是声明与实际不一致（`pack.json` 的 `count: 11` 是对的）
  · **隔离硬验证**（手工给 `CKAI-TECH` 插一条与平台**同名**的 `PART`）：两租户各存一条 ✓（复合唯一生效）；`sysadmin` 类型树仍 **22** 节点 ✓（看不到对方那条）；验完即删 ✓
- ★★ **本次的增量 SQL（现有库必跑；新库由 `schema.sql` 直接建对）** —— 是隔离验证时才暴露出来的**致命缺口**：
  ```sql
  ALTER TABLE ck_type_definition DROP CONSTRAINT IF EXISTS ck_type_definition_code_key;
  ALTER TABLE ck_type_definition ADD CONSTRAINT uk_type_definition_code_tenant UNIQUE (code, tenant_oid);
  ```
  **为什么必跑**：`ck_type_definition` 上还留着一条**全局** `UNIQUE (code)`（`schema.sql` 里是列级写法 `code VARCHAR(50) NOT NULL UNIQUE`）—— 隔离后判重只查本租户（查不到）→ 去 INSERT → **撞全局唯一** → **租户装包时 11 条类型会全部 FAILED**。实测复现：手工插一条 `CKAI-TECH` 的 `PART` 被 `ck_type_definition_code_key` 拒绝。
  ★ 已排查本轮涉及的全部 15 张表：**只有这一张**是全局唯一 —— 其它早已是 `(…, tenant_oid)` 复合（`ck_attribute_definition` / 8 张 template / `ck_type_page_layout`）或按租户独占的 `type_oid`（3 张 link）。`schema.sql` 同步改为表级 `UNIQUE (code, tenant_oid)` 并补 `idx_td_tenant`。
- ✗ **如实交代**：这个缺口**差点漏掉** —— 是"隔离硬验证"那条 insert 探针抓出来的（纯静态审查时我以为只有 `ck_unit` 有全局唯一问题 ✓ 而类型定义更早、更隐蔽 ✗）。教训：**改租户隔离，必须真写一次库**（插入同名行），别只看代码。
- ✗ **遗留（未实测）**：「**租户**装包」这条路径本轮**没能实测** —— 装包接口按 token 所属租户执行，而我**没有 `CKAI-TECH` 的登录凭据**，无法以该租户身份调用。**请走一次引导验证**（`/onboarding` 选行业 → 装包），预期报告：先 `platform-base` 的 **11 条 INSERT**、再行业包类型**全部 INSERT**（父类型已在本租户就绪）；若出现"父类型未就绪"失败，说明依赖链未按父包在前的顺序执行，需立即回看

#### 流程表单模板（ck_process_form_template）改为按租户隔离（无 SQL 迁移）
- **用户决策**：「业务配置中的流程表单也需要租户隔离」—— 承接前两轮（计量单位 / 类型定义）的同一口径
- **本次的难点与解法**：内置 3 张（`CKPLM_SETUP_ASSIGNEE` 设置流程参与者 / `CKPLM_APPROVAL_OPINION` 审批意见 / `CKPLM_COUNTERSIGN` 会签）**不是"平台预置字典"**，而是**引擎与前端的渲染契约** —— 前端 `dsl-core/forms.ts` 按 code 派发到同名 Vue 组件，DSL 的 `formRef`、编译出的 `flowable:formKey`、运行期渲染都用同一个 code。它们先前靠"表是 PLATFORM_SHARED、平台一行全员可见"满足；隔离后必须**每个租户各持一份**。
  · ✗ 不能把清单抄进包 JSON（`ProcessFormTemplateInitializer` 与前端 `forms.ts` 已是"同一份契约的两端、code 必须一字不差"，再抄一处必漂移）
  · ✓ 采用**按租户补齐副本**：`FormInstaller` 装 `platform-base` 的 FORM 资源时，从**平台租户**读那份母版（新 Mapper 方法 `selectBuiltins(tenantOid)`，**显式带租户**，绕过拦截器），按 code 判存在后写进本租户 —— 清单仍只有一处真源
- 改动（9 个文件）：
  · 拦截器：`ck_process_form_template` **移出 `PLATFORM_SHARED_TABLES`**
  · `ProcessFormTemplateMapper`：新增 `selectBuiltins(String tenantOid)`；`selectByCode` 去掉原先硬编码平台 oid 的"平台行优先" `CASE`（隔离后已不需要）
  · `PostgreSqlProcessFormTemplateMapper`：实现 `selectBuiltins`（`WHERE tenant_oid = #{tenantOid} AND builtin = TRUE`，显式租户条件导致拦截器跳过注入 —— 正是本意）
  · `FormInstaller`：`install()` 改为两步（① 先 `ensureBuiltinCopies` 补齐内置副本 → ② 再装 `forms.json` 的行业自定义表单）；新增 `ensureBuiltinCopies`（目标租户取 `context.getTenantOid()`、母版固定读平台租户，两者都显式传参；预演只算不写；平台租户自身跳过并如实记 SKIP）；类注释改写（原文"那三张**不能搬进包**，搬了运行期就取不到表单"的前提已随隔离失效）
  · `platform-base/pack.json`：新增 `FORM` 资源声明（`reusable: false`）；新建 **空** `platform-base/v1.0.0/forms.json` 作为安装点（`forms: []` + note 说明内置清单不在此复制）
  · 四处 javadoc 同步：`ProcessFormTemplateInitializer` / `ProcessFormTemplate` 实体 / `ProcessFormTemplateMapper` 接口与实现 / `ProcessFormTemplateServiceImpl`
- **无增量 SQL** ✓：本表只有 `PRIMARY KEY`、**没有任何唯一约束**（已排查确认，不会重演 `ck_type_definition` 那个坑），表结构不动
- **实测**（重启后端，真调接口 + 真查库）：
  · 编译 0 / ERROR 0 ✓
  · 装 `platform-base` → FORM 明细 1 条 **SKIP**：message =「**平台租户自身：内置表单已由启动初始化器登记，无需复制**」✓
  · ★ 启动日志里 **没有**"平台租户没有内置表单记录"这条 WARN ✓ —— 它的**缺席**正好证明 `selectBuiltins(平台)` 真读到了那 3 条母版（读不到就会打它）
  · **隔离硬验证**（手工给 `CKAI-TECH` 插一条与平台同名的 `CKPLM_APPROVAL_OPINION`）：两租户各存一条 ✓；`GET /api/form-templates` 以 sysadmin 视角仍为 **8** 条、且**不含探针** ✓（查询隔离成立）；验完即删 ✓
  · 库里：平台租户 `builtin=f` 5 条（electronics-core 的自定义）+ `builtin=t` 3 条（内置）✓
- **语义变化**：① 租户现在**可以停用自己那张内置表单副本**（原来只有平台管理员能，且停用会对所有租户生效）—— 这正是隔离的收益；② 平台改了内置表单的契约字段后，**新装租户**按新版建副本，**已装租户**的副本**不会自动更新**（`ProcessFormTemplateInitializer` 只刷平台那份）；③ 删不掉、契约字段改不了的保护不变（服务层把关）
- ✗ **遗留（未实测，同前）**：「**租户**装 platform-base」这条路径仍未实测（无该租户登录凭据）。**请走一次引导验证**，预期报告：FORM 资源出现 **3 条 INSERT**、message 为「内置表单副本：「…」（契约 code 与平台母版一致）」—— 若给的是 SKIP「本租户已有该内置表单」，说明该租户此前已有副本 ✓ 也正常

#### 内置流程表单改为随 platform-base 包播种（撤销上一节的"按租户补齐"过渡机制，无 SQL 变更）
- **用户决策**：「设置流程参与者、审批意见、会签三个 form 需要在 platform-base 包中作为平台的基础能力」——
  即让它们**随包声明**，而不是留在 Java 代码里由安装器按租户补齐（上一节的做法）
- **方向与项目既有演进链一致** ✓：`StageTemplateInitializer` / `ViewInitializer` / `UnitInitializer` 都是这么退场的 ——
  "平台级默认内容的播种移入行业包，由 `IndustryPackBootstrap`（`@Order(1)`）在启动时装到平台租户"，
  原话见 `StageTemplateServiceImpl:104`（*"那些类已退场"*）
- 改动：
  · `platform-base/v1.0.0/forms.json`：由空数组改为**承载 3 张内置表单**（`code` / `name` / `nodeTypes` /
    `component`＝`code` / `builtin: true` / `sortOrder` / `description` —— **逐字照搬**原 `Initializer.BUILT_INS`）
  · `FormInstaller`：`installOne` **支持 `builtin: true`** —— 内置表单**不走 `formService.create()`**
    （那个入口按设计强制 `builtin=false`，防租户自建"假内置"），改为直插并显式写契约三字段；类注释重写
  · **撤销上一节的过渡机制**：删 `ensureBuiltinCopies`（连同其 import 与 `install()` 里的调用）、
    删 Mapper 的 `selectBuiltins`（接口 + 实现）
  · **删除 `ProcessFormTemplateInitializer`**（清单已入包，与前述三个 Initializer 同款退场）
  · 文档同步：`schema.sql` 表注释、拦截器名单注释、`ProcessFormTemplate` 实体、`ProcessFormTemplateServiceImpl`、
    `FormInstaller` 类注释
- **真源仍只有两处**：包 JSON + 前端 `dsl-core/forms.ts`（Java 那份随该类删除，**没有增加真源数量**）
- **实测**（重启后端，真调接口 + 真写库；这次**两个分支都走通了**）：
  · 编译 0 / ERROR 0 ✓（删类后无残留引用）
  · **幂等分支**：平台已有内置时装包 → FORM **3 条 SKIP**：「同名内置表单已存在（契约 code，跳过）」
  · **写入分支**：临时把平台 3 条内置挪到 `CKAI-TECH`（`UPDATE 3`）→ 再装包 → FORM **3 条 INSERT**
    「内置表单：「设置流程参与者」（component=CKPLM_SETUP_ASSIGNEE，不可删除、契约字段不可改）」
    ★ 这条与"**新租户首次装包**"**完全等价**（本租户没有 + 包里有）→ 上一节遗留的"租户装包未实测"**就此闭环** ✓
  · 库里：平台 `builtin=f` 5 条（electronics-core 自定义）+ `builtin=t` 3 条（内置）✓；探针副本已删 ✓
- **无增量 SQL** ✓（表结构未动）

#### 预演「父类型未就绪」修正：判据落在**包声明**上，不查平台租户（无 SQL 变更）
- **触发**：引导页预演（`GET /api/industry-packs/electronics-core/1.0.0/plan`）把 `electronics-core` 的 6 个类型
  （`FOOTPRINT` / `SYMBOL` / `ELECTRONIC` / `PCBA` / `SCHEMATIC` / `PCB_LAYOUT`）全报成「**父类型未就绪**」
- **根因**（两因叠加）：① 预演一行都不写库 → 本租户必然还没有基础包的类型；② 2026-10-10 把
  `SoftTypeInstaller` 的父类型解析改为**只查本租户**（隔离的应有之义）→ 预演时 `parent` 恒为 null →
  全部留待下一轮 → 收尾记 FAILED。★ **真装不受影响**（`installChain` 父包在前，platform-base 的类型会真的先落进本租户）
- **第一版修正（✗ 错误，当天被用户否掉）**：在 dryRun 分支回退去查**平台租户那份**（新增 `selectByCodeInTenant`）。
  用户指出：*"未来平台上不会注册对应的业务对象，应该是检查基类包中是否包含了依赖安装作为前提"* ——
  那等于把"平台恰好有一份"当**前提**，迟早失效；且在已隔离的体系上开了个后门
- **最终修正（按"包声明"判定，与库状态、与平台租户都无关）**：
  · `InstallContext` 新增 `chainTypeCodes`：本链（父包在前）**已声明**会装出的软类型 code；5 参构造器保留（委托为空白集）
  · `IndustryPackEngine`：`installChain` 建集合 → `installRecursive` 逐层传递 → **父包递归装完后**调用新方法
    `collectDeclaredTypeCodes(source, chainTypeCodes)`（只读包内 `SOFT_TYPE` 资源声明的 `types[].code`，读不动只告警不抛）
    → `installOne` → 带入 `InstallContext`
  · `SoftTypeInstaller` 父类型就绪判据分两种模式：**真装**＝父类型是否**真落进本租户**（查不到留待下一轮，收尾仍无则如实报错）；
    **预演**＝本文件内声明过（`declaredCodes`）∨ **本链前面的包**声明过（`chainTypeCodes`）
  · **撤销**第一版的两个后门方法（`selectByCodeInTenant` / `selectAllInTenant`，接口+实现）与两处 dryRun 平台回退；
    `installPageLayouts` 的预演回退一并撤（预演时记 SKIP 属预期，不借平台那份假装精确）
  · 文档：接口 3 处过时 javadoc（原文"优先本租户，无则回退平台租户"）修正；`SoftTypeInstaller`「父类型可能跨包」节重写并注明本次纠正
- **实测**（端到端复现租户视角：临时把 `sysadmin.tenant_oid` 指向 `CKAI-TECH`，**验完已改回**并复查 ✓）：
  · 编译 0 / ERROR 0 ✓（中途修掉两处编译错误：`LinkedHashSet` 未导入改 `HashSet`；`selectAllInTenant` 残留实现未删）
  · **[平台视角]** plan electronics-core → `FAILED=0`，那 6 条 `SKIP「本租户已有该类型」`
  · **[租户视角]** plan electronics-core → **`FAILED=0`**，那 6 条全部为
    `INSERT  预演：将新增类型「封装」（SOFT_TYPE，宿主 ENG_DOCUMENT）` 等 —— ★ 宿主名被正确解析，说明父类型被判为"会就绪"
  · ★ 由于平台回退已删净，租户视角仍能通过**只可能是 `chainTypeCodes` 在起作用** ✓
- ✗ **过程教训**：第一版把"预演能过"建在"平台恰好有一份"上，当天即被用户否掉 —— 判据要落在**包元数据（声明）**上，
  不要落在**数据现状**上；配置态与数据态的混用是这类缺陷的温床
- **遗留（未实测）**：「租户**真装**」仍未能实测（无该租户登录凭据）。预期：先 `platform-base` 的 11 条类型 INSERT，
  再 `electronics-core` 的 6 条全部 INSERT（父类型届时已真在本租户就绪）

#### 资源库按租户隔离 + 依赖判定统一到"链上包声明" + 「跳过」与「已就绪」分离（含 SQL 迁移）
- **触发**（用户三问，全部命中）：① 预演里"对象类型定义 将新增 11 · 已就绪 5"的"已就绪"从何而来；② 企业资源库报"平台已配置 8 条，系统级共享（本企业直接使用）"是否平台兜底；③ 依赖关系判定"应与上一个任务（链上包声明）一致"
- **查实的四类问题**：
  1. **"已就绪 5"是显示假象**：5 条实为 `softtypes.json` 的 `pageLayouts` 逐宿主 SKIP「该宿主下没有已注册的类型，页面布局跳过」（预演不写库、预期内）—— 前端把一切 `SKIP` 一律画成"已就绪"
  2. **企业资源库是"半共享"**：`PostgreSqlResourceContainerMapper` / `PostgreSqlResourceLibraryMapper` / `PostgreSqlPackageSymbolMapper` 的容器与归属阶段 SQL **硬编码平台 oid**（读、写、判定全走平台），而 `ck_resource_container` / `ck_stage` **不在拦截器任何共享名单**（普通查询注入 `tenant_oid = 本租户`）→ 同一张表两套口径：租户装包写不进本租户还报"已就绪"（实测平台 8 容器 / 22 阶段，CKAI-TECH 0 条）
  3. **检查规则的阶段前置是真兜底**：`RuleTemplateInstaller` 传 `PLATFORM_TENANT_OID`，`PostgreSqlStageTemplateMapper.existsByCode` 的 SQL 是 `tenant_oid IN (#{tenantOid}, #{platformOid})` —— 全仓唯一一处 `IN` 真正生效的兜底
  4. **废弃第三参残留**：`ReviewTemplateInstaller` / `SoftTypeInstaller` 多处仍传 `PLATFORM_TENANT_OID`（当前 SQL 已忽略，无实害；Mapper 一改就退化成兜底）
- **改动**：
  · **① 资源库真隔离（含 SQL）**：`schema.sql` 的 `uk_resource_container_code` 改 `UNIQUE(code, tenant_oid)`（否则第二租户装包撞全局唯一）；三个 Mapper 全部去平台硬编码、改显式 `#{tenantOid}`（容器 4 方法 + 阶段 4 方法，接口与实现同步）；`ResourceLibraryInstaller` 按 `context.getTenantOid()` 判重/落库（根、子库、归属阶段）；`ResourceContainerController` / `ResourceLibraryServiceImpl` / `PackageSymbolServiceImpl` 改传当前租户；`ResourceLibraryServiceImpl` 的**平台绑定回退删除**（隔离后它只能返回本租户读不到的分类 oid）；两个 `pack.json` 的 `RESOURCE_LIBRARY` `reusable` 改 `false`
  · **② 依赖判定统一**：`InstallContext` 的 `chainTypeCodes` 泛化为 `Map<资源类型, Set<code>> chainDeclaredCodes`（保留 `getChainTypeCodes()` 兼容入口）；`IndustryPackEngine.collectDeclaredTypeCodes` → `collectDeclaredCodes`（SOFT_TYPE/STAGE/GATE/DELIVERABLE 四类声明，键名各异已分支）；`RuleTemplateInstaller` 三处引用与 `ReviewTemplateInstaller` 归属门改为"真装以本租户为准 ∨ 预演时链上声明过即视为会就绪"，不再传平台 oid
  · **③ 废弃第三参清理**：`SoftTypeInstaller` 5 处传值改 `tenant`（判重/父类型/布局取数/布局判存/布局套用）
  · **④ 「跳过」与「已就绪」分离（前后端）**：`InstallResult` 新增 `ready` 字段（默认 true）+ `skippedPending()` 工厂；14 个安装器与引擎的**预期性跳过**改用它（"没有 xx 段"、"布局没有可套用的类型"、"引用的 xx 尚未装入"、"尚无安装器"、"平台未实现的能力"等）；`OnboardingController` 逐条带 `ready`、分组与分层新增 `skipReady`/`skipPending`（保留 `skip` 总数）、汇总文案拆为"X 条已就绪 / Y 条跳过"；前端 `OnboardingPage.vue` 的标签、明细悬停、层级徽标、顶部提示全部按此分开
- **实测**（租户视角端到端；临时把 sysadmin 指向 CKAI-TECH，**验完已改回**并复查 ✓）：编译 0 / ERROR 0（中途 12 个报错 = `PackageSymbolMapper` 接口漏改，已补）；资源库预演从"系统级共享/根节点已存在"→ **`将新增资源库根节点「企业资源库」` + 子库/归属阶段全 INSERT**；租户视角 `GET /api/resource-containers/children → len=0`、`GET /api/resource-libraries/context → containerOid=null`（隔离生效）；7 条检查规则全部 INSERT（原先被阶段兜底判 SKIP）；SKIP 分类 = 已就绪 18 / 跳过 6（布局 5 条 `ready=false`）
- **SQL 迁移（已执行；库外备份 1.58 MB）**：`DROP INDEX uk_resource_container_code; CREATE UNIQUE INDEX uk_resource_container_code ON ck_resource_container(code, tenant_oid);` —— 数据无需搬迁（容器/阶段只有平台一份）
- ✗ **如实交代（未实测）**：`ResourceLibraryInstaller` 的"**真装**"路径（租户装包写本租户容器+阶段）未实测（无该租户凭据，且装包属对库的重大写操作）—— 请走一次引导验证，预期：先建根+子库、再建归属阶段
- **遗留（刻意不动）**：`SoftTypeInstaller` 布局汇总行仍为 SKIP（其消息逐项说明"补了几条/跳过几条/失败几条"，不致误导）；各 Mapper 的"历史第三参"仅调用处改传本租户、**签名未删**（删签名属扩散改动，留待统一清理）

#### 引导页"假完成页"修复（无 SQL 变更）
- **现象**（用户截图）：租户管理员首次登录的**欢迎说明页**上，同时渲染出第 4 步的「行业解决方案已安装」成功页，
  且文案为 `已选定 undefined（方法论 undefined），已安装行业包 undefined，落地 undefined 个阶段模板`
- **根因**（两处叠加）：
  · `OnboardingPage.vue` 向导主链是 `v-if="step === 0" → v-else-if 1 → v-else-if 2 → v-else`，
    末尾那个 **`v-else` 成了兜底** —— 而 `step` 初始值是 **-1**（欢迎说明页，链外独立渲染），
    不满足 0/1/2 便直接落进完成页 → 两块同时显示
  · `result` 是 `ref({})`，完成页模板无空值兜底 → `result.industryCode` 等一律渲染成 `undefined`
- **改动**（`OnboardingPage.vue` 一处）：完成页改 **`v-else-if="step === 3"`**（完成态只在 `completeOnboarding`
  成功、`submit` 里 `step = 3` 之后才可能出现），并在注释里写明"这里曾经是 v-else"防回归；
  文案对空值兜底（`|| '—'` / `|| 0`）双保险
- **验证**：`step = -1` 时主链四个分支均不满足 → 只渲染欢迎页 ✓（前端 lint 0 ✓）
- **同类隐患提醒**：凡"向导步骤"用 `v-else` 收尾的页面，都要确认初始/边界取值不会误落 —— 本次即为此例

#### 生命周期（状态/模板/迭代/关联）、编码规则、版本规则、编码段改为按租户隔离（含 SQL 迁移）
- **用户决策**（承上一轮 A/B 二选一，选 B 真隔离）：范围＝生命周期状态、生命周期模板（含迭代 / 状态关联 / 流转关联）、版本规则、编码规则；**视图与角色不在范围**（保持共享）
- **隔离机制**：8 张表从拦截器 `PLATFORM_SHARED_TABLES`（读注入 `IN(平台,本租户)`）与 `SHARED_TABLES`（不注入）**移出** → 全部走默认业务表（注入 `= 本租户`）；`platform-base` 的 LIFECYCLE / VERSION_RULE / NUMBER_RULE 与 `electronics-core` 的 LIFECYCLE 改 `reusable:false`（租户装包时**真的各装一份**）
- **迁移前的三处暗礁（不修会真出事）**：
  · **4 条 UPDATE 的 `SET … tenant_oid = #{tenantOid}`**：拦截器见 SQL 含 tenant_oid 会整体跳过注入 → `WHERE code` 变成**跨租户更新**（平台+各租户同 code 行全改）。改法：删掉 SET 里的 tenant_oid，整条 SQL 无 tenant 字样后由拦截器追加本租户条件（Status / Template / Number / VersionRule 四个 Mapper）
  · **2 个列表查询的 JOIN/子查询裸奔**（`ResourceLibrary.selectLibraryItems` / `PackageSymbol.selectPackageSymbolItems` 对 `ck_lifecycle_status` / `ck_lifecycle_template_state` 的显示名兜底）：双份后行膨胀 / `LIMIT 1` 取到别家行 → JOIN/子查询补 `AND x.tenant_oid = p.tenant_oid`
  · **`ck_number_segment` 无 tenant_oid 列且外键 `rule_code → ck_number(code)`**：`ck_number` 唯一键一改复合，旧外键引用非唯一列，PG 直接拒绝 → 先删旧外键 → 加列回填 → 唯一键改复合 → **重建复合外键** `(rule_code, tenant_oid) → ck_number(code, tenant_oid)`
- **另修 4 个"创建入口不落租户"**（隔离前已存在的隐患、隔离后会变成"创建即失踪"）：`DefaultLifecycleStatusService.create` / `DefaultLifecycleTemplateService.create` / `DefaultNumberService.create` / `VersionRuleServiceImpl.createRule` 补 `setTenantOid(TenantContext.get())`
- **SQL 迁移（已执行；库外备份 1.59 MB；首次因段外键依赖失败 → 单事务整体回滚未留痕 → 修正顺序后成功）**：
  · `uk_lifecycle_status_code_tenant` / `uk_lifecycle_template_code_tenant` / `uk_number_code_tenant` / `uk_version_rule_code_tenant` = `UNIQUE (code, tenant_oid)` ✓（已核实）
  · `fk_number_segment_rule_tenant` = `FOREIGN KEY (rule_code, tenant_oid) REFERENCES ck_number(code, tenant_oid) ON DELETE CASCADE` ✓；编码段 32 条回填平台租户 ✓
- **实测**（租户视角端到端；sysadmin 临时指向 CKAI-TECH，**已改回并复查**）：编译 0 / ERROR 0 ✓
  · LIFECYCLE：6 个状态 + 2 个模板 → 全部 `INSERT 预演：将新增…` ✓✓（原先「平台已配置 8 条，系统级共享」）
  · VERSION_RULE `LETTER_26` → `INSERT`；NUMBER_RULE 6 条 → `INSERT … 及其 N 个段` ✓✓
  · electronics-core 的 NRND / SUPERSEDED / COMPONENT_CODE → `INSERT` ✓
  · **视图仍「平台已配置 3 条，系统级共享」**（范围外，保持原设计）✓；**角色仍「角色已存在」**（RBAC 根基未动；`ck_role` 仍在共享名单，该结论正确）✓
- ✗ **如实交代**：以上为**预演**验证；"真装"（装包后本租户拥有自己的状态/模板/规则/段，且不动平台那份）待走一次引导验证。预期：LIFECYCLE 8 条 + electronics-core 3 条 + NUMBER_RULE 6 条 + VERSION_RULE 1 条全部 INSERT
- **边界（已知且刻意）**：视图与角色保持共享（用户指定范围外）；`default` 租户无用户无数据（实测 ✓ 无需补装）；`SysAdminInitializer` 的"NULL 归属回填为平台"保留（隔离后仍正确）

#### 视图、角色、基本属性按租户隔离（含角色数据迁移与 RBAC 加固）
- **用户决策**：继续隔离三样 —— **视图**（`ck_view` / `ck_view_transition`）、**角色**（`ck_role` / `ck_role_member`）、**基本属性**（`ck_attribute_definition`）
- **视图**：从 `PLATFORM_SHARED` 移出 → 默认业务表（读注入 = 本租户）；`ck_view` 唯一键改 `(code, tenant_oid)`；transition 的 from/to 外键**改复合**（原引用 `ck_view(code)`，改键后即失效）；`platform-base` 的 VIEW `reusable:false`；`PostgreSqlViewMapper.update` 删 `SET tenant_oid`（否则 SQL 含 tenant_oid 使拦截器跳过注入 → WHERE code 跨租户更新）+ search 补 OR 括号（否则注入条件被 OR 短路、搜索会漏出别家行）
- **角色（RBAC 加固）**：
  · `ck_role` 唯一键改 `(code, tenant_oid)`；两表从 `PLATFORM_SHARED` 移出（`ck_role_member` 的两个 FK 引用的是 role 主键 oid，不受影响）
  · **成员归属口径改为"用户所在租户"**：`RoleServiceImpl.addRoleMember`（原用"角色的租户"——绑平台角色时成员行会落平台）、`UserServiceImpl.assignRole`（原不设 → 写 NULL）两处修正
  · **两个 raw JDBC 校验补租户条件**（`TenantController` 平台管理员校验、`OnboardingController.isTenantAdmin`）：role.code 各租户各一份后，缺 `r.tenant_oid = rm.tenant_oid` 会命中别家副本
  · `SysAdminInitializer`：成员行 NULL 回填由"统一写平台"改为"**按用户租户**"（原写法会把租户管理员的成员行改到平台 → 隔离后丢角色）；sysadmin 兜底补 `PLATFORM_ADMIN` 的 JOIN 限定平台角色
  · **`TenantServiceImpl.approve` 新增 `ensureSystemRoles`**：审核通过时为租户从平台复制系统角色副本（`role_type='PLATFORM'` 且非 `PLATFORM_ADMIN`），并**无条件**确保管理员绑定本租户 `TENANT_ADMIN`（注册时租户还没有角色副本，那次绑定必然落空）
  · pack.json：`electronics-core` 与 `smart-hardware-ipd` 的 ROLE `reusable:false`
  · **数据迁移**：为 CKAI-TECH 复制 4 个系统角色；**Roney.Liu 的成员绑定改绑到本租户的 TENANT_ADMIN**（原指向平台角色行 —— 隔离后登录解析会"查不到角色"＝管理员身份失效）
- **基本属性**：`ck_attribute_definition` 此前是"形式已隔离、实际半拉"——读注入=本租户，但**写入全在平台**（`AttributeInitializer` 显式平台）→ 平台写的 SYSTEM 字段租户读不到。现改为：**装包把包内 `entityFields` 声明真正写入本租户**（`AttributeInstaller` 注入 `AttributeDefinitionService`，逐条幂等 `register`；写入后仍做双向对齐校验）；`PostgreSqlTypeIBAMapper` 两处 JOIN 补 `ad.tenant_oid = ti.tenant_oid`（否则跨租户连行）；相关过时注释同步
- **SQL 迁移（已执行；库外备份 1.59 MB）**：`uk_view_code_tenant`、`fk_view_transition_from/to_tenant`、`uk_role_code_tenant`；角色数据复制 4 条 + 成员改绑 1 条 + 归属校正
- **实测**：编译 0 / ERROR 0（中途一处"名单末项尾随逗号"语法错误，已修）；**Roney.Liu 的成员绑定 → TENANT_ADMIN(tenant=CKAI-TECH)** ✓、**sysadmin 登录 `roles=["PLATFORM_ADMIN"]`** ✓；租户视角：ROLE 11 条全部「将新增业务角色」✓（原先"角色已存在"）、**VIEW 3 条全部「将新增视图」** ✓（原先"平台共享"；补改 reusable 并重启后生效 —— pack.json 是启动时加载缓存的）、`GET /api/views → count=0`、`GET /api/roles → 4 条`（AUDIT_ADMIN / CATEGORY_ADMIN / SECURITY_ADMIN / TENANT_ADMIN = 本租户副本；平台 16 条不可见）—— **隔离三探针全部通过** ✓
- **排障记录**：迁移后首测中曾出现"租户 `GET /api/views` 返回 3 条"的瞬时假象 —— 系服务冷启动期间的时序问题（请求时无租户上下文 → 不过滤）；稳定后以 `/api/roles`（平台 16 + 租户 4）为**决定性探针**复测：租户只看到自己的 4 条 ✓（同款手法：凡"共享表刚刚切隔离"的验证，都用**两租户都有数据**的表做探针，避免"全表只有平台一份"造成的假阳性）
- ✗ **如实交代**：预演里 VIEW 行当时仍显示"平台已配置 3 条，系统级共享"—— 因 `platform-base` 的 VIEW `reusable` **漏改**（已补 `false`）；**Roney.Liu 的真实登录**未实测（无其密码）—— 请用该账号登一次，验证菜单与管理入口
- **未纳入（范围外）**：视图/角色的进一步 UI 收敛；`ck_role` 的 code 契约不变（Flowable candidateGroups、前端 `roles.includes(...)` 等按 code 的用法不受影响）

#### 修正：`entityFields[]` 的"跳过"是漏判 —— 预演报「将登记 N 条」、真装写入本租户（无 SQL 变更）
- **用户质疑**（预演清单截图）：`attributes.json`（基本属性声明）显示"**跳过** · 本文件没有 IBA 扩展属性（ibaGroups[] 为空）"——
  用户指出：*"entityFields 不是已经租户隔离了吗？也需要按租户进行初始化导入"*
- **查实**：该文件 `ibaGroups[]` 确为空，但带 **5 组 `entityFields[]` 声明**
  （DOCUMENT 29 / ENG_DOCUMENT 40 / PART 31 / PRODUCT_LINE 12 / PRODUCT_MODEL 12，共 **124 条**）；
  而 `install()` 在"ibaGroups 非 List"时**提前 return** —— 连 entityFields 都没往下看：
  真装时那段"写入本租户"（上一轮已实现）**根本没被调用到**，预演更是只剩一句"跳过"✗
- **修正**（`AttributeInstaller`）：
  · `install()`：`verifyBaseGroups` 的调用保持在 IBA 早退**之前**，并**去掉 `if (!dryRun)` 包裹 —— 预演也跑**；
    "跳过"只在**两种声明都没有**时才报（"既没有 ibaGroups[] 也没有 entityFields[]"），不再用"没有 IBA"冒充整文件跳过
  · `verifyBaseGroups`：重排为"**先查本租户已有 → 只补缺失**"——预演只统计
    （报「预演：将把 X 的 N 条实体字段声明登记到本租户」），真装才 `register`（幂等），报「已登记到本租户（本次新增 N 条）」
- **实测**（租户视角复现；sysadmin 临时指向 CKAI-TECH，**已改回**并复查）：编译 0 / ERROR 0 ✓
  · `[platform-base] ATTRIBUTE`：5 组全部 `INSERT 预演：将把 … 条实体字段声明登记到本租户` ✓（29/40/31/12/12 = 124 ✓）
  · `[electronics-core] ATTRIBUTE`：26 条 IBA 属性照常「将新增属性…」✓（两条通路互不影响）
- ✗ **未实测**：真装写库（走引导时发生）—— 预期本租户 `ck_attribute_definition` 得到 124 条 SYSTEM 行 + 行业 IBA 行

#### 资源顺序与「分类初始化绑定」：分类树 → 业务对象定义 → 属性（无 SQL 变更）
- **用户要求**（原话）：
  · *"将分类树的顺序调整到对象类型定义之前，因为 softtype 需要进行分类的初始化绑定，比如电子元器件绑定元器件分类节点，
    结构件绑定结构件分类节点，标准件绑定标准件分类节点"*
  · *"将 attribute.json 顺序调整到对象类型定义的后面"*；
  · *"将对象类型定义修改成业务对象定义"*
- **顺序调整**（各 `pack.json` 的 `resources[]`；安装顺序 = 数组顺序，引擎逐条遍历该数组）：
  · `platform-base`：… NUMBER_RULE → **CLASSIFICATION → SOFT_TYPE → ATTRIBUTE** → RESOURCE_LIBRARY → UNIT → VIEW → FORM
  · `electronics-core`：LIFECYCLE → CLASSIFICATION → SOFT_TYPE → **ATTRIBUTE**（原在倒数第二）→ RESOURCE_LIBRARY → …
  · 依据：ATTRIBUTE 的 `bindTo` 是类型码（pack.json 原注已写"必须排在 SOFT_TYPE 之后"）；分类绑定要求分类节点先装
- **显示名**：SOFT_TYPE 的 display「对象类型定义」→「**业务对象定义**」。
  查证："对象类型定义"的唯一出处就是 pack.json 的 `display`（前端 0 命中、后端 java 0 命中），界面显示即它
- **分类绑定（接上早已存在、但装包从未使用的机制）**：`ck_type_classification_link` 表与
  `TypeClassificationLinkService` 早已存在（javadoc 语义：*"为该类型创建 Part 实例时自动继承此分类"*），
  但 `SoftTypeInstaller` 装包时只写四绑（编码 / 版本 / 生命周期 / 域），从未写过它 —— 本次接上：
  · 声明：`softtypes.json` 的 `types[]` 新增 `clsIdentifier`（分类 **identifier** —— 业务唯一键，跨包跨租户稳定；
    oid 每次装包都会变，写不进声明）
  · 三处声明：`STRUCTURAL → tpl-structure`；`STD_PART → tpl-structure-standard-part`；
    `ELECTRONIC → tpl-electronic`（与分类树按"父 identifier + '-' + slug"拼出的键一致）
  · `SoftTypeInstaller`：新增 `bindClassification`（照 `bindNumberRule` 三兄弟模子：exists 幂等 + 建 link +
    显式落租户）；**新装类型**时执行；**已存在类型也补绑**（SKIP 分支 —— 否则"先装类型、后加分类树 / 声明"的库
    永远补不上）；预演在类型文案里预告"绑定分类 tpl-x"；查不到节点只记一笔（"未就绪，未绑定"）不抛错
- **实测**（编译 0 / ERROR 0；租户视角复现，`sysadmin` 已改回并复查）：
  · 预演：`STRUCTURAL` / `STD_PART` / `ELECTRONIC` 三条均显示"绑定分类 tpl-…" ✓
  · 真装（平台侧重启自动装包，走"类型已存在 → 补绑"路径）：`ck_type_classification_link` 得到 3 行 ——
    `ELECTRONIC|tpl-electronic|电子元器件`、`STD_PART|tpl-structure-standard-part|标准件`、
    `STRUCTURAL|tpl-structure|结构件` ✓（查节点 / 写 link / 名称解析全链路验证）
- ✗ **未实测**：**新装路径**（`insertType` 内的绑定）—— 需新租户真装（走引导时发生）；
  与实测路径共用同一 `bindClassification`，只剩调用点差异
- **过程教训**：首个编译错（找不到 `TypeClassificationLinkMapper`）＝ 加 import 时漏写一行 ——
  多处小改后**完整编译是底线**，"改完立刻编译"没有替代品

#### 引导页：标题与副标题表达修正 + 安装过程进度展示（纯前端，无 SQL 变更）
- **用户反馈**：*"行业配置的名义好像没有表达清楚，需要修改显示；导入方法论配置的时候需要有进度的展示，
  如果导入的速度特别快，可以适当的增加 wait 的时间。这样用户的体验会更好"*
- **标题与副标题**（`OnboardingPage.vue`）：
  · 原标题「{{租户名}} · **行业配置**」→「{{租户名}} · **业务配置引导（研发体系落地）**」
    （2026-10-11 用户定名；本页实际是"选行业 → 定方法论 → 装解决方案"的初始化向导）
  · 副标题原先只写"第一步是选行业…"，却对第 2/3 步一并显示 → 改为**随步骤切换**的 `stepHint`
    （第一步选行业 / 第二步确认方法论与画像 / 第三步行业解决方案与"确认前不写库" / 完成）
- **安装进度**（点「确认并安装行业解决方案」之后）：
  · 约束：后端 `/onboarding/complete` 是**同步**返回（一次请求内把 3 层写完），没有可轮询的进度接口
    → 进度只能在前端做，且**必须基于真实数据**（不编造阶段）
  · 做法：不可关闭的模态进度框 —— 进度条 + 逐层状态（待装 / 进行中 / 完成）；
    **层次与新增数取自预演 `layers`**（平台基础包 → 行业共用基 → 行业包，各自"将新增 N 项"或"平台已就绪·直接复用"）
  · 百分比由定时器每 240ms 推进、**92% 封顶**（不假装已完成）；真完成时才置 100%（绿色 success），
    停留 420ms 再翻到第 4 步
  · **最小展示时长 2.6s**（`INSTALL_MIN_MS`）：安装很快（本地库常 < 1s）时也等满 ——
    正是用户说的"速度特别快就适当增加 wait 时间"
  · 失败/异常同样收尾（清定时器、复位百分比）；`onUnmounted` 清理，避免离开页面后定时器空转
- **验证**：`npm run build` 通过（`✓ built in 54.21s`，`OnboardingPage` chunk 17.23 kB 正常产出）；lint 0 error
- ✗ **未实测**：真机点击流程（需在 dev server 上实际提交一次）；进度逻辑与数据来源已逐项核对
  （92% 封顶、完成态全绿、最小等待、异常收尾）

#### 修复：业务配置中心「生命周期」页签表格被裁、无滚动条（前端样式，无 SQL 变更）
- **用户反馈**：*"生命周期状态页签没有垂直的滚动条"*
- **定位**（浏览器探针实测容器链）：根因不在高度链（`.bc-content` → `.bc-embedded-page` → `.lc-page` 均正常），
  而在 `.lc-table-wrap` —— 它写了 `flex:1; min-height:0` 却**不是 flex 容器**，于是内层 DataTable 的
  `flex:1` 失效、按内容自然高（535px）溢出；溢出部分最终被 `.ant-tabs-content-holder` 的 `overflow:hidden`
  裁掉 170px —— 分页器正在被裁区域（看不到"共 N 条"，且无任何滚动条）
- **修复**（`LifecycleConfig.vue`）：
  · `.lc-table-wrap` 补 `display:flex; flex-direction:column; overflow:hidden`（让内层 flex:1 生效）
  · 两个表格的固定 `:max-height="400"` 改为 DataTable 自带的 **`stretch` 模式**
    （表体自动滚动、表头与分页器固定在容器内 —— 正是该模式的设计用途）
- **实测**（浏览器探针，修复前后对比）：
  · 修复前：content-holder `h=411 / scrollH=581`（裁 170）、data-table-wrapper 535、分页器不可见
  · 修复后：content-holder `411/411`；table-wrap `365/365`；`ant-table-body` `222/392` 且 **ovY=scroll**（表体可滚）；
    分页器 `h=24` 可见 ✓（截图确认"共 8 条 / 1 / 10 per page"回归可见位）
- **抽查**：同容器的「视图定义」当前数据少未触发（表格 147px），但其结构同构（固定 max-height + 非 flex 外层），
  数据一多（> 400 + 分页器）会复现同一裁切。全库 `:max-height=` 共 **17 处 / 14 个页面**
  （Dashboard / Deliverable / Gate / IssueType / Metric / NumberRule / PlatformMember / Review / Role /
  StageTemplate / RuleTemplate / View / VersionRule / Unit）—— **本次只修生命周期**，其余待逐页评估

#### 修复：角色定义页出现两条垂直滚动条（前端样式，无 SQL 变更）
- **用户反馈**：*"角色页面为什么有两个垂直的滚动条，如果只需要外层的滚动条可以吗？"* —— 可以，已按此实施
- **根因**（两条滚动条各有出处）：
  · **内层**：`RoleConfig.vue` 的 DataTable 写死 `:max-height="480"` → 表格内容（12 行 ≈ 520px）超出后
    表体自己滚动 —— 这是第一条
  · **外层**：挂载容器 `OrgPage.vue` 的 `.org-roles-page`（`height:100%; overflow:auto`）——
    整页内容（页头 + 统计 + 480 表格 + 分页 ≈ 712px）超容器 592px → 外层滚动 —— 第二条
  · 另外 `RoleConfig.vue` 自身写着"撑满型"样式（`.role-config-page{height:100%;overflow:hidden}` +
    `.rc-table-wrapper{flex:1;overflow:hidden}`），在父高确定时会对自然高内容形成"压 + 裁"的组合 ——
    与双滚动条同族，一并断根
- **修复**（`RoleConfig.vue` 三处，取"只保留外层滚动"即用户选定口径）：
  · 表格去掉 `:max-height="480"`（不再产生表体滚动，12 行完整展开）
  · `.role-config-page` 去掉 `height:100%` + `overflow:hidden`（自然高）
  · `.rc-table-wrapper` 去掉 `flex:1/min-height:0/overflow:hidden`（不压不裁，滚动交给外层）
- **实测**（浏览器探针，全页扫描所有可滚容器）：
  · 修复后全页**只剩一个 scroller**：`.org-roles-page  h=592 / scrollH=712` ✓
  · `role-config-page h=712 / scrollH=712`（自然高、无裁切）；`.ant-table-body` 不再滚动；文档层不滚
  · 截图确认：右侧单条滚动条、表格完整向下延伸、无内容被裁
- **说明**：与「生命周期」页的选择不同（那页依赖页签 + 表头固定，采用"撑满 + 表体滚动"）；
  本页列表短，外层滚动即可，两种都只保留一条滚动条 —— 按页面形态各取其宜
- ✗ 同类排查提示：`ViewConfig`（`:max-height="400"` + 同款容器）最有可能出现同样双滚动，数据多时可见

#### 修复：版本规则页出现两条横向滚动条（前端样式，无 SQL 变更）
- **用户反馈**：*"版本规则页面有两个横向的滚动条，只保留最下面的滚动条是否可以？"* —— 可以，已按此实施
- **根因**（浏览器探针带坐标实测）：
  · 表格列宽合计 ≈ 1260px > 容器 682px → `.data-table-wrapper`（DataTable 自带）横向滚动 ——
    第一条，位置在表格容器底部（分页器下方），即用户要保留的"最下面"那条
  · 同时 antd 在设置 `scroll.y`（`:max-height="420"`）时会给 `.ant-table-body` 也加 `overflow-x: auto`，
    而它的横向溢出量只有 **20px**（竖向滚动条占位造成的假溢出）→ 表体又冒出一条 —— 第二条
  · 两条滚的是同一份内容，属冗余；`html` / `body` 层不滚（页面级没有第三条）
- **修复**（`VersionRuleConfig.vue` 一条规则）：`.vr-table-wrapper :deep(.ant-table-body) { overflow-x: hidden; }`
  —— 裁掉表体那条假溢出条；列宽超出容器时仍由容器那条横向滚动看全
- **实测**（探针复验）：全页横向滚动容器**只剩 `.data-table-wrapper`**（cw=682 / sw=1260 / bot=478）✓；
  `.ant-table-body` 的 `overflow-x` 已是 `hidden` ✓；lint 0 error；截图确认表格内容仍可完整横向查看
- ✗ **同类提示**：其余使用 `:max-height` 的页面（共 17 处 / 14 页）在列宽超出时大概率有同款"表体假溢出条"，
  可同法一行修掉 —— 需要时逐页处理

#### 「克隆平台布局」改为「克隆根类型布局」：从本租户 root_type_code 取布局（无 SQL 变更）
- **用户要求**：*"克隆平台布局修改成克隆根类型布局，即 root_type_code 的布局"*
- **旧实现**（`PageLayoutServiceImpl.cloneFromPlatform`）：从**平台租户**读同名实体的布局复制到本租户 ——
  与"按租户隔离、不拿平台兜底"的口径相悖（2026-10-10 布局隔离后，平台那份对租户本已不可见）
- **新实现**（`cloneFromRootType`；接口 / 控制器 / Mapper 注释同步）：
  · 当前类型（entityCode）→ 本租户内解析 `root_type_code`（能力宿主，如 电子元器件 → PART）→
    取**本租户**根类型的同名操作布局 → 复制给**当前类型**
  · ★ 关键差异：克隆行的 `entity_oid` / `entity_code` 用**当前类型自己**的（旧实现沿用布局源的 oid ——
    那是"同一实体"才成立；根类型 oid 与当前类型不同）
  · 边界如实报错：平台租户无需克隆（400）/ 本租户已有该操作布局（409）/ 当前类型已是根类型（400）/
    根类型不存在（400）/ 根类型尚无该操作布局（400）
  · 顺带修复：`/page-layouts/clone` 异常未按全站惯例转 `ApiResponse.fail` → 裸奔成 Spring 默认 500 JSON
    （没有 message）→ 前端只能弹笼统"克隆失败"；现按惯例 400/409 返回，原因可直接显示
- **前端**（`PageDesigner.vue` / `api/index.js`）：按钮「克隆平台布局」→「**克隆根类型布局**」；
  函数名与提示文案同步；`canClone` 注释更新（判断逻辑不变，后端二次校验兜底）
- **实测**（编译 0 / ERROR 0；CKAI-TECH 已完成引导：18 条类型、真实布局在场）：
  · **正路**：租户视角对 ELECTRONIC 克隆 create → 返回 200 成功并落库 1 条（`entity_oid` = ELECTRONIC 自己 ✓）
    —— 旧实现绝不可能是这个结果（它读平台那份、且平台租户被第一道校验拦下）✓
    · 该测试数据**已清理**（`DELETE 1`），复查回到"10 类型 × 4 布局"的干净状态 ✓
  · 平台视角 → `{code:400, message:"平台租户无需克隆"}` ✓
  · 租户视角重复克隆 → `{code:409, message:"本租户已有该操作的页面布局…"}` ✓
- ✗ **发现（待定）**：`electronics-core` 的 7 个类型在 CKAI-TECH **一条布局都没有** ——
  该包 softtypes.json 只有 `types[]`、没有 `pageLayouts` 声明，而 platform-base 的 `byHost` 只覆盖它自己的
  10 个类型（DOCUMENT / PART / PRODUCT_LINE 等各 4 操作）→ 电子域类型的列表 / 新建 / 详情页目前是空白布局
  · 两条路：给 electronics-core 补 `pageLayouts`（复用 PART / ENG_DOCUMENT 基准）；或用户逐个用本按钮克隆

#### 修复：方法论配置 8 个页签的表格各带一条竖向滚动条（前端样式，无 SQL 变更）
- **用户反馈**：*"研发阶段、交付件、阶段门、评审、检查规则、问题类型、指标、驾驶舱都存在 2 条垂直的滚动条，
  只保留外层的滚动条"*
- **根因**（与角色定义页同款）：8 个页签各自给 DataTable 写了固定 `:max-height`
  （研发阶段 420 / 其余 480）→ 表格自身可竖滚（内层）；同时整页内容（页头 + 统计 + 表格 + 分页）高于
  `.bc-embedded-page`（`height:100%; overflow:auto`）→ 外层也滚 → **两条**
- **修复**（8 个文件各删一行 `:max-height`，取"只保留外层"口径 —— 与本页列表短、无需固定表头的形态相配）：
  · `StageTemplateConfig.vue`（研发阶段，表格 inline 在本文件）
  · `DeliverableTemplateConfig.vue`（交付件）/ `GateTemplateConfig.vue`（阶段门）/
    `ReviewTemplateConfig.vue`（评审）/ `RuleTemplateConfig.vue`（检查规则）/
    `IssueTypeTemplateConfig.vue`（问题类型）/ `MetricTemplateConfig.vue`（指标）/
    `DashboardTemplateConfig.vue`（驾驶舱）
  · 查证这 8 个文件里其它 `overflow` 命中均与本次无关（弹窗 `body-style`、`.xx-ellipsis` 文本省略），
    也没有"`height:100%` + `overflow:hidden`"式撑满裁切样式 → 只需删限高，无"删了会被裁"的副作用
- **实测**（浏览器探针；租户视角 CKAI-TECH —— 与用户截图同一路径，测完已切回平台并复查 ✓）：
  · 【研发阶段】唯一滚动容器 = `.bc-embedded-page`（ch=349 / sh=905），表格真实 **11** 行 ✓
  · 【交付件】唯一滚动容器 = `.bc-embedded-page`（ch=349 / sh=566），表格真实 **13** 行 ✓
  · `.ant-table-body` 已不存在（不再设 `scroll.y` → antd 不生成表体滚动容器）✓
  · 截图确认右侧只剩一条滚动条 ✓；8 个文件 lint 0 error ✓
- ✗ **全库仍有 6 处 `:max-height` 在其它页面**（NumberRuleConfig / PlatformMemberConfig / UnitConfig /
  ViewConfig ×2 / VersionRuleConfig）—— 同款双滚动隐患，需要时一并统一

#### 修复：分类 IBA 值串写进 ck_type_iba_data（行操作「编辑」漏拆 clsIbaValues）
- **用户报告**：*"创建和编辑 Part 时分类属性的 iba 值被存储到 ck_type_iba_data 中了，而非 ck_cls_iba_data 中，
  是否是之前 attribute_model.md 的 skill 中的描述有错误？"*
- **查证结论（文档 / 代码 / 数据 三层对质）**：
  · **skill 文档没有错**（`.codebuddy/skills/type-attribute-operations/references/attribute-model.md`）：
    它对两张值表的分工（type 表挂**主对象**、cls 表挂**迭代 oid**、`entity_oid=''` 表分类节点默认值）
    与各自写入入口（`IbaDataSupport` vs `ClsIbaDataSupport`）的描述与代码完全一致；
    仅两处"实测快照"过时（"当前均 0 行"、布局查找"回落平台租户"—— 都是 2026-10-10 的口径，非结构性错误）
  · **后端设计也是对的**：`PartServiceImpl.createPart/updatePart` 两条路都调
    （`saveClsIbaValues` + `saveIbaValues` / `mergeIbaValues`），且 `extractIbaFromBody` 明确**排除**
    `clsIbaValues` —— 只要前端按约定提交，两表各归各位
  · **错在前端的一条提交路径**：`components/PartRowActionMenu.vue` 的「操作 → 编辑」弹窗
    （元器件库、封装·图符库每行都用它）把 DynamicForm 的**全部字段**直接提交，
    没有像其它入口那样"从顶层剔除分类字段 + 单独放 `clsIbaValues`"
- **证据（库内实测）**：`ck_type_iba_data` 有 5 行同日 19:31 写入的 PART 数据 ——
  RESISTANCE / TOLERANCE / POWER_RATING / TEMP_COEFFICIENT / PACKAGE：
  · 这 5 个码在 `ck_type_iba` **0 条绑定**、在 `ck_cls_iba` 大量绑定（PACKAGE 102 / TOLERANCE 24 / …）
    → 是**纯分类参数**，出现在 type 表 100% 是错的 ✓
  · `ck_cls_iba_data` 全程 **0 行** ✓（保存路径根本没拿到 clsIbaValues ✓）
- **修复**（统一到既有正确口径 —— "delete 顶层字段 + `payload.clsIbaValues`"）：
  · `PartRowActionMenu.vue#confirmEdit`（覆盖元器件库 + 封装·图符库的"操作→编辑"）
  · `FootprintSymbolLibrary.vue#confirmCreate`（新建封装/图符；ENG_DOCUMENT 系，表带 `cls_oid`）
  · 已核查**无需改**：ProductSeries（PRODUCT_LINE / PRODUCT_MODEL 表无 `cls_oid` 列）、
    ComponentLibrary 与 ProductLineDashboard（本就有拆分）、MpnManagement（AML/AVL 子表，无关）、
    PartDetail（不提交 Part 编辑）
- **已清理**：删除那 5 行错表数据（`DELETE 5`），两值表回到 0 行干净基线；
  用户那条"200欧电阻"重新编辑保存一次即写入正确表（回显走 `entity.clsIba` 正道，DynamicForm:560 本就有）
- **实测（端到端，浏览器真机操作）**：在元器件库对"200欧电阻"点「操作 → 编辑」，填「阻值=10 / 精度=1」保存：
  · `ck_cls_iba_data` 立即出现 2 行 ✓ —— `entity_oid` = **迭代 oid**（A-Z.2 的最新迭代 ✓ 与文档规定的粒度一致）、
    `classification_oid` = 贴片电阻 ✓、`attr_code` = RESISTANCE / TOLERANCE ✓
  · `ck_type_iba_data` **0 行** ✓（不再串表）
  · 编辑弹窗的「分类属性」区回显正常、保存后列表刷新正常 ✓
  · 测试值（10 / 1）保留在正确表内，用户可在界面直接改（回显走 `entity.clsIba` 正道，改存走 cls 表 ✓）

#### 零件详情页「基本属性」补充"分类"展示（前端，无 SQL 变更）
- **用户反馈**：*"详情页面没有展示所属的分类"*（元器件库列表有"分类"列，详情页「基本信息」区没有该项）
- **现状**：`PartDetail.vue` 的「基本信息」是硬编码 `a-descriptions`（名称 / 编码 / 类型 / 描述 / 单位 / …），
  没有"分类"；数据侧 `Part.clsOid` 详情接口本就返回，只差把它解析成分类名并渲染
- **修复**（3 处）：
  · 引入 `getClassification` API；`loadDetail` 拿到 `clsOid` 后与既有 `loadClsIbaDefs` 同处解析分类名
  · 新增 `clsName` + `loadClsName`（取不到就留空，界面显示 '-'，不显示无意义的 oid）
  · 「基本信息」在"类型"后新增一项「分类」
- **实测**（浏览器真机）：打开"200欧电阻"详情 → 基本属性 → **分类：贴片电阻** ✓（与列表页"分类"列一致）；
  「分类 IBA 属性」区（额定功率 3 / 精度 1 / 阻值 10 / 温度系数 3）正常展示 ✓

#### Part 详情页新增「MPN」页签：AML（认可制造商料号）+ AVL（供货渠道）全功能（前端，无 SQL 变更）
- **用户要求**：*"在 Part 对象的详情页面增加 MPN 页签，并实现 MPN 的相关功能"*
- **实现方式**（同一能力不做两份实现 —— 与「MPN 管理」注释里"同一对组件，不是两份实现"的口径一致）：
  · 新增共享组件 `components/PartMpnPanel.vue`：自 `views/resource/MpnManagement.vue` 的「AML · AVL」
    部分抽出 —— props 收 `partOid`（去掉原页面的选件器），能力原样保留：AML 增删改 + 行内展开
    AVL（供应商 / 供应商料号 / 单价 / 交期 / MOQ / SPQ / 首选）增删改 + 汇总与单一来源提示
  · `MpnManagement.vue` 改用该组件（选件器 + `<PartMpnPanel :part-oid>`），script 与样式同步精简 ——
    管理页只负责"选件"，两处共用同一实现
  · `PartDetail.vue`：在「双向替代」之后新增「MPN」页签（`:part-oid="oid"` 直接传当前件，无选件器）；
    顺带修复共享逻辑里的一处小瑕疵：列表的"制造商 / 供应商"列原来依赖"打开弹窗才加载"的候选
    （先看到的是 oid），现在面板加载时预载候选
- **实测（浏览器真机全链路）**：
  · 详情页「MPN」页签渲染正确（工具栏 + 汇总条 + 表格 + 空态提示）
  · 新增 AML：德州仪器 + `RC0805JR-0710RL` → 表格 1 行（制造商列显示"TI · 德州仪器"✓）、
    `ck_part_aml` 落库 1 行（`part_oid` 挂"200欧电阻"✓ APPROVED ✓）
  · 展开行新增 AVL：乘恺电子 → 渠道列变"1 家"✓、汇总条「去重供应商 1 家」并触发
    **「单一来源风险（R-SH-11）」**红色提示 ✓、`ck_part_avl` 落库 1 行（`aml_oid` 关联正确 ✓）
  · lint 0 error；测试数据（1 条 AML + 1 条 AVL）保留在库内，可在界面直接编辑 / 删除

#### 界面用词：「MPN 数据」定名为「MPN 厂商」（无 SQL 变更）
- **用户要求**：*"导航栏中的 MPN 数据修改成 MPN 厂商"*
- **四处显示名同改**（菜单 / 面包屑 / 浏览器标签 / 页面标题 —— 只改菜单会出现"菜单叫厂商、面包屑叫数据"的割裂）：
  · `MainLayout.vue`：菜单项 `MPN 数据` → `MPN 厂商`；面包屑 `MPN 数据（制造商 / 供应商）` → `MPN 厂商（制造商 / 供应商）`
  · `router/index.js`：`meta.title` → `MPN 厂商`（拼进浏览器标签）
  · `MasterDataPage.vue`：页面标题 `<h3>` → `MPN 厂商`
- **只改界面字样**：内部标识全部未动 —— 路由 `path: master-data`、组件名 `MasterData` / `MasterDataPage`、
  菜单 key `/master-data`、API 函数名；5 处注释里的显示名引用同步更新（并留"2026-10-11 由「MPN 数据」定名"的沿革说明）
- **实测**（浏览器，CKAI-TECH 视角，测完已切回平台并复查）：左侧菜单 / 面包屑 / 页面标题 / 浏览器标签
  四处均显示「MPN 厂商」✓；6 个文件 lint 0 error；页面功能（制造商列表）渲染正常 ✓

#### 移除「克隆平台分类」功能（前后端全删 + 一个 DTO 文件，无 SQL 变更）
- **用户要求**：*"去掉克隆平台分类功能"*（分类管理页右上角按钮）
- **删除前先证消费方唯一**（前端 API / 后端接口与实现均只有分类管理页一个入口）：
  · 前端 `ClassificationPage.vue`：按钮 + 克隆弹窗（平台树勾选 / 进度条 / 取消确认）+ 全部状态与逻辑
    （`openCloneModal` / `toPlatformNode` / `resetCloneModal` / `handleClone` + 进度定时器）+ 克隆专用图标导入
  · 前端 `api/index.js`：`getPlatformTree` / `clonePlatform` 两个导出
  · 后端 `ClassificationController`：`GET /platform-tree` + `POST /clone-platform` 两接口
  · 后端 `ClassificationService` / `ClassificationServiceImpl`：`findPlatformTree` / `cloneFromPlatform` 及
    私有链（`cloneOneFromPlatform` / `cloneSubtree` / `syncIbasAndLayouts` / `cloneIbaDefinition`）+
    「分类页面布局初始化」辅助段（`syncClsPageLayouts` / `insertClsPageLayout` / `buildLayoutJson` /
    `buildField` / `uiComponentFor` —— 经调用图确认**仅**克隆链路使用）+ 连带清理
    （`ClsPageLayoutMapper` 注入、`ObjectMapper` / `Logger` 字段与相关 import）
  · 删除 `cls/dto/ClassificationCloneResult.java`（唯一用途即克隆统计）
- **未动**（名字相近但属**其它功能**：基线模块的阶段 / 交付件 / 阶段门 / 评审 / 检查规则 / 问题类型 /
  指标 / 驾驶舱模板的 `cloneFromPlatform` —— 逐类核对过，各自服务于自己的"克隆平台模板"）
- **实测**（编译 0 / ERROR 0 / 启动 ✓；浏览器 CKAI-TECH 视角，测完已切回平台并复查）：
  · 分类管理页右上角只剩「IBA 扩展」✓；分类树与统计正常渲染（107 / 2 / 105）✓
  · 接口探针：`POST /classifications/clone-platform` → **405**（无 POST 路由 = 已删）；
    `GET /classifications/platform-tree` → 200 但 `data: null` —— 是通用路由 `GET /{oid}` 把
    "platform-tree" 当 oid 查的正常结果（**非接口残留**：接口在时会返回克隆统计对象）；
    `GET /classifications/tree` → 200 ✓（树功能未受影响）
  · 前端 lint 0 ✓；全仓残留扫描 0 ✓（`克隆平台分类` / `cloneModal` / `platformTree` / `getPlatformTree` …）

#### MPN 优先级对齐修复：AML 列表改按状态语义排序 + AVL 表单补「显示顺序」（无 SQL 变更）
- **由来**：用户问*"MPN 中的优先级怎么管理？"*→ 查证后如实指出两处不一致，用户*"回到 MPN 优先级的问题"*，本次对齐
- **两层优先级机制本就已就绪**（AML 的 `approved_status` 三态 + `sort_order`；AVL 的 `preferred` 布尔），
  但 **AML 列表排序与"首选"语义相反** ✗：
  · 原 `ORDER BY sort_order, approved_status, mpn`：sort_order 同为 0（默认）时按**字母序**排
    （APPROVED < DISALLOWED < PREFERRED）→ **PREFERRED（首选）反而排最后**；
    而 AVL 是 `ORDER BY preferred DESC, sort_order, oid`（首选浮顶）✓ —— 两层口径不一致
  · 另：AVL 的 `sort_order` 有列、无表单入口（永远 0）
- **修复**：
  · `PostgreSqlMpnMapper.listAmlByPart`：改为 `ORDER BY CASE approved_status WHEN 'PREFERRED' THEN 0
    WHEN 'APPROVED' THEN 1 WHEN 'DISALLOWED' THEN 2 ELSE 3 END, sort_order, mpn`
    —— 语义序（首选 → 认可 → 禁用），同档再按显示顺序、最后按料号；与 AVL 同口径
  · `PartMpnPanel.vue` AVL 表单：SPQ 后补「显示顺序」输入框（照 AML 表单同款，服务端本就支持）
- **实测**（编译 0 / ERROR 0 / 启动 ✓；浏览器 CKAI-TECH 视角，测完已切回平台并复查）：
  · 库里天然对照组：200Ω 电阻 2 条 AML（sort_order 均 0）——修复前字母序 APPROVED 在前、PREFERRED 在后；
    **修复后 API 返回 `PREFERRED | RC0805JR-0710RL` 第一、`APPROVED | SS000000-0001` 第二** ✓
  · 浏览器：Part 详情 → MPN 页签 → 展开首选 AML 行 → 「编辑供货渠道」弹窗出现「显示顺序」输入框 ✓
  · 前端 lint 0 ✓

#### 界面用词：AML 的「认可状态」定名为「认证状态」（无 SQL 变更）
- **用户反馈**：*"认可状态是不是语义不太正确，应该改成正认证状态"* —— 按「认证状态」处理
  （"正"疑为输入法多打；原词确有语义毛病：**字段名"认可状态"与其三值之一 APPROVED（认可）同名**，
  用其一名命名整个字段，语义窄、读起来循环）
- **改动 3 处**：
  · `PartMpnPanel.vue`：AML 表单 label「认可状态」→「认证状态」；AML 表格列头同改
  · `PartAml.java`：字段注释同步（并注明原名与改名原因）
- **未动**（各自都有明确理由）：
  · 三值标签保持：首选（PREFERRED）/ 认可（APPROVED）/ 禁用（DISALLOWED）—— 若要顺成"认证通过"等一言可换
  · "认可料号（AML）"/"认可制造商料号"等行业语境用词（Approved Manufacturer List 的既有译法）不属字段名范畴
  · 库内存储值（PREFERRED / APPROVED / DISALLOWED）与列名 `approved_status`（无 SQL 变更）
- **实测**（浏览器 CKAI-TECH 视角，测完已切回平台并复查）：
  · AML 表格列头显示「认证状态」✓；「编辑制造商料号」弹窗 label 显示「认证状态」✓
  · 同图顺带印证排序修复：首选行（TI · RC0805JR-0710RL）排在认可行之前 ✓
  · 前端 lint 0 ✓；全仓"认可状态"残留 0 ✓

#### softtype 搬家：ELECTRICAL（电气件）从 platform-base 归回 automotive-apqp 与 military-gjb（纯包资源，无 SQL 变更）
- **用户要求**：*"softtype 中 ELECTRICAL 从 base 中移动到 APQP 和 GJB 行业包中"*
- **完全照既有先例**（FUNCTIONAL 三件套此前就是这么搬的 —— 其 note 早已写明约定："两包同码声明、
  后装方按 code 判重跳过（SoftTypeInstaller 幂等）、两份定义必须保持一致，改一处记得同步另一处"）：
  · `platform-base/softtypes.json`：删除 ELECTRICAL 块（11 → 10 个类型）；note 同步（子类型数 8 → 4，
    并补"★ 电气件（ELECTRICAL）同样**已移出本包** —— 电气件口径因行业而异，随 automotive-apqp 与 military-gjb 各自注册"）
  · `automotive-apqp/softtypes.json`：新增 ELECTRICAL（描述按汽车改写"整车 E/E 架构下的电气专业对象"；
    code / icon（BulbOutlined）/ sortOrder（21）/ 三绑定 / 域与 base 时保持一致）；note 补同步约定
  · `military-gjb/softtypes.json`：同上（描述按军工改写"型号配套的电气专业对象"）；note 补同步约定
  · 两个 `pack.json`：SOFT_TYPE count 4 → 5；**顺带**把 display 旧名"对象类型定义"统一为"业务对象定义"
    （上一轮改名只覆盖 platform-base / electronics-core，这两个包是漏网的 —— 恰好就在本次要改的 count 行上）
  · `platform-base/pack.json`：SOFT_TYPE count 11 → 10
- **依赖核对**：ELECTRICAL 引用的 PART_NUMBER / LETTER_26 / STANDARD（都在 platform-base）、
  ELECTRICAL_DOMAIN（BusinessDomainInitializer 平台级初始化）、parentCode = PART（base）—— **依赖全在平台层**；
  行业包以 basePack 为基、安装顺序（base → 行业包）保证可解析 ✓ （与 FUNCTIONAL 不同：它的 FUNCTIONAL-NUM 也留平台层）
- **实测**（JSON 校验 ✓ 编译 0 / ERROR 0 / 启动 ✓）：
  · 6 个 JSON 全部合法，types 数与 pack count 交叉一致（base 10/10；apqp 5/5；gjb 5/5）
  · 接口：platform-base SOFT_TYPE=10、automotive-apqp=5、military-gjb=5，display 均"业务对象定义"
  · 预演：`[platform-base]` 类型明细 **10 条且无 ELECTRICAL** ✓；`[automotive-apqp]` 含
    FUNCTIONAL/SYSTEM/SUBSYSTEM/CI/**ELECTRICAL** ✓
- **存量数据说明**（重要，非缺陷）：已装过包的老租户（含平台租户）里**已有的 ELECTRICAL 类型行不会消失** ——
  装包器只新增/跳过、从不删数据，故预演显示"本租户已有该类型"（幂等）。本次移动的效果是
  **新租户 / 重装时由行业包提供 ELECTRICAL**，base 不再提供它

#### Part 详情页「MPN」页签接上能力开关（所属能力 = MPN 管理；纯前端，无 SQL 变更）
- **用户要求**：*"Part 的相应页面的 MPN 要增加能力开关，所属能力（组）为 MPN 管理"*
- **改动**（`PartDetail.vue`，2 处）：
  · 模板：`<a-tab-pane key="mpn" tab="MPN">` → 加 `v-if="hasMpn"`
  · 脚本：`const hasMpn = computed(() => has('MPN_MANAGEMENT'))`（`useCapabilities` 本页早已引入 —— BOM_COST 先例）
  · 口径与**同款页签**完全一致（`EnterpriseResource` 的「MPN 管理」页签就是 `v-if="hasMpn"` + 同款 computed）；
    账本由 MainLayout 统一加载、本页只读；取不到能力 → 不显示入口（宁可不显示，也不给空诺）
- **至此 MPN 三处入口同一开关**：导航「MPN 厂商」菜单 / 企业资源页「MPN 管理」页签 / Part 详情页「MPN」页签
- **实测（开关对照实验：临时改账本 `ck_tenant_capability.enabled` 再改回 ✓ 测完已恢复并复查 ✓）**：
  · 开启：Part 详情页签 = `… 双向替代 | MPN | 关联文档 …` ✓；导航「MPN 厂商」在 ✓
  · 关闭：页签 = `… 双向替代 | 关联文档 …`（**MPN 消失、其余 10 页签完好**）✓；导航菜单同步消失 ✓
  · lint 0 ✓；`sysadmin` 已切回平台并复查 ✓
- **关于"所属能力组"**：核对全库 —— 能力条目字段是 `code/name/default/note`（包声明，
  如 electronics-core/capabilities.json 的 `MPN_MANAGEMENT / "MPN 管理"`）与
  `capability_code/capability_name/enabled/source_pack/note`（账本表），**没有 group / 能力组字段**；
  故本次按"**所属能力 = MPN 管理（MPN_MANAGEMENT）**"实现，并在模板/脚本注释中写明。
  若"能力组"另有所指（如行业包详情页"能力开关"表新增一列按组归类、或账本加 group 字段），属新增属性 —— 按要求再补

#### 新建文档弹窗两处修正：所属文件夹显示名称（不再显示 oid）+ 所属产品默认当前系列/型号
- **用户反馈**：*"所属文件夹展示的是 Oid，不是名称；所属产品没有将默认到当前系列或型号"*（系列页 → 过程资料 → 新建文档）
- **① 所属文件夹显示 oid 的根因与修法**：
  · 根因：`DOCUMENT` / `ENG_DOCUMENT` 的 create 布局里，`fld-folder` 是 `uiComponent: "input" + readonly` ——
    DynamicForm 的 RenderFields 对 input 直接显示原值（= folderOid ✗）
  · 修法：布局改用 **`folder-select` + 保留 readonly** —— RenderFields 的 folder-select 由
    `commonProps = { value, disabled: isReadonly }` 驱动，readonly 会渲染成 **disabled 的 TreeSelect**：
    显示文件夹全路径名（按"当前容器 + 当前阶段"过滤的树）、不可改；**提交链路零改动** ——
    `confirmDocument` 本就有 `payload.folderOid = selectedFolder.value.oid` 强制覆盖兜底 ✓
  · 布局源头**两处同步**：`platform-base/v1.0.0/softtypes.json`（replace_all：DOCUMENT + ENG_DOCUMENT）
    与 `PageLayoutInitializer.java`（replace_all 同款）
  · 存量库：`ck_type_page_layout` 里已落库的旧 JSON 需升级（装包 / 初始化器都"已有即跳过"）——
    已对本地库执行并核查（DOCUMENT / ENG_DOCUMENT create 行现为 folder-select ✓）；升级 SQL 见文末
- **② 所属产品未默认的修法**：`openCreateDocument` 的 docForm 补 `ownerOid: line.value?.oid || ''`
  —— 默认到"当前系列 / 型号"（文档本就归属当前对象），选中项显示为对象名称（product-select 是 TreeSelect ✓）
- **实测**（浏览器 CKAI-TECH 视角，测完已切回平台并复查；lint 0）：
  · 弹窗九字段齐全；「所属文件夹」显示【市场评估】（灰底只读 = disabled ✓ 名称为树节点全路径名）；
    「所属产品」显示【X系列智能手表】✓；均不再出现 oid
- **升级 SQL（现有库执行一次；幂等，升级后不再命中）**：
  ```sql
  UPDATE ck_type_page_layout
  SET layout_json = replace(layout_json::text,
          '"readonly": true, "fieldName": "folderOid", "placeholder": "由当前文件夹自动填充", "uiComponent": "input"',
          '"readonly": true, "fieldName": "folderOid", "placeholder": "由当前文件夹自动填充", "uiComponent": "folder-select"')::jsonb,
      updated_at = now()
  WHERE position('"readonly": true, "fieldName": "folderOid", "placeholder": "由当前文件夹自动填充", "uiComponent": "input"' in layout_json::text) > 0;
  ```
- **说明**：PART 各子类型 detail 布局的「所属文件夹」是同款旧写法（无 placeholder 款），本次未动 ——
  属"只读详情页"场景，如需同修照此办理（把该字段的 uiComponent 改为 folder-select 即可）

#### 页面设计器：根类型不再显示「克隆根类型布局」按钮（纯前端，无 SQL 变更）
- **用户要求**：*"如果是根类型则不显示克隆根类型布局"*
- **根因（两处）**：
  · `canClone` 原有三条判据（非平台管理员 / 系统内置操作 / 本租户未存过该操作布局）中
    **缺"当前不是根类型"** —— DOCUMENT 这类根类型打开设计器也显示按钮；而后端克隆逻辑是
    "按当前类型的 root_type_code 找宿主布局"，对根类型即"克隆自己"，无意义
  · 且 `TypePage.goToPageDesigner` 传的键名是 `entityType`，设计器读的却是 `typeKind` ——
    该参数一直吃默认值 `'SOFT_TYPE'`（本次顺带修正了这个参数错位）
- **修复**：
  · `PageDesigner.vue` `canClone` 新增两条判据（前置）：`typeKind === 'OOTB'`（类型树透传、同步可用）
    → 根类型直接隐藏；`typeDefinition.rootTypeCode === entityCode`（兜底"直接改 URL 打开"，懒加载后生效）
  · `TypePage.vue` 跳转 query 补传 `typeKind: selectedNode.typeKind`
- **判据核对（库内实况）**：`type_kind` 语义正是"根 / 软" —— DOCUMENT / ENG_DOCUMENT / PART = OOTB
  且 `root_type_code` 自指；ELECTRONIC / STRUCTURAL / SOFTWARE = SOFT_TYPE 且 `root_type_code = PART` ✓
  因此不会误藏软类型
- **实测**（浏览器，用**租户管理员** `Roney.Liu`（TENANT_ADMIN）对照同账号两类型 ——
  注意平台管理员本就看不到按钮（旧条件），用它验证会被遮蔽、证明不了新判据）：
  · 【根类型 DOCUMENT】工具栏：重置 / 保存 / 预览 —— **无克隆按钮** ✓
  · 【软类型 ELECTRONIC】工具栏：重置 / **克隆根类型布局** / 保存 / 预览 —— 有 ✓（回归通过）
  · 验证用的临时密码对齐已还原并复查（restored = t）✓；lint 0 ✓

#### 阶段区五个页签去掉内层滚动（只保留外层一条）+「过程资料」定名「研发资料」
- **用户反馈**：*"过程资料、交付件、阶段门、评审、检查规则，都有 2 条垂直的滚动条，只保留外层的滚动条；
  另外将过程资料修改成研发资料"*
- **① 双滚动条根因（浏览器探针逐页签实测定位，共三层内滚）**：
  · 表格类四页签：`MethodologyInstancePanel` 给 DataTable 传了 `:max-height="420"` → 表体固定高自滚
    （DataTable 的 maxHeight 默认本就是 undefined = 不限高）
  · 页签内容区：`.pl-stage-view .ant-tabs-content-holder { overflow-y: auto }`（.pl-stage-tabs 时代的滚动链，
    探针证实它确为"可滚者"）
  · 研发资料页签另有：右栏文档列表 `<DataTable stretch>` → **撑满模式**表体固定高自滚
    （实测表体 71px / 内容 151px）+ 左树 `max-height:560px + overflow-y:auto` +
    `.pl-doc-list { overflow-y:auto }` + `.pl-merged-section-files { max-height:220px }`
- **② 修复（统一为"只有外层 `.pl-dashboard` 一条滚动条"）**：
  · `MethodologyInstancePanel.vue`：去掉 `:max-height="420"`
  · `ProductLineDashboard.vue`：
    - `.pl-stage-view` 滚动链全放开（content-holder overflow / content height / tabpane height）
    - `.pl-pipeline { overflow: hidden → visible }`（内层不自滚后须允许内容自然增高，否则会被裁掉）
    - `.pl-folders-tree { max-height:560px }` 删除；`.pl-folders-tree-scroll` / `.pl-doc-list` /
      `.pl-merged-section-files` 的 `overflow-y:auto` / `max-height` 一并放开
    - 右栏文档列表 DataTable 去掉 `stretch`（"固定区 + 内滚"的旧设计，与"只留外层滚"正面冲突）
- **③ 用词**：「过程资料」→「研发资料」（页签 / 空态文案 / 区块注释 / 状态注释；设计器目录
  `widgets/catalog.js` 的组件描述同步）—— 内部标识 `key="docs"`、变量值 `stageView='docs'`、
  类名 `.pl-folders*` 全部保留
- **实测**（浏览器探针**逐页签点击**遍历，1080×560 小视口复现截图尺寸；测完已切回平台并复查）：
  · 修复前研发资料页签 1 个内滚（`ant-table-body`）；修复后 **五个页签全部「内层滚动容器：0 个」**，
    且 `.pl-dashboard：可滚(外层✓)` ✓
  · 页签栏显示「研发资料 | 交付件 (8) | 阶段门 (2) | 评审 (1) | 检查规则 (9)」✓；排版与内容正常 ✓
  · lint 0 ✓；「过程资料」残留仅剩 2 处**有意的定名沿革注释** ✓

#### 修复：研发资料创建 Part 弹「产品型号不存在」+ 未绑定分类时「分类」空区块（无 SQL 变更）
- **用户反馈**：①*"在研发资料中点击创建 Part 对象，提示产品型号不存在"*；
  ②*"如果未绑定分类的话（截图红框）分类区域不应该展示"*
- **① 报错根因**（`ResourceContainerSelect.vue` 解析"所属容器"显示名）：原实现**盲探** —— 先
  `GET /product-models/{oid}` 再 `GET /product-lines/{oid}`。容器实为**产品系列**时第一枪必 404，
  全局响应拦截器把后端消息原样弹出 → 用户看到「产品型号不存在」（随后虽 fallback 成功、容器名
  也能正确显示，但错误提示已经弹过）。修法：**按父组件已知的 containerType 精确请求**，且只走
  产品系列端点（后端读取编排为"系列优先、型号回退"，对型号 oid 也能返回）—— 一次请求覆盖两类
  容器、零 404；类型未知或文件夹容器不发请求，直接用可读兜底文案。
- **② 空区块根因**（`ClassificationBoundSelect.vue` + `DynamicForm.vue`）：「分类」字段 =
  `classification-bound-select`（仅可选类型绑定的分类）。组件自身的 `hideWhenUnbound` 只藏
  **自己**，外层 `Form.Item` 的 label「分类」与空白框仍留在表单里（探针实测：label 在、控件为"无"）。
  修法：`DynamicForm.loadLayout` 预查一次类型分类绑定（`getTypeClassificationSubtree`，与组件同一
  接口），**未绑定时把该字段整体剔除**（含 label；递归处理 layout-row / group 子字段）。
  只影响 DynamicForm（创建/编辑表单）；设计器预览不经此处且 `hideWhenUnbound=false`，展示不受影响。
- **实测**（浏览器 · CKAI-TECH 视角 · 开发阶段 · 「详细设计」文件夹；测完已切回平台并复查）：
  · 修复后：提示消息 `[]` / 失败请求 `[]`（零 4xx）；「所属容器」仍正确显示"X系列智能手表" ✓
  · 弹窗字段清单：编号,名称,版本,所属容器,所属文件夹,研发阶段,单位,来源,描述 —— **无「分类」** ✓
    （依次验证 部件 / 电气件 / PCBA 三个类型）；截图确认红框区块消失、表单其余完好 ✓
  · lint 0（DynamicForm / ResourceContainerSelect / ClassificationBoundSelect）✓
- **排查过程备忘**：探针须先切「开发阶段」再选文件夹，「新建零组件」按钮才出现
  （概念阶段不管理 PART）；`GET /classifications/platform-tree` 式的"疑似残留"勿再误判 —— 见上一条。

#### 修复：文档预览加载主文件时黑屏无提示 → 「内容加载中」遮罩（无 SQL 变更）
- **用户反馈**：*"文档相应页面，加载主文档时，应该有更好的体验，显示内容加载中...."*
  （截图：13.5MB 的 PPTX 打开后预览区是一片黑）
- **根因**（`DocumentViewer.vue`）：加载链分两段 —— ① 拉取文件 Blob（`officeLoading` 显示 spin ✓）；
  ② `vue-office-pptx` **解析渲染**（13.5MB 可达数十秒 ✗）。原实现在第 ① 段结束
  （`officeLoading = false`）就把唯一的 loading 提示关掉了，第 ② 段全程黑屏无提示 ——
  而 `@rendered` 事件（渲染真正完成）此前只是重复关一次 loading，白等一场。
- **修复**：新增 `contentLoading`（"数据已就绪、内容尚未渲染完"）状态：
  · Blob 就绪 → `contentLoading = true`，预览区上方出现**白色遮罩 + Spin + 文案**（遮住黑屏）
  · `vue-office` 的 `@rendered` 统一走新函数 `onOfficeRendered`（清超时 + 关两个 loading → 遮罩消失）
  · 所有结束路径（加载失败 / 解析失败 / 跳过预览 / PPTX >15MB 保护）均关闭遮罩
  · 文案统一：默认「内容加载中，请稍候...」；>10MB 时切换为「正在解析大文件 (13.5 MB)，请耐心等待...」；
    30s 兜底提示"建议下载查看"保留
  · 遮罩用 `.dv-content-mask`（`position:absolute; inset:0` 盖满预览区），`.dv-viewer` 加定位基准
- **实测**（浏览器 · CKAI-TECH 视角 · 开发阶段 · 详细设计 · 《市场调研报告》13.5MB PPTX；
  测完已切回平台并复查）：
  · 打开即见**白底遮罩**：「正在解析大文件 (13.5 MB)，请耐心等待...」✓（不再黑屏）
  · 25 秒后遮罩自动消失、PPTX 首页（蜂巢工软标题页）正常渲染 ✓
  · lint 0 ✓；Word/Excel 走同一套机制（同一函数/同一遮罩）✓
- **附**：文档详情入口 = 列表行「操作 → 详情」（`handleMixedAction` case `'view'`），排查时勿再误点名称。

#### 新功能：交付件 ↔ 产出物 关联（P0 落地 · 硬约束 + 跨容器引用 · 需增量 SQL）
- **由来**：用户问*"交付件也不完全是文档对象，比如原理图就是对应原理图对象等，是否需要为交付件绑定
  业务对象，且只在交付件管理页面可以关联本系列/型号的数据"* → 方案评审后用户拍板：
  **硬约束**（类别必须与模板一致）+ **允许跨容器引用**（资源库 / 其它型号的对象也能挂）
- **调查结论（决定方案"轻"的关键）**：类型定义早已存在 —— `ENG_DOCUMENT`（工程数据）下
  `SCHEMATIC 原理图 / PCB_LAYOUT / SYMBOL / FOOTPRINT`，`PART` 下 `STRUCTURAL / SOFTWARE / ELECTRONIC`；
  且 IPD46 模板的 `objectType` 值与类型 code **一一对应**（此前无人消费）。三张产出物表同构
  （oid / number / name / type_definition_code / container_oid / stage_oid）→ 一张关联表即可统一。
- **数据**：新表 `ck_deliverable_artifact_link`（deliverable_oid / entity_type(DOCUMENT|ENG_DOCUMENT|PART) /
  type_code / entity_oid / iteration_oid(留列) / role(PRIMARY，留列) / container_oid 快照 /
  UNIQUE(deliverable_oid, entity_oid, role)）；`schema.sql` 已收录
- **后端**：`DeliverableArtifactLink` 实体 + Mapper + PostgreSQL 实现 +
  `DeliverableArtifactLinkController`（list / summary / candidates / create / delete）
  · **硬约束**（服务端校验，前端不重复实现）：objectType 非空 → 只认该类型；为空 → bindMode
    落宿主级（DOCUMENT→文档 / PART_SET→零组件 / 其它→三类兜底）
  · 创建时服务端复核产出物存在性并取类型/容器快照；重复关联、类别不符均给中文 400
- **前端**：`DeliverableArtifactModal.vue`（约束展示 + 已关联列表 + 跨容器搜索 + 关联/解除）+
  `MethodologyInstancePanel` 交付件面板加「产出物」列（按宿主开关显示，显示已关联数、点击开弹窗）+
  `ProductLineDashboard` 接线（show-artifacts + summary 同批加载）—— **写入口唯一**：
  只在交付件管理页；文档/对象详情只读回显（后续接）
- **踩坑记录（重要）**：`TenantStatementInterceptor.rewriteWhere` 把租户条件插在「第一个
  CLAUSE_BOUNDARY（RETURNING/ORDER BY/GROUP BY/LIMIT/UNION…）之前」—— 首版列表 SQL 用了
  UNION 子查询拼产出物名称，拦截器把条件拼进子查询 → `FROM ck_document AND l.tenant_oid=…` 语法错。
  已改为**三个 LEFT JOIN + COALESCE**（无子查询、无 UNION），并把规则写进 Mapper 注释：
  业务 SQL 里不要在 WHERE 之前出现 UNION/ORDER BY 等词，除非清楚拦截器的插入位置。
- **实测**（浏览器 + API 双轨；CKAI-TECH 视角，测完已切回平台并复查）：
  · API 10 步全过：候选三场景（EBOM→2 条零件 ✓ / SCHEMATIC→**0 条**（库里无该类型，硬约束铁证）/
    市场评估报告→1 条文档 ✓）；合法关联 200 ✓；**硬约束拒绝**「必须是零组件」✓；重复关联 400 ✓；
    列表带编码/名称 ✓；汇总 1→解除后 {} ✓
  · UI：行内「0 项」→ 弹窗（约束「零组件」+ 候选含【本型号】/【其它容器】标注）→ 点关联→
    提示「已关联」+ 已关联列表出现 → 关闭后行内计数刷新为「1 项」✓（截图确认）
  · 编译 0 / ERROR 0 / 启动 ✓；前端 lint 0 ✓
- **增量 SQL（现有库需执行）**：
  ```sql
  CREATE TABLE IF NOT EXISTS ck_deliverable_artifact_link (
      oid CHAR(36) PRIMARY KEY, deliverable_oid CHAR(36) NOT NULL, entity_type VARCHAR(30) NOT NULL,
      type_code VARCHAR(50) NOT NULL, entity_oid CHAR(36) NOT NULL, iteration_oid CHAR(36),
      role VARCHAR(20) NOT NULL DEFAULT 'PRIMARY', container_oid CHAR(36), tenant_oid CHAR(36),
      creator VARCHAR(100), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updater VARCHAR(100), updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
  CREATE INDEX IF NOT EXISTS idx_dal_deliverable ON ck_deliverable_artifact_link(deliverable_oid);
  CREATE INDEX IF NOT EXISTS idx_dal_entity ON ck_deliverable_artifact_link(entity_type, entity_oid);
  CREATE UNIQUE INDEX IF NOT EXISTS uk_dal_triple ON ck_deliverable_artifact_link(deliverable_oid, entity_oid, role);
  ```
- **未做（后续期）**：P1 模板配置页展示 objectType/bindMode + objectType 预选；P2 `BOUND`/`LIFECYCLE`
  求值（交付件状态自动算 → 门 DELIVERABLE_RATE）；P3 工程数据详情页（关联后点得进去）+ 文档/对象详情
  的只读回显。**注意**：模板里 `DLV-SCHEMATIC` 等 bindMode 写的是 DOCUMENT、objectType 为具体类型 ——
  当前实现 objectType 优先（硬约束到 SCHEMATIC）；若某交付件希望"挂导出文档"而非对象本身，
  按用户决策属预期行为（可改模板 objectType 调整）。

#### 新增行业包「开源版」（open-source）：只继承 platform-base，用于验证能力开关（无 SQL 变更）
- **用户要求**：*"在 industry-packs 下构建一个开源版，且只有 platform-base 的能力，以便我注册一个新租户，
  测试功能开关是否生效"*
- **调查结论（决定了实现极轻）**：`OnboardingController.listIndustryOptions()` 遍历包目录，
  **凡同时声明 `industry` 与 `devMethod` 的包即为一个可选行业** —— 因此只加包文件，**零 Java/前端改动**。
  另查明：`CapabilityController` **只有读侧**（无开关页），能力落账由 `IndustryPackEngine.applyCapabilities`
  按 `default` 决定，且 `default:false` 只记「跳过」**不写账本**；可落账的能力码以
  `PlatformCapabilities` 白名单为准（当前 12 项已实现）。
- **交付**：`src/main/resources/industry-packs/open-source/v1.0.0/` 下两个文件
  · `pack.json`：`basePack: platform-base@1.0.0`、`industry/devMethod: OPEN_SOURCE`、
    `resources: []`（不带任何行业资源）、`license: OPEN_SOURCE`
  · `capabilities.json`：登记**全部 12 项已实现能力**，**default 全为 false**（每项 note 写明开启后
    会出现哪个入口，便于对照验证）
  → 装完效果：租户只有平台级基础设施（生命周期/编码/版本规则/单位/视图/资源库树/通用类型），
    `ck_tenant_capability` 本租户**一行都没有**，所有受能力门控的菜单与页签如实隐藏
- **实测**（重启后端 + API 探针；干跑预演 `planOnboarding` 一行不写）：
  · 日志 `已加载行业包: open-source@1.0.0`（资源 0 个，能力 12 项）✓
  · `/api/onboarding/options` → 可用行业 5 个，`OPEN_SOURCE` 排首位、enabled=true、
    capabilityCount=12、能力清单 12 项全部「默认关」✓
  · `/api/onboarding/plan?industryCode=OPEN_SOURCE&devMethodCode=OPEN_SOURCE` →
    写 3 / 跳 111 / 错误 0；platform-base 写 3 跳 99；open-source **写 0 跳 12（CAPABILITY 全 SKIP）**✓
- **踩坑**：首版 `pack.json` 的 note 里用了半角双引号（`"干净基线"`），字符串提前结束 →
  `解析行业包失败 … Unexpected character ('å')`，包被静默跳过、选项里查无此包。JSON 长文本的引号
  一律用「」，别用半角 `"`。（加载器对该错误有 ERROR 级日志，排查看 `行业包` 关键字。）
- **如何验证「开关」**（当前平台无开关页，用 SQL 即可；`tenant_oid` 换成新租户 oid）：
  ```sql
  -- 开启（示例：MPN 管理 → 左侧出现「MPN 厂商」菜单、零组件详情出现 MPN 页签）
  INSERT INTO ck_tenant_capability (oid, tenant_oid, capability_code, capability_name, enabled, source_pack)
  VALUES (gen_random_uuid()::text, '<新租户 oid>', 'MPN_MANAGEMENT', 'MPN 管理', true, 'open-source@1.0.0');
  -- 关闭 / 再打开（刷新页面即生效：读侧 API → useCapabilities → 各页面 v-if）
  UPDATE ck_tenant_capability SET enabled = false WHERE tenant_oid = '<新租户 oid>' AND capability_code = 'MPN_MANAGEMENT';
  UPDATE ck_tenant_capability SET enabled = true  WHERE tenant_oid = '<新租户 oid>' AND capability_code = 'MPN_MANAGEMENT';
  -- 看本租户账本
  SELECT capability_code, enabled, source_pack FROM ck_tenant_capability WHERE tenant_oid = '<新租户 oid>' ORDER BY capability_code;
  ```
  能力码取自本包 `capabilities.json`（12 项）。注意 `CERTIFICATION_MANAGEMENT / SOFTWARE_VERSION /
  ISSUE_MANAGEMENT / CHANGE_MANAGEMENT` 平台未实现，插了也不会出现入口。
- **未做（如需要请说）**：能力**写侧**（开关页 / `PUT /api/capabilities/{code}`）—— 有了它就能在界面上直接
  开关而不用 SQL；当前按"用户只要测试环境"实现，未擅自加。

#### 修复：产出物关联弹窗「搜索没反应／看似搜不出对象」（无 SQL 变更）
- **用户反馈**：*"输入编码和名称后，没有搜索出对应的对象"*（截图：搜索框已填 `DOC-202610-0001`，
  候选区一片空白；而库中确有该文档）
- **实测结论（先说事实）**：**链路本身是通的** —— API 层（6 种关键词：完整编码 / 片段 / 中文名 /
  小写英文，全部命中 1 条）与浏览器层（含逐字符输入）都验证正常。用户截图那帧是**候选请求返回前**
  或旧前端缓存：当时既无「搜索中」提示、结果区又不占位 → 弹窗高度塌缩，看起来像"什么都没搜出来"。
- **体验加固（DeliverableArtifactModal.vue）**：
  · 候选区固定最小高度（`.dam-cand-box` min-height 150px，空态垂直居中）—— 不再高度塌缩
  · 加载中显式提示「搜索中…」（原来 `a-spin` 包着空内容，图标几乎看不见）
  · 空结果给明确文案（原来是空白）：有关键词时"没有匹配的产出物（或它不属于本交付件被约束的类别）"
  · 防抖 350ms → 250ms；并加**请求序号**（`candSeq`）—— 输入较快时先发的请求可能后到，
    旧结果会盖掉新结果，现在只认最后一次响应
- **实测**（浏览器 · CKAI-TECH · 开发阶段 · DLV-EVT-REPORT）：无关键词 → 1 条、候选区高 150px ✓；
  逐字符输入 `ZZZ-NOT-EXIST` → 0 条 + 空态文案 ✓；逐字符输入 `DOC-202610-0001` → 1 条
  （文档 | DOCUMENT | DOC-202610-0001 | 市场调研报告 | 本型号 | 关联）✓；lint 0 ✓
- **给使用者的提示**：若仍看到旧表现，先 Ctrl+F5 硬刷新一次（前端有缓存时旧包会继续跑）。

#### 交付件面板：删「工期偏移」列 + 状态自动判定（P3 首版 · 无 SQL 变更）
- **用户要求**：*"工期偏移不用展示了，状态需要更新，如果绑定了对象修改更新状态，另外，如果绑定的对象
  进入流程中也需要更新状态"*
- **① 「工期偏移」列删除**（`MethodologyInstancePanel.vue` 的 deliverable 列）—— 字段仍在库里，
  只是不在面板露出
- **② 状态自动判定（新增 `DeliverableStatusEvaluator`）**，判定规则（写在类注释里）：
  · `NA`（不适用）= 人工决定，**永不覆盖**；
  · **无产出物关联** → `PENDING`（未开始）；
  · **有产出物关联** → 至少 `IN_PROGRESS`（进行中）—— 同时覆盖"绑定了对象"与
    "绑定对象进入流程"（在流程中同样是进行中，且明确阻止 DONE）；
  · **完成条件全部可判定且满足** → `DONE`（已完成）：`BOUND`（有关联即满足）、
    `LIFECYCLE(minState)`（全部关联对象的**最新迭代**状态 ≥ minState，如"已评审"→REVIEW）；
    `REVIEW_PASSED / ISSUE_CLOSED / FIELD_CHECK / COVERAGE` 平台尚未实现 → 视为**不可判定**，
    **阻塞 DONE 但不阻塞 IN_PROGRESS**（宁缺勿滥：不假装完成，也不把"未完成"错报成"未开始"）
  · 关联对象"当前大版本有流程在跑"的判定**复用流程侧 `ProcessEntitySetService.findRunningEntityOids`**
    —— 与「发起流程」闸门口径天然一致（大版本粒度、挂起仍算在跑、只在租户内）
- **求值时机（惰性，不做定时任务）**：`GET /api/instances`（交付件面板数据源）返回前重算并写回；
  产出物关联的**创建 / 解除后立即重算**（刚操作完列表就该是新的）。判定失败只告警、按库中现值返回，
  绝不因此打不开面板
- **连带改动**：`DeliverableMapper.updateStatus`（本表原先无 update）；`DeliverableArtifactLinkMapper`
  加 `selectByOwnerOid` 与三类"最新迭代状态"查询（`ck_*_iteration` 的 `latest = TRUE`，
  状态存的是码 DRAFT/INWORK/REVIEW/APPROVED/RELEASED）；前端关联弹窗 `@changed` 后
  **同时刷新状态列与计数**（`onArtifactChanged` → `loadArtifactSummary + loadInstances`），
  避免"产出物 1 项、状态还是未开始"的割裂；面板提示语同步更新（不再写"自动判定待 P3"）
- **实测**（编译 0 / 启动 ✓；API + 浏览器双轨；CKAI-TECH 视角，测完已切回平台并复查）：
  · API：46 条初始全「未开始」→ 绑定（EBOM ← I-WATCH-X7）→ **EBOM=进行中** ✓ →
    解除 → **EBOM=未开始** ✓；最终分布 `{未开始:45, 进行中:1}` —— 那 1 条是存量关联
    （EVT 工程验证报告 ↔ I-WATCH-X7）被自动判成进行中 ✓（存量同样生效）
  · UI：列头 = 编码/名称/专业/强制/预期角色/阶段门/完成条件/产出物/状态（**无工期偏移** ✓）；
    DLV-EVT-REPORT 行状态显示「进行中」✓、其余未绑定的「未开始」✓；lint 0 ✓
- **未做（如实）**：`REVIEW_PASSED`（需评审实例↔交付件关联）/ `ISSUE_CLOSED`（无问题表）/
  `FIELD_CHECK` / `COVERAGE` 四类子项的求值 —— 在实现前，含这些子项的交付件**不会自动判"已完成"**；
  待办（P3 后续）：门按交付件完成率自动判定（`DELIVERABLE_RATE`）。

#### 产品系列 / 型号：按团队可见性过滤（不在团队中不可见 · 无 SQL 变更）
- **用户要求**：*"如果用户不在系列/型号的团队中，不应该可见对应的系列和型号记录"*
- **规则**（新增 `ProductTeamVisibility`，类注释即规则）：
  · **管理员豁免**：`PLATFORM_ADMIN / TENANT_ADMIN / ADMIN` 看全部 —— 否则"没人能加成员"死锁；
    取不到用户/角色时按**非管理员**处理（宁严勿松）
  · **系列**：我是该系列团队成员 → 可见
  · **型号**：我是该型号团队成员，**或**我是其父系列 / 祖先系列团队成员 → 可见
    （与 `ProductModelServiceImpl#getTeamMembers` 的"继承父级团队"口径一致）
  · **祖先补齐（仅用于树）**：子系列可见而父系列团队里没有我时，父系列仍进可见集 ——
    否则树里出现"没有父节点"的孤儿、导航会断
  · **空团队**：团队里没有我 → 不可见（严格口径）；创建者会自动入团队，正常创建的记录不会消失
- **过滤点**（Service 读方法，前端与下游零改动）：`ProductLineServiceImpl` 的
  findAll / search / findTree / findLinesOnlyTree + 树递归里的子系列与型号节点；`ProductModelServiceImpl`
  的 findAll / findByProductLineOid / search。新增 `TeamMemberMapper.selectTeamOidsByUserId`
  （按 username 查我加入的团队）
- **实测**（编译 0 / 启动 ✓；**完全可逆的受控验证**，测完已回滚并复查）：
  · 测试对象：`Pengy.Y`（无任何角色 ✓ 且**不在** X系列智能手表研发团队 ✓）；
    对照组数据：该团队成员 = Kiween.Hu（硬件主管）+ Roney.Liu（产品经理）
  · **不在团队** → `GET /api/product-lines` → **0 条（空列表）** ✓✓（库里确有该系列）
  · **临时加入团队** → 同接口 → **1 条：X系列智能手表(i-watch-x)** ✓✓✓（立即可见）
  · **回滚**：删除临时成员 ✓ 密码恢复（长度校验回 69，与原值一致）✓ 团队回到 2 人 ✓
  · 密码借用方式：临时把 sysadmin 的密码哈希覆盖到 Pengy.Y（其原哈希已备份，
    经 `restore-pwd.sql` 原样写回）—— 敏感备份文件用后即删
- **须知（未擅自改的口径）**：**平台管理员跨租户时也会被过滤** —— 角色按租户隔离，
  `sysadmin` 切到某企业租户后在该租户内既无角色、通常也不在团队，因此看不到该租户的系列/型号。
  如需"平台管理员跨租户可看全部"，说一声我再加豁免（改动很小：判定时额外放行平台租户的
  PLATFORM_ADMIN）。
  → **2026-10-12 用户已确认：保持现状（不加跨租户豁免）**。因此平台管理员若要查看某企业租户的
  系列/型号，两条路：① 用该租户的管理员账号登录；② 把 sysadmin 加入对应团队（`ck_team_member`
  一行）。这是有意保留的口径，不要再当缺陷修。

#### 修复：企业资源页"只剩 3 个页签、有数据的库集体消失" —— 能力账本只读一次、失败永不重试（无 SQL 变更）
- **用户反馈**：*"企业资源页面是不是没有按租户隔离的方式展示，Roney.Liu 对应的租户资源库中有数据"*
  （截图：企业资源页只有「技术文档知识库 / 产品图册 / 其他」3 个页签，首个页签是通用占位）
- **排查结论（三层证据，先排除"隔离失效"）**：
  · **隔离本身正常**：`GET /resource-containers/children` 实测返回的记录逐条 `tenantOid=1a2a2379…`
    （CKAI-TECH）+ `creator=Roney.Liu`；截图页脚"创建 2026-10-11 19:01:55"与该租户 TECH_DOC 的
    `created_at` **逐秒吻合**（平台租户同名记录是 10-02 16:02）—— 页面看到的确实是本租户自己那份
  · 截图**缺的** 4 个页签（元器件 / 标准件 / 通用件 / 封装·图符）+ MPN 管理，恰好等于
    "**能力集为空**"时的可见集合（不受能力控制者只剩技术文档 / 产品图册 / 其他；且默认激活
    第一项 = 技术文档知识库，与截图一致）—— 两个特征同时命中
  · 该租户 `ck_tenant_capability` 实测 **11 条全 enabled**；换用同一租户视角打开页面，
    页签是 **7 个资源库 + MPN 管理**，元器件库里有数据（PART-202610-0001）
- **根因**（`composables/useCapabilities.js`）：模块级单例把请求 Promise 存进 `pending` 后
  **永不清理** —— 首次请求若失败（后端重启窗口、装包正写账本时的瞬态、网络抖动），`codes`
  永久为空集，界面把所有受能力控制的入口全藏掉，**只能整页刷新才恢复**（SPA 内导航一律
  短路在同一失败 Promise 上；企业资源页自身的 `loadCapabilities()` 也救不回来）。
- **修复**：
  · `useCapabilities.load()`：只有"成功拿到账本"才缓存 Promise；请求失败 / 响应异常
    （非 200、非数组）都**清掉 pending**，允许后续 `load()` 重试；`loaded` 语义改为"**成功**加载过"
  · `MainLayout`：首加载未成功时 **3 秒后自动重试一次**（兜住后端重启窗口）
- **实测**（浏览器 · CKAI-TECH 视角 · 用 route 拦掉**首次**能力请求返 500；测完已切回平台并复查）：
  · 请求序列 `拦截并返回500 → 收到响应 500 → 放行 → 收到响应 200` ✓（旧代码不会有第二次请求）
  · 页签恢复「元器件库 | 标准件库 | 通用件库 | 封装·图符库 | 技术文档知识库 | 产品图册 | 其他 |
    MPN 管理」✓，默认落在**有数据的元器件库** ✓（截图确认：分类树 + PART-202610-0001 列表）
  · lint 0 ✓
- **附**：「技术文档知识库（TECH_DOC）」目前是**通用占位页**（"该子库暂未配置专属页面" —— 前端
  有意留的自定义资源库入口），与该租户下该库当前 0 条文档无关；若要给它做专属资料页另开需求。

#### 修复：页面设计器读不到本租户布局（空表单）—— 初始化器跨租户"自愈"污染 entity_oid + 读取改按类型编码（一次性数据修复，无 schema 变更）
- **用户反馈**：*"类型定义中页面布局没有读取到本租户的布局信息，当前布局也已经隔离了，
  在行业配置包选择后初始化对应的数据，故这里直接获取本租户对应 entity_Code 的操作码布局"*
  （截图：业务配置 → 页面设计器，文档 / 新建页，画布空）
- **根因（三层，先排除"隔离失效"）**：
  · **数据被污染**：`PageLayoutInitializer.ensureLayout`（应用**每次启动**都跑）的"幂等 + 自愈"
    查询 `WHERE entity_code=? AND operation_code=?` **漏了 tenant_oid** → `rows.get(0)` 命中
    **租户行**时，把它改挂到**平台的类型 oid**（实测：CKAI-TECH 的 DOCUMENT 等 12 行被改成
    平台 oid `9e00f799…`，而本租户 DOCUMENT oid 是 `41fa4c56…`）→ 设计器按本租户类型 oid 查
    → 查不到 → 空表单
  · 同函数第 1 步"当前租户优先"排序用的是 `TenantContext.get()`（启动时无上下文 → 默认租户），
    同样在跨租户取数；`clonePartLayoutToSoftTypes` 的 PART 布局查询与幂等 count 亦无租户条件
  · 读取口径（设计器按 `entityOid` 取）对存量脏行**零容错**——正是用户指出要改的点
- **修复**：
  · `PageLayoutInitializer`：`ensureLayout` 第 1 步显式用 `PLATFORM_TENANT_OID` 解析类型 oid；
    第 2 步"自愈"查询加 `AND tenant_oid = ?`（**自愈只能治自家的病**）；
    `clonePartLayoutToSoftTypes` 两处查询同样加平台租户条件
  · `PageLayoutServiceImpl.saveOrUpdate`：定位既有行改为「entity_code + operation_code + 本租户」
    （与读取口径一致），找到即顺手把 `entity_oid` 对齐——存量脏行的自愈点，且避免"按 oid 查不到
    → 误插第二份"（同租户同操作两行）
  · `PageDesigner.vue`：`loadLayoutForOp` 改用 `getPageLayoutByCode(entityCode, operationCode)`
    （`GET /page-layouts/by-code`，where 含 `tenant_oid`）——即用户指定的"按 entity_Code 取本租户布局"
  · **数据修复（一次性）**：删 4 条僵尸行（`entity_oid` 指向**已不存在**的平台 PCBA 旧 oid；
    表上有 `(entity_oid, operation_code, tenant_oid)` 唯一约束，先删才能对齐）；把 12 条租户行
    （CKAI-TECH 的 DOCUMENT / PRODUCT_LINE / PRODUCT_MODEL）`entity_oid` 对齐到本租户类型 oid；
    复查「不一致 0 行 / 重复 0 组」
- **实测**（浏览器 · CKAI-TECH 视角 · 设计器 · 文档类型；测完已切回平台并复查）：
  · 接口：`200 /page-layouts/by-code?entityCode=DOCUMENT&operationCode=list` ✓（原为按 oid 的请求）
  · 「新建页」空提示次数 **0**（修复前整块空表单）✓；画布含 编号 / 名称 / 描述 / 版本 ✓；
    操作下拉标「**本租户**」✓；截图确认表单完整渲染（含 所属研发阶段 / 所属文件夹 / 主文档 / 附件上传）✓
  · 后端已重启使新代码生效；重启日志 `OOTB 默认页面布局初始化完成: 新增 0 个, 已存在 16 个`、
    **无任何跨租户自愈动作** ✓；重启后 DB 复查 `stale_rows = 0` ✓（污染不再复发）
  · `mvn compile` 0 错 ✓，前端 lint 0 ✓
- **运维备忘**：本地启动后端需 `DB_PASSWORD=123456` 环境变量（`application.yml` 的 password 取
  `${DB_PASSWORD:}`，空密码时 SCRAM 认证直接失败）；`application-local.yml` 里也备着一份可用的
  本地 datasource（需 `--spring.profiles.active=local`）。

#### 核实（无需改动）：DynamicForm 加载创建/编辑表单已按【本租户】取 pageLayout
- **用户要求**：*"在 dynamic 加载时要获取本租户的 pagelayout 布局"*（截图：研发资料 → 新建文档弹窗）
- **核实（浏览器实测 · CKAI-TECH 视角 · 研发资料 → 新建文档 → 选「文档」类型；测完已切回平台并复查）**：
  · 请求：`GET /page-layouts/by-code?entityCode=DOCUMENT&operationCode=create`
  · 响应：`tenantOid = 1a2a2379…`（本租户 ✓）；`form.fields` = 文档编号 / 文档名称 / 文档类型 /
    描述 / 所属研发阶段 / 所属文件夹 / 所属产品 / 主文档 / 附件上传
  · 弹窗渲染字段与响应**逐个一致（9/9）** ✓
- **链路三环均为"只本租户"**（与 2026-10-10 起的隔离口径一致，无需再改）：
  · 类型树 `GET /type-definitions/tree` → `selectAll(本租户, …)`（平台那份不并入）
  · 布局读取 `GET /page-layouts/by-code` → `WHERE entity_code + operation_code + tenant_oid`
  · 回退链：`DynamicForm` 的 `fallbackEntityCode`（如 DOCUMENT）同样走 by-code → 仍是本租户；
    **不会**退化到平台布局
- **本租户缺该类型该操作布局时的行为（有意）**：显示「未配置表单布局，请在页面设计器中设计」，
  **不**跨租户兜底。
- **备注**：若希望"缺布局时自动从本租户的父 / 根类型布局克隆一份"（对齐设计器的克隆语义），
  属体验增强项，待用户确认后再做。

#### 修复：业务配置 → 资源库配置三个页签没有垂直滚动条（内容被裁）（无 SQL 变更）
- **用户反馈**：*"资源库分类绑定、通用件阈值配置、标准件入库流程页签没有垂直的滚动条"*
  （截图：通用件阈值配置，底部"认定流程模板"被裁掉、滚不动）
- **根因（两层"固定高度 + 裁剪"叠加）**：
  · `BusinessConfig.vue` 的 `.bc-embedded-page { height:100%; overflow:auto }`
  · 子页 `ResourceLibraryCategoryConfig.vue` 的 `.clcc-page { height:100%; min-height:0; overflow:hidden }`
  → 子页被压成"固定 100% 高、超出即裁"；外层因内容"恰好"不溢出，连它自己的 `overflow:auto`
  也不出条 —— 整页一条滚动条都没有、底部永远够不着。
- **修复**：两处均改为**自然高度**（去掉 `height:100%` 与 `overflow`），滚动统一交给
  `.bc-content` 的那一条滚动条（与全站"只留外层一条"口径一致）。
  引用面已确认：`ResourceLibraryCategoryConfig` 仅被 `BusinessConfig` 引用，改动无外溢；
  `datamodel` 的 TypePage 走专属 `.bc-page-fill` 规则，不受影响。
- **实测**（浏览器 · CKAI-TECH 视角；测完已切回平台并复查）：
  · 1080×560（复现截图尺寸）：页签1 `445/375` 可滚 ✓、页签2 `853/375` 可滚 ✓、页签3 `487/375` 可滚 ✓；
    **三个页签"滚到底后末块可见"全部 = true** ✓（文本收尾均见"说明 保存配置"）
  · 1440×900：页签2 可滚（`853/715`）✓；页签1/3 内容本就矮（面板 311 / 401 < 715）不出条 ✓ 属正常
  · 截图确认：小视口滚到底可见 启用开关 / 入库流程模板下拉 / 说明 / 保存配置 ✓
  · lint 0 ✓

#### 文案：「企业资源 → MPN 管理」页签定名「MPN 库」（无 SQL 变更）
- **用户要求**：截图红框 *"MPN管理修改成MPN库"*
- **改动**：唯一显示名 —— `EnterpriseResource.vue` 的 `tab="MPN 管理"` → `tab="MPN 库"`；
  同步 6 处前端注释（EnterpriseResource / MpnManagement / MasterDataPage / ManufacturerPanel /
  PartDetail / MainLayout）中"指代该页签"的写法
- **有意保留**：
  · **能力名**仍是「MPN 管理」（`MPN_MANAGEMENT`，能力账本的 `capability_name` 与后端能力说明沿用，
    与页签显示名是两回事）
  · 内部标识：`key="__mpn"`、组件名 `MpnManagement`、`api/index.js` 的 API 分组注释 —— 均不动
    （与本项目"改显示名不牵动内部标识"惯例一致，如 `path: resource` 显示「企业资源」）
  · 导航页「**MPN 厂商**」（master-data）不受影响
- **实测**（浏览器 · CKAI-TECH 视角；测完已切回平台并复查）：
  · 页签：`元器件库 | 标准件库 | 通用件库 | 封装·图符库 | 技术文档知识库 | 产品图册 | 其他 |
    MPN 库 | 制造商 | 供应商 | AML · AVL`
  · `含[MPN 库]: true`、`含[MPN 管理]: false` ✓；「MPN 库」内子页签（制造商 / 供应商 / AML·AVL）
    与列表数据不变 ✓；截图确认（TI 德州仪器 / Samsung 三星 两行照常）
  · lint 0 ✓

#### IPN（电子元器件）绑定电子行业生命周期模板「COMPONENT_CODE」+ 草稿态挂「元器件引入流程」（含一次性数据修复，无 schema 变更）
- **用户指令**：*"执行，同时修复的时候需要给 Electronic 类型的草稿状态绑定 component_introduction 流程"*
- **① 包声明（治本 · 未来装包即得）**：
  · `electronics-core/softtypes.json`：ELECTRONIC 的 `lifecycleTemplate` 由 `STANDARD` → **`COMPONENT_CODE`**
    （「元器件内部编码生命周期」：草稿 → 审核中 → 已发布 → NRND 不推荐新设计 → 已下线 / 已被替代 → 已归档；
    7 态 10 流转 —— NRND / SUPERSEDED 正是本包为电子行业补的口径）
  · `lifecycles.json` 的 note 同步：原"本模板【未绑定任何对象类型】"的悬空状态到此结束；
    记存量迁移结论 + "状态 → 流程"绑定属各租户运行配置
- **② 存量数据修复（一次性，三处）**：
  · **模板绑定**：平台 + CKAI-TECH 的 ELECTRONIC → COMPONENT_CODE（各 1 行）
  · **存量对象迭代**：CKAI-TECH 3 条 IPN 迭代的 `lifecycle_template_iteration_oid` → 该租户 COMPONENT_CODE
    迭代（状态全为 DRAFT，与新模板初始态一致 → **无需任何状态映射**；另有 1 条 `PART` 类型迭代**有意不动**）
  · **状态 → 流程绑定**（`ck_type_lifecycle_state_process_link`，此前 0 行）：平台 + CKAI-TECH **各插一行** =
    `ELECTRONIC ×〈本租户 COMPONENT_CODE 迭代〉× DRAFT → component-introduction`
    ★ 关键点：**类型与流程模板都按租户隔离、oid 各不相同**，服务注释里"平台行作共享默认"只在
    "类型共享"时代成立 —— 实际必须**逐租户各配一行**（已按此落）
- **③ 验收（接口级 · CKAI-TECH · IPN「200欧电阻」DRAFT 迭代）**：
  · `GET /workflow/instance/start-options?typeDefinitionCode=ELECTRONIC&statusCode=DRAFT&…` →
    `startable=true`，`processTemplate.key = component-introduction`（元器件引入流程，v2 已部署本租户）✓
  · 对照 `statusCode=APPROVING` → `startable=false` + 引导语（"该类型在状态「APPROVING」下未绑定流程模板…"）
    ✓ 精准反证"只绑了草稿态"
  · 该流程的行业内涵（其描述原文）：需求与选型初审 → 寻源与样品 → 来料检验 → 样品验证（电气 + 可靠性会签）
    → 小批量试产验证 → 认定评审（会签，一票否决）→ 入库与 AVL 发布 ✓
  · 数据库复查：模板绑定 2 行 ✓ 状态-流程 2 行（`process_key=component-introduction` ✓）迭代指向 3 条 ✓
- **附**：包 json 改动已 `mvn compile` 同步到 `target/classes`（**未重启服务** —— 存量已由数据修复对齐，
  声明项对未来装包 / 下次重启生效）；本次无 schema 变更
- **运维提示**：其它状态（APPROVING / PUBLISHED …）今后要挂流程，可在「业务配置 → 模型定义 → 类型 →
  生命周期模板 / 状态-流程」面板自助配置 ✓

#### 扩展：包安装器支持「类型 + 生命周期状态 → 流程模板」声明（softtypes.json 的 lifecycleStateProcesses）
- **用户反馈**：*"看到 softtype.json 中 ELECTRONIC 没有配置草稿状态绑定 Component_Introduction 流程"*
  —— 上一轮只落了存量数据；包声明里确实没有（当时安装器尚无此能力，现补齐）
- **声明形状**（softtypes.json 的类型条目上）：
  ```json
  "lifecycleStateProcesses": { "DRAFT": "component-introduction" }
  ```
  · 键 = 生命周期状态码（须在该类型所绑模板的**当前子版本**里，否则该条 FAILED）
  · 值 = 流程模板 key（**宽松匹配**：忽略大小写、`_` 与 `-` 等价 —— 写 `Component_Introduction`
    也能命中 `component-introduction`）
- **实现**（`SoftTypeInstaller` 第五绑，与 `pageLayouts` 同思路）：
  · 新增 `installStateProcesses(...)`：放在 **types 段之后**、按 code **从库里回查类型** →
    本次新建与早已存在的类型都能轮到（**重装本包即补齐**）
  · 解析链：类型 → 所绑模板（`lifecycleTemplate`）→ **最新子版本** → 校验状态在版本内 →
    按 key 解析流程模板（**并校验其归属租户**，防跨租户误绑）→ 幂等判重（按 (类型, 子版本, 状态)
    只认**本租户**行）→ 写入
  · **预演也走完整解析链**（先解析、后判重，最后才分"预演 / 真装"）→ "key 拼错 / 流程未装"
    在预演报告里就暴露；action 标签如实（已存在 → SKIP"真装将跳过"，待新增 → INSERT"将为…绑定"）
  · 新增 4 个注入：`TypeLifecycleStateProcessMapper` / `LifecycleTemplateMapper` /
    `LifecycleTemplateIterationMapper` / `ProcessTemplateMapper`
- **与 PROCESS 资源的顺序（如实交代）**：pack.json 里 PROCESS 声明在 SOFT_TYPE **之后** →
  **首次装包**该条记 pending（不假装已就绪），**重装本包**（引擎口径：幂等、只补缺失项）即绑定成功；
  已在 `lifecycles.json` 的 note 写明
- **实测**（浏览器 · CKAI-TECH 视角 · **dry-run 零写入**；测完已切回平台并复查）：
  · `GET /api/industry-packs/electronics-core/1.0.0/plan` → 报告中出现
    `{"resourceType":"SOFT_TYPE","resourceKey":"ELECTRONIC/DRAFT","action":"SKIP",
      "message":"预演：本租户已有该状态→流程配置，真装将跳过（不覆盖）","ready":true}`
    ✓ 一条即证：**声明被解析** ✓ **状态校验通过** ✓ **流程 key 解析成功**（否则会 pending）✓
    **幂等生效** ✓
  · `mvn compile` 0 错 ✓ json 双文件语法校验 OK ✓ 后端已重启（18s）✓
- **附**：本次**未真装**（dry-run 足以验证；真装的写入路径与既有绑定同表同幂等语义，
  且库里两租户的绑定已在上一轮落好）

#### 修复（含一次自我回归修正）：业务配置 → 生命周期状态清单"表格塌成一两行"（无 SQL 变更）
- **用户反馈**：*"生命周期状态清单没有获取本租户的状态清单"*（截图：表格只显示 2 行、下方大片空白；
  顶部"共 8 条"，来源列全标"本租户"）
- **排查结论：数据无罪**（先排除取数问题）：
  · `GET /lifecycle-statuses` 实测返回 **8 条、逐行 `tenantOid = 1a2a2379…`（本租户）** ✓
  · 库侧核对：平台 8 条 / CKAI-TECH 8 条 / 开源社区 6 条（未装 electronics-core）—— 隔离与内容都对 ✓
  · 「来源」列按 `tenantOid === 平台 oid` 判断 → 截图全标"本租户" ✓ 与响应一致
- **真因：一次自我回归** —— 同日先修「资源库配置页没有滚动条」时，把 `.bc-embedded-page` 的
  `height:100%` 一并删成"自然高度"；而**撑满型**子页（生命周期 / 视图定义 / 单位配置…：根类自带
  `height:100%`、表格用 DataTable 的 **stretch** 模式）因此失去确定的高度参照 →
  表格塌成一两行（1440×900 下表格容器只剩约 90px；1080×560 下表体仅 62px）
- **修复（两处，让两类页面各得其所）**：
  · `BusinessConfig.vue`：`.bc-embedded-page` 恢复 `height: 100%`（**不带** overflow）——
    撑满型页面拿到高度、表体内部滚动条生效；自然高型页面（资源库配置）内容溢出到 `.bc-content`，
    仍由它那条外层滚动条兜住（**上一轮的修复同时保住**，已回归验证）
  · `LifecycleConfig.vue`：`.lc-table-wrap` 加 `min-height: 320px` 保底 +
    `.ant-tabs-content-holder` 由 `overflow:hidden` 改 `visible`（矮视口抬起的内容放行给外层，不被裁）
- **实测**（浏览器 · CKAI-TECH 视角 · 两种视口；测完已切回平台并复查）：
  · 1440×900：生命周期页可见行数 **8/8** ✓ 表格容器 545 ✓ 表体 392/392（全展开）✓
  · 1080×560（复现截图尺寸）：wrap 320（保底生效）✓ 表体可视 **62 → 177** ✓、`.bc-content`
    可滚 ✓ **滚到底分页器可见** ✓；截图确认可见 4 行（APPROVING / ARCHIVED / 草稿 / NRND）
  · 回归：资源库配置页签2（超屏场景）仍 `可滚=true (837/375)`、滚到底末块可见 ✓
  · lint 0 ✓

#### 文案：元器件行操作「发起流程」→「发起器件认证」（按类型条件化，无 SQL 变更）
- **用户要求**：截图（元器件库 → 行操作菜单）*"发起流程修改成发起器件认证"*
- **关键判断：共享组件必须条件化** ✗ 全局改名 —— `PartRowActionMenu.vue` 被元器件库 /
  标准件库 / 通用件库 / 封装·图符库共用，若全局改名，结构件 / 工程数据也会被叫成"器件认证"（不对）
- **改动**：
  · `PartRowActionMenu.vue`：新增 `isElectronic`（`record.typeDefinitionCode === 'ELECTRONIC'`）+
    `workflowLabel`（元器件 →「发起器件认证」，其余 →「发起流程」）；菜单项与打开后的
    `StartProcessModal` 标题**共用同一文案来源**
  · `StartProcessModal.vue`：新增可选 prop `title`（默认「发起流程」）—— 其它调用方
    （系列/型号页的零组件/文档列表等）不传则行为完全不变
  · 弹窗内「暂不能发起流程」等**流程层面**的通用措辞保留（那是共享组件的状态说明，不是动作名）
- **实测**（浏览器 · CKAI-TECH 视角 · 元器件库；测完已切回平台并复查）：
  · 行操作菜单：`详情 | 重命名 | 另存为 | 查看历史版本 | 检出 | 检出并编辑 | 变更分类 |
    新建视图版本 | 设置生命周期状态 | **发起器件认证** | 删除` ✓（已无「发起流程」✓）
  · 点击后弹窗标题 = **发起器件认证** ✓；截图确认
  · 附带观察（非缺陷）：该元器件当前**已有流程在执行** → 弹窗如实提示"暂不能发起流程…
    请先完成或终止"（防重复发起的既有闸门 ✓ 与本次改动无关）
  · lint 0 ✓

#### 新增：流程详情节点抽屉补「表单填写」—— 看得到每个节点办理时填了什么（无 SQL 变更）
- **用户反馈**：*"流程监控页面，看不到节点历史表单填写情况"*（截图：点开「设置审批人」节点，
  只有责任人 / 结论 / 时间 + "执行日志 0 条"）
- **排查结论：数据一直都在，缺的是展示** ✗：
  · 「设置审批人」办理时写入的各环节审批人就在**流程变量**里（`ckplmSetupAssignees_<节点id>`）；
    实测该实例：`ckplmSetupAssignees_n_iqc = Kiween.Hu` 等 8 项 + `ckplmDecision = APPROVE`
  · 而 `ck_process_node_log` 是**引擎后台执行日志**（level / source / message），不含表单 ——
    所以"执行日志 0 条"是**如实**的（人工节点本就无后台动作），不是数据丢了
- **实现（三段）**：
  · 后端 `GET /api/workflow/instance/{id}/activity-form?activityId=xxx` → `[{key, label, value}]`
    （新 VO `ProcessActivityFormVO`）：
    - 归因两条线：变量 `taskId` 命中该活动的历史任务；或写入时间落在该活动**执行时间窗**内
      （「设置审批人」这类流程作用域变量 `task_id_` 为空，只能靠时间窗 —— 前后各放宽 5 秒）
    - 中文标签：`ckplmSetupAssignees_<节点id>` → 从**该实例跑的那版 BPMN** 取 `<userTask id=… name=…>`
      → 「审批人 · 样品来料检验（IQC）」；其余**原样露出键名**（能确定的才翻译，不猜语义）
    - 平台内部变量（businessObjectSet / initiator / tenantOid / lifecycleStatus / approved /
      lastAction / lastComment / ckplmDecision）不返回（结论 / 意见在节点头部已展示）
  · 前端 `ProcessNodeDetailDrawer`：新增「表单填写」节（在"执行日志"之前）—— 只列**有值**项
    （空值不逐行刷屏，只在提示里给个数），空态说明"审批节点通常只填意见"
  · `api/index.js` 加 `getActivityForm`（抽屉已有 `instanceId` prop，父组件已在传，无需改父页）
- **实测**（浏览器 · CKAI-TECH 视角 · 实例 a4a48cf5…「元器件引入流程」；测完已切回平台并复查）：
  · 接口：`200`，8 项、中文标签全部命中 BPMN 节点名，例：
    `{"key":"ckplmSetupAssignees_n_iqc","label":"审批人 · 样品来料检验（IQC）","value":"Kiween.Hu"}`
  · 页面：点「设置审批人」节点 → 抽屉「表单填写 **8 项有值**」（申请填报与完善 / 元器件认定评审（会签）/
    需求与选型初审 / 样品来料检验（IQC）/ 小批量试产验证 / 入库与 AVL 发布 / 寻源与样品申请 /
    样品验证（电气 + 可靠性））；执行日志空态文案保留 ✓；截图确认
  · `mvn compile` 0 错 ✓ 后端已重启（18s）✓ lint 0 ✓

#### 修复：「设置审批人」提交后指定人收不到任务 —— 单人候选人未自动指派（含 1 条存量任务修复，无 SQL 变更）
- **用户反馈**：*"设置审批人节点填写的内容如图，提交后 Roney.Liu 未收到申请填报与完善任务，
  我看数据库中的 assignee_ 字段为空"*
- **排查（用户判断完全正确）**：
  · `act_ru_task`：`n_apply | 申请填报与完善 | assignee_ = 空`；
    `act_ru_identitylink`：`type = candidate, user_id = Roney.Liu`
    → 任务**存在**且他是**唯一候选人** —— 但 Flowable 的"候选人"≠"指派"，只出现在「可认领」✗
  · 变量正常：`ckplmSetupAssignees_n_apply = [Roney.Liu]`（serializable 集合）
  · **根因在设计器编译链**（`frontend/src/flow-designer/bpmn-compiler/compile.ts` 的 `emitTask`）：
    被「设置审批人」管理的**办理（TASK）节点**一律编译成
    `flowable:candidateUsers="${ckplmSetupAssignees_<节点id>}"`，其注释写着
    "办理：给一个人就是指定了他" —— 但 Flowable 语义里**单人候选人仍需认领**才会成待办
    （对照：审批节点走 `assignee="${ckplmApprover}"` / 多实例 `collection`，不受影响）
- **修复（运行期兜底；对在途实例同样生效）**：`ProcessServiceImpl.autoAssignSingleCandidate(...)`
  · 任务创建后（`completeTask` 正常分支 + 驳回退还分支，**先于** `notifyTodoTasks` —— 顺序不能反，
    否则通知到的是"assignee 还空着"的任务）扫本实例任务：
    `assignee` 为空 **且** 候选人（userId 型）恰好一人、无候选组 → `setAssignee` 直接指派
  · **多候选人 / 带候选组 → 保持候选**（那是"谁合适谁认领"的场景，不替用户做主）
  · 候选人**原样**指派（用户名与人员 oid 两种历史写法都认，见 `ProcessIdentitySupport`）
  · 兜底不阻断办理：异常只记 WARN，任务仍在（可认领）
- **存量修复（1 条）**：把卡住的 `n_apply` 任务按同一口径指派 —— `assignee_ = Roney.Liu`
- **实测**：
  · `mvn compile` 0 错 ✓ 后端已重启（18s，新代码生效）✓
  · DB 复查：`4f4e9591-… | n_apply | 申请填报与完善 | assignee_ = Roney.Liu` ✓
  · 待办口径：`act_ru_task.assignee_ = 'Roney.Liu'` → 他登录后「任务中心 → 待办」即见该任务 ✓
- **说明（未擅自动的一处）**：设计器编译链**未改** —— 若要"流程定义本身就输出 assignee"
  （单人办理节点编译成 `flowable:assignee="${…[0]}"`），需重新保存并部署流程（在途实例不受影响），
  属另一项决策，待确认后再做；当前的后端兜底已同时覆盖"既有 + 将来"。

#### 新增：行业表单「新器件申请认证表」（component-qualification-apply）—— 带前端专属渲染组件（无 schema 变更）
- **用户要求**：*"新器件申请认证的表单模板"* → *"按照 1+3 执行"*
  （① 加专属表单组件：设计器能选、办理页真渲染字段；③ 写进行业包分发）
- **交付**：
  · **契约**（`frontend/src/flow-designer/dsl-core/forms.ts`）：导出
    `COMPONENT_QUALIFICATION_FORM_CODE`。刻意**不进** `TASK_FORM_TEMPLATES` —— 那是内置清单
    （随 platform-base 装到每个租户）；本表是行业资产，只随 `electronics-core` 包走
  · **组件**（`components/task/taskForms/ComponentQualificationForm.vue`）：三段式 ——
    器件信息（名称 / 目标料号 / 类别 / 关键参数 / 使用位置）、认证与可靠性
    （认证要求多选 [UL｜CE｜国标｜AEC-Q｜SRRC｜3C｜整机认证一致性]、可靠性验证项多选
    [HTOL｜温循｜湿热｜振动｜ESD｜可焊性]）、供应与准入（是否必需替代料 / 替代与 AVL 影响 /
    目标准入状态 SAMPLING｜QUALIFIED / 申请说明）。提交口径与其它模板一致：
    `{ action:'approve', comment, variables }`；变量统一 `ckplmQualify_` 前缀
    （同 `ckplmSetupAssignees_` 口径）；申请说明同时写入本节点办理意见（act_hi_comment）
  · **视图登记**（`components/task/taskForms/index.js`）：`TASK_FORM_VIEWS` 增加一行
    —— 未登记的 code 一律落到兜底通用表单（同意 / 驳回 + 意见）
  · **行业包**（`electronics-core/v1.0.0/processes/forms/forms.json`）：增第 6 张
    （nodeTypes=START,TASK，sortOrder=160，fields 与组件逐项对应；note 注明"改其一请同步另一处"）。
    新租户 / 重装随包自动带上；已装的平台租户与 CKAI-TECH 用 SQL 幂等补齐（INSERT 0 2）
  · **顺手修**：办理页不加载表单模板清单 → 自定义表单的表名回落成兜底文案「审批处理」。
    新增 `ensureFormTemplates()`（幂等，与设计器共享同一次请求），`TaskHandle` 取数前 await；
    设计器改用同一入口
- **实测**（浏览器 · CKAI-TECH 视角 · 元器件引入流程的在办任务；测完已切回平台并复查）：
  · `GET /form-templates/for-node?nodeType=TASK` → **200 且含**
    `component-qualification-apply|新器件申请认证表` —— 设计器节点下拉能选到 ✓
  · 办理页拦截 context 响应把 formKey 指向本表（**不改库、不提交，零副作用**）→
    卡片标题 `办理 · 新器件申请认证表` ✓；渲染「新器件申请认证」告示与全部字段 ✓；
    兜底表单特征（审批动作 / 审批意见）消失 ✓；截图确认 ✓
  · lint 0 ✓；包 JSON 解析校验 forms=6 ✓
- **怎么用起来**：在流程设计器里把某个 **开始 / 办理** 节点的「节点表单」选为
  「新器件申请认证表」并保存部署，该节点办理页即渲染此表
  （在途实例仍用它发起时部署的那一版表单）。

#### 落地：把「新器件申请认证表」接到流程节点上（CKAI-TECH 租户 · 元器件引入流程 v4）
- 承上一条（新增行业表单的三环）—— 这一次是第三环「节点接线」：
  · CKAI-TECH 租户的 `component-introduction` 模板：`n_apply` 节点 `formRef`
    `component-introduction-apply` → `component-qualification-apply`，保存为 **v4** 并部署
    （部署产物：流程定义 `component-introduction:4`，部署 id `292535c3-…`）
  · **平台租户的模板未动**（保持 OOTB 标准；要同步说一声）
- **端到端实测（全链真跑，无任何拦截）**：
  · 发起新实例（businessKey=TEST-NPI-…）→ 第 1 个节点「设置审批人」办理后 →
    `act_ru_task`：`n_apply | 申请填报与完善 | form_key_ = component-qualification-apply
    | assignee_ = Roney.Liu` ✓
    （同时印证上一轮的「单人候选自动指派」在真实流程里生效：identitylink 中他是唯一 candidate，
    任务被自动指派，不再需要手工认领）
  · 打开该任务办理页：标题「办理 · 新器件申请认证表」，渲染全部字段，
    兜底表单特征（审批动作 / 审批意见）消失 ✓ 截图确认 ✓
- **备注**：老的**在途实例**（用定义 v3 发起的那条）仍用旧表单 —— 引擎语义改不了；
  新发起的实例一律走 v4 的新表单。
- **留给环境的测试数据**：一条 `TEST-NPI-…` 实例（当前停在 n_apply，负责人 Roney.Liu），
  要清理用「流程监控 → 删除实例」或说一声我删。

#### 改版：「新器件申请认证表」去掉手填器件字段，改为带出流程绑定的器件 + 其 MPN（只读）
- **用户要求**：*"器件信息中的器件名称、目标料号、器件类别和关键参数不需要。
  但需要把流程中绑定的器件以及对应 MPN 信息带过来展示"*
- **改动**（`components/task/taskForms/ComponentQualificationForm.vue` + 包 `forms.json` 的 fields 同步）：
  · **删除** 4 个手填字段：器件名称 / 目标料号 / 器件类别 / 关键参数
    （理由写进组件注释：器件身份不该由申请人手抄，抄错就与库里不一致）
  · **新增** 只读区「关联器件与 MPN」：
    - 数据源 = 任务上下文 `context.entities`（流程关联的业务对象）+ `fetchEntityInfo` 回查显示名
      （编号 / 名称 / 状态 —— 与办理页页头同一口径，见 composables/useEntityInfo）
    - 宿主为 `PART` 的实体再查 `/mpn/parts/{oid}/aml` + `/summary` → MPN 表
      （制造商 / 制造商料号 / 原厂描述 / 认证状态 / 原厂生命周期 / 供货渠道数）
      + 汇总条（AML 数 / 去重供应商数 / **单一来源风险 R-SH-11**）
    - 制造商 oid → 名称映射用 `listManufacturers()`；所有回查失败只影响展示，不挡填写与提交
  · **保留**可填字段：使用位置（必填）/ 认证要求 / 可靠性验证项 / 是否必需替代料 /
    替代与 AVL 影响 / 目标准入状态 / 申请说明；提交变量同步（去掉 4 个 `ckplmQualify_*`）
- **实测**（浏览器 · CKAI-TECH · 发起**绑定真实器件**的实例并推进到 n_apply）：
  · 卡片渲染：器件行（`PART-202610-0001` / 200欧电阻 / 版本 / 状态 / ELECTRONIC）
    + MPN 表两行（德州仪器 RC0805JR-0710RL【首选】；三星 SS000000-0001【认可 · ACTIVE】）
    + 汇总「认可料号（AML）2 个 / 去重供应商 1 家 / **单一来源风险（R-SH-11）**」✓
  · 四个手填字段消失 ✓（页面上唯一的"器件类别"字样来自可靠性验证项的 placeholder 说明文案）
  · 截图确认 ✓ lint 0 ✓
  · **后续（同日）**：按用户要求**移除卡片顶部提示条**（"新器件申请认证 / 器件与 MPN 由流程关联的
    业务对象带出…"那条 a-alert）—— 卡片直接以「关联器件与 MPN」开头；复测截图确认 ✓
  · **再改（同日）**：「使用位置」由自由文本改为**选择系列或型号**（`a-tree-select`）——
    与 DynamicForm 的「所属产品」同一数据口径：产品线树 `/product-lines/tree` + 每个系列挂其型号
    `/product-models?productLineOid=…`；提交时同时写 `ckplmQualify_usageLocationOid`（对象 oid）
    与 `ckplmQualify_usageLocation`（人读名，流程详情直接展示）。包 forms.json 的 fields 已同步。
    **实测**：占位「选择系列或型号」出现 ✓、旧自由文本占位消失 ✓、点开下拉列出「X系列智能手表」✓
    （该租户 `ck_product_model` 当前 0 行，故暂时只有系列一层；有型号时会作为子节点列出）。
    实测前把 `sysadmin` 加入该系列团队（`ck_team_member` 一行 —— 产品线树按团队可见性过滤，
    不在团队里树是空的；撤销 SQL 见 `.playwright-cli/add-sysadmin-to-team.sql` 文末注释）。

#### 改文案：「是否必需替代料」→「是否关键件（必须维护替代料）」并补常显说明（用户选 B 方案）
- **用户反馈**：*"是否必须替代料的语义和上下文不清楚，不知道对应的用意？"* → 选择 *"B. 留着但讲清语义"*
- **实查出的真实语义（供后人免再排查）**：
  · 出处 = 器件属性组 `ALTERNATE`「替代与复用」的 `NEED_ALTERNATE`（绑 `ELECTRONIC` 软类型）
  · 服务规则 **R-SH-09「关键器件必须维护替代料」**：`needAlternate=true` 且替代件台账计数=0 → 提醒
    （该规则是组合条件，现有 4 类判定暂覆盖不到，规则侧先只校验标记本身）
  · 配对属性 `ALTERNATE_COUNT`（derived：`count(PartAlternate where status='ACTIVE')`）；
    替代件表 `ck_part_alternate_link` 实测 **0 行**（平台尚无替代件数据）
  · 与 **R-SH-11「单一来源」** 互补：后者看 AML/AVL 的**供货渠道数量**（自动算），
    本标记看是否**准备了替代件** —— 两条规则各管一头
  · 另一个事实：属性表现有 399 条里**没有** `alternate` 相关项 ——
    `attributes.json` 声明的属性**尚无 ATTRIBUTE 安装器**（包文件自述），故该属性暂无落点、规则也还没跑起来
- **改动**：
  · 组件：标签改「是否关键件（必须维护替代料）」；单选文案「是 —— 关键件，须有替代料」；
    表单下方**常显说明**（`.cqf-extra`）讲清"这是**要求标记**不是现状"、引 R-SH-09 与 R-SH-11 的分工；
    组件文档注释补第 ④ 条说明。变量名保持 `ckplmQualify_needAlternate`
    （与属性 `NEED_ALTERNATE` 对齐，将来安装器落地可直接对接）
  · 包 `forms.json`：同名字段的 `name` / `note` 同步（含 2026-10-12 改名留痕）
- **实测**：办理页命中「是否关键件（必须维护替代料）| 是 —— 关键件，须有替代料 | 要求标记 |
  R-SH-09 | R-SH-11 | 供货渠道够不够 | 有没有准备替代件」✓ 截图确认 ✓ lint 0 ✓

#### 增强：「新器件申请认证表」MPN 表的「供货渠道」支持 hover 查看渠道明细
- **用户要求**：*"鼠标移动到供货渠道可以查看对应的渠道信息"*
- **改动**（`ComponentQualificationForm.vue`）：
  · 「供货渠道」列的「N 家」标签外包 `a-popover`（**不用 tooltip** —— 内容是多行结构化信息，tooltip 排不开）
  · 面板内容 = 该料号（MPN）下的渠道明细：**供应商**（名称，新加 `listSuppliers()` 做 oid→名映射）/
    供应商料号 / 单价（含币种）/ 交期 / MOQ / SPQ / **首选**标记；多条渠道分行、虚线分隔；
    无渠道时显示"尚无供货渠道（AVL）记录"
  · 标题带上料号：`供货渠道（AVL）—— <mpn>`，避免两行的面板分不清是哪一条
- **实测**（浏览器 · CKAI-TECH · 办理页 hover「RC0805JR-0710RL」行的「1 家」）：
  面板显示「乘恺电子【首选】/ 供应商料号 CK-RC0805JR-0710RL / 单价 15 CNY / 交期 40 天 /
  MOQ 10 / SPQ 20」✓ 截图确认 ✓ lint 0 ✓

#### 机制改造：表单组件固化在系统中 —— 行业包 forms.json 改为「开启 + 标注 component 名」
- **用户要求**：*"未来流程的表单组件都固化在系统中，在行业包的 form.json 文件中只需要开启，
  并标注对应的 component 名称即可，类似 platform-base 中的 forms.json 的定义，
  请更新 electronics-core 中的 forms.json"*
- **范式落点（前端 + 后端 + 包 + 数据）**：
  · **前端解析**（`components/task/taskForms/index.js`）：新增组件名视图表 `FORM_COMPONENT_VIEWS`
    （键 = 组件名，如 `ComponentQualificationForm`）；解析顺序变为
    「语义固定 → 内置按 **code** → 行业表单按**模板声明的 component 名** → 兜底」；
    `hasOwnTaskForm` 同步两边（否则标题会回落成兜底文案「审批处理」）
  · **后端安装器**（`base/pack/install/FormInstaller.java`）：修掉硬编码 ——
    行业表单原来把 `component` 写成 `null`（包里的标注落不了库），现在原样落库；
    安装明细也如实打印 component（或"未标注，走兜底渲染"）
  · **包清单**（`electronics-core/v1.0.0/processes/forms/forms.json`）：6 张全部标注 component 名；
    **移除全部 fields**（字段由组件定义 —— 与 platform-base 一致，两处各写一份必然漂移）；
    note 重写为新范式；未实现组件的 5 张在 description 注明"组件待补：当前运行期走兜底通用表单"
  · **数据补录**：已安装租户（平台 + CKAI-TECH）的 `component-qualification-apply` 行补上
    component（UPDATE 2）
- **实测（证明新链路真通）**：把 `ComponentQualificationForm` **从按 code 的视图表移出**后，
  办理页仍完整渲染 —— 命中的只能是**按 component 名**的新分支：
  · `GET /form-templates/for-node?nodeType=TASK` → `component-qualification-apply
    component=ComponentQualificationForm` ✓
  · 页面命中「办理 · 新器件申请认证表 | 关联器件与 MPN | RC0805JR-0710RL |
    是否关键件（必须维护替代料）| 选择系列或型号」，兜底特征（审批动作/审批意见）**无** ✓
    截图确认 ✓
  · `mvn compile` 0 错 ✓ lint 0 ✓ 包 JSON 解析校验 6/6 ✓
- **约定（已写进包 note 与代码注释）**：行业新增 / 更换表单渲染器 = 补一个
  `components/task/taskForms/` 下的 Vue 组件 + 在 `FORM_COMPONENT_VIEWS` 登记一行 +
  在包 forms.json 标注组件名；未实现的组件名自动落兜底，不影响办理。

#### 补齐：4 张行业表单的专属组件（技术评审申请单 / 技术评审检查表 / 阶段门决策申请单 / 决策结论登记表）
- **用户要求**：*"补齐技术评审申请单 / 技术评审检查表 / 阶段门决策申请单 / 决策结论登记表 组件"*
- **新增组件**（`components/task/taskForms/`，全部按"组件固化在系统 + 包上只标注名"的新范式）：
  · `TechnicalReviewApplyForm.vue`（START,TASK）：**待评对象**从流程关联对象只读带出（同新器件表手法）；
    评审模板（`/review-templates`，值=code）/ 所处阶段（`/stage-templates`）/ 关联阶段门（`/gate-templates`）
    三个下拉 + 送审材料与申请说明文本。变量 `ckplmTechReview_*`
  · `TechnicalReviewChecklistForm.vue`（APPROVAL）：**检查项不手填** —— 从流程变量
    `ckplmTechReview_reviewTemplate` 取上游选定模板 → 解析其 `checkItems`（`{seq,text,severity}`）
    逐条出 符合/不符合/不适用；实时算不符合项数与 BLOCK 级数；结论选项优先取模板 `verdictOptions`；
    **通过 / 有条件通过 → approve，不通过 → reject**；有条件通过时附加条件必填；
    拉不到清单给明确提示但不挡办理
  · `GateDecisionApplyForm.vue`（START,TASK）：门下拉 + 交付物完成率 + 未关闭阻断问题数
    （对应 DELIVERABLE_RATE / ISSUE_OPEN，表内注明待门引擎落地后改自动算）+ 认证齐套 / 供应已确认开关
    （CERT_COMPLETE / SUPPLY_CONFIRMED）+ 决策材料。变量 `ckplmGateDecision_*`
  · `GateDecisionRecordForm.vue`（TASK）：决策（GO / GO_WITH_CONDITION / HOLD / TERMINATE / RECYCLE）
    + 决策依据（必填）+ 三类风险（商业 / 技术 / 供应）+ 附加条件 + 表决结果；
    **GO / GO_WITH_CONDITION → approve，其余 → reject**。变量 `ckplmGateRecord_*`
- **登记**：`FORM_COMPONENT_VIEWS` 新增 4 项（`component-introduction-apply` 仍未实现，
  已在字典里留注释说明会落兜底）
- **数据补齐**：这 5 张（含元器件引入申请单）在已安装租户的 `component` 列原为 null →
  SQL 按包内标注补齐（UPDATE 10）
- **实测**（浏览器 · CKAI-TECH · 拦截 formKey 逐张切换，零副作用）：
  ```
  technical-review-apply      专属 ✓ 4/4  评审模板 / 所处阶段 / 待评对象 / 送审材料
  technical-review-checklist  专属 ✓ 3/3  检查项逐条结论 / 评审结论 / 附加条件
  gate-decision-apply         专属 ✓ 5/5  交付物完成率 / 未关闭阻断问题数 / 认证齐套 / 供应已确认 / 决策材料
  gate-decision-record        专属 ✓ 5/5  决策依据 / 商业风险 / 技术风险 / 供应风险 / 表决结果
  ```
  · **首次全落兜底** —— 原因：库里这 4 张的 `component` 是 null（上一轮只补了"新器件"那一张）；
    正好反证这套是**数据驱动**的（包上标了名，数据还要到位），补齐后全通 ✓
  · lint 0 ✓ 截图确认 ✓
- **后续（同日）**：补上第 5 张「元器件引入申请单」（`component-introduction-apply` →
  `ComponentIntroductionApplyForm`，用户圈定即此组件名）。它属**需求侧**表单 —— 要引入的器件通常
  **还不存在**（没有料号、没有 AML），所以「需求名称 / 目标参数要求 / 使用场景」必须手填；
  与「新器件申请认证表」（器件已存在、身份与 MPN 只读带出）形成互补，这条分工写进了组件注释。
  其余字段：合规要求（RoHS / RoHS+REACH / RoHS+REACH+无卤）、是否关键件（沿用 R-SH-09 语义与说明）、
  目标准入状态（SAMPLING / QUALIFIED）；变量 `ckplmComponentIntro_*`。
  库中该条的 component 已在上一批 SQL 里补齐，无需再动数据。
  **回归实测（6 张一次过）**：
  ```
  component-qualification-apply  [专属 ✓] 1/1
  technical-review-apply         [专属 ✓] 3/3
  technical-review-checklist     [专属 ✓] 2/2
  gate-decision-apply            [专属 ✓] 2/2
  gate-decision-record           [专属 ✓] 2/2
  component-introduction-apply   [专属 ✓] 6/6  需求名称/目标参数要求/使用场景/合规要求/是否关键件/目标准入状态
  ```
  lint 0 ✓ 截图确认 ✓

#### 新增：元器件引入流程专用「评审表」与「会签表」（替代内置审批/会签表单）+ 流程接线（v5 已部署）
- **用户要求**：*"新器件进入流程的评审表单和会签表单，也需要构建，不能用系统内置的审批表单模板，
  因为看不到元器件申请信息。所以结合内置的审批和会签表单与新器件认证申请表单，构建两个新的表单"*
- **新增组件**（1 块共用只读摘要 + 2 张专用表）：
  · `QualificationRequestSummary.vue`（**共用只读摘要**，不暴露 collect）：
    器件 + MPN（流程关联对象：AML 表 + 单一来源汇总）＋「申请信息」（流程变量；
    同时兼容 `ckplmQualify_*`（新器件申请认证表）与 `ckplmComponentIntro_*`（元器件引入申请单）
    两种前缀 —— 谁在跑读谁；枚举码翻中文、数组拼"、"）
  · `ComponentQualificationReviewForm.vue`（评审 / 办理节点）：摘要 + 审批结论 ——
    **直接复用** `buildApprovalOpinionModel` / `validateApprovalOpinion`（允许驳回、驳回目标、
    意见必填全随节点声明），与内置「审批意见」口径**零差异**
  · `ComponentQualificationCountersignForm.vue`（会签节点）：摘要 + 会签规则与各人办理情况
    （复用 `buildCountersignModel` / `validateCountersign`，含一票否决语义）+ 本人结论与意见
  · 提交口径与内置完全一致（`{ action, comment }`），不额外写流程变量
- **表单模板**：包 `electronics-core/forms.json` 新增 2 条
  （`component-qualification-review` nodeTypes=APPROVAL,TASK；
  `component-qualification-countersign` nodeTypes=COUNTERSIGN_APPROVAL）；已装租户 SQL 补齐（INSERT 0 4）
- **流程接线**（CKAI-TECH · `component-introduction` v4 → **v5**，部署成功，流程定义
  `component-introduction:5`）：
  ```
  n_demand(APPROVAL)             -> 评审表
  n_sourcing(TASK)               -> 评审表
  n_iqc(APPROVAL)                -> 评审表
  n_verify(COUNTERSIGN_APPROVAL) -> 会签表   （「样品验证（电气+可靠性）」本就是会签，自动命中）
  n_pilot(TASK)                  -> 评审表
  n_ce_review(COUNTERSIGN_APPROVAL) -> 会签表
  n_publish(TASK)                -> 评审表
  n_apply 保持 component-qualification-apply（认证申请表）
  ```
- **端到端实测**（浏览器 · CKAI-TECH · 真发起并推进；顺带清理了两条验证用旧实例）：
  ```
  推进：set_assignee -> CKPLM_SETUP_ASSIGNEE | n_apply -> component-qualification-apply
       | n_demand -> component-qualification-review      ← 引擎返回的 formKey 即新表 ✓
  【评审表】6/6：申请信息（只读）/ RC0805JR-0710RL / 德州仪器 / 审批结论 / 同意 / 驳回 ✓
  【会签表】4/4：申请信息（只读）/ 会签规则 / 会签情况 / 我的会签意见 ✓
  ```
  · 摘要确实拉到了器件 MPN（RC0805JR-0710RL / 德州仪器 / 单一来源风险）—— 不是空壳
  · lint 0 ✓ 截图确认 ✓
- **遗留项（待办，需单独一轮谨慎做）**：**包内** `processes/component-introduction.dsl.json`
  与 `component-introduction.bpmn20.xml` 尚未同步这 7 处 formRef / formKey ——
  装包是「直接部署包内 BPMN」（后端不编 BPMN，XML 由前端编译器预编），手改 XML 有风险，
  故本轮只改运行时（CKAI-TECH）。影响面：仅**未来新租户/重装**会拿到旧表单接线。

#### 修正校验：「设置审批人」位置规则 —— 由"必须紧跟开始"改为"之前的活动必须都由发起人办理"（WARNING → ERROR）
- **用户口径**：*"「设置审批人」应直接跟在「开始」之后（它是流程启动后的第一个节点）：在它之前
  执行的活动，人员不会被它指派，这个结论不一定。如果它之前的活动的节点责任人都是发起人就可以了，
  其他的都应该当错误来处理"*
- **新规则**（`dsl-core/validate.ts` 的 `checkSetup` ③，2026-10-13）：
  · 取「设置审批人」**上游可达**（含间接）的全部节点（`canReach`），筛出其中的人力活动
    （`SETUP_COVERED_TYPES`：审批 / 会签 / 办理）；
  · 这些活动**全部由发起人办理**（`strategy=INITIATOR`，承办人明确）→ **不报**（原结论不成立）；
  · 只要有一个的责任人不是发起人 → **ERROR**（拦保存），提示里**点名**是哪几个活动，
    并给出两条出路（改为「由发起人办理」/ 把「设置审批人」移到它们之前）。
- **配套**：
  · `IssueCode.SETUP_NOT_FIRST` 语义注释更新（含升为 ERROR 的理由）
  · 旧表述"固定是流程启动后的第一个节点"按新口径软化为"通常就是第一个节点（非硬约束）"——
    涉及 `constants.ts`（SETUP_ASSIGNEE 常量）、`validate.ts`（模块头）、
    `node-schemas.ts`（面板注释 + 帮助文案）、`assignee-setup.ts`（模块头）
  · 单测：原「没有紧跟开始 → 提醒」用例改为「之前有非发起人活动 → **错误**（并断言点名）；
    新增「之前活动全部由发起人办理 → **不报**」用例（`assignee-setup.test.ts`，25 个用例）
- **实测**：`npx vitest run src/flow-designer` 两轮全绿（21 个文件，无 FAIL）✓；
  lint 0 ✓；元器引入流程（v5，设置审批人紧跟开始）在设计器里**仍是"校验通过"** —— 新规则不误报 ✓

#### 修复：租户审批通过后管理员首次登录进了 home，需按 F5 才进引导页（无数据库变更）
- **用户反馈**：*"租户审批通过后，租户管理员第一次登录系统，进入的是 home 页面，
  需要按 F5 刷新才能进入 onboarding 页面"*
- **根因**（路由层的 `onboardingState` 是**模块级缓存**，且**登录 / 登出都不清**）：
  · 守卫 `guardOnboarding` 用 `loadOnboardingState()` 判断，缓存一次会话有效
   （原先只有"从 /onboarding 离开"才强制重取）；
  · **登出走 `router.replace('/login')`** —— SPA 内导航、页面不刷新 → 缓存仍在；
  · 于是"同一浏览器先登录过别的账号（如平台管理员，状态被缓存成 `needsOnboarding=false`）
    → 再登录刚审批通过的租户管理员"时，守卫拿到**上一个账号的旧状态**直接放行进 home；
    F5 清空内存 → 重新请求 → 才跳 /onboarding。
- **修复**（`frontend/src/router/index.js` + `views/Login.vue`）：
  · 新增并导出 `resetOnboardingState()`：清空缓存（值 + 取回时间），**登录成功后调用** ——
    登录是"换身份进入系统"的动作，此前缓存的可能是别的账号或"租户还没被审批通过"时的状态；
  · 缓存加 **30 秒 TTL**：兜住"登录之后平台才审批通过"这类同会话内的外部变化；
  · 取数失败仍返回 null 并放行（引导拦截绝不把"没登录 / 接口异常"误判成"未引导"）—— 保持原设计。
- **实测（端到端 · 浏览器 · 真注册 + 真审批，不是模拟）**：
  ```
  序列：登录 sysadmin（缓存 DONE）→ 注册租户 → 登出 → 登录新管理员（未审批：停在登录页）
        → 登出 → 登录 sysadmin 审批通过 → 登出 → 登录新管理员
  ✗ 对照（临时注释掉修复调用）：S6 => /home       ← 精确复现用户报的现象
  ✓ 修复后（复查复跑）：        S6 => /onboarding ✓（页面「企业审核已通过…还剩最后一步」）
  ```
  · 期间抓到并修掉一处手误：`Login.vue` 里调用 `resetOnboardingState()` 漏了 import ——
    首轮探针当场报 `ReferenceError: resetOnboardingState is not defined`（表现为登录后卡在登录页），
    补 import 后通过；
  · lint 0 ✓；为验证注册的 4 个测试租户及管理员账号已全部清理（`DELETE 4` + `DELETE 4`，复查 0 残留）✓

#### 修复：会签节点被锁死为内置「会签」→ 行业会签表配不上（下拉只显示 code）+ 三层放开
- **用户反馈**：*"元器件引入会签表没有显示中文"*（截图为设计器右侧「节点表单」下拉显示
  `component-qualification-countersign`）
- **根因（不是显示问题，是三层锁死）**：
  · `dsl-core/forms.ts`：内置「会签」模板 `fixed: true`；
  · `bpmn-compiler/compile.ts:270`：`const formRef = fixedFormCodeOfNode(node) ?? node.formRef
    ?? defaultFormCodeOfNode(node)` —— **固定表单优先于节点显式 formRef**，会签节点编译出的
    `flowable:formKey` 永远是 `CKPLM_COUNTERSIGN`（上一轮给流程节点写的行业会签表被**静默覆盖**）；
  · `app/panel/node-schemas.ts`：会签节点 `formAndCommentFields({ fixed: true })` →
    下拉只留内置会签且只读；DSL 里已有的 code 不在候选项里，antd 就直接回显 code（用户看到的"没中文"）。
  · 附带发现：`taskFormOptionsOf(nodeType)` 只按**类型**过滤 → 「审批 + 会签模式」的节点连
    会签模板都列不出来。
- **修复（会签从"固定"降为"默认"，可换）**：
  · `forms.ts`：内置会签模板 `fixed: false`；新增导出 `isCountersignNode(node)`（专门节点 或
    审批+会签模式）；`fixedFormCodeOfNode` 只保留**真正的语义固定**（「设置审批人」），
    会签的默认值移到 `defaultFormCodeOfNode`（未指定时仍默认内置「会签」—— 行为不倒退）。
  · `node-schemas.ts`：会签节点表单改为**可选**；新增 `taskFormOptionsOfNode(node)` 按节点过滤
    （会签语义 → 列会签类模板）；`optionsFromDsl` 改用；help 文案补「会签 → 会签」。
  · `compile.ts` **无需改**：`fixedFormCodeOfNode` 收窄后，`node.formRef` 自然生效，
    `defaultFormCodeOfNode` 兜住未指定的情形 —— 面板与编译器仍同源。
- **测试**：`field-schema.test.ts` 会签两条用例改写为「可选 + 默认内置会签」，
  新增「注入行业会签表后出现在候选里、默认值不被顶掉」；**全套 22 文件 / 409 用例通过** ✓
- **落地（CKAI-TECH）**：用新编译器**重新部署** `component-introduction`（v6 → 部署
  `component-introduction:7`）。库级验证：
  ```
  ck_process_template_version.bpmn_xml（最新版）：
    行业会签表命中 4 次（2 个会签节点 × flowable:formKey + ckplm:formRef），内置会签 0 次 ✓
  n_ce_review 节点原文：flowable:formKey="component-qualification-countersign"
                       ckplm:formRef="component-qualification-countersign" ✓
  真实流程实例（PART-202610-0002 发起）逐节点 formKey：
    n_apply -> component-qualification-apply       （认证表）
    set_assignee -> CKPLM_SETUP_ASSIGNEE
    n_demand -> component-qualification-review     （评审表）
    n_sourcing -> component-qualification-review   （评审表）✓
  ```
- **说明**：**在途实例**仍用发起时那一版定义的表单；新发起的实例按 v7 走行业会签表。
- **自查**：上一轮交付的会签表**当时并未真实生效**（那次是靠拦截 formKey 验证的），
  本轮才真正接通 —— 教训：表单接线要在**真实流程**上验，不能在拦截层验。

#### 改版：驳回统一为「画线」—— 属性面板去掉「允许驳回 / 驳回目标」配置（无 schema 变更）
- **用户要求**：*"驳回采用画线的方式，在属性面板中去掉驳回的配置，以便重复"*
  （缘起：画了「驳回」出边后，节点上残留的「驳回目标」让校验同时报两条错 ——
  `EDGE_ROUTE_CONFLICT`（两套机制抢戏）与 `SETUP_NOT_FIRST`（回环让下游节点被当成上游））
- **改动（设计器三处 + 运行期一处）**：
  · **面板**（`app/panel/node-schemas.ts` 的 `rejectFields()`）：删掉「允许驳回」（object-toggle）、
    「驳回目标」（上一步/发起人/指定节点）、「目标节点」、「驳回时意见必填」四个字段，
    只留一条说明 —— "再连一条出边，在【连线】的属性里把「路由」设为「驳回」"
  · **加载归一**（`dsl-core/schema.ts`）：`stripStaleRejectTargets`（只清"画了驳回边"的节点）
    换成 `stripLegacyReject` —— **无条件清掉**节点上的 `reject`。
    理由：面板已无处可改，留着就是"看不见却仍然生效"（编译进 `ckplm:reject`、运行期一键跳回）
  · **校验**（`dsl-core/validate.ts`）：删掉整个 `checkRejectTarget` 函数（上一步/发起人/指定节点
    的可用性判定）与 `EDGE_ROUTE_WITHOUT_REJECT`（画了线却没开开关）、`EDGE_ROUTE_CONFLICT`
    （画了线又配了目标）两条判定 —— 它们判定的都是已经不存在的配置项。
    路由本身的校验（重复 / 部分声明 / 缺「通过」/ 不适用 / 未声明）保持不变
  · **运行期**（`app/task/approval-opinion.ts`）：可驳回判定改为
    **「画了 route=REJECT 的出边」**（老实例仍带 `reject` 声明 → 照旧放行，后端 RejectRouter 也还认）；
    不再预填 `target`，因此没有旧声明时文案是「按流程图上的「驳回」路径 → 某节点」，
    而不是假称"退回上一步"
- **编译层与后端无需改**：`compile.ts` 的 `if (node.reject)` 自然不再命中（DSL 里已无）；
  后端 `completeTask` 本来就有两条腿 —— 有 `ckplm:reject` 走 RejectRouter 跳回，
  没有则写 `approved=false` 走图上 `${!approved}` 那条**驳回边** ✓（本次正是启用后者）
- **顺带修正**：办理节点（TASK）不再"无条件可驳回"。以前"节点上没有开关就默认允许"，
  于是办理节点也能点驳回，而它的驳回会照走默认出边继续往前 —— 界面写着"驳回"、流程实际往前走
- **同时修掉"驳回回边污染顺序判定"**（截图中第二条错误的真凶）：`canReach` 新增
  `{ ignoreReject: true }`，三处"顺序"判定全部改用它 —— 规则 ③（「设置审批人」的上游）、
  覆盖范围（`setupControlledIds`）、「设置审批人」表单的下游槽位（`flowOrderFrom`）。
  驳回是**回退**边，不代表执行先后；带着它走，一条"驳回回到申请填报"的边会让两侧反过来：
  · **修前**：① 校验误报「设置审批人」之前有不由发起人办理的活动（`SETUP_NOT_FIRST`）——
    用户画完驳回边就存不了盘；②「申请填报」被算成设置审批人的**下游** →
    编译时它的 `flowable:assignee` 从 `${initiator}` 变成 `candidateUsers`，
    申请人自己的任务成了"可认领"（前几轮"任务没派给人"的同类坑）。
  · **修后**：两条都消失 —— 审批仍在设置审批人下游 ✓、申请填报保持 `${initiator}` ✓。
  · 回归用例：`edge-route.test.ts` 新增 2 条「驳回回环不算顺序」（含编译产物断言）
- **测试**：删除 `dsl-core/__tests__/reject.test.ts`（13 个用例，全针对旧配置）；
  改写 `edge-route` / `approval-route` / `approval-opinion` / `field-schema` / `dsl-core` 五个文件的用例
  —— **21 文件 / 391 用例全过** ✓
- **实测**（浏览器 · CKAI-TECH · 元器件引入（认定）流程 v6）：右面板含"再连一条出边"说明、
  **不含**「允许驳回 / 驳回目标」 ✓；右上「校验」计数 = **0** ✓
- ⚠️ **行为变化（务必知悉）**：v6 里 4 个审批节点原本配的是「驳回目标 = 发起人」（运行期一键跳回），
  统一后这些配置在加载时被清掉 → **这些节点变为不可驳回**（办理页只剩「同意」）。
  要让某节点可驳回，请在该节点上连一条出边、把它的「路由」设为「驳回」
  （可指向「申请填报与完善」或先接自动化节点）。
  **已部署的老流程不受影响**：BPMN 里仍带着 `ckplm:reject`，运行期照旧一键跳回。

#### 改版：审批 / 会签 / 办理节点去掉「绑定业务对象」开关（无 schema 变更）
- **用户要求**：*"将办理节点、审批和会签节点的绑定业务对象的开关去掉"*
- **改动**：
  · **面板**（`app/panel/node-schemas.ts`）：审批（APPROVAL）与办理（TASK）的字段列表移除
    `...bindingFields()`；**会签节点本来就没有这个字段**。`bindingFields()` 现在只给
    **开始节点**用（注释已注明）。理由：业务对象在流程发起时就绑定了（businessObjectSet），
    各节点再声明一次既没人读（后端不读 `ckplm:binding`，它只是设计期声明）、又要用户多配一遍
  · **加载归一**（`dsl-core/schema.ts`）：新增 `stripNodeBinding` —— 清掉审批 / 会签 / 办理节点上
    残留的 `binding`（否则是"看不见却仍然生效"：照旧被编译进 `ckplm:binding`）。
    **开始节点的绑定保留** —— 发起时确定业务对象是它的职责
- **未动**：DSL 类型 / zod schema / 编译 / 解析仍支持 `binding`
  （老部署与手写 BPMN 的往返不受影响；开始节点也照旧能配）
- **回归用例**：`field-schema.test.ts`（三类节点无 binding 字段、开始节点保留）+
  `edge-route.test.ts`（加载清理、开始节点保留）—— **21 文件 / 395 用例全过** ✓
- **实测**（浏览器 · CKAI-TECH · 元器件引入（认定）流程 v6）：
  审批节点面板与办理节点面板均**不含**「绑定业务对象」 ✓；「节点表单」「路由」说明仍在 ✓；
  右上「校验」计数 = **0** ✓

#### 修复：节点贴太近导致连线看不见 —— 松手后自动让开（无 schema 变更）
- **用户反馈**：*"如果两个节点的距离太近，就会出现画线截图中的情况"*
  （截图：设置审批人 ↔ 需求与选型初审 几乎贴在一起，两者之间的连线短到看不见）
- **改动**（`canvas/graph.ts`）：
  · 新增纯几何函数 `pushApartFrom(self, others, gap)`：**完全/部分重叠**沿"出去所需位移最小"
    的方向让开（同等距离向上）；**水平投影重叠**（上下相邻）垂直间隙不足就让开；
    **垂直投影重叠**（左右相邻）同理沿水平让开；**斜对角**不动（那是错开摆放，不是贴住）。
    让开后可能与第三个节点又贴住 → 最多迭代几轮（无处可去时停在最后一轮，属兜底）
  · `MIN_NODE_GAP = 24`（两个网格）：够画出连线与箭头，又不至于把紧凑布局推开太多
  · **触发时机**：`node:change:position` 后 **debounce 150ms**（拖动中不打断手势）+
    window `mouseup` 兜底；**只动被拖的那一个节点**；**多选拖动不纠正**（成组移动保持相对关系）
- ⚠️ **过程中发现的两件事**（都写进代码注释与 CHANGELOG，供后来者少走弯路）：
  · X6 的 `node:moved` 对 **Selection 插件的拖动不派发** —— 原先"拖完记一步撤销"因此失效
    （拖完按 Ctrl+Z 回不到拖动前）。本轮顺带修复：收尾也挂在 window mouseup 上
    （撤销栈对"与当前快照相同"的 push 会忽略，两处都调用安全）
  · 验证时的"屏幕上量到 12px"一度被误判为纠正失效 —— 实为**画布视口缩放**造成的读数错觉；
    改读节点 DOM 的 `transform`（X6 写的是画布坐标）才定论
- **测试**：新增 `canvas/__tests__/overlap.test.ts`（7 条几何用例：上下/左右/重叠/斜对角/多邻居/无邻居）
  —— **22 文件 / 402 用例全过** ✓
- **实测**（浏览器 · CKAI-TECH · 元器件引入（认定）流程 v6，人为把节点拖到间距 5px）：
  ```
  拖动前 画布坐标: set_assignee=translate(240,198)  n_demand=translate(240,300)  → 间距 58
  拖动后 画布坐标: set_assignee=translate(240,198)  n_demand=translate(240,266)  → 间距 24 ✓
  ```
  （间距从 10 收到 24 —— 连线与箭头重新可见）

#### 改版：审批节点的「审批模式」固定为单签，编辑者不可选（无 schema 变更）
- **用户要求**：*"审批节点的审批模式只能是单签，禁止流程编辑者选择"*
- **改动**：
  · **面板**（`app/panel/node-schemas.ts` 的 APPROVAL 字段）：删掉「审批模式」下拉（原 5 选项：
    单签 / 串行 / 并行全员 / 或签 / 会签）**及其连带的「会签规则」四个字段**
    （通过规则 / 通过比例 / 必须票数 / 弃权处理 —— 原先按 `approvalMode === COUNTERSIGN` 显隐），
    改为一条说明：「**固定为「单签」**：一人办理即通过。需要多人集体决策（会签）请改用
    「会签审批」节点 —— 它有通过规则与每个人的办理情况」
  · **加载归一**（`dsl-core/schema.ts`）：新增 `normalizeApprovalMode` —— 审批节点的
    `approvalMode` 一律规范成 `SINGLE`，并清掉只对会签有意义的 `passRule`。
    否则会变成"面板上没有这个开关、编译却照旧出多实例"的看不见却生效的配置
  · **会签语义完整保留在「会签审批」节点**（自带通过规则、各人办理情况、表单可换）——
    这样"审批"与"会签"各自只有一种语义，编辑者不会选错
- ⚠️ **行为变化**：老模板里配过串行 / 并行全员 / 或签 / 会签的**审批节点**，加载保存后按单签跑
  （本环境 v6 的「需求与选型初审」原本是「或签（一票通过）」→ 现在是单签）。
  **已部署的实例不受影响**（它们用的还是当时那一版定义）。
- **未动**：编译层（仍按 `approvalMode` 生成多实例，用于兼容导入的老 BPMN）与校验层
  （`checkCountersignAggregation` 保留，同样只对导入的老数据生效）
- **测试**：`field-schema.test.ts` 两条"会签规则可见性"用例改写为针对「会签审批」节点，
  新增「审批节点已固定单签：不再有审批模式下拉与会签规则字段」，并删掉一条已不存在的
  "审批 + 会签模式"用例 —— **22 文件 / 401 用例全过** ✓
- **实测**（浏览器 · CKAI-TECH · 元器件引入（认定）流程 v6）：审批节点面板「审批规则」区
  只剩一条说明 ✓；原「审批模式」下拉与四个会签规则字段**全部消失** ✓；
  右上「校验」计数 = 0 ✓（截图确认）

#### 改版：办理节点的「交付物」改成「是否需要交付件」+「业务对象类型」多选（DSL 字段替换）
- **用户要求**：*"修改成是否需要交付件，如果需要交付件则绑定业务对象类型，业务对象类型下拉，可多选"*
- **改动**：
  · **面板**（`app/panel/node-schemas.ts` 的 TASK 字段）：删掉「交付物」（`control: 'tags'`，
    让人手填文档类型 code），换成两个字段 ——
    「**是否需要交付件**」开关（`needDeliverables`）；
    「**业务对象类型**」（`control: 'multi-select'` + `optionsSource: 'OBJECT_TYPES'`，
    候选来自对象类型定义清单，与「绑定业务对象」同源），仅在开关打开时显示且 `clearOnHide`
    —— 关掉开关时清空绑定，不留"看不见却仍然生效"的配置
  · **DSL**（`dsl-core/schema.ts`）：`TaskNode.deliverables` 换成
    `needDeliverables?: boolean` + `deliverableObjectTypes?: string[]`，zod 同步。
    老模板里的 `deliverables` 由 zod 默认丢弃（未声明键直接 strip），加载即清干净，
    不需要额外的归一函数
  · **编译 / 解析**（`bpmn-compiler/{namespaces,compile,parse}.ts`）：扩展属性名
    `ckplm:deliverables` → `ckplm:needDeliverables` + `ckplm:deliverableObjectTypes`
- **兼容性**：`ckplm:deliverables` 在后端**没有任何读取**（它只是设计期声明，编译期附带的那条
  "需运行期校验"提示也一并更新），所以这次替换不影响运行期行为；导入老 BPMN 时该属性被忽略
- **测试**：`field-schema.test.ts` 新增「办理节点：交付件改成『是否需要』开关 +『业务对象类型』多选」
  （含"关着时不显示 / 开着时显示 / 有 clearOnHide / 旧字段已退场"四条断言）——
  **22 文件 / 402 用例全过** ✓
- **实测**（浏览器 · CKAI-TECH · 元器件引入（认定）流程 v6 · 办理节点「申请填报与完善」）：
  · 「数据」区只剩「是否需要交付件」开关 ✓；旧的「输入文档类型 code」输入框消失 ✓
  · 打开开关 → 出现**多选**下拉（`.ant-select-multiple` 0 → 1）✓，
    placeholder「选择业务对象类型（可多选）」✓
  · 右上「校验」计数 = 0 ✓（不保存，零副作用）

#### 优化：同层节点（差不多在一条线上）只走左右中心锚点 —— 不再退到上下绕一圈
- **用户反馈**：*"当两个节点差不多在一条线上时，锚点应该自动优化到连接两个节点的左侧或右侧的
  中心锚点上"*（截图：需求与选型初审 与左右两个服务节点同层并排）
- **改动**（`canvas/node-style.ts` 的 `sideScores`）：**同层判定** = 两边的垂直投影有重叠
  （"差不多在一条线上"）。命中时把 `top` / `bottom` 的得分直接压成 **-1（背对）**：
  · 出边的 `facingPortSides`（≥0 过滤）会**直接排除上下** → 只落在左/右的中心锚点上；
  · 入边的 `rankPortSides` 把上下排到朝向侧之后，只在左右都被占满时才可能兜底用到；
  · 给 -1 而不是 0：0 会与其它 0 分平局，仍可能被基准序（{@link PORT_SIDE_ORDER}）挑中；
  · 左右都被占满时宁可**复用主方位**（两条线重叠一小段、随后由 manhattan 自然分开），
    也不绕到上下 —— 与 `facingPortSides` 的既有取向一致。
- **不同层的连线不受影响**（上下方位照常参与排序）—— 有一条用例专门钉住这点，避免改过头
- **测试**：`canvas.test.ts` 新增 2 条（同层只用左右 / 不同层照常）——
  **22 文件 / 405 用例全过** ✓
- **实测**（浏览器 · CKAI-TECH · v11）：`需求与选型初审` 与左右两个服务节点之间是
  **水平直连**（左出右进、落在中心锚点）✓（截图确认）

#### 优化：复杂流程的自动走线 —— 回边（驳回这类）的车道改按**目标方位**选择
- **用户反馈**：*"复杂的流程画线还需要优化，比如自动判断合理的锚点等"*
- **先盘点现状**（锚点自动分配已经有的规则，避免重复造轮子）：
  · 方位按**半尺寸归一化**的中心位移排序（`sideScores` / `rankPortSides`）—— 宽节点不吃亏；
  · 出边只从"朝向目标"的方位里挑（`facingPortSides`），且优先挑**还有空槽**的方位
    （每方位最多 3 个锚点，见 `portSlotCount`）；
  · 入边按朝向排序后同样逐个占空槽 —— 多条入边不会汇在同一个点（"从哪来"看得清）；
  · 用户手钉的锚点（`edge.anchor`）永远优先于自动分配；
  · 正交路由用 manhattan（`excludeTerminals` + 放宽 `maxLoopCount`，见 `EDGE_ROUTER`）。
- **本轮改掉的一处确定性短板**（`canvas/render.ts`）：**回边的"车道"原先只看源在画布中线的哪侧** ——
  于是"目标在源的右上方、而源恰好位于中线左侧"的回边会**先向左出、再向上、再向右绕回来**，
  复杂流程里就是一条绕半个画布的线。现在：
  · 优先按**目标在源的哪一侧**定车道（回边终究要回到目标，朝它那侧绕最短），
    容差取两节点宽度的四分之一，小于它视为"同一列"；
  · 同一列时才退回"源在画布中线哪侧" —— 那条判据是给"多条回边分列画布两侧、不互相压住"用的；
  · 用户手钉的锚点照旧优先。
- **测试**：`canvas.test.ts` 新增「回边的车道朝目标那一侧：目标在右上方时不再从左边绕一圈」
  —— **22 文件 / 403 用例全过** ✓
- **后续可继续的**：斜对角时的方位取舍、多分支扇出的优先级等。欢迎带**具体截图** ——
  有截图就能对着那一处算，比泛泛调参数可靠。

#### 新功能：开始节点的「节点表单」真正生效（发起时就能填申请单）
- **用户提问**：*"为什么开始节点可以选择表单模板，具体是什么意思？"* —— 查证下来这条配置
  一直**没有任何消费方**，属于"看得见、选得了、不生效"：
  · 编译层 `emitStart()` 老老实实写成 `<startEvent flowable:formKey="...">`（库里 v11 原文可见）；
  · 前端 `StartProcessModal.vue` 只传 `processKey/businessKey/entities/variables` —— 全文件搜
    `formRef/formKey/dsl` 命中 0；
  · 后端 `startProcess` 不收表单，全仓库搜 `StartForm/startFormKey` 命中 0；
  · 任务侧读的是 `task.getFormKey()`（userTask），与 startEvent 无关。
- **接通（设计期 → 编译期 → 运行期同一条链）**：
  · **后端**：新增 `GET /api/workflow/instance/start-form?processKey=`（`ProcessStartFormVO`，
    与办理侧 `GET /workflow/task/{id}/form` 成对）—— 取**最新部署版**的 BPMN 原文读开始事件的
    `flowable:formKey`（`org.flowable.bpmn.model.StartEvent` 没有 formKey 字段，读部署产物
    才不失真），连同 `startEventId`、该部署版的 `dslJson` 一并返回；未部署返回 200 + 空字段。
    **查询显式带租户** —— 漏掉会跨租户撞上同 key 的其它部署（实测 5 个租户各一份，
    报 `Query return 5 results instead of max 1`）。
  · **前端派发**（`taskForms/index.js` 的 `resolveStartFormView`）：只派发**自足模板**
    （`START_CAPABLE_COMPONENTS`：元器件引入申请单 / 阶段门决策申请单 / 阶段门决策记录单 ——
    它们不读 `props.taskId`）。认证表 / 评审表 / 会签表靠 `getTaskContext(taskId)` 带出关联器件与
    流程变量，发起时没有任务，返回 null **不渲染**（渲染一张取不到数据的空表比不渲染更糟）。
  · **发起弹框**（`StartProcessModal.vue`）：解析出流程后取 start-form，新增「申请信息」段；
    上下文做成与办理侧**同形**（`taskDefinitionKey ← startEventId`），模板组件无需区分两种来源；
    提交前调模板 `collect()` 校验并收集，字段并入发起变量（`...formVariables` 在前、
    `lifecycleStatus` 等系统口径在后 —— 表单覆盖不了系统事实）。配了"填不了"的表单时
    给明确说明（指出该表依赖流程实例上下文、应换自足申请单），不让人对着空框发呆。
- **落地（CKAI-TECH）**：设计器把开始节点表单从「新器件申请认证表」改为「元器件引入申请单」，
  保存为模板 v13 并部署（引擎定义 v13）。
- **端到端实测**：
  ```
  GET /instance/start-form?processKey=component-introduction
    → { formKey: "component-introduction-apply", startEventId: "start_1",
        version: 13, dslJson: 5394B } ✓
  器件 PART-202610-0001「发起器件认证」弹框：显示「元器件引入申请单 · 发起前填写」，
    内含 需求名称 / 目标参数要求 / 使用场景 … ✓（截图级证据：弹框文本已含这些字段）
  弹框内填写并点「发起」→ 实例 b774923d-… 启动 ✓
  act_ru_variable（该实例，前缀 ckplmComponentIntro_）6 个变量全部落库 ✓：
    partName=车规级 100nF 0402 X7R 陶瓷电容 / targetParams=100nF±10% 50V X7R 0402 -55~125℃
    usageScenario=SW-2026 主控板 电源去耦 / targetQualification=QUALIFIED / needAlternate …
  ```
- **测试**：`task-form-registry.test.ts` 新增 5 条（未配 → null；自足模板 → 组件；依赖 taskId 的
  三张行业表 → null；内置办理表单 → null；未知 code → null 且**不落兜底**「同意/驳回」）；
  **全套 29 文件 / 497 用例通过** ✓
- **说明**：没在开始节点配表单的老流程 → 发起弹框什么都不多显示（零影响）。
  遗留同类项：开始节点的「绑定业务对象」也是"编译进 `ckplm:binding`、运行期暂无消费方"的声明，
  需要时按本轮的同一模式接通。

#### 改进：申请单「使用场景」从手填改为**选择产品系列 / 型号（可多选）**
- **用户要求**：*"截图中的使用场景是选择系列和型号，可以多选"*（发起弹框里「元器件引入申请单」）
- **改动**（`ComponentIntroductionApplyForm.vue`）：
  · 「使用场景」由一个 textarea 改为**两个多选框**：产品系列（`getProductLines`）、
    产品型号（`getProductModels`）；都支持搜索、清空、多选。系列与型号是两级
    （型号的 `parentOid` 指向系列），分两个框是因为有时只选到系列、型号又要跨系列多选；
    型号选项 label 带所属系列名（`SW-2026（SW 系列）`），跨系列勾选不会认错同名型号。
  · 必填口径不变：**系列、型号至少选一项**（原来就是必填，只是从"文本非空"变成"至少选一项"）。
  · 选项加载失败时两个框置灰 + 显式说明（且拦截提交并说明原因），
    不静默给人一个空下拉猜；**列表为空与请求失败分开判断**（企业还没建系列/型号是正常状态）。
- **存储（三个变量，`ckplmComponentIntro_` 前缀）**：
  · `usageScenario` —— 人类可读文本 `使用系列：A、B · 使用型号：X、Y`，
    评审 / 会签摘要卡片（`QualificationRequestSummary.vue`）按字符串直接显示，**无需改动**；
  · `usageSeries` / `usageModels` —— oid 逗号连接，给"按型号统计用量、按系列聚合供应风险"
    这类后续需求留结构化入口（显示文本随时可由 oid 重算，oid 丢了找不回来）。
- **可测性**：选项组装与文本拼接抽到 `taskForms/introduction-usage.ts`（纯函数）——
  本项目没有组件测试基础设施（无 `@vue/test-utils`），抽出来才测得到。
  新增 `introduction-usage.test.ts` 7 条：系列选项（name 优先/退回 code/过滤脏行）、
  型号选项（label 带系列名）、文本拼接（系列+型号 / 只系列 / 只型号 / 都空 → 空串 /
  查不到的 oid 原样保留不静默丢失）、oid 留痕（空列表给空串）。
- **落地实测（CKAI-TECH，真实发起）**：
  ```
  弹框「使用场景」＝ 两个多选框 ✓（placeholder：选择产品系列（可多选；也可只选型号）/ 选择产品型号（可多选））
  系列下拉实测列出「X系列智能手表」✓   选中后提交 → 实例 4304d84e-… ✓
  act_ru_variable：
    ckplmComponentIntro_usageScenario = 使用系列：X系列智能手表                    ✓
    ckplmComponentIntro_usageSeries   = b6bba136-b7bb-443c-986d-c306b2695779      ✓
    ckplmComponentIntro_usageModels   = （空）                                     ✓
  ```
- **注意（数据侧）**：CKAI-TECH 目前只有 1 个产品系列、**0 个产品型号** ——
  「选择产品型号」下拉此刻是空的，在「系列/型号」里建好型号后即可选。
- **测试**：**全套 30 文件 / 504 用例通过** ✓

#### 改进：发起流程采用向导（wizard）—— 开始节点配了流程表单时分两步（+ 设计期防误配提示）
- **用户要求（两轮）**：先 *"点了发起后重新弹出一个画面再填写申请单"* → 首版按独立弹框实现；
  随后 *"如果开始节点配置的有流程表单，则采用 wizard 的形式体验会更好一些"* → **改为向导**：
- **最终形态（`StartProcessModal.vue`）**：
  · 只在开始节点配了**能填的**申请单时走向导（`hasForm`）；没配的老流程不显示步骤条、一步直达；
  · 弹框内 `a-steps` 两步：「选择业务对象」→「填写申请信息 ·〈表单名〉」；
  · 底部按钮自绘（不再用 antd 的 ok/cancel，按钮要随步骤变）：
    第 1 步 [取消] [下一步]，第 2 步 [取消] [**上一步**] [提交发起]；
  · 申请单用 **v-show 保持挂载**：回上一步改业务对象再回来，填过的内容不丢，`collect()` 随时可用；
  · 「下一步」时复检"同类型 + 同状态"；「提交发起」先 `collect()` 校验，通过才真正发起；
  · 逻辑拆三段：`advance`（前进/单步发起）→ `submitApply`（第 2 步：校验 + 收集）→
    `doStart(formVariables)`（真正发起，单步 / 向导共用）；步数不跨次残留（重开弹框回到第 1 步）。
- **设计期防误配提示（`node-schemas.ts`）**：开始节点的「节点表单」补上口径 —— 发起时还没有任务实例，
  「元器件认证申请」「技术评审申请单」这类靠任务带出关联器件 / 流程变量的表单**在发起页填不了**；
  请选「元器件引入申请单」「阶段门决策申请单」这类不依赖任务上下文的申请单。
  与其等到发起时才解释（运行期那条提示），不如在设计器里配置时就说清。
- **落地实测（CKAI-TECH）**：
  ```
  开始节点表单改回「元器件引入申请单」→ 保存为模板 v20 并部署 ✓
  第 1 步：步骤条「选择业务对象 ●— 填写申请信息」，按钮 [取 消] [下一步] ✓，表单区不可见 ✓
  点「下一步」→ 第 2 步：步骤条高亮「填写申请信息」，按钮 [取 消] [上一步] [提交发起] ✓
    内容「元器件引入申请单 · 发起前填写」，字段含使用场景（系列/型号多选）✓
  点「上一步」→ 回到第 1 步，按钮恢复 [取 消] [下一步] ✓
  第 2 步填写 →「提交发起」→ 实例 8468b446-… ✓
  act_ru_variable：ckplmComponentIntro_ 8 个变量全部落库 ✓
    （partName=22uF 1206 X7R 陶瓷电容 / usageScenario=使用系列：X系列智能手表 / usageSeries=b6bba136-…）
  ```
- **测试**：**全套 30 文件 / 504 用例通过** ✓
- **另记（排查结论）**：本轮"开始节点表单不适用于发起"的提示**不是故障** —— 版本历史显示
  v14~v19（21:03–21:43，Roney.Liu）把开始节点的表单在「引入申请单 / 认证表」之间反复改写，
  代码侧无任何程序化改写（只有"未写值时显示默认"的显示逻辑，且认证表不是开始节点的默认值）。
  典型成因是**多个设计器页面同时开着**：旧画布保存时把旧值一起提上去。
  改流程时的经验：保存前确认没有别的设计器标签页；保存后刷新一次再继续改。

#### 改名：三个认证族表单统一到「元器件认证…」体系
- **用户要求**：*"component-qualification-apply 组件的 name 修改成元器件认证申请；
  component-qualification-review 组件名称修改为元器件认证审批；
  component-qualification-countersign 组件名修改为元器件认证会签"*
- **改前 → 改后**：
  ```
  component-qualification-apply       新器件申请认证表    → 元器件认证申请
  component-qualification-review      元器件引入评审表    → 元器件认证审批
  component-qualification-countersign 元器件引入会签表    → 元器件认证会签
  ```
- **显示名的来源链，三处都改了**：
  · 行业包 `electronics-core/v1.0.0/processes/forms/forms.json` 的三条 `name`（新租户安装用）；
  · 数据库 `ck_process_form_template` 三租户共 9 行（`UPDATE 3 / 2 / 2`）——
    **当前生效的显示名读的是这里**（行业包重装只 SKIP、不覆盖已存在记录）；
  · 代码/文案里的旧名引用：`node-schemas.ts`（开始节点 help，用户可见）、`forms.ts`
    （内置会签模板 description 里的举例，用户可见）、`form-templates.ts`、
    `taskForms/index.js`、三个表单组件与 `QualificationRequestSummary.vue` 的类注释、
    两个测试的夹具 label。
- **实测**：设计器会签节点「节点表单」下拉 = `["会签（内置）", "元器件认证会签"]` ✓
  （下拉、模板接口、办理卡片标题同源，都读 `ck_process_form_template.name` → 一并生效）；
  **全套 30 文件 / 504 用例通过** ✓   lint 0 ✓
- **备注**：另有文件被改过 —— `component-introduction-apply` 的 name 在包里曾从
  「新器件申请认证表」变成「新器件申请表认证」（非本轮改动、与库中值也不一致），
  本轮未动该表；若最终决定**去掉**这张表，上一轮提的两个选项（彻底删除 / 只停用、
  发起时改填哪张）还等你定。

#### 移除：「元器件引入申请单」（component-introduction-apply）整条去掉
- **用户要求**：*"继续去掉 component-introduction-apply 流程表单组件"*
- **删除范围（四层全清，未留悬空引用）**：
  · 行业包 `electronics-core/v1.0.0/processes/forms/forms.json` 该条目（note 里"6 张"→"5 张"）；
  · 前端组件 `ComponentIntroductionApplyForm.vue` 及其专用逻辑 `introduction-usage.ts`、
    专用测试 `introduction-usage.test.ts`；
  · 视图注册表 `taskForms/index.js`：import / `FORM_COMPONENT_VIEWS` 项 /
    `START_CAPABLE_COMPONENTS`（发起可填名单，现只剩阶段门两张）；
  · 数据库 `ck_process_form_template` 三租户记录（`DELETE 3`）。
- **测试调整**：`task-form-registry.test.ts` 里"自足的行业申请单 → 拿到组件"这条改用
  「阶段门决策申请单」（`GateDecisionApplyForm`）继续覆盖同一行为（自足模板能派发到发起页）。
- **顺带修掉一处误导提示**（本轮删除正好暴露）：开始节点配的表单**已被删除/停用**时，
  发起页原来会说"需要流程实例上下文（关联器件或流程变量）"—— 原因是错的。现在分两种情形：
  ① 模板不存在 → "该表单已不存在（可能被删除或停用），请重选或清空"；
  ② 模板在、但发起时填不了 → 原来的上下文说明。
- **实测**：
  ```
  ck_process_form_template 剩余 7 行（元器件认证申请 / 审批 / 会签 各租户）✓ 被删记录已无 ✓
  流程模板 v21 的 BPMN：对被删表单 code 的引用 = 0、对组件名的引用 = 0 ✓
    （v21 开始节点当前配的是「元器件认证申请」—— 22:14 由 Roney.Liu 调整，非本轮改动）
  **全套 29 文件 / 497 用例通过** ✓（少的那 1 文件 7 用例正是删除的专用测试）
  ```
- **待定（问了两次，等答复）**：开始节点现在配「元器件认证申请」，它靠任务上下文取关联器件，
  **发起页填不了**（会显示上面那条说明，也不出向导）。三选一：
  ① 给它加"发起模式"（用发起时选中的器件代替任务上下文）—— 向导保留、发起即填认证表；
  ② 开始节点表单留空 —— 发起不填表单，申请信息都到流程节点里填；
  ③ 保持现状。

#### 改进：元器件认证申请改为「双来源」表单 —— 发起节点也能用（用户定）
- **用户要求**：*"将其修改为 start 节点可用的表单，数据有两种来源，一个是发起流程时所选择的对象，
  如果在办理或其他人工节点使用则来源于流程关联的 PBS（process_business_set）"*
- **改动（三处）**：
  · `ComponentQualificationForm.vue` 新增 `resolveEntityRefs()` ——
    有 `taskId` 走 `getTaskContext(taskId)`（**流程关联的 PBS**，即 `ck_process_entity_set`，
    以实例实际关联为准）；无 `taskId`（发起场景）走表单上下文 `form.entities`
    （**发起弹框里选中的对象**）。拿到 ref 之后一切（器件详情 / AML / AVL / MPN 汇总）
    两条来源完全共用 —— 它们只吃 `entityOid`。
    配套：`isPartRef()` —— 办理来源带 `rootTypeCode` 按它判；发起来源没有，先按器件试一次
    （非器件查空即被吞掉，模板"MPN 表为空不渲染"，不会露出空表头）；
    `watch(form.entities)` —— 回上一步改了对象清单再回来，器件区跟着刷新（表单是 v-show 保持挂载的）。
  · `StartProcessModal.vue`：`startFormPayload` 带上 `entities: startEntityPayload(entities.value)`
    —— 与提交给后端的实体清单是**同一份选择**。
  · `taskForms/index.js`：`START_CAPABLE_COMPONENTS` 加入 `ComponentQualificationForm`。
- **测试**：原"依赖任务上下文的表单 → null"拆分 —— 评审表 / 会签表仍为 null；
  新增"元器件认证申请是双来源表 → 发起页给视图"（`task-form-registry.test.ts` 16 → 17 条）。
- **端到端实测（CKAI-TECH · 器件 PART-202610-0001「200欧电阻」）**：
  ```
  发起弹框：向导两步回归 ✓（[取消][下一步] → [取消][上一步][提交发起]）
  第 2 步「元器件认证申请」：器件区 PART-202610-0001 / 200欧电阻 ✓
    MPN 表 2 行 ✓（来自"发起时选中的对象"，无任务上下文）
  选「使用位置 = X系列智能手表」→「提交发起」→ 实例 48c7b691-… ✓
  act_ru_variable（7 个 ckplmQualify_*）：usageLocation=X系列智能手表 /
    usageLocationOid=b6bba136-… / targetQualification=QUALIFIED ✓
  ck_process_entity_set：c537a477-… | 版本 A-Z | ELECTRONIC | PART ✓（版本由后端解析，前端不传）
  首个任务：设置审批人（内置表单）；认证表的办理侧代码路径未改动
  **全套 29 文件 / 498 用例通过** ✓   lint 0 ✓
  ```
- **语义说明**：发起时按"选中的对象"带出器件与 MPN（此刻还没有实例关联可读）；
  实例创建后 `ck_process_entity_set` 由后端按**同一份选择**写入 —— 两侧口径一致；
  办理时一律以**实例实际关联**为准（发起失败重试、后续增删对象等极端情形下更可靠）。

#### 修复：「分类」在元器件库「IPN 申请」弹框里消失（10-11 那次剔除逻辑误伤）
- **用户反馈**：*"分类为什么没有了，是不是之前修改分类的显示的时候弄错了"*（截图：企业资源 → 元器件库 → + IPN申请）
- **根因（确实是那笔改动的误伤）**：
  · 2026-10-11 的「未绑定分类 → 把分类字段**连 label**一起剔除」
    （`DynamicForm.stripUnboundClassificationFields`）**只看外部传入的 `props.typeDefinitionOid`**；
  · 而组件自己本来就会按 `entityCode` 反查 typeOid（`resolvedTypeOid`，模板里也用
    `resolvedTypeOid || typeDefinitionOid` 往下传）；
  · `ComponentLibrary.vue` 调 DynamicForm 时**没传 `type-definition-oid`** → `bound` 恒为 false
    → 「分类」被**无条件**删掉（连"该不该剔"都没判）。
  · **数据是好的**：`ck_type_classification_link` 里 ELECTRONIC 在三个租户都绑定了分类
    （电子元器件 / tpl-electronic）—— 所以这不是配置问题。
- **修复（`DynamicForm.vue`，两处）**：
  · `stripUnboundClassificationFields` 判定改用 `props.typeDefinitionOid || resolvedTypeOid.value`；
    并**拿不到 typeOid 时不再动字段** —— "判不了"不等于"未绑定"：控件自身的 `hideWhenUnbound`
    兜得住未绑定的展示，整块删除却是不可逆的；
  · `loadLayout` 在拉布局前 `await resolveTypeDefinitionOid()`，别把"还没解析出来"当成"未绑定"。
- **实测**（CKAI-TECH · 元器件库 · 点「+ IPN申请」）：
  ```
  弹框字段：编号 / 名称 / 版本 / 【分类】 / 所属容器 / 单位 / 来源 / 规格 ✓
    （修复前该清单里没有「分类」）
  ```
- **影响面**：全部 DynamicForm 调用方（没传 typeOid 的一律自愈，反查基于 entityCode）；
  类型**确实未绑定**时仍照旧剔除 —— 10-11 的初衷不丢（部件 / 电气件 / PCBA 那些类型依旧不显示空区块）。

#### 新功能：发起流程的申请信息支持「保存草稿」（关掉弹框不白填，下次自动恢复）
- **用户要求**：*"发起流程的时候，start 节点如果配置了表单，填写了一大堆内容后，如果不小心关闭了就
  没有，能否支持保存草稿"*
- **交互**：向导第 2 步底部多一个「**保存草稿**」（位于 [取消] 与 [上一步] 之间，仅当该表单支持草稿时出现）；
  下次打开同一器件 × 同一流程 × 同一张表时**自动恢复**，并在表单上方提示
  「已恢复 今天 14:20 保存的草稿 [清除草稿]」。
- **为什么存本地而不是服务端**：要挡的是"几分钟内手滑关掉"，不是跨设备续填 —— 服务端草稿要新建表、
  定租户隔离、写清理策略，成本远大于收益。提交成功后立刻清掉本地那份。
- **改动**：
  · 新增 `utils/startFormDraft.js`（纯函数 + 可注入 storage）：键 = 租户 + 用户 + 流程 key +
    **起点业务对象** + 表单 code（不同器件/不同表各存各的，绝不串）；TTL 7 天，过期读取时顺手清；
    坏数据/不可用存储一律当"没有草稿"，绝不影响发起主流程。
  · `ComponentQualificationForm.vue` 新增**可选契约** `snapshot()` / `restoreDraft(data)`
    （快照就是 model 的浅拷贝 —— 不校验、不转变量名，草稿要能容纳"刚填一半"；
    用 model 键名可以直接回填，省一层易错的双向映射）。**未实现该契约的表单不显示按钮**、也不会被存。
  · `StartProcessModal.vue`：进第 2 步时探测能力 + 尝试恢复并提示；**关闭弹框时自动存**
    （cancel / X / Esc 全覆盖）；**每 5 秒定时快照**兜住"刷新 / 崩溃"（这类不走 close）；
    提交成功先清草稿再关（否则 close 里的保存会把刚提交的内容又写回去）；
    「清除草稿」= 清本地 + 重建表单（回空表单）。
- **测试**：新增 `utils/__tests__/startFormDraft.test.js` 8 条（键不串 / 过期丢弃并顺手清 /
  坏数据当没有 / 空对象不算内容 / 时间文案 今天 vs 日期 / 存储不可用不抛错）；
  **全套 30 文件 / 506 用例通过** ✓   lint 0 ✓
- **端到端实测（CKAI-TECH · 器件 PART-202610-0001）**：
  ```
  填「使用位置 = X系列智能手表」→ 点「保存草稿」→ 提示"草稿已保存…" ✓
  点「取 消」关闭 → 重新打开「发起器件认证」→ 下一步：
    使用位置 = X系列智能手表（内容已恢复）✓
    顶部提示「已恢复 今天 22:54 保存的草稿 [清除草稿]」✓
    footer = [取 消] [保存草稿] [上一步] [提交发起] ✓
  ```
- **说明**：另两张可发起的表单（阶段门决策申请单 / 决策记录单）暂未实现草稿契约 —— 需要的话
  按同一契约各加 `snapshot/restoreDraft` 即可（按钮会自动出现）。

#### 修复：「重新提交申请」等办理节点没有回填之前填过的申请内容
- **用户反馈**：*"重新提交元器件认证申请页面，还是使用的元器件认证申请表单，但没有将之前填写的数据带过来"*
- **根因**：`ComponentQualificationForm` 此前只解决"器件引用"的两种来源，**表单自己的字段从未读过流程变量** ——
  于是「重新提交申请」（其存在意义就是"改一改再交"）呈现一张空表，
  已经躺在流程变量里的内容被当成不存在。
- **修复（`ComponentQualificationForm.vue`）**：新增 `restoreFromVariables()`，挂载时与 `loadEntities()` 并行：
  · 取数链：`getTaskContext(taskId)` → `ctx.process.id` → `getProcessInstanceDetail(id).variables`
    （与 `QualificationRequestSummary` 同一路径、同一容错口径）；
  · 只跑**有任务**的场景（发起侧没有"之前"，首次发起本就该是空的）；
  · **不覆盖已填的值**（发起侧的草稿恢复排在它之后、优先级更高）；
  · 容错：变量形状兼容 `{name:value}` 与 `[{name,value}]`；多选值兼容数组与 JSON 字符串；
    失败静默 —— 回填是锦上添花，不该挡住填写；
  · 兼容两种前缀：`ckplmQualify_*` 与本表历史上的 `ckplmComponentIntro_*`（在途老实例仍在用）。
- **实测（复用用户那个在途任务 · 实例 58905d5b-… · 节点「重新提交申请」）**：
  ```
  接口：GET /workflow/task/{id}/context → 200；GET /workflow/instance/{id} → variables 为 {name:value}
    usageLocationOid=b6bba136-… / avlImpact="没有替代" / certRequirements=["R_CE","R_GB"] / needAlternate=false
  浏览器打开 /workflow/task/8a30fc69-…（办理页）实测：
    使用位置 = X系列智能手表 ✓        认证要求 = CE、国标（GB）✓
    替代与 AVL 影响 = 没有替代 ✓      目标准入状态 = QUALIFIED ✓
  ```
- **说明**：验证为**只读**（未提交，未改变该任务状态）。
- **测试**：**全套 30 文件 / 506 用例通过** ✓   lint 0 ✓

#### 修复：审批 / 会签侧的 MPN「供货渠道」hover 没有明细（与认证申请侧不一致）
- **用户反馈**：*"元器件认证审批组件，MPN 栏，当数据移到渠道上的时候没有对应的渠道内容展示，
  类似元器件认证申请中渠道的展示效果"*
- **根因**：渠道明细 popover 只写在「元器件认证申请」里；审批 / 会签侧的 MPN 表由
  `QualificationRequestSummary`（申请信息摘要卡）渲染，那里「N 家」只是一个光秃秃的 tag ——
  同一份明细两处各写一遍，自然漏一处。
- **修复（收口成一份，不再漂移）**：
  · 新增共享组件 `AvlChannelPopover.vue`（props：`avls` / `mpn` / `supplierNames`）——
    供应商、料号、单价、交期、MOQ·SPQ、首选；样式随组件走；
  · `ComponentQualificationForm.vue` 与 `QualificationRequestSummary.vue` 都改用它
    （认证表删掉内联模板、`.cqf-avl*` 样式与已无引用的 `supName`）；
  · 摘要卡补拉供应商名录（`listSuppliers` → `supMap`），与认证表同一口径 ——
    否则 hover 面板里只会显示供应商 oid。
- **实测（接口级 · 数据同源）**：
  ```
  GET /mpn/parts/c537a477-…/aml      → 2 个 AML 行、每行 avls=1
      （RC0805JR-0710RL：供应商 0081e23d…、首选、单价 15、交期 40）✓
  GET /mpn/parts/c537a477-…/summary  → amlCount=2 / supplierCount=1 / singleSource=true（R-SH-11）✓
  两条链路现在渲染的是同一个组件、同一份 record.avls
  ```
  · 说明：审批 / 会签的**办理页**是他人名下任务（assignee=Roney.Liu），sysadmin 打开时页面不渲染任务内容，
    故未做浏览器端截图级验证；组件与取数链与认证申请侧**完全相同**（后者此前已实测显示渠道明细）。
- **测试**：**全套 30 文件 / 506 用例通过** ✓   lint 0 ✓
- **另记（本次验证时发现，与本条无关）**：该流程的自动化节点会把器件置为 `APPROVING`，
  而当前 ELECTRONIC 生命周期模板只允许 `DRAFT / IN_WORK / RELEASED` ——
  提交「设置审批人」时报"目标状态 APPROVING 不在该对象的生命周期模板内"。
  **流程配置与生命周期模板需要对齐**（模板加状态，或节点改目标状态），否则流程推不动。

#### 操作列图标化 + 预期角色可编辑 + 权限改按「系列/型号团队角色」（项目级）
- **用户要求**：*"操作栏直接使用两个图标，并在图标上增加提示，同时可以修改预期角色，普通用户进来后，
  根据用户在系列/型号下的角色来判断是否可以具备关联交付项、发起流程的权限"*
- **口径（用户选定）**：
  · 操作列 = **两个图标**（裁剪 + 编辑），提示在 tooltip 上；编辑弹窗里同时改「预期角色」与「强制/可选」；
  · **裁剪** = 我在该系列/型号团队里是「产品经理」；**关联交付项** = 产品经理，或我的团队角色 =
    该交付件的预期角色（谁负责谁关联）；**发起流程** = 「产品经理 / 项目经理 / IPDT 组长」；
    管理员（PLATFORM_ADMIN / TENANT_ADMIN）全豁免；
  · 预期角色下拉取本租户角色表（`ck_role`，显示「产品经理（PRODUCT_MANAGER）」码名对照）。
- **关键变化：权限从「全局角色」改为「项目级团队角色」**。此前判的是 `ck_role_member`
  里的全局角色 —— 但同一个平台管理员可能在 A 系列是产品经理、在 B 系列什么都不是。
  现在以 `ck_team_member.role_name`（中文角色名）为准，经 `ck_role`（code ↔ name）作桥与
  交付件的英文预期角色码对齐；型号继承父系列团队（与 `ProductTeamVisibility` 同口径）。
- **实现（后端 + 前端）**：
  · **新增** `ProductTeamRoleResolver`：`myRoleNamesUnder / myRoleCodesUnder / canTailorUnder /
    canStartProcessUnder / canLinkArtifactUnder / isProductOwner`（管理员豁免；
    取不到用户 / 团队 / 角色映射一律按无权限 —— 宁严勿松）；
  · `GET /api/instances` 增加 **permissions** 字段（`InstancePermissions`：
    `admin / canTailor / canStartProcess / myRoleNames / myRoleCodes`）—— 前端按钮显隐的唯一依据；
  · `tailor` 扩展 `ownerRole`（交付件改预期角色），权限改为**按实例反查归属单元**后判团队角色；
    gate / review / rule 给 `ownerRole` 明确 400；
  · **关联交付项**：`POST/DELETE /api/deliverable-artifact-links` 加同口径校验（403 + 中文原因）——
    创建与解除一致（能关联就能解除）；**发起流程**：`start-option-by-key` 里对"项目对象"判定，
    非项目对象（器件 / 文档）不在此拦；
  · **前端**：面板操作列改为图标（`stop/rollback` 裁剪、`edit` 编辑、`send` 发起）+ tooltip；
    新增编辑弹窗（预期角色下拉 + 强制项单选）；「产出物」列按 `canLink` **禁用**（数据还在，只是不能改，
    原因写在 tooltip）；权限 props 由 `canTailor` 换成 `permissions`；宿主页删掉自算的全局角色判断。
- **实测（CKAI-TECH / X系列智能手表）**：
  ```
  权限摘要：{ canTailor: true, canStartProcess: true, myRoleNames: ["产品经理"],
             myRoleCodes: ["PRODUCT_MANAGER"] } ✓  ← sysadmin 凭"团队里的产品经理"拿到权限
  改预期角色：PRODUCT_MANAGER → QA_ENGINEER → 200|success，改回 200 ✓（回读一致）
  类别校验：对阶段门给 ownerRole → 400「只有交付件有「预期角色」」✓
  UI：交付件行两个图标（stop / edit）✓；编辑弹窗「预期角色 产品经理（PRODUCT_MANAGER）」✓；
      「产出物 0 项」按钮可按（有权限）✓
  ```
- **测试**：前端全量 30 文件 / 507 用例通过 ✓；后端 `mvn compile` + 相关单测通过 ✓；lint 0 ✓
- **说明**：此前 sysadmin（仅 PLATFORM_ADMIN）会被 403；现在他恰是团队里的「产品经理」，
  因此拿到权限 —— 这正是"按项目角色判"的意义。若某用户既不是管理员、又不在任何团队，
  则该系列/型号下的裁剪 / 发起 / 关联都会被他看到禁用或拒绝（后端 403）。

#### 元器件库按钮文案：「IPN申请」→「新建IPN」
- **用户要求**：*"将IPN申请修改成新建IPN"*（企业资源 → 元器件库右上角按钮）
- **改动**（`ComponentLibrary.vue`，按钮与弹窗标题共用一份文案）：
  · `libLabel`：元器件库那一份由 `IPN` 改为 `新建IPN`；`libAction` 对元器件库置空
    —— 组合结果：**按钮「新建IPN」、弹窗标题「新建IPN」**（标准件 / 通用件仍是「标准件入库」「通用件入库」，不受影响）；
  · 提交成功提示由「IPN申请已提交：…」改为「**IPN 已创建**：…」（与新按钮口径一致）；
  · 同步三处注释（本文件两处 + `ProductLineDashboard` / `DynamicForm` 各一处）；
    另在本文件保留一句"2026-10-14 由「IPN申请」改为「新建IPN」"的由来说明。
- **实测**：`/resource` 页按钮文本 `新建IPN` ✓；点开弹窗标题 `新建IPN` ✓
- **说明**：只改文案，不改任何提交行为与字段（仍走 `createPart`，类型固定 ELECTRONIC）。

#### 实例裁剪：产品经理 + 租户管理员可对交付件 / 阶段门 / 评审 / 检查规则裁剪
- **用户要求**：*"产品经理具备对交付件、阶段门、技术评审和检查规则裁剪的权利"* —— 口径确认为
  **本项目实例层**（产品系列页的清单上直接裁）：停用 / 恢复（「本项目不适用」）+ 交付件改强制项；
  权限 = `PRODUCT_MANAGER` + `TENANT_ADMIN`（管理员兜底）。
- **查证（数据侧早就齐了，缺的是写入口 + 角色闸门）**：
  · 四类实例表都已有 `disabled` 列（`ck_deliverable` 另有 `mandatory`），实测 46 / 10 / 8 / 11 条全为未裁剪；
  · 角色 `PRODUCT_MANAGER`（产品经理）在租户里已存在；
  · 既有"裁剪"入口在**模板层**（业务配置 → 模型定义：改强制/完成条件/角色/停用，影响以后新建的项目）——
    本次做的实例层与之是两层，各管一段、互不覆盖。
- **实现（后端四层 + 前端三处）**：
  · **Mapper**：四类实例各加 `updateDisabled`（交付件另有 `updateMandatory`）—— 只写该字段 + `updated_at`；
  · **Service**：`MethodologyInstanceService#tailor(kind, oid, disabled, mandatory)` +
    `requireTailorRole()`（照 `ProductTeamVisibility` 的写法取当前用户角色：UserContext → 用户 → 角色码；
    **取不到 / 判定异常一律按无权限**）+ `requireAffected`（更新 0 行 = oid 不存在或不属于本租户 → 400，不假装成功）；
  · **端点**：`PATCH /api/instances/{kind}/{oid}`，body `{disabled?, mandatory?}`（至少一个字段；
    `mandatory` 只对 deliverable 有效，其余三类给了报 400）；无权限返回 code=403；
  · **前端**：`tailorInstance()` API；`MethodologyInstancePanel` 的「操作」列扩展为
    「发起（门/评审）+ 标记不适用/恢复 + 改为可选/强制（交付件）」，显隐由宿主的 `canTailor` 控制；
    被裁剪的行：名称置灰划掉 + 「本项目不适用」标签；摘要文案改为
    "共 N 项，其中强制 M 项…**已裁剪 K 项（本项目不适用）**"（强制/已完成口径**排除**被裁剪项 ——
    否则"强制 6 项"里混着 2 项本项目根本不做的，数字会误导）；
    `ProductLineDashboard` 按 `userStore.roles` 判 `canTailor` 并统一处理 tailor 事件（成功重拉实例、
    失败按后端 message 提示）。指标 / 驾驶舱不参与裁剪（展示口径，不在要求的四类内）。
- **实测（CKAI-TECH / X系列智能手表 / DLV-MKT-ASSESS 市场评估报告）**：
  ```
  权限闸门：sysadmin（仅 PLATFORM_ADMIN）→ code=403「没有裁剪权：该操作限
           「产品经理（PRODUCT_MANAGER）」或「租户管理员（TENANT_ADMIN）」」✓
  后端往返（临时授予 TENANT_ADMIN 后）：
    disabled true→false、mandatory true→false→true 全部 200|success，回读字段逐一验证 ✓
    （验证后临时角色已删除 —— 现只剩 PLATFORM_ADMIN，库回到原状）
  UI：交付件行出现「标记不适用 / 改为可选」✓
    点「标记不适用」→ 行名划掉 + 「本项目不适用」标签 + 摘要「强制 6→5 项、已裁剪 1 项」✓
    点「恢复」→ 标签消失、按钮翻转、摘要回到「强制 6 项」✓
  ```
- **测试**：前端全量 **30 文件 / 507 用例通过** ✓；后端 `mvn compile` 0 错误 ✓；lint 0 ✓
- **说明（权限口径）**：平台管理员（`PLATFORM_ADMIN`）**不在**白名单里（按你选的口径只放产品经理 +
  租户管理员）—— 所以 sysadmin 当前会收到 403，属预期行为；若要平台管理员也能兜底，说一声即可加。

#### 「技术评审申请单」设为 Start 节点可用（发起时即可填申请信息）
- **用户要求**：*"将技术评审申请单改成 Start 节点可用"*
- **查证（数据侧早就齐了，只缺前端名单一项）**：
  · 表单声明：`ck_process_form_template` 里 `technical-review-apply` 的 `node_types = START,TASK` ✓；
  · 流程 DSL：`technical-review`（已部署 v3）的 **`start_1` 的 formRef 就是它** ✓；
  · 发起弹框：`startFormPayload` 早已把"本次选中的业务对象"以 `form.entities` 传给表单 ✓；
  · 唯一缺口：前端 `START_CAPABLE_COMPONENTS`（taskForms/index.js）**没登记** `TechnicalReviewApplyForm`
    → `resolveStartFormView` 返回 null，发起页只显示"该表单不适用于发起"。
- **改动（前端两处 + 测试）**：
  · `taskForms/index.js`：把 `TechnicalReviewApplyForm` 加入 `START_CAPABLE_COMPONENTS`，
    并按既有惯例写明理由（评审模板 / 阶段 / 关联门 / 送审材料全部手填，
    待评对象双来源 —— 没有"只有办理时才拿得到"的字段）；
  · `TechnicalReviewApplyForm.vue`：待评对象改**双来源**（对齐 `ComponentQualificationForm` 的
    2026-10-13 口径）—— `entityRefs()` 有 `taskId` 走 `getTaskContext`（以实例实际关联为准），
    无 `taskId` 走 `form.entities`（发起时选中的对象，与提交给后端的实体清单是同一份选择）；
    并加 `watch(form.entities)`：发起场景回上一步改了对象清单、再回到本步时待评对象跟着刷新
    （表单是 v-show 保持挂载的，不会重新 onMounted）。
- **实测（CKAI-TECH，X系列智能手表 → 阶段门 → TR1「发起」）**：
  ```
  弹框由「一步直达」变为两步：① 选择业务对象 → ② 填写申请信息 ✓
  ② 的标题为「技术评审申请单」（3 个下拉 + 2 个文本域：评审模板 / 所处阶段 / 关联阶段门 /
     送审材料 / 申请说明）✓    底部按钮：取 消 / 下一步 ✓
  流程信息：技术评审（TR）流程 · technical-review · v3 · 已部署 ✓
  ```
- **测试**：`task-form-registry.test.ts` 新增 1 条（技术评审申请单 → 发起页拿到组件）；
  **18 用例全过** ✓；lint 0 ✓
- **说明**：办理侧不受影响（`n_apply` 仍用同一张表，走 `taskId` 分支）；
  本表暂无 `snapshot/restore` 草稿能力（与其它行业表一致，发起页只显示"保存草稿"当组件暴露了才出现）。

#### 阶段门 / 评审：补上「发起」入口（技术评审 TR + 决策评审 DCP）
- **用户反馈**：*"阶段门中的技术评审和决策没有发起的入口"*（截图：产品系列 → 阶段门标签，
  TR1 / CHARTER 两行只有状态与决策留痕）
- **查证**：数据侧其实**全是齐的** —— `ck_gate` / `ck_review` 每行都带 `process_key`
  （TR1–TR6 → `technical-review`；CHARTER / PDCP / ADCP / LDCP → `gate-decision`），
  两个流程模板都已部署（v1；开始节点**未配表单** → 发起后一步直达）；
  缺的只是 UI 入口 —— 该面板原本是**只读呈现**（组件注释原话："状态 / 决策 / 结论的写回路属 P3"）。
- **实现（两端对称，四条改动）**：
  · **后端**：`ProcessStartOptionService` 新增 `resolveByKey(processKey, typeCode, statusCode, entityOid)`
    —— 与 `resolve` 只差"模板从哪来"（不再查类型的状态-流程绑定，按 key 直取）；
    组装与"当前租户能否发起"的判定抽成 `fillTemplateAndVersion` **两条入口共用一份**，
    `startable / reason` 语义不会漂移。新增端点
    `GET /api/workflow/instance/start-option-by-key`（返回同一个 `ProcessStartOptionVO`）。
  · **前端 API**：新增 `getProcessStartOptionByKey`。
  · **发起弹框**（`StartProcessModal.vue`）：新增 `processKey` 属性 —— 非空时走**直传分支**
    （跳过"类型 + 状态 → 流程"解析），其余链路（开始节点表单、草稿、实体清单、发起）完全复用。
  · **面板 / 宿主页**：`MethodologyInstancePanel` 的门 / 评审列加「发起」按钮
    （`canStart`：有 `processKey` 且**未决策 / 未评审**才给；已出结论的不再给入口 —— 重复发起没有语义），
    emit `start-process`；`ProductLineDashboard` 接事件后以**当前产品系列**为发起上下文
    （`typeDefinitionCode: PRODUCT_LINE`）打开同一个弹框。
- **实测（CKAI-TECH，X系列智能手表 / b6bba136-…）**：
  ```
  阶段门标签两行（TR1 / CHARTER）均出现「发起」按钮 ✓
  点 TR1 → 弹框：技术评审（TR）流程 · technical-review · 已部署 v1 ·
              业务对象 1 个（i-watch-x / X系列智能手表 / PRODUCT_LINE）✓
  点「发起」→ 实例 6ca52fee-c772-11f1-91f2-9e8489415e52 启动 ✓
    proc_def_id = technical-review:1:…，business_key = b6bba136-…（产品系列）✓
    首个任务「发起与材料准备」(n_apply) 指派 sysadmin ✓
  收尾：POST …/terminate → 在途 0 ✓
  ```
- **测试**：前端全量 **30 文件 / 506 用例通过** ✓；后端 `mvn compile` 0 错误 ✓；lint 0 ✓
- **说明**：门 / 评审的**状态与决策留痕**本身仍是只读（判定回路属 P3 —— 与本次"能不能发起"是两件事）；
  发起后门的 `status` 不会立即变，留痕由流程走完后回写。

#### 被裁剪（「本项目不适用」）的条目：只有本项目团队里的产品经理看得见
- **用户要求**：*"除了产品经理之外，标记为不适宜本项目的都不用显示"* → 追问后澄清：
  不是按**交付件的责任人**（`ownerRole`）筛，而是按**查看者在本项目团队里的角色** ——
  被裁剪的条目只有产品经理可见，团队里其他成员一律看不见。
- **改动（前端两处，后端不动 —— 权限判定本来就随 `GET /api/instances` 回来了）**：
  · `MethodologyInstancePanel.vue`：新增 `canSeeTailored` = `canTailor || admin`
    （与后端 `ProductTeamRoleResolver#canTailorUnder` 同一口径：团队角色 PRODUCT_MANAGER，或管理员）；
    `displayRows` 在它为 false 时滤掉 `disabled` 的行 —— 交付件 / 阶段门 / 评审 / 检查规则四类都生效；
    汇总文案改用 `displayRows` 计数（"共 N 项" 与实际看到的行数一致）；
    另把 `mismatched`（"本阶段无匹配、改列全集"）的判据固定在**未过滤**的 `props.rows` 上 ——
    否则"本阶段有数据、只是全被裁剪且我看不见"会被误判成阶段错位，把其它阶段的全集搬出来。
  · `ProductLineDashboard.vue`：页签计数改用 `visibleCount()`（同一口径）——
    否则会出现"页签写 9 条、只看到 6 条"。
- **实测（真实登录态，同一账号切换团队角色对照）**：
  临时建一个团队角色 = **系统工程师** 的账号（口令哈希复用 sysadmin 的，验证后已删）：
  ```
  系统工程师  交付件 (5)  5 行  「本项目不适用」0 处   汇总「共 5 项，其中强制 3 项，已完成 0 项」
  产品经理    交付件 (8)  8 行  「本项目不适用」3 处   汇总「共 8 项 … 已裁剪 3 项（本项目不适用）」
  ```
  8 − 3 = 5 ✓ —— 被裁剪的三条（市场评估报告 / 客户需求调研纪要 / 产品概念方案）只在产品经理视角出现。
- **测试**：lint 0 ✓；前端全量 31 文件 / 515 用例通过 ✓；临时账号与团队成员记录已清理（0 行）✓。
- **说明**：口径与"谁能裁剪"完全一致 —— 看得见的人就是能恢复的人（非产品经理本来也没有恢复入口）。

#### 资源库「其他」子库做出页面：左文件夹树 + 右业务对象列表（并补上它缺失的归属阶段）
- **用户要求**：*"资源库的其他构建成左侧是存储的文件夹，右侧是系统的业务对象列表，类似产品系列/型号阶段页面"*
- **查证（根因不止"没配页面"）**：
  · 前端 `EnterpriseResource.vue` 按子库 code 分派视图，未配置的（含 `OTHER`）落在通用占位页
    （"该子库暂未配置专属页面"）；
  · **更深一层**：其它 4 个资源子库（元器件 / 标准件 / 通用件 / 封装·图符）都有**归属阶段**，
    唯独「其他」库没有 —— 而 `/folders/tree` 的 `stageOid` 必填、建文件夹也要 stage，
    **没有阶段连一个文件夹都建不了**，这才是它一直空着的底层原因。
- **改动**：
  · **包声明（新租户）**：`platform-base/v1.0.0/resourceLibraries.json` 的 `stages` 增加
    `{"code":"OTHER","name":"其他","containerCode":"OTHER"}` —— 装包器（`ResourceLibraryInstaller`）
    是数据驱动的，**后端代码零改动**；
  · **存量补齐（已执行）**：幂等 SQL 给已有 4 个租户的「其他」容器各补一条阶段
    （`NOT EXISTS` 保护，重复执行安全）—— 与"提前把包声明成完整形态、存量按说明补齐"的既有做法一致；
  · **前端新组件 `GenericResourceLibrary.vue`**：左侧文件夹树 + 右侧业务对象列表 ——
    与产品系列/型号阶段页同一套交互，与封装·图符库共用 `FolderTreeNode` 与 `/folders/*` 接口
    （资源库的文件夹本来就同源）：树底常驻「新建文件夹」（**建完自动选中**）、
    加载时自动选中第一个文件夹；右侧顶部「当前位置 + 类型筛选」，列 = 类型 / 编码 / 名称 / 状态 / 更新时间，
    零组件可点进 `/part/:oid` 详情（其余类型暂无详情页，纯文本 —— 不造假链接）；
  · **四类对象合并显示**：`文档 / 工程数据 / 零组件 / 功能系统` 四个"按文件夹取对象"的接口各取一份，
    前端归一化成一个列表（字段名不统一，缺失字段留空不编造）—— 该库没有"存储类别"限制，
    文件夹里放什么都能看见，而不是逼用户先想"我要看哪类"。
- **实测（UI 截图 + 分步断言，真实登录态）**：
  ```
  页面：左「文件夹 / 暂无文件夹 / + 新建文件夹」，右「当前位置：未选择文件夹 | 全部类型 |
        类型 编码 名称 状态 更新时间 · No data」✓
  新建：点「新建文件夹」→ 弹窗（标题「新建文件夹」）→ 输入 → 创建 →
        树出现「验证-结构件图纸」且【自动选中】（当前位置随之变为该文件夹）✓
  再建一个 → 树两个文件夹、第二个自动选中 ✓
  加载：已有文件夹时自动选中第一个 ✓
  ```
- **说明**：验证用的两个文件夹已清理（0 行）；「技术文档知识库」等其它未配置专属页面的子库仍是占位页 ——
  要同样处理时复用本组件即可（分派处加一行）。
- **测试**：前端全量 31 文件 / 515 用例通过 ✓；lint 0 ✓。

#### DELIVERABLE_RATE 自动计算接上：门检查两项齐了，「交付物完成率」也从手填变自动
- **用户要求**：*"还剩一项：交付物完成率改成自动计算"*
- **后端（`gateCheck` 升级为"每门多项检查"）**：
  · 门的结果从"一条 ISSUE_OPEN"改成 `{ gateOid, gateCode, gateName, passed, checks: [...] }` ——
    `passed` = **所有检查都通过**；
  · 新增 **DELIVERABLE_RATE** 求值：读门的 `auto_checks` 里的 `{"type":"DELIVERABLE_RATE","stage":…,"minPercent":…}`，
    按该阶段统计交付件完成率，`≥ minPercent` 判过；
  · **口径与交付件面板完全一致**（status 由 `DeliverableStatusEvaluator` 重算后写回，这里只读不算）；
    分母排除「本项目不适用」（`disabled`）与 NA —— 裁掉的条目不该算进完成率；某阶段一条不剩时按 100%
    （没有要交的东西，不该拿 0% 卡住门）；
  · 返回新增 `stageRates`（各阶段完成率：percent / doneCount / totalCount）供表单按"所选门的阶段"取用。
- **前端**：
  · **门决策申请表单**：「交付物完成率（%）」也**自动带出** —— 选定阶段门后按其所属阶段取（只读 + 摘要
    "本阶段交付件 4/8 已完成（50%）—— 自动统计自交付件清单"）；未选门 → 退化为人工填写并提示；
  · **门面板**：列名「未闭环问题」→「**自动检查**」，显示 通过 / 不通过，悬停给**逐项**明细
    （✓ 无未闭环问题 / ✗ 本阶段交付件完成率仅 0%（0/8），要求 ≥ 100%）；
  · **发起前提示**：汇总**所有**不通过项（不再只提问题数），并分别指向处理去处。
- **实测（API）**：
  ```
  各阶段完成率：IPD_CONCEPT 0%(0/8) / IPD_PLAN 0%(0/8) / IPD_DEVELOP 0%(0/9) /
               IPD_VERIFY 0%(0/9) / IPD_RELEASE 0%(0/7) / IPD_LIFECYCLE 0%(0/5)   ← 分母与库一致
  逐门：✓ [ISSUE_OPEN] 无未闭环问题（要求 ≤ 0）；✗ [DELIVERABLE_RATE] 本阶段交付件完成率仅 0%（0/8），要求 ≥ 100%
  动态验证：把 IPD_CONCEPT 的 8 条里 4 条标为 DONE → 完成率立刻变 50%（4/8）→ 复原为 0%
  ```
- **实测（UI，截图）**：门面板出现「自动检查」列（TR1 / CHARTER 均显示**不通过**）；
  办理页选门（TR1）后「交付物完成率」= 0、**只读**、字段下"本阶段交付件 0/8 已完成（0%）——
  自动统计自交付件清单" ✓
- **明确未做**：其余门检查（COST_ACHIEVE 成本达成 / RULE_VIOLATION 规则违规 / CERT_COMPLETE / SUPPLY_CONFIRMED）
  仍未求值；检查规则的 `ISSUE_OPEN` 条件待接。
- **说明**：验证用的流程实例已删、交付件状态已复原；用户自建数据未动。

#### 问题闭环补上填写环节：关联对象 / 根因分析 / 处理措施有地方填了（并说明那两次 500 的来龙去脉）
- **用户反馈**：*"填了计划完成，但没有显示出来，关联对象、根因分析和处理措施在跟踪闭环的环节中，没有对应的环节可填写"*
  （截图里还有两个「服务器错误 (500)」）。
- ✗ **那两次 500 的来龙去脉（如实交代）**：正是**上一轮已修掉的那个 bug** —— `update()` 的 patch 兜底漏了
  `severity`，而它是 NOT NULL 列 → 保存整行失败 → **HTTP 500**（前端整行保存失败，计划完成自然没落库）。
  同一轮里已修复（`severity` 兜底补上 + 验证脚本补单字段编辑用例）；本轮复现确认**已不再发生**
  （日期单独保存、带日期创建均 200）。本轮另做一处加固：**前端不再传空日期串**
  （空串语义模糊，可能把已填的日期清掉），日期为空时干脆不发该字段。
- **补齐填写环节（本轮主要改动）**：
  · **关联对象**：编辑弹窗新增「关联对象类型（零组件 / 文档 / 产品系列 / 产品型号 / BOM / 其他）+
    关联对象编码」—— 此前只有抽屉里的展示位、没有输入位；
  · **根因分析 / 处理措施**：加进**「提交验证」的流转弹窗**（处理完成、请人来验 —— 正是填这两项的环节），
    并**预填现值**（本来就是接着往下写，不该让人重抄）；编辑弹窗里原有的这两项保留；
  · **类型约束落地**：问题类型声明「必须做根因分析」（`ck_issue_type_template.require_root_cause`，
    如「设计缺陷」）时，提交验证的根因**必填** —— 前端标必填 + 后端校验（400 消息带类型名）；
  · **终态可编辑**：闭环后经常要补填根因 / 处理措施 / 关联对象 —— 这些是"记录"不是"状态"，
    前后端都放开（此前"已关闭的问题不可编辑"逼人去重开）；**状态 / 关闭时间 / 验证结论仍不可经此改动**，
    且每次编辑都写时间线（如"更新了：关联对象"）；
  · 顺手：流转弹窗 / 编辑弹窗的按钮文本显式中文（流转弹窗此前显示 OK / Cancel）。
- **实测（API 全流程）**：
  ```
  创建（含计划完成 2026-10-25）→ dueDate=2026-10-25 ✓
  提交验证（未填根因，类型=设计缺陷）→ 400「问题类型「设计缺陷」要求做根因分析，提交验证前请先填写根因分析」
  提交验证（带根因+措施）→ 200，两项均落库 ✓ → 验证通过 → CLOSED ✓
  已关闭后补填「关联对象 PART-202610-0007」+ 计划完成 → 200，status 未被改、时间线多一条 UPDATE ✓
  ```
- **实测（UI，截图）**：已关闭问题的抽屉动作 = [重开问题, **编辑**]；编辑弹窗字段 =
  标题 / 类型 / 严重度 / 来源 / 关联产品 / 关联阶段门 / 责任人 / **计划完成** /
  **关联对象类型** / **关联对象编码** / 问题描述 / **根因分析** / **处理措施** ✓；
  「提交验证」弹窗含「根因分析」「处理措施」✓；列表里「计划完成 2026-11-05」正常显示 ✓
- **说明**：验证数据已清理（用户自建的那条「X系列概念方案问题」保留未动）。
  **它的计划完成仍是空的**（当时因 500 没落库）—— 现在可直接点开该问题 → 编辑 → 补填（已关闭也能编辑）✓

#### 门决策申请表单的「未关闭阻断问题数」改为自动带出（顺带补上任务上下文的业务对象）
- **用户要求**：*"需要将门决策申请表单里的'未关闭阻断问题数'改成自动带过去"* —— 即上一条里如实记录的
  未做项（表单侧拿不到产品 oid）正式落地。
- **拦路石与搬法**：任务表单上下文 `ProcessTaskFormVO` 此前不带业务对象。本轮：
  · **后端**：`ProcessTaskFormVO` 新增 `businessOid` / `businessTypeCode`；`taskForm(taskId)` 组装时
    用现成的 `entityRefsOf`（读 `ck_process_entity_set`）取**第一条**（= 发起时记的 primary）填入 ——
    与 `entityRefsOf` 同一口径：只给"引用 + 类型"，不回查业务表；
  · **前端**：`GateDecisionApplyForm` 打开时按 `form.businessOid` 调
    `GET /api/issues/gate-check?ownerOid=`（与门面板、发起前提示**同一份**数据），把 `blockOpen`
    填入字段并**置为只读**，下方绿字给出摘要（"未闭环共 N 个（阻塞 M / 提示 K）—— 自动统计自
    「持续改进」，无需手填"）；拉不到（老实例无关联 / 接口异常）→ **退化为人工填写**并在字段下明说
    "未能自动带出"，不假装有值；
  · **发起场景同形状补齐**：`StartProcessModal` 的 `startFormPayload` 同步带上
    `businessOid` / `businessTypeCode`（entities 第一条 / props.business）—— 两个场景的上下文形状
    继续保持一致（这是既有约定）。
- **查证中的一个事实（如实记录）**：`gate-decision` 流程的**开始节点没配表单**
  （`start-form` 返回 `formKey=null`）—— 所以"门决策申请表单"实际出现在**办理页**
  （任务节点 `n_apply`「决策申请与自检结果」，formKey=`gate-decision-apply`）；
  发起侧路径本轮一并补好（将来开始节点若配表也能用），但真正生效的是办理侧。
- **实测（API）**：发起 `gate-decision`（X系列）→ 待办出现「决策申请与自检结果」→
  `GET /workflow/task/{id}/form` → `formKey=gate-decision-apply`、**`businessOid` = X系列 oid**、
  `businessTypeCode=PRODUCT_LINE` → 表单将带出的 `blockOpen` 与问题数一致 ✓
- **实测（UI，真实登录态）**：办理页打开 →
  「未关闭阻断问题数」`value="2"`、**disabled=true**、
  字段下："未闭环共 2 个（阻塞 2 / 提示 0）—— 自动统计自「持续改进」，无需手填" ✓
- **说明**：交付物完成率（DELIVERABLE_RATE）仍是人工填（待完成率判定落地）；验证用的流程实例与测试问题
  已清理（用户自建的那条「X系列概念方案问题」保留未动）。

#### 门的 ISSUE_OPEN 自动检查接上：「开门前还有几个未闭环问题」从声明变成可算
- **用户要求**：*"门的 ISSUE_OPEN 自动检查（"开门前还有几个 BLOCK 问题"）—— 现在问题已挂在门上，接上就是水到渠成"*
- **现状查证**：门的 `auto_checks` 里 **ISSUE_OPEN 早就声明了**（`{"type":"ISSUE_OPEN","severity":"BLOCK","maxCount":0}`，
  实测 X系列智能手表 10 道门**全部**带这条，来自 IPD 包 gates.json）；但它此前**从未求值** ——
  门决策申请表单里的"未关闭阻断问题数"还是**人工填写**（表单提示原文："待门引擎落地后改为自动算"）。
- **后端**（`improve` 模块，现算不落库 —— 与"不编造数值"同口径）：
  · 新接口 `GET /api/issues/gate-check?ownerOid={产品系列/型号}`；
  · 统计作用域 = 该产品下的**未闭环**问题（OPEN / IN_PROGRESS / VERIFYING），分总 / 阻塞(BLOCK) / 提示(WARN)；
  · 逐门读取快照来的 `auto_checks`，找 ISSUE_OPEN 声明，按 **未闭环数 ≤ maxCount** 判过与不过；
    返回 `{ ownerOid, openTotal, blockOpen, warnOpen, gates: [逐门判定+message], items: [未闭环明细] }`；
  · **口径说明**：`severity` 字段是这条检查自身的阻塞级别（与 DELIVERABLE_RATE 等同一位置），
    故 `maxCount` 比的是**未闭环总数**而不是只数 BLOCK —— 明细里 block/warn 分开给，信息不藏。
- **前端**（两处消费）：
  · **门面板**（系列/型号页 → 阶段门）：新增「未闭环问题」列 —— 数字 = 该产品未闭环问题数，
    🟢 0 = 满足 / 🔴 N = 不满足，悬停给完整判定与 block/warn 拆分；门没声明该检查时**整列不显示**（不给空诺）；
  · **发起前提示**：点门的「发起」时，若检查不通过 → 先弹确认框
    「这道门还有未闭环问题：还有 N 个未闭环问题（含 M 个阻塞），要求 ≤ 0」（「仍要发起」/「先去处理」）——
    走不走由人决策，但不能不知情；"先去处理"直接把人指到持续改进页。
  · 能力门控沿用：租户没有 GATE_REVIEW / 拉取失败 → 列与提示都不出现。
- **实测（API 全流程）**：
  ```
  基线：10 道门全部 passed=true
  造 2 条（BLOCK 挂 TR3 + WARN 不挂门）→ openTotal=2 block=1 → TR3 passed=false
       「还有 2 个未闭环问题（含 1 个阻塞），要求 ≤ 0」
  把 BLOCK 那条走完闭环 → openTotal=1 → 仍 false（WARN 也算，见口径说明）
  全部闭环 → openTotal=0 → 全部 passed=true ✓
  ```
- **实测（UI，真实登录态）**：系列页 → 阶段门 tab：表头出现「未闭环问题」、TR1 行显示 🔴1；
  点「发起」→ 弹确认框（文案与上面一致）✓
- **明确未做（如实记录）**：
  · **门决策申请表单的"未关闭阻断问题数"仍是人工填** —— 任务表单上下文 `ProcessTaskFormVO`
    不带业务对象（只有 formKey / taskDefinitionKey / dslJson…），表单里**拿不到产品 oid**，
    没法自动算；要做需给流程任务上下文补"业务对象"（流程模块的改动，另开一轮）；
  · 其余三类检查（DELIVERABLE_RATE / RULE_VIOLATION / COST_ACHIEVE）仍未求值；
  · 检查规则的 ISSUE_OPEN 条件待接（`PlatformCapabilities` 依据已同步这份进度）。

#### 阶段门关联收敛到能力开关（GATE_REVIEW）：不是 IBA，是 `ck_tenant_capability`
- **用户提问**：*"阶段门是IPD独有的，应该只IPD对应的pack中，采用iba属性来实现能力开关？"*
- **核实结论（前半句对、机制要纠正）**：
  · **对**：门资源确实只在 IPD 包 —— `gates.json` 全仓只此一份（`smart-hardware-ipd/v1.0.0/`）；
    `traditional-stage-gate` 只有 stages.json（只有阶段没有门）、`automotive-apqp` 也无门；
    `GATE_REVIEW` 能力码也只有 smart-hardware-ipd 的 capabilities.json 声明；
    **且这条链已经通了**：包声明 → `PlatformCapabilities` 登记（防假就绪）→ 装包写 `ck_tenant_capability`
    账本 —— 实测平台租户账本**没有** GATE_REVIEW，装了 IPD 包的两个租户才有。
  · **纠正**：能力开关**不该用 IBA**。IBA（`ck_attribute_definition` + `ck_type_iba` + `ck_iba`）是
    「**某类型的对象挂哪些字段**」——实测 `ck_type_iba` 的形态是 `type_oid + entity_code + iba_oid +
    required + default_value`，是对象字段层面的机制；而"租户能用哪些功能"的专门机制是 **capability**
    （`CapabilityService.has()` / 前端 `useCapabilities().has()`，已在 8 个页面在用）。
    另外：问题（ck_issue）不是软类型实例（没有类型定义），IBA 本身也无处可挂。
- **真正缺的一环（上轮疏忽，本次补上）**：问题模块的「关联阶段门」入口此前是**无条件**的，没跟随能力开关。
  · **后端**（真正的闸门）：`validateGate` 先查 `capabilityService.has("GATE_REVIEW")`，未启用即 400
    「本租户未启用「阶段门与评审」能力（需安装 IPD 类行业包），问题不能关联阶段门」；
  · **前端**（入口显隐）：`useCapabilities()` 门控四处 —— 弹窗"关联阶段门"选择器、列表门徽标、
    详情抽屉门行、"按产品拉门清单"的请求本身（无能力时不发请求）。
- **实测（同一份代码，两个租户两种形态）**：
  ```
  [平台租户] GATE_REVIEW=false
    API：创建不带门 → 200 ✓（门本就是可选项）；创建带门 → 400「本租户未启用「阶段门与评审」能力…」
    UI ：弹窗 labels = [标题,类型,严重度,来源,关联产品（系列/型号）,责任人,计划完成,问题描述] —— 无「关联阶段门」✓
  [CKAI-TECH] GATE_REVIEW=true
    API：创建带门 → 200 ✓（未误伤）
    UI ：弹窗 labels 多出「关联阶段门」✓
  ```
- **说明**：`gate_oid` 列本身保留在 `ck_issue`（平台表结构不必为个别行业分叉）；**开关的是"能不能用"，
  不是"有没有这列"** —— 无能力的租户永远写不进值（后端闸门），列表/抽屉也不展示（前端门控）。

#### 问题加「关联产品系列/型号 + 阶段门」：IPD 上下文补全（含一个上轮遗留 bug 的修复）
- **用户提问**：*"缺少关联的产品系列或型号，如果是 IPD 的方法论，是否应该将阶段门加上？"* —— **是，两层都加**：
  · **关联产品（必选）**：无归属的问题统计不出"哪个项目在拖后腿"——与"问题必须结构化"同一条理由；
  · **阶段门（可选）**：门是 IPD 的节拍器，"哪道门发现的、卡在哪道门"是决策时要看的第一列；
    但现场/客户反馈不见得来自门评审，故为可选（不强制）。
- **数据模型**（`ck_issue` 三列 + 一个索引；本地库已 ALTER，DDL 已进 schema.sql）：
  `product_oid`（系列/型号 oid）/ `product_type`（PRODUCT_LINE / PRODUCT_MODEL，与 /instances 的 ownerType
  同口径）/ `gate_oid`（→ ck_gate.oid）；索引 `(tenant_oid, product_oid)`。
  列可空是为存量库平滑升级，**服务端必填校验**（新建/编辑都过同一条校验）。
- **服务端**：
  · 校验：产品必填 + 类型白名单；门必须存在且**属于所选产品**（串数据在写入前拦下，
    消息：`所选阶段门不属于该产品，请重新选择`）；
  · 装配：列表/详情/创建/编辑/流转的返回都带 `gateLabel`（如 `TR3 详细设计评审（投板门）`）——
    前端不必再取门清单；同页同门去重，避免重复查询；
  · 流转与编辑的字段兜底同步补齐（产品/门不会被流转或单字段编辑冲掉）。
- **前端**：弹窗新增一行「关联产品（系列 / 型号）| 关联阶段门」——产品用**分组下拉**
  （产品系列 + 产品型号，值 `类型:oid` 复合串），选定产品后自动按该产品拉门清单（复用
  `/instances` 六类实例接口），换产品自动清空已选门；列表加「关联产品」列（产品名 + 门 code 徽标，
  全文在 tooltip）；详情抽屉同步展示两项。
- ✗ **顺带修复一个上轮遗留 bug（本轮验证发现）**：`IssueServiceImpl.update()` 的 patch 兜底**漏了
  `severity`** —— 单独编辑其它字段（如清空门）时 `severity` 以 null 进 UPDATE，而它是 NOT NULL 列
  （DEFAULT 只在 INSERT 时生效），整行更新直接 500（`null value in column "severity" violates
  not-null constraint`）。上轮没暴露是因为验证脚本只测了"关闭后编辑"（被状态检查拦住，没走到 SQL）。
  已补兜底 + 验证脚本补"单字段编辑"用例；教训：NOT NULL 列在整行 UPDATE 里必须逐个兜底。
- **实测（API，CKAI-TECH 租户 / X系列智能手表有 10 道 IPD 门）**：
  ```
  不带产品创建 → 400「请选择关联的产品系列或型号（问题要能按项目统计）」
  门不属于所选产品 → 400「所选阶段门不属于该产品，请重新选择」
  正常创建（X系列 + TR3）→ 200，gateLabel=TR3 详细设计评审（投板门）
  列表装配 ✓  流转后产品/门字段仍在 ✓
  单字段编辑（severity→BLOCK）→ 200 且其余字段未丢（bug 修复验证）✓
  清空门（空串）→ 200 gateOid=null ✓；换产品 → 200 ✓；换产品后再挂旧门 → 400 ✓
  ```
- **实测（UI，真实登录态）**：弹窗产品下拉 → `X系列智能手表（i-watch-x）`；选定后门下拉出 **10 道门**
  （TR1/CHARTER/TR2/PDCP/TR3/TR4/ADCP/TR5/LDCP/TR6）；提交后列表行：
  `ISS-0001 …  X系列智能手表  TR3  警告  待处理 …` ✓；验证数据已清理。
- **升级 SQL（现有库）**：
  ```sql
  ALTER TABLE ck_issue ADD COLUMN IF NOT EXISTS product_oid  CHAR(36);
  ALTER TABLE ck_issue ADD COLUMN IF NOT EXISTS product_type VARCHAR(30);
  ALTER TABLE ck_issue ADD COLUMN IF NOT EXISTS gate_oid     CHAR(36);
  CREATE INDEX IF NOT EXISTS idx_issue_tenant_product ON ck_issue (tenant_oid, product_oid);
  ```
  注意：该 ALTER 之后，**存量问题行（无产品）在编辑时会被必填校验拦下** —— 需要先补产品再改其它字段
  （本次迁移时两库均为 0 行，无实际影响；将来若有存量行，按此口径处理）。

#### 持续改进：问题的跟踪与闭环（新功能 —— ISSUE_MANAGEMENT 能力落地）
- **用户要求**：*"在持续改进中增加问题的跟踪与闭环"*
- **背景**：「持续改进」页此前是占位页（`a-result` + 4 张特性卡、按钮无功能）；后端零实现 ——
  `PlatformCapabilities` 自己记着"ISSUE_MANAGEMENT 问题闭环管理 —— 无 ck_issue 类表"。
- **数据模型（两张新表，DDL 已进 schema.sql，并已建到本地库）**：
  · `ck_issue`：问题单 —— `code`（ISS-#### 租户内递增）/ `name`（标题）/ `description` / `issue_type`
    （→ `ck_issue_type_template.code`，服务层校验存在）/ `severity`（BLOCK·WARN，与检查规则、阶段门同一口径）/
    `source`（AUDIT 审核·CUSTOMER 客户·PRODUCTION 生产·TEST 测试·OTHER）/ `status` / `owner`（责任人）/
    `reporter`（提出人，自动 = 创建者）/ `due_date` / `related_type` + `related_code`（关联对象，可空）/
    `root_cause` / `resolution` / `verify_result` / `closed_at` + 审计列；
    索引 `(tenant_oid, status, created_at DESC)`、`(tenant_oid, owner)`、`UNIQUE (code, tenant_oid)`；
  · `ck_issue_activity`：处理记录（时间线）—— **只增不改**；`action` / `from_status` / `to_status` /
    `comment` / `operator`；外键级联（删问题记录一并删）。
- **闭环状态机（`IssueFlow`，前后端同一口径）**：
  ```
  OPEN 待处理 ─START→ IN_PROGRESS 处理中 ─SUBMIT_VERIFY→ VERIFYING 待验证 ─VERIFY_PASS→ CLOSED 已关闭
       （VERIFYING ─VERIFY_FAIL→ 退回处理中；三态均可 CANCEL → CANCELLED；CLOSED ─REOPEN→ OPEN）
  ```
  两条不让步的口径：**① 不许一步关闭** —— 必须经"提交验证"再由"验证通过"关闭（处理人自己说"好了"不算数，
  这正是"闭环"与"标记完成"的区别）；**② CANCELLED 是终态**。必填约束：验证通过要验证结论；退回/取消/重开要说明。
- **后端**（新模块 `cn.ck.plm.improve`，10 个新文件）：entity×2 / mapper×2（接口 + PG 实现，显式书写
  `tenant_oid`）/ `flow/IssueFlow` / service api + impl / controller。
  接口：`GET /api/issues`（状态·严重度·责任人·关键词 + 后端分页）、`GET /stats`（各状态计数 + **闭环率**
  = CLOSED /（总数 - CANCELLED））、`GET /{oid}`（本体 + 时间线）、`POST /api/issues`（编号自动生成、
  状态恒为 OPEN、提出人 = 当前用户）、`PUT /{oid}`（patch 语义；**已关闭/已取消不可编辑**）、
  `POST /{oid}/transition`、`POST /{oid}/comments`、`DELETE /{oid}`；编辑与流转都写时间线
  （含"更新了：标题、责任人"这类字段级摘要）。
- **前端**：`ContinuousImprovement.vue` 从占位页重写为真页面 —— 统计卡（6 张，点击即筛选）/
  列表（状态·严重度·责任人筛选 + 关键词 + 后端分页 + 超期红字）/ 新建·编辑弹窗 / 详情抽屉
  （字段 + 闭环动作按钮 + 处理记录时间线 + 追加备注）/ 流转弹窗（要说明或结论的动作才弹）；
  新增 `utils/issueFlow.js`（状态机前端镜像，决定按钮与必填判断）；`api/index.js` 追加 8 个函数。
  路由与菜单（`/improve`）沿用，无需改动。
- **能力登记**：`PlatformCapabilities` 新增 `ISSUE_MANAGEMENT`（依据：两表 + `/api/issues` + 持续改进页）；
  "已知未实现"注释同步（ContinuousImprovement 已落地；ChangeControl 仍是占位页）。
- **实测（API 全流程脚本）**：
  ```
  创建 ISS-0001（BLOCK / reporter=sysadmin）
  一步关闭被拦：VERIFY_PASS on OPEN → 400「当前状态 OPEN 不允许动作 VERIFY_PASS（允许的当前状态：[VERIFYING]）」
  未知动作被拦：CLOSE → 400「未知动作 'CLOSE'，取值只能是 [START, SUBMIT_VERIFY, VERIFY_PASS, VERIFY_FAIL, CANCEL, REOPEN]」
  逐跳：START → IN_PROGRESS；SUBMIT_VERIFY → VERIFYING；VERIFY_FAIL（缺原因 400）→ IN_PROGRESS；
        SUBMIT_VERIFY(2) → VERIFYING；VERIFY_PASS（缺结论 400）→ CLOSED（closedAt 填充）
  关闭后编辑 → 400「已关闭的问题不可编辑，请先重开」；REOPEN（缺原因 400）→ OPEN（closedAt 清空）
  CANCEL（带原因）→ CANCELLED；取消后 REOPEN → 400（终态）
  时间线 8 条完整（CREATE→START→SUBMIT_VERIFY→VERIFY_FAIL→SUBMIT_VERIFY→VERIFY_PASS→REOPEN→CANCEL）；
  备注 → COMMENT；stats / 筛选正确；验证数据清理后归零
  ```
- **实测（UI，真实登录态）**：`/improve` 渲染 ✓；新建 → 列表出现 `ISS-0001 … 警告 待处理 … [提出人] 平台管理员`；
  行内"开始处理" → `处理中`（操作列按钮自动切"提交验证"）；详情抽屉时间线 2 条（提出 / 开始处理 待处理→处理中）✓
- **测试**：后端新增 `IssueFlowTest`（8 用例，纯状态机）✓；前端新增 `issueFlow.test.js`（8 用例）✓；
  前端全量 **31 文件 / 515 用例**通过 ✓；后端 `mvn compile` 0 错误 ✓；lint 0 ✓
- **明确未做（如实记录）**：门 / 检查规则的 `ISSUE_OPEN` 自动统计（"开门前还有几个阻塞问题"）尚未接 ——
  问题数据已结构化，接上是下一步；问题类型下拉取自 `ck_issue_type_template`（随行业包装入，CKAI-TECH 现有 7 类）。

#### 显示名统一：「预期角色」→「责任人」（交付件的 ownerRole，数据与字段名未动）
- **用户要求**：*"预期角色修改为责任人"*（产品系列页交付件表的列标题）
- **改动（只改**显示名与提示**，字段仍是 `ownerRole` / `owner_role`，权限判定与接口一字未动）**：
  · **界面**：产品系列页交付件列标题、编辑弹窗 label、编辑图标 tooltip（"编辑：责任人与强制项"）、
    产出物列的权限提示（"…或本件的责任人（…）"）、宿主页成功提示（"责任人已改为「…」"）；
    交付件模板配置页同步（页头说明 / 表单 label / 列表列标题）—— 两层界面口径一致，
    不会出现"模板页叫预期角色、实例页叫责任人"；
  · **后端面向用户的提示**：`只有交付件有「责任人」；…`（tailor 400）、
    `没有关联权：本操作限该系列 / 型号团队里的「产品经理」，或本交付件的责任人（…）`（关联 403）；
  · **注释 / javadoc 同步** 10 个后端文件（Deliverable / DeliverableTemplate / DeliverableMapper /
    InstancePermissions / ProductTeamRoleResolver / MethodologyInstanceService[Impl] /
    MethodologyInstanceController / DeliverableArtifactLinkController / DeliverableTemplateServiceImpl）
    + schema.sql 两处；其中两处**保留与 `owner` 字段的区分**（`owner_role` = 按角色的责任人，
    `owner` = 具体指派到的人）：`责任人（→ ck_role.code 角色码）；具体指派到人记在 owner`。
- **实测**：产品系列页 → 交付件表头 `[… "强制", "责任人", "阶段门" …]` ✓；
  点编辑图标 → 弹窗 `编辑「市场评估报告」  责任人  产品经理（PRODUCT_MANAGER） …` ✓
- **测试**：后端 `mvn compile` 0 错误 ✓；前端全量 30 文件 / 507 用例通过 ✓；lint 0 ✓
- **说明**：`ck_role` 里的角色名（如「产品经理」）与团队角色（`ck_team_member.role_name`）
  不受影响；上一条"按团队角色判权限"的规则里，责任人（角色码）与团队角色的对齐逻辑照旧。

#### platform-base 的 LETTER_26 版本规则：ruleDefinition 改为逗号分隔的 26 个大写字母
- **用户要求**：*"修改 platform-base 包中的 versionRule.json 文件中的 ruleDefinition 属性，修改为 26 个大写英文字母，每个字母采用逗号分割"*
- **改动**（`platform-base/v1.0.0/versionRules.json`）：
  · `ruleDefinition`：`(A-Z)` → `(A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V,W,X,Y,Z)`（26 字母，逗号分隔）
  · `description` 同步为"大版本字母序列，26 个大写字母逐列（逗号分隔，A 到 Z；类型默认绑定这条）"
  · `code` / `name` / `applicableType` / `sequenceValue` / `enabled` 未动 —— 类型绑定（`softtypes.json` 的
    `"versionRule": "LETTER_26"`）不受影响
- **顺带查实的两件事（都写进了测试）**：
  · ① `parseRevisionSequence`（大版本序列 A→B→C 的真实链路）的正则
    `\(([A-Z]([,\-][A-Z])*)\)` **先**匹配"逗号列表"，范围分支 `(A-Z)` 排在后面 —— 实测 `(A-Z)` 被列表分支
    吃掉，得到**单元素 `["A-Z"]`**（范围分支永远走不到）。改成逗号分隔后走列表分支，得到**正确的 26 个元素**：
    新口径顺带绕开了这个陷阱。
  · ② `generateByRule`（"生成下一个版本"按钮的链路）对逗号序列的处理是 `segment.replace(",", "")`
    （**返回全部字母连写**），而 `(A-Z)` 走"取第 N 个字母" —— ⇒ 若有人拿 LETTER_26 去点"生成"
    （`POST /api/version-rule/generate/LETTER_26`），结果会由 `A/B/C…`（按序号取）变成
    `ABCDEFGHIJKLMNOPQRSTUVWXYZ`。**这是行为变化，如实记录**；版本推进（新建对象定 A、`newRevision` 推 A→B）
    不受影响。
- **测试**：新增 `VersionRuleSequenceTest`（4 用例；`parseRevisionSequence` 为包内可见纯函数，直接 new 服务实例调用，
  不碰 mapper）：26 元素 / 顺序与 A→B 推演 / **记录 `(A-Z)` 的既有单元素行为**（若将来修正范围分支顺序，
  该断言应随之更新）/ 无字母段返回空 ✓
- **生效范围（与前一节同口径）**：`VersionRuleInstaller` 对已存在的规则 **SKIP（"版本规则已存在"）** ——
  本次改动**只影响新装租户**；批量查库确认 4 个租户（平台 / CKAI-TECH / 90f0db93 / ce50ab19）
  的 `ck_version_rule.rule_definition` 仍是 `(A-Z)`、`sequence_value=0`。要同步存量库，说一声即可。
- **实测**：`GET /api/industry-packs/platform-base/1.0.0/plan` → PLANNED、VERSION_RULE 1 条
  「版本规则已存在」SKIP ✓；`GET /api/industry-packs/validate` → platform-base `valid: true`、`problems: []` ✓

#### 导出入库：「元器件引入（认定）流程」的 BPMN + DSL 更新进 electronics-core 包
- **用户要求**：*"将器件引入（认定）流程的BPMN导出，并替换electronics-core中的器件引入流程文件"*
- **来源 → 目标**：库内模板 `component-introduction`（CKAI-TECH）**最新版 v22**（10-13 23:45 Roney.Liu，已部署）
  → `electronics-core/v1.0.0/processes/component-introduction.bpmn20.xml` + `.dsl.json`（包内原文件还是 10-04 的旧结构）
- **导出方式（保真）**：psql 的 `\o` 直接落盘会把行尾写成 CRLF（实测 BPMN 多出 156 个 `\r` 字符），
  改用 **`encode(convert_to(...,'UTF8'),'base64')` + 本地解码**，得到与库**逐字符等价**的文件
  （BPMN 13637 字符 / DSL 5346 字符；无 CRLF；扫描租户/用户 UUID 0 个）
- **结构差异（v22 vs 包内旧版）**：删 `n_apply`（申请填报与完善）；新增
  `set_assignee_emt11t`（设置审批人）、`service_p7h86n`（→ 审核中 APPROVING）、
  `service_jlyzi8`（→ PUBLISHED）、`service_nia4v7`（→ DRAFT）、`task_qhmpz1`（重新提交申请）
- ✗ **途中踩坑并修复一处（替换时我自己引入的）**：包内 `*.dsl.json` **不是裸 DSL**，而是「流程定义行」
  `{ key, name, displayName, category, description, enabled, dsl: {...} }` —— 首版按"库里导出的裸 DSL"写入后，
  装包器 `row.get("dsl")` 读到 null，把该流程当 **BPMN-only** 处理（预演文案由"将补部署 BPMN"变成
  "模板已存在；本包只有 BPMN，未重复部署"）。已按同目录 `gate-decision.dsl.json` 的格式重建
  （2 空格缩进、CRLF 行尾对齐），元数据取自库（key / name / displayName / description / enabled + 分类「元器件」），
  `dsl` 嵌 v22 裸 DSL
- **装包器语义（已读码确认，`ProcessInstaller` 的 B 分支）**：模板已存在 → **绝不覆盖**：
  · 已存在且已部署 → SKIP；已存在但从未部署 → 只补部署 BPMN（模板内容仍不覆盖）；
  · 只有"从未装过"的租户才走新建（用包内 DSL 建模板 + 包内 BPMN 部署）
  ⇒ 本次替换**只影响新装租户**；CKAI-TECH 等现有租户零影响（它们的库本来就已是 v22）
- **实测（dry-run，真装未执行）**：`GET /api/industry-packs/electronics-core/1.0.0/plan`
  ```
  平台链：platform-base[PLANNED] → electronics-core[PLANNED]，errors 0
  PROCESS 3 条（component-introduction / gate-decision / technical-review）
    → 全部「预演：模板已存在，将补部署 BPMN（装完即可发起）」✓ 三者语义一致
  ```
- **校验**：BPMN XML 可解析（process id=component-introduction）✓；DSL JSON 可解析（14 节点 / 16 边）✓；
  表单引用 4 个均有着落（3 个在包内 `forms.json`，`CKPLM_SETUP_ASSIGNEE` 为内置）✓
- **注**：包内 BPMN 行尾为 LF（与库一致），同目录 dsl.json 为 CRLF（与 gate-decision 对齐）—— 不影响解析

#### 生命周期状态码统一：IN_WORK / RELEASED → WORKING / PUBLISHED（字典口径）
- **用户提问**：*"为什么数据库中存在两个已发布生命周期状态"*。查证结果：不是数据重复，而是**两个层次各有一个"已发布"**——
  · 状态字典 `ck_lifecycle_status`（租户级、平台预置）里是 **PUBLISHED = 已发布**（字典里从来没有 RELEASED）；
  · 内置模板 `STANDARD`（类型定义默认绑它）与 `SIMPLE` 的状态清单里却写着 **RELEASED = 已发布**（`ck_lifecycle_template_state`）；
  · 两者**显示名相同、状态码不同** —— 同租户下"已发布"因此出现两条（CKAI-TECH 实测：STANDARD/SIMPLE 的 RELEASED + COMPONENT_CODE 的 PUBLISHED）。
- **根因**：`platform-base/lifecycles.json` 的 note 自己记着 ——「字典被改过名（WORKING / PUBLISHED），模板没跟着改（IN_WORK / RELEASED）」，此前只做搬迁未统一。后果有二：
  · 判断"是否已发布"要同时认两个码，漏一个就出现"对象明明已发布、功能说没发布"；
  · 按字典校验状态的地方会失配 —— 本文件上方那条"流程设 APPROVING 被模板打回（模板允许 DRAFT / IN_WORK / RELEASED）"正是这一类。
- **统一（用户选定：统一到字典口径）**：
  · 播种源 `lifecycles.json`：STANDARD → DRAFT/WORKING/PUBLISHED、SIMPLE → DRAFT/PUBLISHED（transitions / rejections 同步），note 改写为"已统一"并记原因；
  · `LifecycleTemplateDef.createStandard/createSimple`（遗留静态常量，实测仅 javadoc 示例引用）同步；
  · 全仓注释示例码 13 处同步（PartCheckoutProvider / SoftTypeInstanceCapability / BomSnapshot / TypeLifecycleStateProcessLink / SoftTypeInstanceController / DefaultLifecycleTemplateService / LifecycleTemplateService ×2 / PostgreSqlResourceLibraryMapper / LifecycleStatusService / SetLifecycleStateHandler / PlmServiceDelegate / PartServiceImpl ×2 / schema.sql）+ 测试字面量 1 处。
- **影响面（两轮全库扫描，结论：干净）**：
  · 文本列扫描：只有 `ck_lifecycle_template_state.status_code`（12 行）与 `ck_lifecycle_template_transition.from/to_status_code`（16+16 行）命中；业务对象迭代表（part / document / eng_document / functional）**无** RELEASED / IN_WORK 值（只有 DRAFT / APPROVING）；
  · JSON/XML 列扫描（流程 DSL、BPMN、各类 json 配置）：**0 命中** → 流程"设置状态"节点没有配这两个码，无联动；
  · 前端 0 命中、项目 skill 0 命中。
- **实测**：
  ```
  迁移前：state IN_WORK=4 / RELEASED=8；trans.from=16 / to=16；link=0
  迁移后：全部 0（UPDATE 实改 4+8+32 行；业务迭代表 8 条语句各 0 行，与预扫一致）
  接口（真实登录态）：GET /api/lifecycle-templates/STANDARD/domain
    → statuses [DRAFT, WORKING, PUBLISHED]；transitions DRAFT→WORKING→PUBLISHED；
      rejections WORKING→DRAFT、PUBLISHED→WORKING ✓（SIMPLE：DRAFT→PUBLISHED ✓）
    —— 无需重启后端，读库实时生效
  ```
- **测试**：`ServiceParamVariablesTest` 7 用例通过 ✓；后端 `mvn compile` 0 错误 ✓
- **明确不动（独立状态域，与生命周期模板无关）**：`DeliverableStatusEvaluator` 的交付物阶段码（DRAFT/INWORK/REVIEW/APPROVED/RELEASED）、`EcadProject.projectPhase`（PLAN/DESIGN/REVIEW/RELEASED/ARCHIVED）—— 各自成体系、不读生命周期模板；若也要统一是另一个话题。

### 升级说明（现有库需手工执行增量 SQL）

**本次（生命周期状态码统一）的增量 SQL** —— 幂等，可重复执行；实测 CKAI-TECH 及其余 3 租户共 12 行状态 + 32 行规则：

```sql
BEGIN;
-- ① 模板状态清单
UPDATE ck_lifecycle_template_state SET status_code = 'WORKING'   WHERE status_code = 'IN_WORK';
UPDATE ck_lifecycle_template_state SET status_code = 'PUBLISHED' WHERE status_code = 'RELEASED';
-- ② 模板迁移规则（前进 / 回退）
UPDATE ck_lifecycle_template_transition SET from_status_code = 'WORKING'   WHERE from_status_code = 'IN_WORK';
UPDATE ck_lifecycle_template_transition SET to_status_code   = 'WORKING'   WHERE to_status_code   = 'IN_WORK';
UPDATE ck_lifecycle_template_transition SET from_status_code = 'PUBLISHED' WHERE from_status_code = 'RELEASED';
UPDATE ck_lifecycle_template_transition SET to_status_code   = 'PUBLISHED' WHERE to_status_code   = 'RELEASED';
-- ③ 类型「状态 → 流程」绑定（实测 0 行，保留为完整性）
UPDATE ck_type_lifecycle_state_process_link SET status_code = 'WORKING'   WHERE status_code = 'IN_WORK';
UPDATE ck_type_lifecycle_state_process_link SET status_code = 'PUBLISHED' WHERE status_code = 'RELEASED';
-- ④ 业务对象迭代表（实测 0 行，保留以防其它环境有在途值）
UPDATE ck_part_iteration         SET status = 'WORKING'   WHERE status = 'IN_WORK';
UPDATE ck_part_iteration         SET status = 'PUBLISHED' WHERE status = 'RELEASED';
UPDATE ck_document_iteration     SET status = 'WORKING'   WHERE status = 'IN_WORK';
UPDATE ck_document_iteration     SET status = 'PUBLISHED' WHERE status = 'RELEASED';
UPDATE ck_eng_document_iteration SET status = 'WORKING'   WHERE status = 'IN_WORK';
UPDATE ck_eng_document_iteration SET status = 'PUBLISHED' WHERE status = 'RELEASED';
UPDATE ck_functional_iteration   SET status = 'WORKING'   WHERE status = 'IN_WORK';
UPDATE ck_functional_iteration   SET status = 'PUBLISHED' WHERE status = 'RELEASED';
COMMIT;
```

**（历史）六类实例表的增量 SQL** —— 注意第一段是 `DROP`：旧的 `ck_deliverable` 是"半张残表"（无 `tenant_oid` 列，而它按业务表被租户拦截器处理，任何语句都会被注入租户而报错，等于物理不可访问；实测 0 行、无任何代码引用），故**就地重定义**：

```sql
DROP TABLE IF EXISTS ck_deliverable;

-- 交付件实例
CREATE TABLE IF NOT EXISTS ck_deliverable (
    oid             CHAR(36)      PRIMARY KEY,
    owner_oid       CHAR(36)      NOT NULL,
    owner_type      VARCHAR(10)   NOT NULL DEFAULT 'LINE',
    code            VARCHAR(50)   NOT NULL,
    name            VARCHAR(200)  NOT NULL,
    description     VARCHAR(1000),
    stage_key       VARCHAR(50)   NOT NULL,
    discipline      VARCHAR(30),
    mandatory       BOOLEAN       NOT NULL DEFAULT FALSE,
    owner_role      VARCHAR(50),
    object_type     VARCHAR(50),
    bind_mode       VARCHAR(30),
    completion_rule TEXT,
    due_offset_days INTEGER,
    gate            VARCHAR(30),
    industry_note   VARCHAR(1000),
    template_code   VARCHAR(50),
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    owner           VARCHAR(100),
    due_date        DATE,
    evidence        TEXT,
    artifact_refs   TEXT,
    disabled        BOOLEAN       NOT NULL DEFAULT FALSE,
    sort_order      INTEGER       NOT NULL DEFAULT 0,
    tenant_oid      CHAR(36),
    creator         VARCHAR(100),
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater         VARCHAR(100),
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (owner_oid, owner_type, code)
);
CREATE INDEX IF NOT EXISTS idx_del_owner  ON ck_deliverable (owner_oid, owner_type, stage_key);
CREATE INDEX IF NOT EXISTS idx_del_status ON ck_deliverable (owner_oid, owner_type, status);

-- 阶段门实例
CREATE TABLE IF NOT EXISTS ck_gate (
    oid                   CHAR(36)      PRIMARY KEY,
    owner_oid             CHAR(36)      NOT NULL,
    owner_type            VARCHAR(10)   NOT NULL DEFAULT 'LINE',
    code                  VARCHAR(50)   NOT NULL,
    name                  VARCHAR(200)  NOT NULL,
    description           VARCHAR(1000),
    stage_key             VARCHAR(50),
    type                  VARCHAR(30),
    process_key           VARCHAR(100),
    decision_options      TEXT,
    auto_checks           TEXT,
    required_participants TEXT,
    record_fields         TEXT,
    evidence_required     BOOLEAN       NOT NULL DEFAULT FALSE,
    industry_note         VARCHAR(1000),
    template_code         VARCHAR(50),
    status                VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    decision              VARCHAR(50),
    decided_by            VARCHAR(100),
    decided_at            TIMESTAMP,
    decision_note         VARCHAR(1000),
    disabled              BOOLEAN       NOT NULL DEFAULT FALSE,
    sort_order            INTEGER       NOT NULL DEFAULT 0,
    tenant_oid            CHAR(36),
    creator               VARCHAR(100),
    created_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater               VARCHAR(100),
    updated_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (owner_oid, owner_type, code)
);
CREATE INDEX IF NOT EXISTS idx_gate_owner ON ck_gate (owner_oid, owner_type, stage_key);

-- 评审实例
CREATE TABLE IF NOT EXISTS ck_review (
    oid             CHAR(36)      PRIMARY KEY,
    owner_oid       CHAR(36)      NOT NULL,
    owner_type      VARCHAR(10)   NOT NULL DEFAULT 'LINE',
    code            VARCHAR(50)   NOT NULL,
    name            VARCHAR(200)  NOT NULL,
    description     VARCHAR(1000),
    gate            VARCHAR(30),
    type            VARCHAR(30),
    process_key     VARCHAR(100),
    check_items     TEXT,
    output_types    TEXT,
    verdict_options TEXT,
    industry_note   VARCHAR(1000),
    template_code   VARCHAR(50),
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    verdict         VARCHAR(50),
    verdict_note    VARCHAR(1000),
    reviewed_at     TIMESTAMP,
    disabled        BOOLEAN       NOT NULL DEFAULT FALSE,
    sort_order      INTEGER       NOT NULL DEFAULT 0,
    tenant_oid      CHAR(36),
    creator         VARCHAR(100),
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater         VARCHAR(100),
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (owner_oid, owner_type, code)
);
CREATE INDEX IF NOT EXISTS idx_review_owner ON ck_review (owner_oid, owner_type, gate);

-- 检查规则实例
CREATE TABLE IF NOT EXISTS ck_rule (
    oid              CHAR(36)      PRIMARY KEY,
    owner_oid        CHAR(36)      NOT NULL,
    owner_type       VARCHAR(10)   NOT NULL DEFAULT 'LINE',
    code             VARCHAR(50)   NOT NULL,
    name             VARCHAR(200)  NOT NULL,
    description      VARCHAR(1000),
    trigger_type     VARCHAR(30),
    scope            VARCHAR(50),
    category         VARCHAR(50),
    stage_key        VARCHAR(50),
    gate             VARCHAR(30),
    condition        TEXT,
    severity         VARCHAR(20),
    message          VARCHAR(1000),
    default_override VARCHAR(200),
    fix_action       VARCHAR(500),
    industry_note    VARCHAR(1000),
    template_code    VARCHAR(50),
    status           VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    last_checked_at  TIMESTAMP,
    findings         TEXT,
    disabled         BOOLEAN       NOT NULL DEFAULT FALSE,
    sort_order       INTEGER       NOT NULL DEFAULT 0,
    tenant_oid       CHAR(36),
    creator          VARCHAR(100),
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater          VARCHAR(100),
    updated_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (owner_oid, owner_type, code)
);
CREATE INDEX IF NOT EXISTS idx_rule_owner ON ck_rule (owner_oid, owner_type, stage_key);

-- 指标实例
CREATE TABLE IF NOT EXISTS ck_metric (
    oid           CHAR(36)      PRIMARY KEY,
    owner_oid     CHAR(36)      NOT NULL,
    owner_type    VARCHAR(10)   NOT NULL DEFAULT 'LINE',
    code          VARCHAR(50)   NOT NULL,
    name          VARCHAR(200)  NOT NULL,
    description   VARCHAR(1000),
    layer         VARCHAR(50),
    level         VARCHAR(50),
    source        VARCHAR(500),
    industry_note VARCHAR(1000),
    template_code VARCHAR(50),
    value         NUMERIC(20,4),
    value_text    VARCHAR(200),
    readiness     VARCHAR(10),
    evaluated_at  TIMESTAMP,
    disabled      BOOLEAN       NOT NULL DEFAULT FALSE,
    sort_order    INTEGER       NOT NULL DEFAULT 0,
    tenant_oid    CHAR(36),
    creator       VARCHAR(100),
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater       VARCHAR(100),
    updated_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (owner_oid, owner_type, code)
);
CREATE INDEX IF NOT EXISTS idx_metric_owner ON ck_metric (owner_oid, owner_type, layer);

-- 驾驶舱实例
CREATE TABLE IF NOT EXISTS ck_dashboard (
    oid                CHAR(36)      PRIMARY KEY,
    owner_oid          CHAR(36)      NOT NULL,
    owner_type         VARCHAR(10)   NOT NULL DEFAULT 'LINE',
    code               VARCHAR(50)   NOT NULL,
    name               VARCHAR(200)  NOT NULL,
    description        VARCHAR(1000),
    audience           VARCHAR(100),
    page_count         INTEGER,
    widgets            TEXT,
    generate_policy    VARCHAR(50),
    primary_scope      BOOLEAN       NOT NULL DEFAULT FALSE,
    industry_note      VARCHAR(1000),
    template_code      VARCHAR(50),
    last_generated_at  TIMESTAMP,
    disabled           BOOLEAN       NOT NULL DEFAULT FALSE,
    sort_order         INTEGER       NOT NULL DEFAULT 0,
    tenant_oid         CHAR(36),
    creator            VARCHAR(100),
    created_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater            VARCHAR(100),
    updated_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (owner_oid, owner_type, code)
);
CREATE INDEX IF NOT EXISTS idx_dashboard_owner ON ck_dashboard (owner_oid, owner_type);
```

**存量数据的补齐**（可选）：本次只在**创建时**初始化。已有系列 / 型号如需补齐，可对每个 `oid` 调用一次 `MethodologyInstanceService#initFor(oid, 'LINE'|'MODEL')`（幂等，已有即跳过）—— 目前没有开放接口，需要时再加一个一次性管理端点。

`schema.sql` 只对新装完整 —— 项目口径是"结构只留一处来源、不做老库迁移"（未集成 Flyway/Liquibase，应用启动也不自动建表）。**已有库请手工执行下面这段**（幂等，可重复执行）：

```sql
CREATE TABLE IF NOT EXISTS ck_deliverable_template (
    oid             CHAR(36)      PRIMARY KEY,
    code            VARCHAR(50)   NOT NULL,
    name            VARCHAR(200)  NOT NULL,
    description     VARCHAR(1000),
    industry        VARCHAR(50),
    stage_key       VARCHAR(50)   NOT NULL,
    discipline      VARCHAR(30),
    mandatory       BOOLEAN       NOT NULL DEFAULT FALSE,
    owner_role      VARCHAR(50),
    object_type     VARCHAR(50),
    bind_mode       VARCHAR(30),
    completion_rule TEXT,
    due_offset_days INTEGER,
    gate            VARCHAR(30),
    industry_note   VARCHAR(1000),
    disabled        BOOLEAN       NOT NULL DEFAULT FALSE,
    sort_order      INTEGER       NOT NULL DEFAULT 0,
    tenant_oid      CHAR(36),
    creator         VARCHAR(100),
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater         VARCHAR(100),
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (code, tenant_oid)
);

CREATE INDEX IF NOT EXISTS idx_dlt_tenant ON ck_deliverable_template (tenant_oid, industry, sort_order);
CREATE INDEX IF NOT EXISTS idx_dlt_stage  ON ck_deliverable_template (stage_key);
```

阶段门模板表同理（一并执行）：

```sql
CREATE TABLE IF NOT EXISTS ck_gate_template (
    oid                   CHAR(36)      PRIMARY KEY,
    code                  VARCHAR(50)   NOT NULL,
    name                  VARCHAR(200)  NOT NULL,
    description           VARCHAR(1000),
    industry              VARCHAR(50),
    stage_key             VARCHAR(50)   NOT NULL,
    type                  VARCHAR(30)   NOT NULL,
    process_key           VARCHAR(100),
    decision_options      TEXT,
    auto_checks           TEXT,
    required_participants TEXT,
    record_fields         TEXT,
    evidence_required     BOOLEAN       NOT NULL DEFAULT TRUE,
    industry_note         VARCHAR(1000),
    disabled              BOOLEAN       NOT NULL DEFAULT FALSE,
    sort_order            INTEGER       NOT NULL DEFAULT 0,
    tenant_oid            CHAR(36),
    creator               VARCHAR(100),
    created_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater               VARCHAR(100),
    updated_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (code, tenant_oid)
);

CREATE INDEX IF NOT EXISTS idx_gt_tenant ON ck_gate_template (tenant_oid, industry, sort_order);
CREATE INDEX IF NOT EXISTS idx_gt_stage  ON ck_gate_template (stage_key);
```

评审模板表同理（一并执行）：

```sql
CREATE TABLE IF NOT EXISTS ck_review_template (
    oid             CHAR(36)      PRIMARY KEY,
    code            VARCHAR(50)   NOT NULL,
    name            VARCHAR(200)  NOT NULL,
    description     VARCHAR(1000),
    industry        VARCHAR(50),
    gate            VARCHAR(50)   NOT NULL,
    type            VARCHAR(30)   NOT NULL,
    process_key     VARCHAR(100),
    check_items     TEXT,
    output_types    TEXT,
    verdict_options TEXT,
    industry_note   VARCHAR(1000),
    disabled        BOOLEAN       NOT NULL DEFAULT FALSE,
    sort_order      INTEGER       NOT NULL DEFAULT 0,
    tenant_oid      CHAR(36),
    creator         VARCHAR(100),
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater         VARCHAR(100),
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (code, tenant_oid)
);

CREATE INDEX IF NOT EXISTS idx_rt_tenant ON ck_review_template (tenant_oid, industry, sort_order);
CREATE INDEX IF NOT EXISTS idx_rt_gate   ON ck_review_template (gate);
```

检查规则表同理（一并执行）：

```sql
CREATE TABLE IF NOT EXISTS ck_rule_template (
    oid              CHAR(36)      PRIMARY KEY,
    code             VARCHAR(50)   NOT NULL,
    name             VARCHAR(200)  NOT NULL,
    description      VARCHAR(1000),
    industry         VARCHAR(50),
    trigger_type     VARCHAR(30)   NOT NULL,
    scope            VARCHAR(30)   NOT NULL,
    category         VARCHAR(50),
    stage_key        VARCHAR(50),
    gate             VARCHAR(50),
    condition        TEXT,
    severity         VARCHAR(20)   NOT NULL,
    message          VARCHAR(500),
    default_override TEXT,
    fix_action       TEXT,
    industry_note    VARCHAR(1000),
    disabled         BOOLEAN       NOT NULL DEFAULT FALSE,
    sort_order       INTEGER       NOT NULL DEFAULT 0,
    tenant_oid       CHAR(36),
    creator          VARCHAR(100),
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater          VARCHAR(100),
    updated_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (code, tenant_oid)
);

CREATE INDEX IF NOT EXISTS idx_rulet_tenant  ON ck_rule_template (tenant_oid, industry, sort_order);
CREATE INDEX IF NOT EXISTS idx_rulet_trigger ON ck_rule_template (trigger_type, scope);
```

问题类型表同理（一并执行）：

```sql
CREATE TABLE IF NOT EXISTS ck_issue_type_template (
    oid                CHAR(36)      PRIMARY KEY,
    code               VARCHAR(50)   NOT NULL,
    name               VARCHAR(200)  NOT NULL,
    description        VARCHAR(1000),
    industry           VARCHAR(50),
    default_severity   VARCHAR(20)   NOT NULL,
    require_root_cause BOOLEAN       NOT NULL DEFAULT FALSE,
    require_retest     BOOLEAN       NOT NULL DEFAULT FALSE,
    industry_note      VARCHAR(1000),
    disabled           BOOLEAN       NOT NULL DEFAULT FALSE,
    sort_order         INTEGER       NOT NULL DEFAULT 0,
    tenant_oid         CHAR(36),
    creator            VARCHAR(100),
    created_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater            VARCHAR(100),
    updated_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (code, tenant_oid)
);

CREATE INDEX IF NOT EXISTS idx_itt_tenant ON ck_issue_type_template (tenant_oid, industry, sort_order);
```

指标表同理（一并执行）：

```sql
CREATE TABLE IF NOT EXISTS ck_metric_template (
    oid         CHAR(36)      PRIMARY KEY,
    code        VARCHAR(50)   NOT NULL,
    name        VARCHAR(200)  NOT NULL,
    description VARCHAR(1000),
    industry    VARCHAR(50),
    layer       VARCHAR(30)   NOT NULL,
    level       VARCHAR(2)    NOT NULL,
    source      VARCHAR(150),
    industry_note VARCHAR(1000),
    disabled    BOOLEAN       NOT NULL DEFAULT FALSE,
    sort_order  INTEGER       NOT NULL DEFAULT 0,
    tenant_oid  CHAR(36),
    creator     VARCHAR(100),
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     VARCHAR(100),
    updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (code, tenant_oid)
);

CREATE INDEX IF NOT EXISTS idx_mt_tenant ON ck_metric_template (tenant_oid, industry, sort_order);
CREATE INDEX IF NOT EXISTS idx_mt_layer  ON ck_metric_template (layer, level);
```

驾驶舱（布局）表同理（一并执行）：

```sql
CREATE TABLE IF NOT EXISTS ck_dashboard_template (
    oid             CHAR(36)      PRIMARY KEY,
    code            VARCHAR(50)   NOT NULL,
    name            VARCHAR(200)  NOT NULL,
    description     VARCHAR(1000),
    industry        VARCHAR(50),
    audience        VARCHAR(100),
    page_count      INTEGER,
    widgets         TEXT,
    generate_policy VARCHAR(50),
    primary_scope   BOOLEAN       NOT NULL DEFAULT FALSE,
    disabled        BOOLEAN       NOT NULL DEFAULT FALSE,
    sort_order      INTEGER       NOT NULL DEFAULT 0,
    tenant_oid      CHAR(36),
    creator         VARCHAR(100),
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater         VARCHAR(100),
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (code, tenant_oid)
);

CREATE INDEX IF NOT EXISTS idx_dbt_tenant ON ck_dashboard_template (tenant_oid, industry, sort_order);
```

装完包的企业：重装一次行业包（或等下次启动）即会补齐 46 条交付件模板、10 道门模板、8 份评审模板、11 条检查规则、7 类问题类型、11 项指标与 3 个驾驶舱视角；已裁剪过的不会被覆盖。

MPN 管理（参考 Windchill 的 AML/AVL）四张表同理（一并执行）：

```sql
CREATE TABLE IF NOT EXISTS ck_manufacturer (
    oid             CHAR(36)      PRIMARY KEY,
    code            VARCHAR(50)   NOT NULL,
    name            VARCHAR(200)  NOT NULL,
    description     VARCHAR(1000),
    english_name    VARCHAR(200),
    website         VARCHAR(500),
    country         VARCHAR(100),
    note            VARCHAR(1000),
    disabled        BOOLEAN       NOT NULL DEFAULT FALSE,
    sort_order      INTEGER       NOT NULL DEFAULT 0,
    tenant_oid      CHAR(36),
    creator         VARCHAR(100),
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater         VARCHAR(100),
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (code, tenant_oid)
);
CREATE INDEX IF NOT EXISTS idx_mfr_tenant ON ck_manufacturer (tenant_oid, disabled, sort_order);

CREATE TABLE IF NOT EXISTS ck_supplier (
    oid             CHAR(36)      PRIMARY KEY,
    code            VARCHAR(50)   NOT NULL,
    name            VARCHAR(200)  NOT NULL,
    description     VARCHAR(1000),
    full_name       VARCHAR(300),
    contact         VARCHAR(100),
    phone           VARCHAR(50),
    email           VARCHAR(200),
    website         VARCHAR(500),
    note            VARCHAR(1000),
    disabled        BOOLEAN       NOT NULL DEFAULT FALSE,
    sort_order      INTEGER       NOT NULL DEFAULT 0,
    tenant_oid      CHAR(36),
    creator         VARCHAR(100),
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater         VARCHAR(100),
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (code, tenant_oid)
);
CREATE INDEX IF NOT EXISTS idx_supplier_tenant ON ck_supplier (tenant_oid, disabled, sort_order);

CREATE TABLE IF NOT EXISTS ck_part_aml (
    oid                CHAR(36)      PRIMARY KEY,
    code               VARCHAR(50),
    name               VARCHAR(200),
    description        VARCHAR(1000),
    part_oid           CHAR(36)      NOT NULL,
    manufacturer_oid   CHAR(36)      NOT NULL,
    mpn                VARCHAR(200)  NOT NULL,
    mpn_desc           VARCHAR(500),
    approved_status    VARCHAR(20)   NOT NULL DEFAULT 'APPROVED',
    lifecycle_state    VARCHAR(50),
    effectivity_json   JSONB,
    note               VARCHAR(1000),
    sort_order         INTEGER       NOT NULL DEFAULT 0,
    tenant_oid         CHAR(36),
    creator            VARCHAR(100),
    created_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater            VARCHAR(100),
    updated_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_part_aml_part         FOREIGN KEY (part_oid) REFERENCES ck_part(oid) ON DELETE CASCADE,
    CONSTRAINT fk_part_aml_manufacturer FOREIGN KEY (manufacturer_oid) REFERENCES ck_manufacturer(oid)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_part_aml_mpn ON ck_part_aml(part_oid, manufacturer_oid, mpn);
CREATE INDEX IF NOT EXISTS idx_part_aml_part ON ck_part_aml(part_oid);
CREATE INDEX IF NOT EXISTS idx_part_aml_mfr  ON ck_part_aml(manufacturer_oid);
CREATE INDEX IF NOT EXISTS idx_part_aml_tenant ON ck_part_aml(tenant_oid);

CREATE TABLE IF NOT EXISTS ck_part_avl (
    oid                 CHAR(36)      PRIMARY KEY,
    code                VARCHAR(50),
    name                VARCHAR(200),
    description         VARCHAR(1000),
    aml_oid             CHAR(36)      NOT NULL,
    supplier_oid        CHAR(36)      NOT NULL,
    supplier_part_no    VARCHAR(200),
    preferred           BOOLEAN       NOT NULL DEFAULT FALSE,
    price               NUMERIC(18,4),
    currency            VARCHAR(10),
    lead_time_days      INTEGER,
    moq                 INTEGER,
    spq                 INTEGER,
    effectivity_json    JSONB,
    note                VARCHAR(1000),
    sort_order          INTEGER       NOT NULL DEFAULT 0,
    tenant_oid          CHAR(36),
    creator             VARCHAR(100),
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater             VARCHAR(100),
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_part_avl_aml      FOREIGN KEY (aml_oid) REFERENCES ck_part_aml(oid) ON DELETE CASCADE,
    CONSTRAINT fk_part_avl_supplier FOREIGN KEY (supplier_oid) REFERENCES ck_supplier(oid)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_part_avl_pair ON ck_part_avl(aml_oid, supplier_oid);
CREATE INDEX IF NOT EXISTS idx_part_avl_aml      ON ck_part_avl(aml_oid);
CREATE INDEX IF NOT EXISTS idx_part_avl_supplier ON ck_part_avl(supplier_oid);
CREATE INDEX IF NOT EXISTS idx_part_avl_tenant   ON ck_part_avl(tenant_oid);
```

若四张表已存在于早期试验库，只需补列（实体继承 `WithoutVersionEntity`，`code / name / description` 三个字段在结构上必须留列）：

```sql
ALTER TABLE ck_manufacturer ADD COLUMN IF NOT EXISTS description  VARCHAR(1000);
ALTER TABLE ck_supplier     ADD COLUMN IF NOT EXISTS description  VARCHAR(1000);
ALTER TABLE ck_part_aml     ADD COLUMN IF NOT EXISTS code         VARCHAR(50);
ALTER TABLE ck_part_aml     ADD COLUMN IF NOT EXISTS name         VARCHAR(200);
ALTER TABLE ck_part_aml     ADD COLUMN IF NOT EXISTS description  VARCHAR(1000);
ALTER TABLE ck_part_avl     ADD COLUMN IF NOT EXISTS code         VARCHAR(50);
ALTER TABLE ck_part_avl     ADD COLUMN IF NOT EXISTS name         VARCHAR(200);
ALTER TABLE ck_part_avl     ADD COLUMN IF NOT EXISTS description  VARCHAR(1000);
```

MPN 能力随 `electronics-core` 包启用：现有企业**重装一次该包**（或等平台重启时自动补装该包）即会在 `ck_tenant_capability` 落上 `MPN_MANAGEMENT`，资源库页随即出现「MPN 管理」页签。

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
