<template>
  <div class="product-series-page">
    <!-- 页头 -->
    <div class="ps-header">
      <div class="ps-header-left">
        <h3 class="ps-title">产品系列</h3>
        <span class="ps-subtitle">管理产品线及其研发团队</span>
      </div>
    </div>

    <!-- 统计栏 -->
    <div class="ps-stats-bar">
      <div class="ps-stat-item">
        <ClusterOutlined class="ps-stat-icon" />
        <span class="ps-stat-value">{{ allLines.length }}</span>
        <span class="ps-stat-label">产品线总数</span>
      </div>
      <div class="ps-stat-item">
        <TagOutlined class="ps-stat-icon" style="color:#52c41a" />
        <span class="ps-stat-value">{{ allModelsCount }}</span>
        <span class="ps-stat-label">产品型号总数</span>
      </div>
    </div>

    <!-- 产品线表格 -->
    <div class="ps-table-wrapper">
      <DataTable
        :columns="columns"
        :data-source="treeData"
        :loading="loading"
        search-placeholder="搜索产品线编码 / 名称 / 描述..."
        :search-fields="['code', 'name', 'description']"
        :enable-resize="true"
        :show-column-toggle="true"
        :pagination="false"
        row-key="oid"
        children-column-name="children"
        size="middle"
      >
        <template #toolbar>
          <a-button type="primary" size="small" @click="openModal(null)">
            <template #icon><PlusOutlined /></template>
            新建产品系列
          </a-button>
        </template>

        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'code'">
            <a-tag color="blue">{{ record.code }}</a-tag>
          </template>
          <template v-else-if="column.key === 'name'">
            <a-space :size="4">
              <component :is="record.nodeType === 'PRODUCT_MODEL' ? TagOutlined : ApartmentOutlined"
                         :style="{ color: record.nodeType === 'PRODUCT_MODEL' ? '#52c41a' : '#1677ff' }" />
              <a-tag v-if="record.nodeType === 'PRODUCT_MODEL'" color="green" style="margin-right:4px">型号</a-tag>
              <span class="ps-name" @dblclick="handleNameDblClick(record)" title="双击进入产品线管业">
                {{ record.name }}
              </span>
            </a-space>
          </template>
          <template v-else-if="column.key === 'description'">
            <span class="ps-cell-text">{{ record.description || '-' }}</span>
          </template>
          <template v-else-if="column.key === 'childrenCount'">
            <template v-if="record.nodeType !== 'PRODUCT_MODEL'">
              <a-badge
                v-if="getStat(record.oid, 'childrenCount') > 0"
                :count="getStat(record.oid, 'childrenCount')"
                :number-style="{ backgroundColor: '#52c41a' }"
                :overflow-count="99"
              />
              <span v-else style="color:#ccc">-</span>
            </template>
            <span v-else style="color:#ccc">-</span>
          </template>
          <template v-else-if="column.key === 'modelCount'">
            <template v-if="record.nodeType !== 'PRODUCT_MODEL'">
              <a-badge
                v-if="getStat(record.oid, 'modelCount') > 0"
                :count="getStat(record.oid, 'modelCount')"
                :number-style="{ backgroundColor: '#1677ff' }"
                :overflow-count="99"
              />
              <span v-else style="color:#ccc">-</span>
            </template>
            <span v-else style="color:#ccc">-</span>
          </template>
          <template v-else-if="column.key === 'parent'">
            <a-tag v-if="record.parentOid" color="default">{{ parentNameMap[record.parentOid] || '—' }}</a-tag>
            <span v-else class="ps-cell-text" style="color:#999">-</span>
          </template>
          <template v-else-if="column.key === 'thumbnail'">
            <div class="ps-thumbnail" v-if="record.thumbnail">
              <img :src="record.thumbnail" alt="缩略图" />
            </div>
            <span v-else class="ps-cell-text">-</span>
          </template>
          <template v-else-if="column.key === 'createdAt'">
            {{ formatTime(record.createdAt) }}
          </template>
          <template v-else-if="column.key === 'team'">
            <a-badge v-if="record.nodeType === 'PRODUCT_MODEL'" :count="modelMemberCounts[record.oid] || 0" :overflow-count="99">
              <a-button size="small" @click="openModelTeamDrawer(record)">
                <template #icon><TeamOutlined /></template>
                团队
              </a-button>
            </a-badge>
            <a-badge v-else :count="teamMemberCounts[record.oid] || 0" :overflow-count="99">
              <a-button size="small" @click="openTeamDrawer(record)">
                <template #icon><TeamOutlined /></template>
                团队
              </a-button>
            </a-badge>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space size="small">
              <!-- ProductLine 节点 -->
              <template v-if="record.nodeType !== 'PRODUCT_MODEL'">
                <a-button type="link" size="small" @click="openModelDrawer(record)">型号</a-button>
                <a-button type="link" size="small" @click="openStageDrawer(record)">阶段</a-button>
                <a-button type="link" size="small" @click="openModal(record)">编辑</a-button>
                <a-popconfirm
                  title="确定删除该产品线？关联的团队及成员将被一并删除。"
                  @confirm="handleDelete(record)"
                >
                  <a-button type="link" size="small" danger>
                    <template #icon><DeleteOutlined /></template>
                    删除
                  </a-button>
                </a-popconfirm>
              </template>
              <!-- ProductModel 节点 -->
              <template v-else>
                <a-button type="link" size="small" @click="openModelStageDrawer(record)">阶段</a-button>
                <a-button type="link" size="small" @click="openModelEdit(record)">编辑</a-button>
                <a-popconfirm
                  title="确定删除该产品型号？关联的团队及成员将被一并删除。"
                  @confirm="handleModelDelete(record)"
                >
                  <a-button type="link" size="small" danger>
                    <template #icon><DeleteOutlined /></template>
                    删除
                  </a-button>
                </a-popconfirm>
              </template>
            </a-space>
          </template>
        </template>
      </DataTable>
    </div>

    <!-- 新增 / 编辑弹窗 -->
    <a-modal
      v-model:open="modal.visible"
      :title="modal.isEdit ? '编辑产品系列' : '新建产品系列'"
      @ok="handleSave"
      @cancel="onModalCancel"
      :confirm-loading="modal.saving"
      width="640px"
      :mask-closable="false"
      :destroy-on-close="true"
    >
      <!-- 动态表单（根据 PageDesigner 布局渲染） -->
      <DynamicForm
        ref="dynamicFormRef"
        entity-code="PRODUCT_LINE"
        :operation-code="modalOperationCode"
        :entity-oid="modal.editingOid"
        v-model="modalFormData"
        :external-product-line-tree="editingFilteredProductLineTree"
      />

    </a-modal>

    <!-- 团队管理抽屉 -->
    <a-drawer
      :title="teamDrawer.title"
      :open="teamDrawer.visible"
      :width="580"
      @close="teamDrawer.visible = false"
    >
      <!-- 添加成员 -->
      <div class="ps-add-member">
        <a-space>
          <a-select
            v-model:value="teamDrawer.newUserId"
            show-search
            placeholder="输入用户名搜索..."
            :filter-option="false"
            :loading="teamDrawer.userSearching"
            style="width: 260px"
            @search="searchUsersForTeam"
            size="middle"
          >
            <a-select-option v-for="u in teamDrawer.userOptions" :key="u.username" :value="u.username">
              <a-avatar size="small" :style="{ marginRight:'8px', background:'#1677ff', verticalAlign:'middle' }">
                {{ (u.displayName || u.username).charAt(0) }}
              </a-avatar>
              {{ u.displayName || u.username }}
              <span style="color:#999; margin-left:8px; font-size:12px">{{ u.username }}</span>
            </a-select-option>
          </a-select>
          <a-select
            v-model:value="teamDrawer.newRoleName"
            show-search
            placeholder="选择自定义角色"
            :loading="teamDrawer.roleLoading"
            allow-clear
            style="width: 200px"
            size="middle"
            :options="teamDrawer.roleOptions.map(r => ({
              label: `${r.name} (${r.code})`,
              value: r.name
            }))"
          />
          <a-button type="primary" :loading="teamDrawer.adding" @click="handleAddTeamMember">
            <template #icon><UserAddOutlined /></template>
            添加
          </a-button>
        </a-space>
      </div>

      <a-divider />

      <!-- 成员列表 -->
      <div class="ps-member-list">
        <h4 class="ps-member-title">
          团队成员
          <a-tag color="blue">{{ ownMembers.length }}</a-tag>
          <span v-if="inheritedMembers.length > 0" style="color:#8c8c8c; font-weight:400; font-size:12px; margin-left:4px">
            &nbsp;+&nbsp;继承 {{ inheritedMembers.length }}
          </span>
        </h4>
        <a-empty v-if="teamDrawer.loading" description="加载中..." />
        <a-empty v-else-if="teamDrawer.members.length === 0" description="暂无团队成员" />
        <a-list
          v-else
          :data-source="teamDrawer.members"
          size="small"
        >
          <template #renderItem="{ item }">
            <a-list-item :class="{ 'ps-inherited-member': item.inherited }">
              <a-list-item-meta>
                <template #avatar>
                  <a-avatar :style="{ background: item.inherited ? '#8c8c8c' : '#1677ff' }">
                    {{ (item.displayName || item.username).charAt(0) }}
                  </a-avatar>
                </template>
                <template #title>
                  {{ item.displayName || item.username }}
                  <a-tag v-if="item.inherited" color="orange" size="small" style="margin-left:6px">继承</a-tag>
                  <a-tag v-if="item.enabled === false" color="default" size="small" style="margin-left:6px">已禁用</a-tag>
                  <a-tag v-if="item.locked" color="error" size="small" style="margin-left:4px">已锁定</a-tag>
                </template>
                <template #description>
                  {{ item.username }} &nbsp;|&nbsp; {{ item.roleName || '未指定角色' }}
                  <span v-if="item.inherited && item.sourceProductLineName" style="color:#fa8c16; margin-left:8px">
                    继承自「{{ item.sourceProductLineName }}」
                  </span>
                </template>
              </a-list-item-meta>
              <template #actions>
                <a-popconfirm
                  v-if="!item.inherited"
                  title="确定将该成员移出团队？"
                  @confirm="handleRemoveTeamMember(item)"
                >
                  <a-button type="link" size="small" danger>
                    <template #icon><CloseOutlined /></template>
                    移除
                  </a-button>
                </a-popconfirm>
                <a-tooltip v-else title="继承成员不可在本级移除，请在父级产品线中管理">
                  <a-button type="link" size="small" disabled>
                    <template #icon><CloseOutlined /></template>
                    移除
                  </a-button>
                </a-tooltip>
              </template>
            </a-list-item>
          </template>
        </a-list>
      </div>
    </a-drawer>

    <!-- 阶段管理抽屉 -->
    <a-drawer
      :title="stageDrawer.title"
      :open="stageDrawer.visible"
      :width="640"
      @close="stageDrawer.visible = false"
    >
      <div v-if="stageDrawer.loading" style="text-align:center;padding:40px">
        <a-spin tip="加载阶段数据..." />
      </div>
      <div v-else-if="stageDrawer.stages.length === 0" style="text-align:center;padding:40px">
        <a-empty description="暂无研发阶段">
          <a-button type="primary" :loading="stageDrawer.initializing" @click="handleInitStages">
            初始化默认阶段
          </a-button>
        </a-empty>
      </div>
      <div v-else>
        <div class="ps-stage-header">
          <h4 class="ps-stage-title">
            <NodeIndexOutlined /> 研发阶段 ({{ stageDrawer.stages.length }})
          </h4>
          <a-button size="small" type="dashed" :loading="stageDrawer.initializing" @click="handleInitStages">
            重置为默认
          </a-button>
        </div>
        <a-list :data-source="stageDrawer.stages" size="small">
          <template #renderItem="{ item, index }">
            <a-list-item>
              <a-list-item-meta>
                <template #avatar>
                  <a-badge
                    :count="item.sortOrder || index + 1"
                    :number-style="{ backgroundColor: item.color || '#1677ff', boxShadow: 'none' }"
                  />
                </template>
                <template #title>
                  <a-tag :color="item.color?.replace('#','') || 'blue'">{{ item.code }}</a-tag>
                  <span v-if="stageDrawer.editingCode !== item.code" style="margin-left:8px;font-weight:500">
                    {{ item.name }}
                  </span>
                  <a-input
                    v-else
                    v-model:value="stageDrawer.editForm.name"
                    size="small"
                    style="width:120px"
                    @keyup.enter="handleSaveStage(item)"
                  />
                  <span v-if="stageDrawer.editingCode !== item.code" style="margin-left:8px;color:#999;font-size:12px">
                    {{ item.description }}
                  </span>
                </template>
                <template #description>
                  <template v-if="stageDrawer.editingCode === item.code">
                    <a-space size="small" style="margin-top:4px">
                      <a-input v-model:value="stageDrawer.editForm.description" size="small" placeholder="阶段描述" style="width:280px" />
                      <a-input v-model:value="stageDrawer.editForm.color" size="small" placeholder="颜色(#xxx)" style="width:90px" />
                      <a-input-number v-model:value="stageDrawer.editForm.sortOrder" size="small" :min="1" :max="99" style="width:60px" />
                      <a-button size="small" type="primary" @click="handleSaveStage(item)">保存</a-button>
                      <a-button size="small" @click="stageDrawer.editingCode = null">取消</a-button>
                    </a-space>
                  </template>
                  <span v-else style="color:#8c8c8c;font-size:12px">
                    图标: {{ item.icon || '-' }} | 颜色: {{ item.color || '-' }} | 排序: {{ item.sortOrder || '-' }}
                  </span>
                </template>
              </a-list-item-meta>
              <template #actions>
                <a-tooltip :title="item.showOnDashboard !== false ? '取消仪表盘显示' : '设为仪表盘显示'">
                  <a-button
                    type="link" size="small"
                    @click="handleToggleStageShow('line', item)"
                  >
                    <template #icon>
                      <EyeOutlined v-if="item.showOnDashboard !== false" />
                      <EyeInvisibleOutlined v-else />
                    </template>
                  </a-button>
                </a-tooltip>
                <a-button
                  v-if="stageDrawer.editingCode !== item.code"
                  type="link" size="small"
                  @click="startEditStage(item)"
                >编辑</a-button>
              </template>
            </a-list-item>
          </template>
        </a-list>
      </div>
    </a-drawer>

    <!-- 产品型号管理抽屉 -->
    <a-drawer
      :title="modelDrawer.title"
      :open="modelDrawer.visible"
      :width="720"
      @close="modelDrawer.visible = false"
    >
      <div class="ps-model-toolbar">
        <a-button type="primary" size="small" @click="openModelCreate()">
          <template #icon><PlusOutlined /></template>
          新建产品型号
        </a-button>
      </div>
      <a-divider style="margin:12px 0" />
      <div v-if="modelDrawer.loading" style="text-align:center;padding:40px">
        <a-spin tip="加载产品型号..." />
      </div>
      <div v-else-if="modelDrawer.models.length === 0" style="text-align:center;padding:40px">
        <a-empty description="暂无产品型号">
          <a-button type="primary" @click="openModelCreate()">新建产品型号</a-button>
        </a-empty>
      </div>
      <a-list
        v-else
        :data-source="modelDrawer.models"
        size="small"
      >
        <template #renderItem="{ item }">
          <a-list-item>
            <a-list-item-meta>
              <template #title>
                <a-space size="small">
                  <a-tag color="green">{{ item.code }}</a-tag>
                  <span class="ps-name" @dblclick="router.push(`/product/${item.oid}`)" style="font-weight:500">
                    {{ item.name }}
                  </span>
                </a-space>
              </template>
              <template #description>
                <span style="color:#8c8c8c">{{ item.description || '暂无描述' }}</span>
              </template>
            </a-list-item-meta>
            <template #actions>
              <a-space size="small">
                <a-button type="link" size="small" @click="openModelTeamDrawer(item)">团队</a-button>
                <a-button type="link" size="small" @click="openModelStageDrawer(item)">阶段</a-button>
                <a-button type="link" size="small" @click="openModelEdit(item)">编辑</a-button>
                <a-popconfirm
                  title="确定删除该产品型号？关联的团队及成员将被一并删除。"
                  @confirm="handleModelDelete(item)"
                >
                  <a-button type="link" size="small" danger>删除</a-button>
                </a-popconfirm>
              </a-space>
            </template>
          </a-list-item>
        </template>
      </a-list>
    </a-drawer>

    <!-- 产品型号 新建/编辑 弹窗 -->
    <a-modal
      v-model:open="modelModal.visible"
      :title="modelModal.isEdit ? '编辑产品型号' : '新建产品型号'"
      @ok="handleModelSave"
      @cancel="modelModal.visible = false"
      :confirm-loading="modelModal.saving"
      width="640px"
      :mask-closable="false"
      :destroy-on-close="true"
    >
      <DynamicForm
        ref="modelDynamicFormRef"
        entity-code="PRODUCT_MODEL"
        :operation-code="modelModalOperationCode"
        :entity-oid="modelModal.editingOid"
        v-model="modelModalFormData"
        :external-product-line-tree="allLinesTreeForModel"
      />
    </a-modal>

    <!-- 产品型号 团队管理抽屉 -->
    <a-drawer
      :title="modelTeamDrawer.title"
      :open="modelTeamDrawer.visible"
      :width="580"
      @close="modelTeamDrawer.visible = false"
    >
      <div class="ps-add-member">
        <a-space>
          <a-select
            v-model:value="modelTeamDrawer.newUserId"
            show-search
            placeholder="输入用户名搜索..."
            :filter-option="false"
            :loading="modelTeamDrawer.userSearching"
            style="width: 260px"
            @search="searchUsersForModelTeam"
            size="middle"
          >
            <a-select-option v-for="u in modelTeamDrawer.userOptions" :key="u.username" :value="u.username">
              <a-avatar size="small" :style="{ marginRight:'8px', background:'#1677ff', verticalAlign:'middle' }">
                {{ (u.displayName || u.username).charAt(0) }}
              </a-avatar>
              {{ u.displayName || u.username }}
            </a-select-option>
          </a-select>
          <a-select
            v-model:value="modelTeamDrawer.newRoleName"
            show-search placeholder="选择角色" allow-clear
            style="width: 200px" size="middle"
            :options="modelTeamDrawer.roleOptions.map(r => ({ label: `${r.name}`, value: r.name }))"
          />
          <a-button type="primary" :loading="modelTeamDrawer.adding" @click="handleAddModelTeamMember">
            <template #icon><UserAddOutlined /></template>添加
          </a-button>
        </a-space>
      </div>
      <a-divider />
      <div class="ps-member-list">
        <h4 class="ps-member-title">
          团队成员
          <a-tag color="blue">{{ modelOwnMembers.length }}</a-tag>
          <span v-if="modelInheritedMembers.length > 0" style="color:#8c8c8c; font-weight:400; font-size:12px; margin-left:4px">
            &nbsp;+&nbsp;继承 {{ modelInheritedMembers.length }}
          </span>
        </h4>
        <a-empty v-if="modelTeamDrawer.loading" description="加载中..." />
        <a-empty v-else-if="modelTeamDrawer.members.length === 0" description="暂无团队成员" />
        <a-list v-else :data-source="modelTeamDrawer.members" size="small">
          <template #renderItem="{ item }">
            <a-list-item :class="{ 'ps-inherited-member': item.inherited }">
              <a-list-item-meta>
                <template #avatar>
                  <a-avatar :style="{ background: item.inherited ? '#8c8c8c' : '#1677ff' }">
                    {{ (item.displayName || item.username).charAt(0) }}
                  </a-avatar>
                </template>
                <template #title>
                  {{ item.displayName || item.username }}
                  <a-tag v-if="item.inherited" color="orange" size="small" style="margin-left:6px">继承</a-tag>
                  <a-tag v-if="item.enabled === false" color="default" size="small" style="margin-left:6px">已禁用</a-tag>
                </template>
                <template #description>
                  {{ item.username }} &nbsp;|&nbsp; {{ item.roleName || '未指定角色' }}
                  <span v-if="item.inherited && item.sourceProductLineName" style="color:#fa8c16; margin-left:8px">
                    继承自「{{ item.sourceProductLineName }}」
                  </span>
                </template>
              </a-list-item-meta>
              <template #actions>
                <template v-if="item.inherited">
                  <a-tooltip title="继承成员不可在本级移除，请在所属产品线中管理">
                    <a-button type="link" size="small" disabled>
                      <template #icon><CloseOutlined /></template>
                      移除
                    </a-button>
                  </a-tooltip>
                </template>
                <template v-else>
                  <a-popconfirm title="确定将该成员移出团队？" @confirm="handleRemoveModelTeamMember(item)">
                    <a-button type="link" size="small" danger>
                      <template #icon><CloseOutlined /></template>
                      移除
                    </a-button>
                  </a-popconfirm>
                </template>
              </template>
            </a-list-item>
          </template>
        </a-list>
      </div>
    </a-drawer>

    <!-- 产品型号 阶段管理抽屉 -->
    <a-drawer
      :title="modelStageDrawer.title"
      :open="modelStageDrawer.visible"
      :width="640"
      @close="modelStageDrawer.visible = false"
    >
      <div v-if="modelStageDrawer.loading" style="text-align:center;padding:40px">
        <a-spin tip="加载阶段数据..." />
      </div>
      <div v-else-if="modelStageDrawer.stages.length === 0" style="text-align:center;padding:40px">
        <a-empty description="暂无研发阶段">
          <a-button type="primary" :loading="modelStageDrawer.initializing" @click="handleModelInitStages">初始化默认阶段</a-button>
        </a-empty>
      </div>
      <div v-else>
        <div class="ps-stage-header">
          <h4 class="ps-stage-title"><NodeIndexOutlined /> 研发阶段 ({{ modelStageDrawer.stages.length }})</h4>
          <a-button size="small" type="dashed" :loading="modelStageDrawer.initializing" @click="handleModelInitStages">重置为默认</a-button>
        </div>
        <a-list :data-source="modelStageDrawer.stages" size="small">
          <template #renderItem="{ item, index }">
            <a-list-item>
              <a-list-item-meta>
                <template #avatar>
                  <a-badge
                    :count="item.sortOrder || index + 1"
                    :number-style="{ backgroundColor: item.color || '#1677ff', boxShadow: 'none' }"
                  />
                </template>
                <template #title>
                  <a-tag :color="item.color?.replace('#','') || 'blue'">{{ item.code }}</a-tag>
                  <span v-if="modelStageDrawer.editingCode !== item.code" style="margin-left:8px;font-weight:500">
                    {{ item.name }}
                  </span>
                  <a-input
                    v-else
                    v-model:value="modelStageDrawer.editForm.name"
                    size="small"
                    style="width:120px"
                    @keyup.enter="handleSaveModelStage(item)"
                  />
                  <span v-if="modelStageDrawer.editingCode !== item.code" style="margin-left:8px;color:#999;font-size:12px">
                    {{ item.description }}
                  </span>
                </template>
                <template #description>
                  <template v-if="modelStageDrawer.editingCode === item.code">
                    <a-space size="small" style="margin-top:4px">
                      <a-input v-model:value="modelStageDrawer.editForm.description" size="small" placeholder="阶段描述" style="width:280px" />
                      <a-input v-model:value="modelStageDrawer.editForm.color" size="small" placeholder="颜色(#xxx)" style="width:90px" />
                      <a-input-number v-model:value="modelStageDrawer.editForm.sortOrder" size="small" :min="1" :max="99" style="width:60px" />
                      <a-button size="small" type="primary" @click="handleSaveModelStage(item)">保存</a-button>
                      <a-button size="small" @click="modelStageDrawer.editingCode = null">取消</a-button>
                    </a-space>
                  </template>
                  <span v-else style="color:#8c8c8c;font-size:12px">
                    图标: {{ item.icon || '-' }} | 颜色: {{ item.color || '-' }} | 排序: {{ item.sortOrder || '-' }}
                  </span>
                </template>
              </a-list-item-meta>
              <template #actions>
                <a-tooltip :title="item.showOnDashboard !== false ? '取消仪表盘显示' : '设为仪表盘显示'">
                  <a-button
                    type="link" size="small"
                    @click="handleToggleStageShow('model', item)"
                  >
                    <template #icon>
                      <EyeOutlined v-if="item.showOnDashboard !== false" />
                      <EyeInvisibleOutlined v-else />
                    </template>
                  </a-button>
                </a-tooltip>
                <a-button
                  v-if="modelStageDrawer.editingCode !== item.code"
                  type="link" size="small"
                  @click="startModelEditStage(item)"
                >编辑</a-button>
              </template>
            </a-list-item>
          </template>
        </a-list>
      </div>
    </a-drawer>


  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  PlusOutlined, TeamOutlined, ClusterOutlined,
  DeleteOutlined, UserAddOutlined, CloseOutlined,
  NodeIndexOutlined, EyeOutlined, EyeInvisibleOutlined,
  ApartmentOutlined, TagOutlined,
} from '@ant-design/icons-vue'

