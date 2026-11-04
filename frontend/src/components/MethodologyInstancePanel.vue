<template>
  <div class="mi-panel">
    <!-- 一行都没有时不摆"共 0 项 + 口径说明"：空表自己会说"暂无/本阶段没有…"，上面再啰嗦两句只是噪音 -->
    <div v-if="displayRows.length" class="mi-head">
      <span class="mi-counts">{{ summaryText }}</span>
      <span v-if="hint" class="mi-hint">{{ hint }}</span>
    </div>

    <!-- 本阶段 0 条、但本对象有 —— 不把已有数据藏起来：改列全集 + 说清为什么 -->
    <a-alert
      v-if="mismatched"
      type="warning"
      show-icon
      class="mi-mismatch"
      :message="`本阶段无匹配，已改列本对象全部 ${allRows.length} 条${SUBJECT[kind] || '数据'}`"
      description="它们的「所属阶段」不在当前阶段下 —— 常见于阶段与内容方法学不一致的存量对象（阶段按传统六阶段建、内容按 IPD 包落地）。改模板不会回溯已有数据；新建的对象不会再有这个问题。"
    />

    <!-- 2026-10-11 去掉 :max-height="420"：表格内滚与外层页签滚动曾在小屏上形成右侧"并排双滚动条"。
         DataTable 的 maxHeight 默认 undefined（不限高）→ 表格自然展开，统一交给整页（.pl-dashboard）滚动。 -->
    <!-- 2026-10-30 用户要求「数据指标取消分页」：11 项指标一屏放得下，
         分成两页后"共 11 项 / 已评估 5 项"和下面的行对不上账；
         交付件与门仍保留分页（条数可能几十条）。 -->
    <DataTable
      :columns="columns"
      :data-source="tableRows"
      :loading="loading"
      :search-placeholder="searchPlaceholder"
      :search-fields="searchFields"
      :enable-resize="true"
      :show-column-toggle="true"
      scroll-x="max-content"
      row-key="oid"
      size="small"
      :empty-text="emptyText"
      :pagination="kind === 'metric' ? false : undefined"
    >
      <!-- 适宜 / 不适宜 过滤（2026-11-01 用户要求）：只对能看见裁剪行的人出现 ——
           其他成员本来就只看得到"适宜"的条目，给他们一个永远不会变的开关只会添乱。
           2026-11-01 修「过滤不出现」：原来看「本阶段有没有裁剪行」，切到没有裁剪行的阶段
           （如 IPD_PLAN）整个控件就消失，用户以为没做。改为**按人常驻**。
           2026-11-01 再去掉「不适宜」的置灰（用户：*"不适宜是灰色的，不能选择"*）：
           本阶段没有裁剪行时它照样可选，点进去由空态文案说明"本阶段没有不适宜的条目"——
           一个点不动的选项比一个空列表更像坏了。 -->
      <template v-if="showApplicabilityFilter" #toolbar-left>
        <a-radio-group v-model:value="applicability" size="small" button-style="solid">
          <a-radio-button value="APPLICABLE">适宜</a-radio-button>
          <a-radio-button value="NOT_APPLICABLE">不适宜</a-radio-button>
          <a-radio-button value="ALL">所有</a-radio-button>
        </a-radio-group>
      </template>
      <template #bodyCell="{ column, record }">
        <!-- 被裁剪（disabled）的行：名称置灰划掉 + 显式标签 —— 不删条目、可恢复 -->
        <template v-if="column.key === 'name'">
          <span :style="record.disabled ? 'color:#bfbfbf;text-decoration:line-through' : ''">
            {{ record.name }}
          </span>
          <a-tag v-if="record.disabled" size="small" style="margin-left:6px">本项目不适用</a-tag>
        </template>
        <template v-else-if="column.key === 'stage'">
          <a-tag v-if="record.stageKey" color="blue">{{ stageName(record.stageKey) }}</a-tag>
          <span v-else style="color:#bfbfbf">-</span>
        </template>
        <template v-else-if="column.key === 'mandatory'">
          <a-tag :color="record.mandatory ? 'red' : 'default'" size="small">{{ record.mandatory ? '强制' : '可选' }}</a-tag>
        </template>
        <!-- 自动检查：🟢 全部通过 / 🔴 有不通过（悬停看逐项明细） -->
        <template v-else-if="column.key === 'issueCheck'">
          <a-tooltip v-if="issueCheckOf(record)" :title="issueCheckTooltip(record)">
            <a-tag :color="issueCheckOf(record).passed ? 'green' : 'red'" size="small">
              {{ issueCheckOf(record).passed ? '通过' : '不通过' }}
            </a-tag>
          </a-tooltip>
          <span v-else style="color:#bfbfbf">-</span>
        </template>
        <template v-else-if="column.key === 'completionRule'">
          <a-tooltip :title="ruleText(record.completionRule)">
            <span class="mi-ellipsis">{{ ruleText(record.completionRule) }}</span>
          </a-tooltip>
        </template>
        <!--
          责任人：库里存的是角色 code（SYSTEM_ENGINEER），显示用后端补的 roleName（系统工程师）；
          悬停再给 code —— 核对配置时要能对上角色定义里的那一行。
        -->
        <template v-else-if="column.key === 'ownerRole'">
          <a-tooltip v-if="record.ownerRole" :title="roleLabel(record)">
            <a-tag size="small" color="purple">{{ record.ownerRoleName || record.ownerRole }}</a-tag>
          </a-tooltip>
          <span v-else style="color:#bfbfbf">-</span>
        </template>
        <!--
          状态（交付件）：这一枚「进行中」的依据全在产出物身上（见后端 DeliverableStatusEvaluator），
          所以悬停拉出小浮层 —— 每条产出物的生命周期状态 + 是否在流程中 / 当前节点 / 办理人。
          悬停才请求（46 条 × N 个产出物 × 流程查询，预加载不划算），结果按交付件缓存。
          门 / 评审 / 规则等其余类的状态没有产出物这层含义，保持纯标签。
        -->
        <template v-else-if="column.key === 'status'">
          <a-tooltip
            v-if="kind === 'deliverable'"
            placement="left"
            :overlay-style="{ maxWidth: '460px' }"
            @openChange="(open) => open && loadProgress(record)"
          >
            <template #title>
              <div class="mi-tip">
                <div class="mi-tip-head">
                  交付件「{{ record.code }} {{ record.name }}」· {{ STATUS_LABEL[record.status] || record.status || '-' }}
                </div>
                <div v-if="progressOf(record).loading" class="mi-tip-note">正在读取产出物状态…</div>
                <div v-else-if="!progressOf(record).items.length && progressOf(record).error" class="mi-tip-note mi-tip-error">{{ progressOf(record).error }}</div>
                <template v-else>
                  <!-- 完成条件逐条判定：✓ 满足 / ✗ 不满足 / ？不可判定（平台尚未实现）—— 这就是"为什么还不是已完成" -->
                  <div v-if="progressOf(record).rules.length" class="mi-tip-section">完成条件判定</div>
                  <div v-for="r in progressOf(record).rules" :key="r.type + '|' + r.detail" class="mi-tip-rule">
                    <span class="mi-tip-verdict" :class="verdictClass(r.satisfied)">{{ verdictMark(r.satisfied) }}</span>
                    <span class="mi-tip-rule-label">{{ r.label }}</span>
                    <span class="mi-tip-rule-detail">{{ r.detail }}</span>
                  </div>

                  <div class="mi-tip-section">产出物（{{ progressOf(record).items.length }}）</div>
                  <div v-if="!progressOf(record).items.length" class="mi-tip-note">尚未关联产出物</div>
                  <div v-for="it in progressOf(record).items" :key="it.entityType + '|' + it.entityOid" class="mi-tip-item">
                    <div class="mi-tip-row">
                      <span class="mi-tip-host">{{ HOST_LABEL[it.entityType] || it.entityType }}</span>
                      <code class="mi-tip-code">{{ it.entityCode || '-' }}</code>
                      <span class="mi-tip-name">{{ it.entityName || '-' }}</span>
                      <span class="mi-tip-state" :class="lifecycleTone(it.statusCode)">
                        {{ it.statusName || it.statusCode || '无状态' }}
                      </span>
                    </div>
                    <div class="mi-tip-flow" :class="{ 'mi-tip-flow-on': it.inProcess }">
                      <template v-if="it.inProcess">
                        流程中 · {{ it.processName || '未命名流程' }} · 当前节点 {{ it.currentNode || '—' }}（{{ it.currentAssignees || '未指派' }}）
                      </template>
                      <template v-else>未在流程中</template>
                    </div>
                  </div>
                </template>
                <div v-if="progressOf(record).error && progressOf(record).items.length" class="mi-tip-note mi-tip-error">
                  {{ progressOf(record).error }}（下面是上次读到的结果）
                </div>
                <div class="mi-tip-foot">全部子项满足才算已完成；标「？」的是平台尚未实现的判定，按口径一律不判完成</div>
              </div>
            </template>
            <a-tag :color="STATUS_COLOR[record.status] || 'default'" size="small" style="cursor:help">{{ STATUS_LABEL[record.status] || record.status || '-' }}</a-tag>
          </a-tooltip>
          <!--
            检查规则：这一列问的不是"走到哪一步"（规则没有进度），而是**在本项目适不适用**。
            口径就是裁剪开关 disabled —— 与行名旁的「本项目不适用」标签同一件事，
            显示成「未开始」属于张冠李戴（用户 2026-10-26 指出）。
          -->
          <a-tooltip
            v-else-if="kind === 'rule'"
            :title="record.disabled
              ? '本项目不适用：已被裁剪，不参与检查（可恢复）'
              : '本项目适用：设计保存/门检查会按它的提示语校验'"
          >
            <a-tag :color="record.disabled ? 'default' : 'green'" size="small">
              {{ record.disabled ? '不适用' : '适用' }}
            </a-tag>
          </a-tooltip>
          <!--
            评审 / 阶段门：状态来自流程侧现算（后端 ReviewProcessEvaluator / GateProcessEvaluator），
            所以悬停给出"依据" —— 进行中看到卡在哪个节点、等谁办；已结束看到结束时间（门再带出决策）。
            没有流程实例时保持纯标签。
          -->
          <a-tooltip
            v-else-if="(kind === 'review' || kind === 'gate') && record.processInstanceId"
            placement="left"
            :overlay-style="{ maxWidth: '400px' }"
          >
            <template #title>
              <div class="mi-tip">
                <div class="mi-tip-head">
                  {{ kind === 'gate' ? '阶段门' : '评审' }}「{{ record.code }} {{ record.name }}」· {{ STATUS_LABEL[record.status] || record.status || '-' }}
                </div>
                <template v-if="isProcessRunning(record)">
                  <div class="mi-tip-section">当前节点</div>
                  <div class="mi-tip-rule">
                    <span class="mi-tip-rule-label">{{ record.currentActivityName || '—' }}</span>
                    <span class="mi-tip-rule-detail">办理人 {{ record.currentAssignees || '未指派' }}</span>
                  </div>
                </template>
                <div v-else-if="record.processStatus === 'terminated'" class="mi-tip-note">
                  流程已终止{{ record.processEndedAt ? ' · ' + shortTime(record.processEndedAt) : '' }}，本条{{ kind === 'gate' ? '决策' : '评审' }}可重新发起
                </div>
                <div v-else class="mi-tip-note">
                  流程已结束{{ record.processEndedAt ? ' · ' + shortTime(record.processEndedAt) : '' }}<template
                    v-if="kind === 'gate' && record.decision"
                  > · 决策 {{ gateDecisionLabel(record.decision) }}</template>
                </div>
                <div class="mi-tip-foot">
                  发起人 {{ record.processStartUserName || '—' }} · 开始 {{ shortTime(record.processStartedAt) || '—' }}
                </div>
              </div>
            </template>
            <a-tag :color="STATUS_COLOR[record.status] || 'default'" size="small" style="cursor:help">
              {{ STATUS_LABEL[record.status] || record.status || '-' }}
            </a-tag>
          </a-tooltip>
          <a-tag v-else :color="STATUS_COLOR[record.status] || 'default'" size="small">{{ STATUS_LABEL[record.status] || record.status || '-' }}</a-tag>
        </template>
        <!--
          类型：门与评审是**两套码**（门 TECHNICAL_REVIEW / DECISION_REVIEW，评审 TECHNICAL），
          各自翻译；认不出的码原样显示，不吞掉信息。
        -->
        <template v-else-if="column.key === 'type'">
          <a-tag v-if="record.type" size="small" :color="kind === 'gate' ? gateTypeColor(record.type) : 'default'">
            {{ kind === 'gate' ? gateTypeLabel(record.type) : reviewTypeLabel(record.type) }}
          </a-tag>
          <span v-else style="color:#bfbfbf">-</span>
        </template>
        <template v-else-if="column.key === 'decision'">
          <a-tooltip v-if="record.decision" :title="record.decision">
            <a-tag :color="gateDecisionColor(record.decision)" size="small">{{ gateDecisionLabel(record.decision) }}</a-tag>
          </a-tooltip>
          <!-- 决策流程在跑但还没走到「结论留痕」：说"决策中"而不是"未决策" —— 后者读起来像没人管 -->
          <a-tooltip v-else-if="isProcessRunning(record)" title="决策流程正在进行中，结论在「结论留痕」节点登记">
            <span style="color:#1677ff">决策中</span>
          </a-tooltip>
          <span v-else style="color:#bfbfbf">未决策</span>
        </template>
        <template v-else-if="column.key === 'decidedBy'">
          <span v-if="record.decidedBy" class="mi-small">{{ record.decidedBy }}<template v-if="record.decidedAt"> · {{ shortTime(record.decidedAt) }}</template></span>
          <span v-else style="color:#bfbfbf">-</span>
        </template>
        <template v-else-if="column.key === 'verdict'">
          <!-- 库里存的是码（PASS / PASS_WITH_CONDITION / FAIL），界面说人话 -->
          <a-tag v-if="record.verdict" :color="VERDICT_COLOR[record.verdict] || 'green'" size="small">
            {{ VERDICT_LABEL[record.verdict] || record.verdict }}
          </a-tag>
          <!--
            流程走完了却读不到结论：后端按 ckplmTechReview_finalVerdict / _verdict 取，
            两个都没有才落到这里。如实说「未评定」并指路，而不是编一个"通过"，
            也不是继续显示"未评审" —— 后者会让人以为流程还没开始（2026-10-26 用户反馈）。
          -->
          <a-tooltip
            v-else-if="kind === 'review' && record.processStatus === 'completed'"
            title="流程已结束，但没有读到结论变量（ckplmTechReview_finalVerdict / ckplmTechReview_verdict）—— 可能流程没经过「结论登记」节点。结论内容请点右侧「查看流程」，看该节点的办理意见"
          >
            <a-tag color="orange" size="small" style="cursor:help">未评定</a-tag>
          </a-tooltip>
          <span v-else style="color:#bfbfbf">未评审</span>
        </template>
        <template v-else-if="column.key === 'severity'">
          <a-tooltip v-if="record.severity" :title="record.severity">
            <a-tag :color="SEVERITY_COLOR[record.severity] || 'default'" size="small">
              {{ SEVERITY_LABEL[record.severity] || record.severity }}
            </a-tag>
          </a-tooltip>
          <span v-else style="color:#bfbfbf">-</span>
        </template>
        <!--
          检查项：库里是 [{seq,text,severity}] 的 JSON，列上只放数量 ——
          点开看逐条内容（评审就是照着这几条过，看不到内容等于这列没用）。
        -->
        <template v-else-if="column.key === 'checkItems'">
          <a-popover
            v-if="checkItemsOf(record).length"
            placement="left"
            :overlay-style="{ maxWidth: '540px' }"
            :title="`检查项（${checkItemsOf(record).length} 项）`"
          >
            <template #content>
              <div class="mi-check-list">
                <div
                  v-for="item in checkItemsOf(record)"
                  :key="item.seq + '|' + item.text"
                  class="mi-check-row"
                >
                  <span class="mi-check-seq">{{ item.seq }}</span>
                  <span class="mi-check-text">{{ item.text }}</span>
                  <a-tag
                    v-if="item.severity"
                    size="small"
                    :color="SEVERITY_COLOR[item.severity] || 'default'"
                  >{{ SEVERITY_LABEL[item.severity] || item.severity }}</a-tag>
                </div>
              </div>
            </template>
            <span class="mi-check-count">{{ checkItemsOf(record).length }} 项</span>
          </a-popover>
          <span v-else style="color:#bfbfbf">-</span>
        </template>
        <template v-else-if="column.key === 'source'">
          <a-tooltip :title="record.source">
            <span class="mi-ellipsis">{{ record.source || '-' }}</span>
          </a-tooltip>
        </template>
        <template v-else-if="column.key === 'readiness'">
          <a-tag v-if="record.readiness" size="small" :color="record.readiness === 'A' ? 'green' : (record.readiness === 'B' ? 'blue' : 'orange')">{{ record.readiness }} 级</a-tag>
          <span v-else style="color:#bfbfbf">未评估</span>
        </template>
        <template v-else-if="column.key === 'value'">
          <!-- 有数：值 + 单位，明细（几位小数、几行缺成本…）进悬停；没数：说清楚缺什么，不堆在列里 -->
          <a-tooltip v-if="record.value != null" :title="record.valueText">
            <span class="mi-metric-value">{{ metricText(record) }}</span>
          </a-tooltip>
          <a-tooltip v-else :title="record.valueText || '该指标尚无取数依据'">
            <div class="mi-metric-empty">
              <span class="mi-not-evaluated">暂未评估</span>
              <span v-if="record.valueText" class="mi-metric-why">{{ record.valueText }}</span>
            </div>
          </a-tooltip>
        </template>
        <template v-else-if="column.key === 'widgets'">
          <a-tooltip :title="widgetCodes(record.widgets).join('、')">
            <a-space size="4" wrap>
              <a-tag v-for="w in widgetCodes(record.widgets).slice(0, 3)" :key="w" size="small" :color="WIDGET_LABEL[w] ? 'cyan' : 'default'">{{ WIDGET_LABEL[w] || w }}</a-tag>
              <span v-if="widgetCodes(record.widgets).length > 3" class="mi-small">+{{ widgetCodes(record.widgets).length - 3 }}</span>
              <span v-if="!widgetCodes(record.widgets).length" style="color:#bfbfbf">-</span>
            </a-space>
          </a-tooltip>
        </template>
        <template v-else-if="column.key === 'primaryScope'">
          <a-tag v-if="record.primaryScope" color="gold" size="small">★主视角</a-tag>
          <span v-else style="color:#bfbfbf">-</span>
        </template>
        <template v-else-if="column.key === 'disabled'">
          <a-tag :color="record.disabled ? 'default' : 'green'" size="small">{{ record.disabled ? '已停用' : '启用' }}</a-tag>
        </template>
        <!--
          产出物：点击打开关联弹窗。权限（2026-10-14）= 产品经理 / 管理员，
          或我的团队角色 = 本件的**责任人**（谁负责谁关联）；后端同样校验。
          没权限时**禁用**而不是隐藏 —— 数据还在，只是不能改，说明写在 tooltip 上。
        -->
        <template v-else-if="column.key === 'artifacts'">
          <a-tooltip
            :title="canLink(record)
              ? '关联 / 解除产出物'
              : `没有关联权：需该系列/型号团队里的「产品经理」，或本件的责任人（${roleLabel(record) || '未设置'}）`"
          >
            <a-button
              type="link"
              size="small"
              style="padding:0"
              :disabled="!canLink(record)"
              @click="canLink(record) && emit('open-artifacts', record)"
            >{{ artifactSummary[record.oid] || 0 }} 项</a-button>
          </a-tooltip>
        </template>
        <!--
          操作列（2026-10-14 起只用图标，提示在 tooltip 上 —— 文字按钮会把表格挤满）：
          · 发起（门 / 评审，需项目角色可发起）：行上自带流程 key；已出结论的不再给入口；
          · 裁剪（交付件 / 门 / 评审 / 规则，需项目角色 = 产品经理）：标记「本项目不适用」/ 恢复；
          · 编辑（交付件）：责任人 + 强制项，收进一个弹窗。
        -->
        <template v-else-if="column.key === 'action'">
          <a-space size="2">
            <a-tooltip v-if="canStart(record)" title="发起流程">
              <a-button type="link" size="small" @click="emit('start-process', record)">
                <SendOutlined />
              </a-button>
            </a-tooltip>
            <!--
              评审已发起：给一条去看流程的路。清单只回答"到哪一步"，谁在哪一步写了什么
              得在流程详情里看（结论登记目前是自由意见，只有那里看得到）。
            -->
            <a-tooltip v-if="(kind === 'review' || kind === 'gate') && record.processInstanceId" title="查看流程：当前节点 / 进度 / 各节点意见">
              <a-button type="link" size="small" @click="emit('open-process', record)">
                <EyeOutlined />
              </a-button>
            </a-tooltip>
            <a-tooltip
              v-if="tailorOn"
              :title="tailorTitle(record)"
            >
              <a-button
                type="link"
                size="small"
                :disabled="tailorBlocked(record)"
                @click="!tailorBlocked(record) && emit('tailor', kind, record, { disabled: !record.disabled })"
              >
                <RollbackOutlined v-if="record.disabled" />
                <StopOutlined v-else />
              </a-button>
            </a-tooltip>
            <!-- 编辑（交付件）：责任人 + 强制项。已被裁剪（本项目不适用）的行不显示 ——
                 不适用的项先恢复适用再谈编辑，编辑一个"不存在于本项目"的项只会造成困惑 -->
            <a-tooltip v-if="tailorOn && kind === 'deliverable' && !record.disabled" title="编辑：责任人 / 强制项">
              <a-button type="link" size="small" @click="openEdit(record)">
                <EditOutlined />
              </a-button>
            </a-tooltip>
            <span v-if="!hasActions(record)" style="color:#bfbfbf">-</span>
          </a-space>
        </template>
      </template>

    </DataTable>

    <!--
      编辑弹窗（交付件专用）：责任人 + 强制项。
      提交只 emit tailor 事件，由宿主统一写库并刷新 —— "谁写库"只有一个出口。
    -->
    <a-modal
      v-model:visible="editVisible"
      :title="`编辑「${editRecord?.name || editRecord?.code || ''}」`"
      ok-text="保存"
      cancel-text="取消"
      width="480px"
      centered
      @ok="submitEdit"
    >
      <a-form layout="vertical">
        <a-form-item label="责任人">
          <a-select
            v-model:value="editForm.ownerRole"
            :options="roleOptions"
            :loading="roleLoading"
            show-search
            option-filter-prop="label"
            placeholder="选择角色 —— 决定谁负责关联 / 提交本交付件的产出物"
            allow-clear
          />
        </a-form-item>
        <a-form-item label="强制项">
          <a-radio-group v-model:value="editForm.mandatory">
            <a-radio :value="true">强制（本项目必须完成）</a-radio>
            <a-radio :value="false">可选</a-radio>
          </a-radio-group>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import {
  SendOutlined, StopOutlined, RollbackOutlined, EditOutlined, EyeOutlined,
} from '@ant-design/icons-vue'
import DataTable from '@/components/DataTable.vue'
import { getRoles, getDeliverableArtifactProgress } from '@/api'
// 门 / 评审的类型与决策码 → 中文：与「业务配置 → 阶段门模板」同一份字典（见 utils/gateDefs）
import {
  gateDecisionColor,
  gateDecisionLabel,
  gateTypeColor,
  gateTypeLabel,
  reviewTypeLabel,
} from '@/utils/gateDefs'
// 分组件码 → 中文名：与整机视图的页签、模板管理页共用同一份字典（见 utils/dashboardWidgets）
import { WIDGET_LABEL } from '@/utils/dashboardWidgets'

