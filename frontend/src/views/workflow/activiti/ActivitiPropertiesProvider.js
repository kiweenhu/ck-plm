import { is } from 'bpmn-js/lib/util/ModelUtil'
import { getBusinessObject } from 'bpmn-js/lib/util/ModelUtil'
import { TextFieldEntry, isTextFieldEntryEdited } from '@bpmn-io/properties-panel'

// 命名空间
const ACTIVITI_NS = 'http://activiti.org/bpmn'

/**
 * 获取 Activiti 扩展属性值
 */
function getActivitiProp(element, propName) {
  const bo = getBusinessObject(element)
  const activitiProps = bo.get('extensionElements')
  // 优先从 businessObject 直接属性读取（moddle 扩展已注册）
  const direct = bo.get(`activiti:${propName}`)
  if (direct !== undefined && direct !== null) return String(direct)
  // 尝试从 extensionElements 读取
  if (activitiProps && activitiProps.values) {
    const found = activitiProps.values.find(v => v.$type === `activiti:${propName}`)
    if (found) return found.value || ''
  }
  return ''
}

/**
 * 设置 Activiti 扩展属性值
 */
function setActivitiProp(element, propName, value) {
  const bo = getBusinessObject(element)
  const modeling = element.modeler ? element.modeler.modeling : null
  if (!modeling) return

  if (value && value.trim()) {
    modeling.updateProperties(element, {
      [`activiti:${propName}`]: value
    })
  } else {
    // 清空属性
    modeling.updateProperties(element, {
      [`activiti:${propName}`]: undefined
    })
  }
}

/**
 * 创建"用户任务"属性组
 */
function createUserTaskGroup(element) {
  return {
    id: 'activiti-user-task',
    label: 'Activiti 用户任务',
    component: 'Group',
    entries: [
      {
        id: 'activiti-assignee',
        element,
        component: TextFieldEntry,
        label: '处理人 (assignee)',
        description: '指定唯一处理人，填写用户ID',
        getValue: (e) => getActivitiProp(e, 'assignee'),
        setValue: (e, v) => setActivitiProp(e, 'assignee', v),
        isEdited: isTextFieldEntryEdited
      },
      {
        id: 'activiti-candidateUsers',
        element,
        component: TextFieldEntry,
        label: '候选人 (candidateUsers)',
        description: '多个候选用户，用逗号分隔',
        getValue: (e) => getActivitiProp(e, 'candidateUsers'),
        setValue: (e, v) => setActivitiProp(e, 'candidateUsers', v),
        isEdited: isTextFieldEntryEdited
      },
      {
        id: 'activiti-candidateGroups',
        element,
        component: TextFieldEntry,
        label: '候选组 (candidateGroups)',
        description: '多个候选组，用逗号分隔',
        getValue: (e) => getActivitiProp(e, 'candidateGroups'),
        setValue: (e, v) => setActivitiProp(e, 'candidateGroups', v),
        isEdited: isTextFieldEntryEdited
      },
      {
        id: 'activiti-formKey',
        element,
        component: TextFieldEntry,
        label: '表单标识 (formKey)',
        description: '关联的表单定义 Key',
        getValue: (e) => getActivitiProp(e, 'formKey'),
        setValue: (e, v) => setActivitiProp(e, 'formKey', v),
        isEdited: isTextFieldEntryEdited
      },
      {
        id: 'activiti-dueDate',
        element,
        component: TextFieldEntry,
        label: '截止日期 (dueDate)',
        description: '任务截止时间表达式，如 2024-12-31 或 ${varName}',
        getValue: (e) => getActivitiProp(e, 'dueDate'),
        setValue: (e, v) => setActivitiProp(e, 'dueDate', v),
        isEdited: isTextFieldEntryEdited
      },
      {
        id: 'activiti-priority',
        element,
        component: TextFieldEntry,
        label: '优先级 (priority)',
        description: '数字越大优先级越高，默认 50',
        getValue: (e) => getActivitiProp(e, 'priority'),
        setValue: (e, v) => setActivitiProp(e, 'priority', v),
        isEdited: isTextFieldEntryEdited
      }
    ]
  }
}