const router = useRouter()
import {
  getProductLines, createProductLine, updateProductLine, deleteProductLine,
  getProductLineTree, getProductLineRoots, getProductLineStats,
  getProductLineTeam, getTeamMembers, addTeamMember, removeTeamMember,
  getAllUsers, getRoles, getPageLayoutByCode,
  getStages, initDefaultStages, updateStage, toggleStageShowOnDashboard,
  getProductModels, createProductModel, updateProductModel, deleteProductModel,
  getProductModelTeam, getProductModelTeamMembers,
  addProductModelTeamMember, removeProductModelTeamMember,
} from '@/api'
import DataTable from '@/components/DataTable.vue'
import DynamicForm from '@/components/DynamicForm.vue'

// ==================== 默认列表列（PageLayout 未配置时使用） ====================
const DEFAULT_LIST_COLUMNS = [
  { title: '编码', dataIndex: 'code', key: 'code', width: 130 },
  { title: '名称', dataIndex: 'name', key: 'name', ellipsis: true, width: 160 },
  { title: '描述', dataIndex: 'description', key: 'description', ellipsis: true },
  { title: '子系列', key: 'childrenCount', width: 90, align: 'center' },
  { title: '产品型号', key: 'modelCount', width: 90, align: 'center' },
  { title: '父级', key: 'parent', width: 130 },
  { title: '缩略图', key: 'thumbnail', width: 100 },
  { title: '团队', key: 'team', width: 90 },
  { title: '创建时间', key: 'createdAt', width: 170 },
  { title: '操作', key: 'action', width: 150 },
]