/**
 * 方法论实例面板 —— 六类（交付件 / 阶段门 / 评审 / 检查规则 / 指标 / 驾驶舱）共用一个组件。
 *
 * 本组件只负责"把行显示对"：数据由宿主页面一次取回（GET /api/instances）后按阶段切片传入，
 * 因此切阶段是纯前端动作、不发请求。**评审行与阶段门的状态都已接流程**（后端现算 processStatus /
 * currentActivityName，见 ReviewProcessEvaluator / GateProcessEvaluator）：在跑显示「进行中」
 * 并可点「查看流程」，走完显示「已完成 / 已通过 / 已否决」，悬停给出依据（当前节点、办理人、发起人、
 * 决策）。没有流程实例的行仍是纯标签。
 * **阶段门 / 评审**另有一个动作：行上自带 {@code processKey}，可点「发起」把这条门/评审的流程走起来
 * —— 组件只 emit {@code start-process} / {@code open-process}，弹框、路由与发起由宿主页面负责
 * （与其他行操作的落法一致）。
 *
 * <p><b>裁剪</b>（2026-10-14 用户要求："产品经理具备对交付件、阶段门、技术评审和检查规则裁剪的权利"）：
 * 四类的行上可点「标记不适用 / 恢复」，交付件另有「强制 ↔ 可选」；<b>只动本系列 / 型号的实例</b>，
 * 模板与其它项目不受影响（与模板层"企业裁剪"是两层）。按钮显隐由宿主的 {@code canTailor}
 * （角色判定：PRODUCT_MANAGER / TENANT_ADMIN）控制，组件只 emit {@code tailor(record, patch)}，
 * 调接口与刷新由宿主负责。
 */