/**
 * 创建"服务任务"属性组
 */
function createServiceTaskGroup(element) {
  return {
    id: 'activiti-service-task',
    label: 'Activiti 服务任务',
    component: 'Group',
    entries: [
      {
        id: 'activiti-class',
        element,
        component: TextFieldEntry,
        label: '类全名 (class)',
        description: 'Java 类全限定名，如 cn.ck.plm.workflow.delegate.XXX',
        getValue: (e) => getActivitiProp(e, 'class'),
        setValue: (e, v) => setActivitiProp(e, 'class', v),
        isEdited: isTextFieldEntryEdited
      },
      {
        id: 'activiti-delegateExpression',
        element,
        component: TextFieldEntry,
        label: '代理表达式 (delegateExpression)',
        description: 'Spring Bean 名称，如 ${myDelegate}',
        getValue: (e) => getActivitiProp(e, 'delegateExpression'),
        setValue: (e, v) => setActivitiProp(e, 'delegateExpression', v),
        isEdited: isTextFieldEntryEdited
      },
      {
        id: 'activiti-expression',
        element,
        component: TextFieldEntry,
        label: '表达式 (expression)',
        description: 'UEL 表达式，如 ${myBean.execute(task)}',
        getValue: (e) => getActivitiProp(e, 'expression'),
        setValue: (e, v) => setActivitiProp(e, 'expression', v),
        isEdited: isTextFieldEntryEdited
      },
      {
        id: 'activiti-resultVariable',
        element,
        component: TextFieldEntry,
        label: '结果变量 (resultVariable)',
        description: '存放执行结果的流程变量名',
        getValue: (e) => getActivitiProp(e, 'resultVariable'),
        setValue: (e, v) => setActivitiProp(e, 'resultVariable', v),
        isEdited: isTextFieldEntryEdited
      }
    ]
  }
}

/**
 * 创建"流程级别"属性组
 */
function createProcessGroup(element) {
  return {
    id: 'activiti-process',
    label: 'Activiti 流程配置',
    component: 'Group',
    entries: [
      {
        id: 'activiti-candidateStarterUsers',
        element,
        component: TextFieldEntry,
        label: '启动候选人',
        description: '允许启动此流程的用户，多用户逗号分隔',
        getValue: (e) => getActivitiProp(e, 'candidateStarterUsers'),
        setValue: (e, v) => setActivitiProp(e, 'candidateStarterUsers', v),
        isEdited: isTextFieldEntryEdited
      },
      {
        id: 'activiti-candidateStarterGroups',
        element,
        component: TextFieldEntry,
        label: '启动候选组',
        description: '允许启动此流程的组，多组逗号分隔',
        getValue: (e) => getActivitiProp(e, 'candidateStarterGroups'),
        setValue: (e, v) => setActivitiProp(e, 'candidateStarterGroups', v),
        isEdited: isTextFieldEntryEdited
      }
    ]
  }
}

/**
 * Activiti 属性面板提供者
 * 为 bpmn-js-properties-panel 添加 Activiti 引擎专用属性
 */
export default class ActivitiPropertiesProvider {
  constructor(propertiesPanel) {
    propertiesPanel.registerProvider(500, this)
  }

  getGroups(element) {
    return (groups) => {
      // 用户任务 → 添加 Activiti 用户任务属性
      if (is(element, 'bpmn:UserTask')) {
        groups.push(createUserTaskGroup(element))
      }

      // 服务任务 → 添加 Activiti 服务任务属性
      if (is(element, 'bpmn:ServiceTask')) {
        groups.push(createServiceTaskGroup(element))
      }

      // 流程 → 添加 Activiti 流程配置
      if (is(element, 'bpmn:Process')) {
        groups.push(createProcessGroup(element))
      }

      return groups
    }
  }
}

ActivitiPropertiesProvider.$inject = ['propertiesPanel']