// ==================== 表格列定义（从 PageLayout 动态加载） ====================
const columns = ref([...DEFAULT_LIST_COLUMNS])

// ==================== 数据 ====================
const loading = ref(false)
const allLines = ref([])         // 扁平列表（用于父级下拉选择）
const treeData = ref([])         // 树形数据（用于表格展示）
const parentNameMap = reactive({}) // parentOid -> name 映射
const teamMemberCounts = reactive({})
const modelMemberCounts = reactive({})
const statsMap = reactive({})   // { [oid]: { childrenCount, modelCount } }

/** 产品型号总数（从树形数据中筛选 nodeType === 'PRODUCT_MODEL' 的节点） */
const allModelsCount = computed(() => {
  let count = 0
  const countNodes = (nodes) => {
    for (const node of nodes) {
      if (node.nodeType === 'PRODUCT_MODEL') count++
      if (node.children?.length > 0) countNodes(node.children)
    }
  }
  countNodes(treeData.value)
  return count
})

// ==================== 弹窗 ====================
const modal = reactive({
  visible: false,
  isEdit: false,
  saving: false,
  editingOid: null,
})
const dynamicFormRef = ref(null)
const modalFormData = ref({})
const modalOperationCode = computed(() => modal.isEdit ? 'update' : 'create')