const props = defineProps({
  /** 六类之一：deliverable | gate | review | rule | metric | dashboard */
  kind: { type: String, required: true },
  rows: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  /** 阶段 code → 阶段名（宿主页面传入，避免本组件再打一次阶段接口） */
  stageNames: { type: Object, default: () => ({}) },
  /** 是否显示「所属阶段」列（阶段内嵌面板已按阶段过滤，可不显示） */
  showStage: { type: Boolean, default: false },
  /**
   * 本对象的同类实例**全集**（不是本阶段的行数）。
   * 用途：区分"真的一条都没有"与"有数据但不在当前阶段下"——
   * 后者常见于阶段与内容方法学不一致的存量对象（如阶段=传统六码、交付件=IPD 码）。
   * 这种情况下**改列全集**并明确说明，而不是把已有数据藏起来显示 0。
   */
  allRows: { type: Array, default: () => [] },
  /**
   * 交付件面板专用（2026-10-11）：显示「产出物」列并允许点击打开关联弹窗。
   * 产出物的写入口只在本面板（用户要求：只在交付件管理页关联）；其它宿主不传 = 不显示该列。
   */
  showArtifacts: { type: Boolean, default: false },
  /** 交付件 oid → 已关联产出物数（宿主用 /deliverable-artifact-links/summary 取） */
  artifactSummary: { type: Object, default: () => ({}) },
  /**
   * 「我在这个系列 / 型号下能做什么」—— 随 `GET /api/instances` 一起返回（InstancePermissions）。
   *
   * <pre>
   *   { admin, canTailor, canStartProcess, myRoleNames, myRoleCodes }
   * </pre>
   *
   * 口径（2026-10-14 用户选定，项目级）：
   * · 裁剪 = 团队角色「产品经理」（canTailor）；
   * · 发起流程 = 「产品经理 / 项目经理 / IPDT 组长」（canStartProcess）；
   * · 关联交付项 = 产品经理，或我的团队角色 = 该交付件的责任人（下面 canLink 逐条判）。
   * 为 false / 缺省时不显示对应按钮（后端同样会拦 403，这里只是不给按钮）。
   */
  permissions: { type: Object, default: null },
  /**
   * 阶段门的 ISSUE_OPEN 自动检查结果：`门 oid → { actual, blockCount, warnCount, passed, message, maxCount }`。
   *
   * <p>宿主按 ownerOid 拉 {@code /api/issues/gate-check} 后转成 map 传入（数据由宿主取，与 rows 同口径）；
   * 空对象 = 无门声明该检查（或租户未启用 GATE_REVIEW）→ 不显示「未闭环问题」列。
   */
  gateCheck: { type: Object, default: () => ({}) },
  /**
   * 本阶段 0 条、但本对象有数据时，是否改列全集并弹提示（默认 true，沿用"阶段与内容方法学不一致"的处理）。
   *
   * <p>阶段门页签里的两块要关掉它：某阶段没有决策门 / 没有技术评审是**正常状态**
   * （比如开发阶段只有技术评审），不该把别的阶段的门搬过来当成本阶段的，
   * 更不该弹一条"阶段与内容方法学不一致"的黄条吓人。
   */
  mismatchFallback: { type: Boolean, default: true },
  /** 自定义空态文案（关掉 fallback 时用，如"本阶段没有决策评审内容"） */
  emptyHint: { type: String, default: '' }
})

