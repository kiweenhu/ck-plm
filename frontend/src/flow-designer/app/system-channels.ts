/**
 * 系统通知渠道 —— 服务端 {@code plm.notification} 配置的只读副本（设计器用）。
 *
 * <p><b>为什么设计器要知道它</b>：流程模板里的「通知方式」只是"本流程允许用哪些通道"，
 * 通道到底可不可用由服务端配置决定（凭据在企业集成里，见 application.yml）。
 * 如果设计器凭空让人勾一个系统没启用的渠道，就会配出一个"运行期发不出去"的流程 ——
 * 这类"配了但不生效"最难查。
 *
 * <p>取不到时保持 {@code undefined}：校验里对应那条规则直接跳过（不猜、不误报）。
 * 只取一次并缓存：一次设计会话内系统配置不会变，没必要每次校验都请求。
 */

import { shallowRef } from 'vue'
import { NotifyChannel, type ValidateOptions } from '@flow-dsl-core'

/** 系统启用<b>且凭据齐备</b>的渠道；undefined = 还没取到（或取失败） */
const available = shallowRef<NotifyChannel[] | undefined>(undefined)

/** 已知渠道（用于把接口返回的编码收敛到枚举上：服务端多一个渠道不该让前端崩） */
const KNOWN = new Set<string>(Object.values(NotifyChannel))

/** 由设计器入口在挂载时调用一次；失败时保持 undefined，不抛给界面 */
export function setSystemNotifyChannels(codes: string[] | undefined): void {
  available.value = codes
    ? codes.filter((code): code is NotifyChannel => KNOWN.has(code))
    : undefined
}

/** 供界面提示用（如"系统当前可用：站内信"）；未取到返回 undefined */
export function systemNotifyChannels(): NotifyChannel[] | undefined {
  return available.value
}

/**
 * 校验选项：把"系统可用渠道"喂给 {@code validateDsl}。
 *
 * <p>未取到时返回 undefined —— 校验器会跳过该规则，而不是把"拿不到系统配置"
 * 当成"系统一个渠道都没启用"（那会误报一堆警告）。
 */
export function validateOptions(): ValidateOptions | undefined {
  return available.value ? { availableNotifyChannels: available.value } : undefined
}