/**
 * 打开新建/编辑弹窗。
 * - 编辑：只需传 entityOid，DynamicForm 统一调用 getEntityByCode 加载实体数据
 * - 新建：entityOid 为 null，表单为空
 */
function openModal(line) {
  modal.editingOid = line?.oid || null
  modal.isEdit = !!line
  modalFormData.value = {}
  modal.visible = true
}

/** 弹窗关闭时重置状态，确保下次打开是干净的环境 */
function onModalCancel() {
  modal.visible = false
  modal.editingOid = null
  modal.isEdit = false
  modalFormData.value = {}
}

/** 保存成功后同样重置 */
function closeModal() {
  modal.visible = false
  modal.editingOid = null
  modal.isEdit = false
  modalFormData.value = {}
}

// ==================== 团队抽屉 ====================
const teamDrawer = reactive({
  visible: false,
  title: '',
  lineOid: null,
  team: null,
  members: [],
  loading: false,
  newUserId: null,
  newRoleName: '',
  adding: false,
  userOptions: [],
  userSearching: false,
  roleOptions: [],
  roleLoading: false,
})

// 分组：自身成员 vs 继承成员
const ownMembers = computed(() => teamDrawer.members.filter(m => !m.inherited))
const inheritedMembers = computed(() => teamDrawer.members.filter(m => m.inherited))