const emit = defineEmits(['open-artifacts', 'start-process', 'tailor', 'open-process'])

/** 该评审的流程是否还在跑（running / suspended —— 挂起也算没结束，与后端闸门口径一致） */
function isProcessRunning(record) {
  const s = record?.processStatus
  return s === 'running' || s === 'suspended'
}

// ==================== 项目级权限（随 /api/instances 返回） ====================

/** 权限摘要（缺省 = 什么都不给） */
const perm = computed(() => props.permissions || {})
const isAdmin = computed(() => !!perm.value.admin)
const canTailor = computed(() => !!perm.value.canTailor)
const canStartProcess = computed(() => !!perm.value.canStartProcess)
const myRoleCodes = computed(() => perm.value.myRoleCodes || [])
const myRoleNames = computed(() => perm.value.myRoleNames || [])

/**
 * 该行能不能发起流程：**项目角色可发起** + 行上有 {@code processKey} + **尚未出结论**。
 *
 * <p>已决策的门 / 已评审的评审不再给入口 —— 它们已经走完了这条流程；
 * 无 processKey 说明这条模板没绑流程，按钮不显示比点了报错好。
 */
function canStart(record, kind = props.kind) {
  if (!canStartProcess.value || !record?.processKey) return false
  // 门：已出决策、或决策流程还在跑（2026-10-29 起门状态已由 GateProcessEvaluator 现算）都不再给入口
  if (kind === 'gate') return !record.decision && !isProcessRunning(record)
  // 评审：已出结论、或流程还在跑（running / suspended）都不再给入口 ——
  // 后端本来就会以"该版本已有流程在执行中"拦住，这里先一步不给点，免得点了才报错
  if (kind === 'review') return !record.verdict && !isProcessRunning(record)
  return false
}

/**
 * 可裁剪的四类（与后端 tailor 支持的一致）—— 指标 / 驾驶舱不在此列
 * （它们是展示口径，不是"做不做"的选择，用户需求也只点了这四类）。
 */
const TAILORABLE_KINDS = ['deliverable', 'gate', 'review', 'rule']

/** 本类的行是否显示裁剪按钮（项目角色 = 产品经理 + 类别支持） */
const tailorOn = computed(() => canTailor.value && TAILORABLE_KINDS.includes(props.kind))

/**
 * 交付件的「产出物」关联按钮能不能点：产品经理 / 管理员，
 * 或我的团队角色 = 该交付件的**责任人**（谁负责谁关联）；后端同样校验。
 */
/** 责任人展示文案：有角色名就是「系统工程师（SYSTEM_ENGINEER）」，取不到名就退回角色码 */
function roleLabel(record) {
  const name = (record?.ownerRoleName || '').trim()
  const code = (record?.ownerRole || '').trim()
  if (!code) return name
  return name && name !== code ? `${name}（${code}）` : code
}

