import { ref } from 'vue'
import { listCapabilities } from '@/api'

/**
 * 本租户已启用的能力码 —— `ck_tenant_capability` 这张账本的读侧。
 *
 * 口径（与装包报告一致）：
 *  · 账本里只有"包里声明了、且平台已实现"的能力 —— 所以这里的 code 天然不会假就绪；
 *  · `enabled=false` 的行（管理员手动关闭）不算启用；
 *  · 取不到（未装包 / 接口异常）→ 按"未启用"处理：宁可不显示入口，也不要点了发现什么都没有。
 *
 * 模块级单例：多个组件共用同一次请求。用法：
 *   const { load, has } = useCapabilities()
 *   onMounted(load)
 *   <section v-if="has('DELIVERABLE_MANAGEMENT')">…</section>
 */
const codes = ref(new Set())
/** 是否**成功**加载过账本（失败保持 false —— MainLayout 据此做一次退避重试） */
const loaded = ref(false)
let pending = null

export function useCapabilities() {
  /**
   * 拉取一次（幂等：并发调用共享同一个 Promise —— 但**失败不缓存**）。
   *
   * 2026-10-12 修（用户反馈："企业资源页面是不是没有按租户隔离的方式展示，
   * Roney.Liu 对应的租户资源库中有数据"—— 实际看到的是"只剩 3 个不受控子库页签、
   * 有数据的 4 个库与 MPN 入口全没了"）：
   * 原实现把 Promise 存进 pending 后**永不清理** —— 首次请求若失败（后端重启窗口、
   * 装包正写账本时的瞬态、网络抖动），codes 永久为空集，界面把所有受能力控制的入口
   * 全藏掉，**只能整页刷新才恢复**。现在：只有"成功拿到账本"才缓存 Promise；
   * 失败与异常响应都清掉 pending，允许后续 load() 重试。
   */
  function load() {
    if (!pending) {
      pending = listCapabilities()
        .then((res) => {
          if (res?.code === 200 && Array.isArray(res.data)) {
            codes.value = new Set(
              res.data
                .filter((c) => c && c.enabled !== false && c.capabilityCode)
                .map((c) => c.capabilityCode)
            )
            loaded.value = true
          } else {
            pending = null   // 响应异常（非 200 / 非数组）→ 不认账，允许重试
          }
        })
        .catch(() => {
          pending = null     // 请求失败 → 不缓存；界面仍按"未启用"渲染（不给空诺）
        })
    }
    return pending
  }

  /** 是否启用了某能力码（模板里直接调用，随 codes 变化自动重渲染） */
  function has(code) {
    return !!code && codes.value.has(code)
  }

  return { codes, loaded, load, has }
}