// 产品型号团队分组
const modelOwnMembers = computed(() => modelTeamDrawer.members.filter(m => !m.inherited))
const modelInheritedMembers = computed(() => modelTeamDrawer.members.filter(m => m.inherited))

// ==================== 阶段管理抽屉 ====================
const stageDrawer = reactive({
  visible: false,
  title: '',
  lineOid: null,
  stages: [],
  loading: false,
  initializing: false,
  editingCode: null,
  editForm: { name: '', description: '', color: '', sortOrder: 1 },
})

async function openStageDrawer(line) {
  stageDrawer.visible = true
  stageDrawer.title = `研发阶段管理 - ${line.name}`
  stageDrawer.lineOid = line.oid
  stageDrawer.stages = []
  stageDrawer.editingCode = null
  stageDrawer.loading = true
  try {
    const res = await getStages(line.oid)
    if (res.code === 200) {
      stageDrawer.stages = res.data || []
    }
  } catch {
    message.error('加载阶段数据失败')
  } finally {
    stageDrawer.loading = false
  }
}

async function handleInitStages() {
  stageDrawer.initializing = true
  try {
    const res = await initDefaultStages(stageDrawer.lineOid)
    if (res.code === 200) {
      message.success(`已初始化 ${res.data || 6} 个默认阶段`)
      // 重新加载
      const stagesRes = await getStages(stageDrawer.lineOid)
      if (stagesRes.code === 200) {
        stageDrawer.stages = stagesRes.data || []
      }
    }
  } catch {
    message.error('初始化阶段失败')
  } finally {
    stageDrawer.initializing = false
  }
}

function startEditStage(stage) {
  stageDrawer.editingCode = stage.code
  stageDrawer.editForm = {
    name: stage.name,
    description: stage.description || '',
    color: stage.color || '',
    sortOrder: stage.sortOrder || 1,
  }
}

async function handleSaveStage(stage) {
  const form = stageDrawer.editForm
  if (!form.name?.trim()) {
    message.warning('阶段名称不能为空')
    return
  }
  try {
    const res = await updateStage(stageDrawer.lineOid, stage.oid, {
      name: form.name.trim(),
      description: form.description || '',
      color: form.color || '',
      sortOrder: form.sortOrder,
    })
    if (res.code === 200) {
      message.success('阶段更新成功')
      stageDrawer.editingCode = null
      stage.name = form.name.trim()
      stage.description = form.description
      stage.color = form.color
      stage.sortOrder = form.sortOrder
    }
  } catch {
    message.error('更新阶段失败')
  }
}

// ==================== 产品型号管理 ====================
const modelDrawer = reactive({
  visible: false,
  title: '',
  lineOid: null,
  lineName: '',
  models: [],
  loading: false,
})

async function openModelDrawer(line) {
  modelDrawer.visible = true
  modelDrawer.title = `产品型号管理 - ${line.name}`
  modelDrawer.lineOid = line.oid
  modelDrawer.lineName = line.name
  modelDrawer.models = []
  modelDrawer.loading = true
  try {
    const res = await getProductModels({ productLineOid: line.oid })
    if (res.code === 200) {
      modelDrawer.models = res.data || []
    }
  } catch {
    message.error('加载产品型号失败')
  } finally {
    modelDrawer.loading = false
  }
}

/** 刷新型号列表 */
async function refreshModels() {
  if (!modelDrawer.lineOid) return
  try {
    const res = await getProductModels({ productLineOid: modelDrawer.lineOid })
    if (res.code === 200) {
      modelDrawer.models = res.data || []
    }
    await loadAllLines()  // 刷新统计
  } catch { /* ignore */ }
}