function canLink(record) {
  if (props.kind !== 'deliverable') return false
  if (isAdmin.value || canTailor.value) return true
  const want = (record?.ownerRole || '').trim()
  if (!want) return false
  return myRoleCodes.value.includes(want) || myRoleNames.value.includes(want)
}

/** 该行有没有任何操作可点（没有就显示 "-"） */
function hasActions(record) {
  const canViewProcess = (props.kind === 'review' || props.kind === 'gate') && !!record?.processInstanceId
  return canStart(record) || tailorOn.value || canViewProcess
}

/**
 * 裁剪按钮是否禁用。
 *
 * <p>已经"动起来"的不能标记「本项目不适用」——停用等于把记录从清单里抹掉：
 * <ul>
 *   <li><b>评审进行中</b>（2026-10-26 用户口径："已经在进行中的不可以裁剪"）：办到一半的人回头找不到自己那条；</li>
 *   <li><b>评审已完成</b>（2026-10-29 用户反馈）：结论、条件都留在流程里，清单上却少一条，无法追溯；</li>
 *   <li><b>决策门</b>：状态一旦离开「未开始」，决策留痕同样不能跟着条目消失。</li>
 * </ul>
 * 后端 `tailor` 接口同样会拒；前端置灰只是别让人白点一下（禁止项要说明原因，不能只变灰）。
 * 恢复适用（disabled → false）不受限：那是把条目请回来，不是抹掉。
 */
function tailorBlocked(record, kind = props.kind) {
  if (record?.disabled) return false
  if (kind === 'review') return isProcessRunning(record) || record?.status === 'DONE'
  if (kind === 'gate') return !!record?.status && record.status !== 'PENDING'
  return false
}

/** 裁剪按钮的提示语：禁用时把"为什么不能点"写清楚，恢复适用时照旧 */
function tailorTitle(record, kind = props.kind) {
  if (record?.disabled) return '恢复适用（本项重新生效）'
  if (tailorBlocked(record, kind)) {
    if (kind === 'gate') return '决策流程已开始，不能标记「本项目不适用」；请先结束该决策流程'
    if (isProcessRunning(record)) return '评审进行中，不能标记「本项目不适用」；请先完成或终止该流程'
    return '评审已完成（结论已留痕），不能再标记「本项目不适用」；如需作废请走变更'
  }
  return '标记「本项目不适用」（不删除，可恢复）'
}

// ==================== 状态列浮层：产出物进展 ====================

/** 产出物宿主的中文名（浮层里区分"文档 / 工程数据 / 零组件"） */
const HOST_LABEL = { DOCUMENT: '文档', ENG_DOCUMENT: '工程数据', PART: '零组件' }

/**
 * 生命周期状态 → 浮层里的小色块（按语义归类，不认得的 code 一律走默认白字）。
 * 为什么不用一一对应的固定色表：状态 code 由各租户的生命周期模板定义（DRAFT / INWORK / REVIEW
 * 只是常见那套，本库实际就有 APPROVING），写死一套必然漏 —— 漏了不该变成"看不出是什么状态"。
 */
const LIFECYCLE_TONE = {
  DRAFT: 'is-draft', PENDING: 'is-draft', NEW: 'is-draft',
  INWORK: 'is-inwork', WORKING: 'is-inwork', DESIGN: 'is-inwork',
  REVIEW: 'is-review', REVIEWING: 'is-review', APPROVING: 'is-review', AUDIT: 'is-review',
  APPROVED: 'is-ok', RELEASED: 'is-ok', FROZEN: 'is-ok', PUBLISHED: 'is-ok',
}

function lifecycleTone(code) {
  return LIFECYCLE_TONE[String(code || '').toUpperCase()] || ''
}

/** 浮层缓存：deliverableOid → { loading, items, error }（悬停才拉，按交付件缓存） */
const progressCache = ref({})

/** 某行的浮层状态（无缓存时是空态，模板据此渲染"正在读取 / 无产出物"） */
function progressOf(record) {
  const cached = record?.oid ? progressCache.value[record.oid] : null
  return cached || { loading: false, items: [], rules: [], error: '' }
}

/** 完成条件判定的符号与配色：✓ 满足 / ✗ 不满足 / ？不可判定（平台未实现 —— 同样不判完成） */
function verdictMark(satisfied) {
  if (satisfied === true) return '✓'
  return satisfied === false ? '✗' : '？'
}

function verdictClass(satisfied) {
  if (satisfied === true) return 'is-pass'
  return satisfied === false ? 'is-fail' : 'is-unknown'
}

/**
 * 拉某交付件的产出物进展：每条产出物的生命周期状态 + 是否在流程中 / 当前节点 / 办理人。
 *
 * <p>口径：
 * <ul>
 *   <li><b>悬停才请求</b>（与 BOM 替代明细同一做法）—— 列表里 46 条交付件，每条再乘上
 *       N 个产出物的流程查询，预加载的代价与"只想看一眼"的收益不对等；</li>
 *   <li><b>每次悬停都重取</b>，但先显示上一次的结果（旧值立刻可见，新值到了再替换）——
 *       这几个字段恰恰是"随时会变"的（文档刚被审核通过、流程刚走到下一节点），
 *       缓存住不刷新等于告诉用户一个过时的事实；</li>
 *   <li>失败不吞：浮层里如实写"读取失败"，下一次悬停会重试。</li>
 * </ul>
 */
async function loadProgress(record) {
  const oid = record?.oid
  if (!oid) return
  const cached = progressCache.value[oid]
  if (cached && cached.loading) return // 上一次还没回来：不叠加请求
  const staleItems = cached && cached.items.length ? cached.items : []
  const staleRules = cached && cached.rules ? cached.rules : []
  progressCache.value = {
    ...progressCache.value,
    [oid]: { loading: !staleItems.length, items: staleItems, rules: staleRules, error: '' },
  }
  try {
    const res = await getDeliverableArtifactProgress(oid)
    const data = (res?.code === 200 && res.data) ? res.data : {}
    const items = Array.isArray(data.items) ? data.items : []
    // completion 是"逐条完成条件判定"，与列表状态同源（后端 DeliverableStatusEvaluator#judge）
    const rules = Array.isArray(data.completion?.rules) ? data.completion.rules : []
    progressCache.value = { ...progressCache.value, [oid]: { loading: false, items, rules, error: '' } }
  } catch {
    // 失败原因由响应拦截器统一提示；这里给浮层一句可读的兜底
    progressCache.value = {
      ...progressCache.value,
      [oid]: { loading: false, items: staleItems, rules: staleRules, error: '读取产出物状态失败，稍后重试' },
    }
  }
}

// ==================== 编辑弹窗（责任人 + 强制项） ====================

const editVisible = ref(false)
const editRecord = ref(null)
const editForm = ref({ ownerRole: undefined, mandatory: true })
const roleOptions = ref([])
const roleLoading = ref(false)

/** 打开编辑：拉一次租户角色清单（组件内缓存；失败不阻断，选择器给空） */
async function openEdit(record) {
  editRecord.value = record
  editForm.value = {
    ownerRole: record?.ownerRole || undefined,
    mandatory: record?.mandatory !== false,
  }
  editVisible.value = true
  if (!roleOptions.value.length) {
    roleLoading.value = true
    try {
      const res = await getRoles()
      roleOptions.value = (res?.code === 200 ? (res.data || []) : [])
        .filter((r) => r && r.code)
        .map((r) => ({ value: r.code, label: `${r.name || r.code}（${r.code}）` }))
    } catch {
      roleOptions.value = []
    } finally {
      roleLoading.value = false
    }
  }
}

/**
 * 提交编辑：把有变化的字段交给宿主（与裁剪同一事件 tailor，宿主统一调接口 + 刷新）——
 * 组件不直接打接口，"谁写库"只有一个出口。
 */
function submitEdit() {
  const record = editRecord.value
  if (!record) return
  const patch = {}
  const nextRole = (editForm.value.ownerRole || '').trim()
  if (nextRole && nextRole !== (record.ownerRole || '')) {
    patch.ownerRole = nextRole
  }
  if (editForm.value.mandatory !== (record.mandatory !== false)) {
    patch.mandatory = editForm.value.mandatory
  }
  if (!Object.keys(patch).length) {
    message.info('没有改动')
    return
  }
  editVisible.value = false
  emit('tailor', props.kind, record, patch)
}

// 行切换（切阶段 / 换对象）时关掉编辑弹窗，避免改到"上一条"
watch(() => props.rows, () => { editVisible.value = false })

const STATUS_LABEL = {
  PENDING: '未开始', IN_PROGRESS: '进行中', DONE: '已完成', NA: '不适用',
  READY: '可评审', PASSED: '已通过', REJECTED: '已否决',
  PASS: '通过', FAIL: '未通过'
}
const STATUS_COLOR = {
  PENDING: 'default', IN_PROGRESS: 'processing', DONE: 'green', NA: 'default',
  READY: 'blue', PASSED: 'green', REJECTED: 'red',
  PASS: 'green', FAIL: 'red'
}
/** 评审结论码 → 人话：库里存的是码，直接甩出来用户读不懂 */
const VERDICT_LABEL = {
  PASS: '通过', PASS_WITH_CONDITION: '有条件通过', FAIL: '不通过'
}
const VERDICT_COLOR = {
  PASS: 'green', PASS_WITH_CONDITION: 'orange', FAIL: 'red'
}
const SEVERITY_COLOR = { BLOCK: 'red', WARN: 'orange', INFO: 'blue' }
/** 严重度：库里是码（BLOCK / WARN / INFO），界面要说人话 */
const SEVERITY_LABEL = { BLOCK: '阻断', WARN: '警告', INFO: '提示' }
// WIDGET_LABEL 已抽到 utils/dashboardWidgets（2026-10-26）—— 整机视图页签也要用，三处共用一份
const RULE_TYPE_LABEL = {
  BOUND: '已绑定产出物', LIFECYCLE: '生命周期达标', REVIEW_PASSED: '评审通过',
  ISSUE_CLOSED: '阻断问题清零', FIELD_CHECK: '字段校验', COVERAGE: '覆盖率'
}

const stageCol = { title: '所属阶段', key: 'stage', width: 130 }
const statusCol = { title: '状态', key: 'status', width: 90 }
/**
 * 检查规则专用：这一列表达"本项目适不适用"，与交付件/门/评审的"状态"不是一回事，
 * 表头也照着改，免得用户以为规则还有"未开始 → 进行中 → 完成"的进度。
 */
const ruleApplicabilityCol = { title: '适用性', key: 'status', width: 90 }
/**
 * 「操作」列 —— 按类与权限渲染按钮：
 * <ul>
 *   <li><b>发起</b>（阶段门 / 评审）：行上自带 {@code processKey}，点它把这条门/评审的流程走起来；</li>
 *   <li><b>裁剪</b>（交付件 / 阶段门 / 评审 / 检查规则，且当前用户有裁剪权）：
 *       「本项目不适用」（停用 / 恢复）；交付件另有「强制 ↔ 可选」。</li>
 * </ul>
 * 宽度按最宽情形（交付件三项并排）留。
 */
const actionCol = { title: '操作', key: 'action', width: 170 }

const COLUMNS = {
  deliverable: [
    { title: '编码', dataIndex: 'code', key: 'code', width: 170 },
    { title: '名称', dataIndex: 'name', key: 'name', width: 200 },
    stageCol,
    { title: '专业', dataIndex: 'discipline', key: 'discipline', width: 100 },
    { title: '强制', key: 'mandatory', width: 80 },
    { title: '责任人', key: 'ownerRole', width: 130 },
    { title: '阶段门', dataIndex: 'gate', key: 'gate', width: 90 },
    { title: '完成条件', key: 'completionRule', width: 260 },
    { title: '产出物', key: 'artifacts', width: 110 },
    // 2026-10-11 用户要求：不再展示「工期偏移」（列删掉；字段仍在库里，只是不在面板露出）
    statusCol,
    actionCol
  ],
  gate: [
    { title: '编码', dataIndex: 'code', key: 'code', width: 130 },
    { title: '名称', dataIndex: 'name', key: 'name', width: 240 },
    stageCol,
    { title: '类型', dataIndex: 'type', key: 'type', width: 110 },
    // 自动检查（2026-10-15 接上）：ISSUE_OPEN（未闭环问题）+ DELIVERABLE_RATE（本阶段交付件完成率）
    { title: '自动检查', key: 'issueCheck', width: 100 },
    // 「决策选项」（可分发的 Go / Hold / Terminate 候选）不在这张表里展示（2026-10-29 用户口径）：
    // 它是配置项，不是这道门的结果；真正要看的是「决策」列（实际达成哪个）。规则仍在模板里维护。
    statusCol,
    { title: '决策', key: 'decision', width: 100 },
    { title: '决策留痕', key: 'decidedBy', width: 160 },
    actionCol
  ],
  review: [
    { title: '编码', dataIndex: 'code', key: 'code', width: 150 },
    { title: '名称', dataIndex: 'name', key: 'name', width: 240 },
    { title: '服务于门', dataIndex: 'gate', key: 'gate', width: 100 },
    { title: '类型', dataIndex: 'type', key: 'type', width: 130 },
    { title: '检查项', key: 'checkItems', width: 90 },
    statusCol,
    { title: '结论', key: 'verdict', width: 100 },
    actionCol
  ],
  rule: [
    { title: '编码', dataIndex: 'code', key: 'code', width: 120 },
    { title: '名称', dataIndex: 'name', key: 'name', width: 220 },
    stageCol,
    { title: '触发', dataIndex: 'triggerType', key: 'triggerType', width: 100 },
    { title: '分类', dataIndex: 'category', key: 'category', width: 110 },
    { title: '严重度', key: 'severity', width: 90 },
    ruleApplicabilityCol,
    { title: '提示语', dataIndex: 'message', key: 'message', width: 260 },
    actionCol
  ],
  metric: [
    { title: '编码', dataIndex: 'code', key: 'code', width: 170 },
    { title: '名称', dataIndex: 'name', key: 'name', width: 190 },
    { title: '分层', dataIndex: 'layer', key: 'layer', width: 130 },
    { title: '层级', dataIndex: 'level', key: 'level', width: 90 },
    { title: '取数依据', key: 'source', width: 280 },
    { title: '就绪度', key: 'readiness', width: 100 },
    { title: '数值', key: 'value', width: 190 }
  ],
  dashboard: [
    { title: '视角', dataIndex: 'code', key: 'code', width: 150 },
    { title: '名称', dataIndex: 'name', key: 'name', width: 160 },
    { title: '受众', dataIndex: 'audience', key: 'audience', width: 110 },
    { title: '页数', dataIndex: 'pageCount', key: 'pageCount', width: 80 },
    { title: '分组件', key: 'widgets', width: 320 },
    { title: '生成节奏', dataIndex: 'generatePolicy', key: 'generatePolicy', width: 170 },
    { title: '主视角', key: 'primaryScope', width: 100 },
    { title: '启用', key: 'disabled', width: 90 }
  ]
}

const SUBJECT = {
  deliverable: '交付件', gate: '阶段门', review: '评审',
  rule: '检查规则', metric: '指标', dashboard: '驾驶舱'
}

/**
 * "本阶段无匹配、但本对象有" —— 阶段与内容不同源的特征。
 * 这时不让用户对着 0 发呆：改列全集 + 横幅说明 + 强制带出「所属阶段」列（好看出它们属于哪套码）。
 *
 * <p>注：判据用**未过滤**的 {@code props.rows} —— 本阶段有数据、只是全部被裁剪且我无权看见时
 * （见 {@link canSeeTailored}），不能误报"本阶段无匹配"再把其它阶段的全集搬出来。
 */