// 型号 新建/编辑 弹窗
const modelModal = reactive({
  visible: false,
  isEdit: false,
  saving: false,
  editingOid: null,
})
const modelDynamicFormRef = ref(null)
const modelModalFormData = ref({})
const modelModalOperationCode = computed(() => modelModal.isEdit ? 'update' : 'create')

/** 产品线树转下拉选项 (供 DynamicForm product-line-select 使用，过滤掉型号节点) */
const allLinesTreeForModel = computed(() => {
  const transform = (nodes) => nodes.reduce((acc, n) => {
    if (n.nodeType === 'PRODUCT_MODEL') return acc  // 跳过型号
    acc.push({
      title: n.name, label: n.name, value: n.oid, key: n.oid,
      children: n.children?.length > 0 ? transform(n.children) : undefined,
    })
    return acc
  }, [])
  return transform(treeData.value)
})

function openModelCreate() {
  modelModal.editingOid = null
  modelModal.isEdit = false
  modelModalFormData.value = { parentOid: modelDrawer.lineOid }
  modelModal.visible = true
}

function openModelEdit(model) {
  modelModal.editingOid = model.oid
  modelModal.isEdit = true
  modelModalFormData.value = {}
  modelModal.visible = true
}

async function handleModelSave() {
  if (modelDynamicFormRef.value) {
    const errors = modelDynamicFormRef.value.validate()
    if (errors.length > 0) {
      message.warning(errors[0])
      return
    }
  }
  const formData = modelDynamicFormRef.value?.getFormData() || modelModalFormData.value
  const code = formData.code || ''
  const name = formData.name || ''
  if (!code.trim() || !name.trim()) {
    message.warning('产品型号编码和名称不能为空')
    return
  }
  modelModal.saving = true
  try {
    const payload = { ...formData }
    if (payload.code) payload.code = payload.code.trim()
    if (payload.name) payload.name = payload.name.trim()
    if (!payload.parentOid || payload.parentOid === '') {
      payload.parentOid = modelDrawer.lineOid
    }
    let res
    if (modelModal.isEdit) {
      res = await updateProductModel(modelModal.editingOid, payload)
    } else {
      res = await createProductModel(payload)
    }
    if (res.code === 200) {
      message.success(modelModal.isEdit ? '产品型号更新成功' : '产品型号创建成功')
      modelModal.visible = false
      modelModal.editingOid = null
      modelModal.isEdit = false
      await refreshModels()
    } else {
      message.error(res.message || '操作失败')
    }
  } catch {
    message.error('操作失败')
  } finally {
    modelModal.saving = false
  }
}

async function handleModelDelete(model) {
  try {
    const res = await deleteProductModel(model.oid)
    if (res.code === 200) {
      message.success('产品型号已删除')
      await refreshModels()
    } else {
      message.error(res.message || '删除失败')
    }
  } catch {
    message.error('删除失败')
  }
}

// 型号 团队管理
const modelTeamDrawer = reactive({
  visible: false, title: '', modelOid: null, team: null, members: [], loading: false,
  newUserId: null, newRoleName: '', adding: false,
  userOptions: [], userSearching: false,
  roleOptions: [], roleLoading: false,
})

async function openModelTeamDrawer(model) {
  modelTeamDrawer.visible = true
  modelTeamDrawer.title = `团队管理 - ${model.name}`
  modelTeamDrawer.modelOid = model.oid
  modelTeamDrawer.members = []
  modelTeamDrawer.newUserId = null
  modelTeamDrawer.newRoleName = ''
  modelTeamDrawer.userOptions = []
  loadModelRoleOptions()
  modelTeamDrawer.loading = true
  try {
    const res = await getProductModelTeamMembers(model.oid)
    if (res.code === 200) {
      modelTeamDrawer.members = res.data || []
      modelMemberCounts[model.oid] = (res.data || []).length
    }
  } catch {
    message.error('加载团队信息失败')
  } finally {
    modelTeamDrawer.loading = false
  }
}

async function searchUsersForModelTeam(keyword) {
  if (!keyword || keyword.length < 1) { modelTeamDrawer.userOptions = []; return }
  modelTeamDrawer.userSearching = true
  try {
    const res = await getAllUsers({ keyword })
    if (res.code === 200) {
      const all = res.data || []
      const memberUsernames = new Set(modelTeamDrawer.members.map(m => m.username))
      modelTeamDrawer.userOptions = all.filter(u => !memberUsernames.has(u.username))
    }
  } catch { /* ignore */ } finally { modelTeamDrawer.userSearching = false }
}

async function loadModelRoleOptions() {
  modelTeamDrawer.roleLoading = true
  try {
    const res = await getRoles()
    if (res.code === 200) {
      modelTeamDrawer.roleOptions = (res.data || []).filter(r => r.roleType !== 'PLATFORM')
    }
  } catch { /* ignore */ } finally { modelTeamDrawer.roleLoading = false }
}

async function handleAddModelTeamMember() {
  if (!modelTeamDrawer.newUserId) { message.warning('请选择用户'); return }
  modelTeamDrawer.adding = true
  try {
    const res = await addProductModelTeamMember(modelTeamDrawer.modelOid, modelTeamDrawer.newUserId, modelTeamDrawer.newRoleName)
    if (res.code === 200) {
      message.success('已添加成员')
      modelTeamDrawer.newUserId = null
      modelTeamDrawer.newRoleName = ''
      modelTeamDrawer.userOptions = []
      const membersRes = await getProductModelTeamMembers(modelTeamDrawer.modelOid)
      if (membersRes.code === 200) {
        modelTeamDrawer.members = membersRes.data || []
        modelMemberCounts[modelTeamDrawer.modelOid] = modelTeamDrawer.members.length
      }
    }
  } catch { message.error('添加失败') } finally { modelTeamDrawer.adding = false }
}

async function handleRemoveModelTeamMember(user) {
  try {
    const res = await removeProductModelTeamMember(modelTeamDrawer.modelOid, user.username)
    if (res.code === 200) {
      message.success('已移除成员')
      const membersRes = await getProductModelTeamMembers(modelTeamDrawer.modelOid)
      if (membersRes.code === 200) {
        modelTeamDrawer.members = membersRes.data || []
        modelMemberCounts[modelTeamDrawer.modelOid] = modelTeamDrawer.members.length
      }
    }
  } catch { message.error('移除失败') }
}

// 型号 阶段管理
const modelStageDrawer = reactive({
  visible: false, title: '', modelOid: null,
  stages: [], loading: false, initializing: false,
  editingCode: null,
  editForm: { name: '', description: '', color: '', sortOrder: 1 },
})