const mismatched = computed(() => props.mismatchFallback
  && props.rows.length === 0 && props.allRows.length > 0)

/**
 * 我能不能看见「本项目不适用」（被裁剪）的条目 —— **只按查看者在本项目团队里的角色**判：
 * 产品经理 / 项目经理 / 管理员可见（置灰划掉；恢复入口仍只给产品经理），
 * 团队里其他成员一律看不见 —— 被裁剪的条目对其他成员没有意义，不该占着列表。
 *
 * <p>2026-11-01 用户要求把项目经理也纳入（「产品经理和项目经理，以及租户管理员」），
 * 后端新增权限位 {@code canViewTailored}；旧版后端没有该字段时退回"能裁剪即可见"。
 */
const canSeeTailored = computed(() =>
  perm.value.canViewTailored != null
    ? !!perm.value.canViewTailored
    : (canTailor.value || isAdmin.value))

/** 实际渲染的数据：正常情况下是本阶段的行；错位时列全集；非产品经理滤掉被裁剪的条目 */
const displayRows = computed(() => {
  const base = mismatched.value ? props.allRows : props.rows
  return canSeeTailored.value ? base : base.filter((r) => !r.disabled)
})

/**
 * 适宜 / 不适宜 / 所有 过滤（2026-11-01 用户要求：产品经理页面要能只看其中一类）。
 *
 * <p>{@code disabled} 就是"本项目不适用"：{@code APPLICABLE} = 未被裁剪，{@code NOT_APPLICABLE} = 被裁剪。
 * 只对能看见裁剪行的人出现 —— 其他成员本来就只看得到适宜的条目，开关对他们永远只有一个结果。
 *
 * <p>默认落在<b>适宜</b>（2026-11-01 用户：*"默认到适宜"*）：裁剪过的条目是"本项目不做"，
 * 打开阶段内容先看到要做的那些更合理；想看全貌点「所有」。
 */
const applicability = ref('APPLICABLE')

/**
 * 过滤控件出现与否**只看人**（能看见裁剪行 = 产品经理 / 项目经理 / 管理员）。
 *
 * <p>2026-11-01 用户反馈"前端页面没有出现过滤的操作"：原实现还要求"本阶段确实有裁剪行"，
 * 而默认落在 IPD_PLAN（8 条、0 条裁剪）—— 控件被整块藏起来。裁剪行可能只在某个阶段有，
 * 让工具栏随阶段忽隐忽现比置灰一个选项更糟。
 */
const showApplicabilityFilter = canSeeTailored

/** 表格实际渲染的行（在 displayRows 之上再按"适宜 / 不适宜"筛一道；统计口径仍走 displayRows） */
const tableRows = computed(() => {
  const rows = displayRows.value
  if (applicability.value === 'APPLICABLE') {
    return rows.filter((r) => !r.disabled)
  }
  if (applicability.value === 'NOT_APPLICABLE') {
    return rows.filter((r) => r.disabled)
  }
  return rows
})

/**
 * 失去查看裁剪行的权限时收回默认的「适宜」。
 *
 * <p>不再因为"本阶段没有裁剪行"就替用户改选择：那会把刚点的「不适宜」立刻弹回去，
 * 看着像点不动；空结果由空态文案解释（见 {@link emptyText}）。
 */
watch(showApplicabilityFilter, (visible) => {
  if (!visible) applicability.value = 'APPLICABLE'
})

/** 「适宜」这几天藏掉了几条（其余口径下为 0）—— 用于上方口径行的一句话说明 */
const hiddenByApplicability = computed(() => (
  applicability.value === 'APPLICABLE'
    ? displayRows.value.filter((r) => r.disabled).length
    : 0
))

/**
 * 过滤**真的藏掉了行**时补一句，否则上方"共 8 项"和表里的 2 行对不上账。
 *
 * <p>默认就是「适宜」，所以本阶段没有裁剪行时不写这句 —— 那会儿藏掉的是 0 条，
 * 每块面板都挂一句"当前只显示适宜"只会变成噪音。
 */
const filterNote = computed(() => {
  if (applicability.value === 'NOT_APPLICABLE') {
    return `（当前只显示不适宜的 ${tableRows.value.length} 项）`
  }
  if (hiddenByApplicability.value) {
    return `，已隐藏 ${hiddenByApplicability.value} 项（见「所有」）`
  }
  return ''
})

const columns = computed(() => {
  let cols = COLUMNS[props.kind] || []
  if (!(props.showStage || mismatched.value)) cols = cols.filter((c) => c.key !== 'stage')
  // 「产出物」列只在交付件面板且宿主显式启用时出现（写入口唯一，见 props 注释）
  if (!props.showArtifacts) cols = cols.filter((c) => c.key !== 'artifacts')
  // 「未闭环问题」列只在宿主拉到 ISSUE_OPEN 检查结果时出现（没声明该检查/无能力的租户不显示空列）
  if (props.kind === 'gate' && !hasIssueCheck.value) cols = cols.filter((c) => c.key !== 'issueCheck')
  // 「服务于门」列在扁平化后失去意义（技术评审直接挂阶段），老数据里那条 TR1 只在库里，
  // 界面上不再拿它当一列展示 —— 阶段列已经回答了"挂在哪儿"。
  if (props.kind === 'review') cols = cols.filter((c) => c.key !== 'gate')
  return cols
})

/** 是否至少有一道门声明了 ISSUE_OPEN（有值才显示该列） */
const hasIssueCheck = computed(() => {
  const map = props.gateCheck || {}
  return Object.keys(map).length > 0
})

/** 某道门的检查结果（宿主按门 oid 传 map） */
function issueCheckOf(record) {
  return record?.oid ? (props.gateCheck || {})[record.oid] || null : null
}

/** 检查结果的悬停说明：逐项列出（ISSUE_OPEN / DELIVERABLE_RATE…） */
function issueCheckTooltip(record) {
  const check = issueCheckOf(record)
  if (!check) return ''
  const lines = (check.checks || []).map((c) => (c.passed ? '✓ ' : '✗ ') + (c.message || c.type))
  const head = `${check.gateCode || ''} 自动检查：${check.passed ? '全部通过' : '有不通过项'}`
  return [head].concat(lines).join('\n')
}

const searchPlaceholder = computed(() => `搜索${SUBJECT[props.kind] || ''}编码 / 名称...`)
const searchFields = computed(() => {
  const base = ['code', 'name']
  if (props.kind === 'deliverable') base.push('stageKey', 'gate')
  if (props.kind === 'gate') base.push('stageKey', 'type')
  if (props.kind === 'rule') base.push('stageKey', 'category')
  if (props.kind === 'metric') base.push('layer')
  if (props.kind === 'dashboard') base.push('audience')
  return base
})
/**
 * 空态文案。「不适宜」是一档可能本就为空的筛选（本阶段没裁剪过任何条目），
 * 这时说清"是没有，不是没加载出来"；其余情形沿用宿主给的 emptyHint。
 */
const emptyText = computed(() => {
  if (applicability.value === 'NOT_APPLICABLE') {
    return `本阶段没有「本项目不适用」的${SUBJECT[props.kind] || '条目'}`
  }
  return props.emptyHint || `暂无${SUBJECT[props.kind] || '数据'}`
})

const hint = computed(() => {
  if (props.kind === 'metric') return '数值待数据就绪后评估；未就绪显示「暂未评估」——不编造数值'
  if (props.kind === 'gate') {
    return '「自动检查」列 = 门声明的检查里已实现的部分（ISSUE_OPEN 未闭环问题 / DELIVERABLE_RATE 本阶段交付件完成率）；'
      + '红 = 有检查未通过，悬停看逐项明细。其余检查（成本达成 / 规则违规…）待接；门过没过仍以人工决策留痕为准'
  }
  if (props.kind === 'review') {
    return '技术评审直接挂在阶段下（不再有技术门这一层）：状态由流程算，结论与留痕在流程里；'
      + '检查项是这场会要逐条过的清单'
  }
  if (props.kind === 'deliverable') {
    return props.showArtifacts
      ? '产出物：点「产出物」列关联对象（类别受模板约束、可跨容器引用）。状态自动判定：未绑定=未开始 / 已绑定或对象在流程中=进行中 / 完成条件全满足=已完成（评审通过、问题清零等子项待后续）'
      : '完成条件随模板快照而来；状态自动判定见系列/型号页交付件面板'
  }
  if (props.kind === 'dashboard') return '★主视角 = 产品经理 / 研发的日常工作面'
  return ''
})

const summaryText = computed(() => {
  // 口径按**实际渲染的行**（displayRows）：被裁剪且我看不见的条目不占计数
  const total = displayRows.value.length
  // 被裁剪（本项目不适用）的条目单列出来 —— 它们不计入"强制 / 已完成"口径，
  // 否则"强制 6 项"里混着 2 项本项目根本不做的，数字会误导
  const tailored = displayRows.value.filter((r) => r.disabled).length
  // 默认「适宜」时这部分由 filterNote 说明（"已隐藏 N 项…"），别在同一行里报两遍同一个数
  const suffix = tailored && !hiddenByApplicability.value
    ? `，已裁剪 ${tailored} 项（本项目不适用）`
    : ''
  if (props.kind === 'deliverable') {
    const active = displayRows.value.filter((r) => !r.disabled)
    const mandatory = active.filter((r) => r.mandatory).length
    const done = active.filter((r) => r.status === 'DONE').length
    return `共 ${total} 项，其中强制 ${mandatory} 项，已完成 ${done} 项${suffix}${filterNote.value}`
  }
  if (props.kind === 'metric') {
    const evaluated = displayRows.value.filter((r) => r.value != null || r.readiness).length
    return `共 ${total} 项，已评估 ${evaluated} 项${filterNote.value}`
  }
  return `共 ${total} 项${suffix}${filterNote.value}`
})

function stageName(code) {
  return props.stageNames[code] || code
}

/**
 * 指标数值的展示：比率补 %、计数补「个」，其余原样。
 * 成本类不补单位 —— 币种应由业务决定（BOM 行的 unit_cost 本身不带币种），
 * 界面上写死「元」等于替客户做了个财务假设。
 */
function metricText(record) {
  const code = record.code || ''
  const num = Number(record.value)
  const text = Number.isFinite(num) ? String(Math.round(num * 100) / 100) : String(record.value)
  if (/(_RATE|_COVERAGE)$/.test(code)) return text + '%'
  if (code === 'EOL_RISK_COUNT') return text + ' 个'
  return text
}

function countJson(json) {
  if (!json) return 0
  try {
    const v = typeof json === 'string' ? JSON.parse(json) : json
    return Array.isArray(v) ? v.length : 0
  } catch { return 0 }
}

/**
 * 检查项明细（评审的「检查项」列浮层用）。
 *
 * <p>兼容三种来源：JSON 字符串 / 已是数组 / 数组里混着纯文本项（老数据）。
 * 解析失败返回空数组 —— 列上退回显示「-」，不能因为一条脏数据让整个面板炸掉。
 *
 * @returns {{seq:number|string, text:string, severity?:string}[]}
 */
function checkItemsOf(record) {
  if (!record?.checkItems) return []
  try {
    const value = typeof record.checkItems === 'string'
      ? JSON.parse(record.checkItems)
      : record.checkItems
    if (!Array.isArray(value)) return []
    return value
      .map((item, index) => (typeof item === 'string' ? { seq: index + 1, text: item } : item))
      .filter(item => item && item.text)
  } catch {
    return []
  }
}

function widgetCodes(json) {
  try {
    const v = typeof json === 'string' ? JSON.parse(json) : json
    return Array.isArray(v) ? v : []
  } catch { return [] }
}

function ruleText(json) {
  if (!json) return '-'
  try {
    const v = typeof json === 'string' ? JSON.parse(json) : json
    const all = (v && Array.isArray(v.all)) ? v.all : null
    if (!all) return json
    return all.map((c) => RULE_TYPE_LABEL[c.type] || c.type).join(' + ')
  } catch { return json }
}

function shortTime(t) {
  return typeof t === 'string' ? t.replace('T', ' ').slice(0, 16) : t
}
</script>

<style scoped>
.mi-panel { display: flex; flex-direction: column; gap: 8px; }
.mi-head { display: flex; align-items: center; gap: 12px; font-size: 12px; color: #8c8c8c; }
.mi-counts { color: #595959; }
.mi-hint { color: #fa8c16; }
.mi-ellipsis { display: inline-block; max-width: 250px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; vertical-align: bottom; }
.mi-small { font-size: 12px; color: #8c8c8c; }
.mi-not-evaluated { color: #bfbfbf; font-style: italic; }
/* 指标数值：有值时等宽数字；没值时下面挂一行"缺什么"的小字（悬停看全） */
.mi-metric-value { color: #262626; font-variant-numeric: tabular-nums; }
.mi-metric-empty { display: flex; flex-direction: column; line-height: 1.35; }
.mi-metric-why {
  max-width: 170px;
  font-size: 11px;
  color: #bfbfbf;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ===== 状态列浮层（深色 tooltip 底上的浅色文字） ===== */
.mi-tip { max-width: 440px; font-size: 12px; line-height: 1.6; }
.mi-tip-head { font-weight: 600; margin-bottom: 4px; }
.mi-tip-item { padding: 4px 0; border-top: 1px solid rgba(255, 255, 255, 0.16); }
/* ===== 完成条件逐条判定 ===== */
.mi-tip-section { margin: 6px 0 2px; font-weight: 600; opacity: 0.9; }
.mi-tip-rule { display: flex; align-items: flex-start; gap: 6px; padding: 1px 0; }
.mi-tip-verdict { flex: none; width: 12px; font-weight: 700; }
.mi-tip-verdict.is-pass { color: #b7eb8f; }
.mi-tip-verdict.is-fail { color: #ff7875; }
.mi-tip-verdict.is-unknown { color: #ffd591; }
.mi-tip-rule-label { flex: none; }
.mi-tip-rule-detail { flex: 1; min-width: 0; opacity: 0.8; }
.mi-tip-row { display: flex; align-items: center; gap: 6px; min-width: 0; }
.mi-tip-host { flex: none; opacity: 0.75; }
.mi-tip-code { flex: none; background: rgba(255, 255, 255, 0.16); padding: 0 4px; border-radius: 3px; }
.mi-tip-name { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mi-tip-state { flex: none; padding: 0 6px; border-radius: 3px; background: rgba(255, 255, 255, 0.12); }
.mi-tip-state.is-draft { color: #d9d9d9; }
.mi-tip-state.is-inwork { color: #91caff; }
.mi-tip-state.is-review { color: #ffd591; }
.mi-tip-state.is-ok { color: #b7eb8f; }
.mi-tip-flow { margin-top: 2px; color: rgba(255, 255, 255, 0.65); }
.mi-tip-flow-on { color: #ffd591; }
.mi-tip-note { opacity: 0.8; }
.mi-tip-error { color: #ffccc7; }
.mi-tip-foot { margin-top: 6px; padding-top: 4px; border-top: 1px solid rgba(255, 255, 255, 0.2); opacity: 0.7; }

/* ===== 检查项浮层（白底 popover：逐条正文 + 严重度） ===== */
.mi-check-count { font-size: 12px; color: #1677ff; cursor: pointer; }
.mi-check-count:hover { text-decoration: underline; }
.mi-check-list { max-width: 520px; max-height: 320px; overflow-y: auto; }
.mi-check-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 4px 0;
  font-size: 12px;
  line-height: 1.6;
  border-bottom: 1px dashed #f0f0f0;
}
.mi-check-row:last-child { border-bottom: none; }
.mi-check-seq {
  flex: 0 0 auto;
  min-width: 18px;
  color: #bfbfbf;
  text-align: right;
}
.mi-check-text { flex: 1 1 auto; color: #262626; }
</style>