async function openModelStageDrawer(model) {
  modelStageDrawer.visible = true
  modelStageDrawer.title = `研发阶段 - ${model.name}`
  modelStageDrawer.modelOid = model.oid
  modelStageDrawer.stages = []
  modelStageDrawer.loading = true
  try {
    const res = await getStages(model.oid)
    if (res.code === 200) modelStageDrawer.stages = res.data || []
  } catch { message.error('加载阶段数据失败') } finally { modelStageDrawer.loading = false }
}

async function handleModelInitStages() {
  modelStageDrawer.initializing = true
  try {
    const res = await initDefaultStages(modelStageDrawer.modelOid)
    if (res.code === 200) {
      message.success(`已初始化 ${res.data || 6} 个默认阶段`)
      const stagesRes = await getStages(modelStageDrawer.modelOid)
      if (stagesRes.code === 200) modelStageDrawer.stages = stagesRes.data || []
    }
  } catch { message.error('初始化阶段失败') } finally { modelStageDrawer.initializing = false }
}

function startModelEditStage(stage) {
  modelStageDrawer.editingCode = stage.code
  modelStageDrawer.editForm = {
    name: stage.name,
    description: stage.description || '',
    color: stage.color || '',
    sortOrder: stage.sortOrder || 1,
  }
}

async function handleSaveModelStage(stage) {
  const form = modelStageDrawer.editForm
  if (!form.name?.trim()) {
    message.warning('阶段名称不能为空')
    return
  }
  try {
    const res = await updateStage(modelStageDrawer.modelOid, stage.oid, {
      name: form.name.trim(),
      description: form.description || '',
      color: form.color || '',
      sortOrder: form.sortOrder,
    })
    if (res.code === 200) {
      message.success('阶段更新成功')
      modelStageDrawer.editingCode = null
      stage.name = form.name.trim()
      stage.description = form.description
      stage.color = form.color
      stage.sortOrder = form.sortOrder
    }
  } catch {
    message.error('更新阶段失败')
  }
}

// 统一处理阶段仪表盘显示切换（type: 'line' | 'model'）
async function handleToggleStageShow(type, stage) {
  const drawer = type === 'line' ? stageDrawer : modelStageDrawer
  const ownerOid = drawer.lineOid || drawer.modelOid
  const newVal = stage.showOnDashboard === false ? true : false
  try {
    const res = await toggleStageShowOnDashboard(ownerOid, stage.oid, newVal)
    if (res.code === 200) {
      stage.showOnDashboard = newVal
      message.success(newVal ? '已设为在仪表盘显示' : '已取消仪表盘显示')
    } else {
      message.error(res.message || '操作失败')
    }
  } catch {
    message.error('操作失败')
  }
}

async function openTeamDrawer(line) {
  teamDrawer.visible = true
  teamDrawer.title = `团队管理 - ${line.name}`
  teamDrawer.lineOid = line.oid
  teamDrawer.team = null
  teamDrawer.members = []
  teamDrawer.newUserId = null
  teamDrawer.newRoleName = ''
  teamDrawer.userOptions = []
  teamDrawer.roleOptions = []

  loadRoleOptions()

  teamDrawer.loading = true
  try {
    const [teamRes, memberRes] = await Promise.all([
      getProductLineTeam(line.oid),
      getTeamMembers(line.oid),
    ])
    if (teamRes.code === 200) {
      teamDrawer.team = teamRes.data
    }
    if (memberRes.code === 200) {
      teamDrawer.members = memberRes.data || []
    }
  } catch {
    message.error('加载团队信息失败')
  } finally {
    teamDrawer.loading = false
  }
}

async function searchUsersForTeam(keyword) {
  if (!keyword || keyword.length < 1) {
    teamDrawer.userOptions = []
    return
  }
  teamDrawer.userSearching = true
  try {
    const res = await getAllUsers({ keyword })
    if (res.code === 200) {
      const all = res.data || []
      const memberUsernames = new Set(teamDrawer.members.map(m => m.username))
      teamDrawer.userOptions = all.filter(u => !memberUsernames.has(u.username))
    }
  } catch {
    // ignore
  } finally {
    teamDrawer.userSearching = false
  }
}

async function loadRoleOptions() {
  teamDrawer.roleLoading = true
  try {
    const res = await getRoles()
    if (res.code === 200) {
      // 仅展示自定义角色（BUSINESS），过滤掉平台级角色（PLATFORM）
      teamDrawer.roleOptions = (res.data || []).filter(r => r.roleType !== 'PLATFORM')
    }
  } catch {
    // ignore
  } finally {
    teamDrawer.roleLoading = false
  }
}

async function handleAddTeamMember() {
  if (!teamDrawer.newUserId) {
    message.warning('请选择用户')
    return
  }
  teamDrawer.adding = true
  try {
    const res = await addTeamMember(teamDrawer.lineOid, teamDrawer.newUserId, teamDrawer.newRoleName)
    if (res.code === 200) {
      message.success('已添加成员')
      teamDrawer.newUserId = null
      teamDrawer.newRoleName = ''
      teamDrawer.userOptions = []
      // 刷新成员列表
      await refreshTeamMembers()
      // 更新团队人数
      if (teamDrawer.lineOid) {
        fetchMemberCount(teamDrawer.lineOid)
      }
    }
  } catch {
    message.error('添加失败')
  } finally {
    teamDrawer.adding = false
  }
}

async function handleRemoveTeamMember(user) {
  try {
    const res = await removeTeamMember(teamDrawer.lineOid, user.username)
    if (res.code === 200) {
      message.success('已移除成员')
      await refreshTeamMembers()
      if (teamDrawer.lineOid) {
        fetchMemberCount(teamDrawer.lineOid)
      }
    }
  } catch {
    message.error('移除失败')
  }
}

async function refreshTeamMembers() {
  try {
    const res = await getTeamMembers(teamDrawer.lineOid)
    if (res.code === 200) {
      teamDrawer.members = res.data || []
    }
  } catch {
    // ignore
  }
}

// ==================== 操作 ====================
async function loadAllLines() {
  loading.value = true
  try {
    // 并行加载树形数据、扁平数据和统计信息
    const [treeRes, flatRes, statsRes] = await Promise.all([
      getProductLineTree(),
      getProductLines(),
      getProductLineStats(),
    ])
    if (treeRes.code === 200) {
      treeData.value = treeRes.data || []
    }
    if (flatRes.code === 200) {
      allLines.value = flatRes.data || []
      for (const line of allLines.value) {
        parentNameMap[line.oid] = line.name
        fetchMemberCount(line.oid)
      }
    }
    if (statsRes.code === 200) {
      const stats = statsRes.data || {}
      Object.entries(stats).forEach(([oid, s]) => {
        statsMap[oid] = s
      })
    }
  } catch {
    message.error('加载产品线列表失败')
  } finally {
    loading.value = false
  }
}

// 编辑时过滤掉自身及子孙节点的产品线树（供 DynamicForm 的 product-line-select 使用）
const editingFilteredProductLineTree = computed(() => {
  // 新建时不过滤，返回 null 让 DynamicForm 使用内部树
  if (!modal.isEdit || !modal.editingOid) return null

  const excludeOids = new Set()
  excludeOids.add(modal.editingOid)

  const collectDescendants = (nodes) => {
    for (const node of nodes) {
      if (excludeOids.has(node.oid)) {
        if (node.children) {
          for (const child of node.children) {
            excludeOids.add(child.oid)
            collectDescendants([child])
          }
        }
      } else if (node.children) {
        collectDescendants(node.children)
      }
    }
  }
  collectDescendants(treeData.value)

  // 转换格式为 DynamicForm 所需的 { title, value, key, children }
  const transform = (nodes, parentPath = '') => {
    return nodes
      .filter(n => !excludeOids.has(n.oid))
      .map(n => {
        const label = parentPath ? `${parentPath} / ${n.name}` : n.name
        return {
          title: label,
          label,
          value: n.oid,
          key: n.oid,
          children: n.children ? transform(n.children, label) : undefined,
        }
      })
  }
  return transform(treeData.value)
})

async function fetchMemberCount(lineOid) {
  try {
    const res = await getTeamMembers(lineOid)
    if (res.code === 200) {
      teamMemberCounts[lineOid] = (res.data || []).length
    }
  } catch {
    teamMemberCounts[lineOid] = 0
  }
}

async function handleSave() {
  // 使用 DynamicForm 的校验
  if (dynamicFormRef.value) {
    const errors = dynamicFormRef.value.validate()
    if (errors.length > 0) {
      message.warning(errors[0])
      return
    }
  }

  const formData = dynamicFormRef.value?.getFormData() || modalFormData.value
  const code = formData.code || ''
  const name = formData.name || ''

  if (!code.trim() || !name.trim()) {
    message.warning('产品线编码和名称不能为空')
    return
  }
  modal.saving = true
  try {
    // 直接使用完整 formData 作为 payload，后端通过 @JsonAnySetter 将
    // 非实体固定字段自动捕获到 ext_attrs JSONB 列中存储
    const payload = { ...formData }
    // 确保 code/name 去除首尾空白
    if (payload.code) payload.code = payload.code.trim()
    if (payload.name) payload.name = payload.name.trim()
    // 确保 parentOid 空字符串转为 null
    if (payload.parentOid === '' || payload.parentOid === undefined) {
      payload.parentOid = null
    }
    let res
    if (modal.isEdit) {
      res = await updateProductLine(modal.editingOid, payload)
    } else {
      res = await createProductLine(payload)
    }
    if (res.code === 200) {
      message.success(modal.isEdit ? '产品线更新成功' : '产品线创建成功')
      closeModal()
      await loadAllLines()
    } else {
      message.error(res.message || '操作失败')
    }
  } catch {
    message.error('操作失败')
  } finally {
    modal.saving = false
  }
}

async function handleDelete(line) {
  try {
    const res = await deleteProductLine(line.oid)
    if (res.code === 200) {
      message.success('产品线已删除')
      await loadAllLines()
    } else {
      message.error(res.message || '删除失败')
    }
  } catch {
    message.error('删除失败')
  }
}

/** 双击产品线名称 → 跳转管业看板页面 */
function handleNameDblClick(record) {
  router.push(`/product/${record.oid}`)
}

function getStat(oid, field) {
  const s = statsMap[oid]
  return s ? (s[field] || 0) : 0
}

function formatTime(str) {
  if (!str) return '-'
  return str.replace('T', ' ').substring(0, 19)
}

// ==================== 生命周期 ====================
onMounted(() => {
  loadAllLines()
  loadListLayout()
})

/** 加载列表 PageLayout 并动态构建表格列 */
async function loadListLayout() {
  try {
    const res = await getPageLayoutByCode('PRODUCT_LINE', 'list')
    if (res.code === 200 && res.data) {
      let layout = res.data.layoutJson
      if (typeof layout === 'string') {
        try { layout = JSON.parse(layout) } catch { return }
      }
      const layoutCols = layout?.table?.columns
      if (layoutCols && layoutCols.length > 0) {
        // 将 layout 列格式映射为 DataTable 列格式
        columns.value = layoutCols.map(col => ({
          title: col.label || col.fieldName,
          dataIndex: col.fieldName,
          key: col.fieldName,
          width: col.width || undefined,
          fixed: col.fixed || undefined,
          align: col.align || undefined,
        }))
      }
    }
  } catch { /* 加载失败使用默认列 */ }
}
</script>

<style scoped>
.product-series-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* ===== 页头 ===== */
.ps-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 0 20px;
  flex-shrink: 0;
}

.ps-header-left {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.ps-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #1a1a2e;
}

.ps-subtitle {
  font-size: 13px;
  color: #8c8c8c;
}

/* ===== 统计栏 ===== */
.ps-stats-bar {
  display: flex;
  align-items: center;
  gap: 32px;
  padding: 16px 24px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  flex-shrink: 0;
  margin-bottom: 16px;
}

.ps-stat-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ps-stat-icon {
  font-size: 22px;
  color: #1677ff;
}

.ps-stat-value {
  font-size: 22px;
  font-weight: 700;
  color: #1a1a2e;
  min-width: 24px;
  text-align: center;
}

.ps-stat-label {
  font-size: 13px;
  color: #8c8c8c;
}

/* ===== 表格容器 ===== */
.ps-table-wrapper {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

/* 表格内样式 */
.ps-name {
  font-weight: 500;
  cursor: pointer;
  color: #1677ff;
  transition: color 0.2s;
}

.ps-name:hover {
  color: #0958d9;
  text-decoration: underline;
}

.ps-cell-text {
  color: #595959;
}

.ps-thumbnail img {
  width: 48px;
  height: 48px;
  border-radius: 4px;
  object-fit: cover;
  border: 1px solid #f0f0f0;
}

/* ===== 团队抽屉 ===== */
.ps-add-member {
  display: flex;
  align-items: center;
}

.ps-member-list {
  margin-top: 0;
}

.ps-member-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 12px 0;
  font-size: 14px;
  font-weight: 600;
  color: #1a1a2e;
}

/* 继承成员行样式：浅灰色背景 */
.ps-inherited-member {
  background: #fafafa;
  border-radius: 4px;
  padding: 4px 8px;
  opacity: 0.85;
}
</style>
