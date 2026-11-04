<template>
  <div class="cad-viewer">
    <!-- 画布 + 结构树：装配结构在左，与 CATIA / SolidWorks 的模型树同一侧 ——
         "左边选零件、右边看模型"是从 CAD 带过来的肌肉记忆，反着放每次都要多找一眼 -->
    <div class="cv-body">
      <div v-if="showStructure" class="cv-structure">
        <CadStructureTree
          :nodes="structureNodes"
          :selected-id="selectedId"
          @select="onStructureSelect"
        />
      </div>

      <div class="cv-canvas-wrap">
        <div ref="canvasHost" class="cv-canvas" />

        <!-- 操作按钮：浮动在画布右侧、垂直排列。
             横在顶部会吃掉一整条高度，装配体越大越亏；浮在画布上不占布局空间。
             嵌入窄容器（如 BOM 右侧「轻量化」页签）时用 :show-toolbar="false" 关掉，
             让位给模型本身 —— 那儿的画布宽不到 400px，按钮列会挡住大半个零件 -->
        <div v-if="showToolbar" class="cv-toolbar-float">
          <a-button size="small" class="cv-tool" @click="resetView">重置视角</a-button>
          <a-button size="small" class="cv-tool" @click="fitView">适应窗口</a-button>
          <a-tooltip placement="left" title="切换线框显示">
            <a-button size="small" class="cv-tool" :type="wireframe ? 'primary' : 'default'" @click="toggleWireframe">线框</a-button>
          </a-tooltip>
          <a-tooltip placement="left" title="半透明显示，便于观察内部结构">
            <a-button size="small" class="cv-tool" :type="transparent ? 'primary' : 'default'" @click="toggleTransparent">透明</a-button>
          </a-tooltip>
          <a-tooltip placement="left" title="沿装配方向炸开各零部件">
            <a-button size="small" class="cv-tool" :type="exploded ? 'primary' : 'default'" @click="toggleExplode">爆炸</a-button>
          </a-tooltip>
          <a-tooltip placement="left" title="开启后拖动滑块沿 Z 轴剖切；用于查看装配体内部">
            <a-button size="small" class="cv-tool" :type="clipEnabled ? 'primary' : 'default'" @click="toggleClip">剖切</a-button>
          </a-tooltip>
          <a-tooltip placement="left" title="在模型上依次点两个点测量距离">
            <a-button size="small" class="cv-tool" :type="measuring ? 'primary' : 'default'" @click="toggleMeasure">测量</a-button>
          </a-tooltip>
          <a-tooltip placement="left" title="显示/隐藏左下角的 XYZ 方位坐标">
            <a-button size="small" class="cv-tool" :type="axisVisible ? 'primary' : 'default'" @click="axisVisible = !axisVisible">坐标</a-button>
          </a-tooltip>
        </div>

        <!-- 剖切滑块：放左下角，避免和右侧按钮列叠在一起 -->
        <div v-if="clipEnabled" class="cv-clip-float">
          <span class="cv-label">剖切位置</span>
          <a-slider
            v-model:value="clipRatio"
            :min="0" :max="100" :step="1"
            style="flex:1;margin:0"
            @change="applyClip"
          />
        </div>

        <div v-if="measuring" class="cv-measure-float">
          <span class="cv-measure-dot" />
          <span>{{ measurePoints.length === 1 ? '已选第一点，请点击第二个点' : '在模型上依次点击两点测量距离' }}</span>
          <a-button size="small" type="link" @click="clearMeasure">清除</a-button>
        </div>

        <!--
          测量结果单独做成卡片：原先只在底部状态栏塞一句"距离 3.70 mm"，
          和"三角面/零件/单位"混排、字号又和普通文字一样，测完几乎找不到读数。
        -->
        <div v-if="measureResult" class="cv-measure-result">
          <div class="cv-measure-result-body">
            <div class="cv-measure-caption">测量距离</div>
            <div class="cv-measure-value">{{ measureResult }}</div>
          </div>
          <a-button size="small" type="text" @click="clearMeasure">清除</a-button>
        </div>
      </div>
    </div>

    <!-- 状态提示 -->
    <div v-if="loading" class="cv-mask">
      <a-spin :tip="loadTip" />
    </div>

    <div v-if="error" class="cv-mask">
      <a-empty :description="error">
        <a-button v-if="props.retryable" size="small" type="primary" @click="$emit('retry')">重新转换</a-button>
      </a-empty>
    </div>

    <!-- 测量结果与统计 -->
    <div class="cv-footer">
      <a-space size="middle" wrap>
        <span v-if="stats.triangles">三角面 {{ formatNumber(stats.triangles) }}</span>
        <span v-if="stats.parts">零件 {{ stats.parts }}</span>
        <span v-if="stats.units">源单位 {{ stats.units }}</span>
        <!-- 测量读数已由画布上的大卡片承载，这里不再重复一遍，免得两个数字对不上时还要猜 -->
        <span v-if="selectedName" class="cv-selected">已选：{{ selectedName }}</span>
      </a-space>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js'
import { DRACOLoader } from 'three/examples/jsm/loaders/DRACOLoader.js'
import CadStructureTree from './CadStructureTree.vue'

const props = defineProps({
  /** GLB 文件访问地址（走 /api/ckfiles/{oid}/stream） */
  glbUrl: { type: String, default: '' },
  /** 结构树 JSON 地址；为空则不显示结构树面板 */
  structureUrl: { type: String, default: '' },
  /** 是否显示结构树 */
  showStructure: { type: Boolean, default: true },
  /** 是否显示右侧操作按钮列（重置视角 / 适应窗口 / 线框 / 透明 / 爆炸 / 剖切 / 测量 / 坐标） */
  showToolbar: { type: Boolean, default: true },
  /** 是否允许重新转换（转换失败时显示） */
  retryable: { type: Boolean, default: false },
  /** 统计信息：三角面 / 零件数 / 单位 */
  stats: { type: Object, default: () => ({}) }
})

const emit = defineEmits(['retry', 'select'])

const canvasHost = ref(null)
const loading = ref(false)
const loadTip = ref('正在加载模型...')
const error = ref('')

const wireframe = ref(false)
const transparent = ref(false)
const exploded = ref(false)
const clipEnabled = ref(false)
const clipRatio = ref(50)
const measuring = ref(false)
const measurePoints = ref([])
const measureResult = ref('')

const structureNodes = ref([])
const selectedId = ref('')
const selectedName = ref('')

let renderer = null
let scene = null
let camera = null
let controls = null
let model = null
let raycaster = null
let pointer = new THREE.Vector2()
let resizeObserver = null
let animationId = 0
let clipPlane = null
/** 测量标注（粗线段 + 端点小球）整体挂在这个 Group 上，便于一次性清理 */
let measureGroup = null
let homePosition = null
/** 初始视角的注视点：只复位相机位置会让"重置视角"看向别处，必须连 target 一起还原 */
let homeTarget = null
/** 视角过渡（选中零件时把注视点平移过去）；用户一动鼠标就让位，绝不和操作抢相机 */
let viewTween = null
/** 左下角方位坐标（独立小场景，跟着主相机转） */
let axisScene = null
let axisCamera = null
/** 罗盘的白底圆盘：每帧要贴到相机光轴上 */
let axisBg = null
/** 坐标罗盘的像素边长 */
const AXIS_GIZMO_SIZE = 92
/**
 * 罗盘相机到原点的距离。
 * 太小 → 轴末端的字母被视锥边缘裁掉；太大 → 92px 里那三根轴细得看不清。
 * 3.4 是"字母放得下、轴还够长"的折中：半高 3.4·tan25° ≈ 1.59 > 字母外沿 1.54。
 */
const AXIS_GIZMO_DIST = 3.4
/** 白底圆盘再往相机反方向放一点，排在所有轴后面（透明物体按距离由远及近绘制） */
const AXIS_BG_OFFSET = 1
/** 圆盘的世界尺寸：保持和"放在原点"时相同的视觉大小 */
const AXIS_BG_SCALE = 2.6 * (AXIS_GIZMO_DIST + AXIS_BG_OFFSET) / AXIS_GIZMO_DIST
/** 是否显示方位坐标：工程件"哪边是 Y"必须一眼可见，默认开 */
const axisVisible = ref(true)

/** 结构树 id → three 对象 */
const nodeIndex = new Map()
/** 高亮前的原始材质，按 mesh 存 */
const originalMaterials = new Map()
/** 结构树里确实存在的节点 id 集合：点击拾取时用它判断"这一层是不是树里的节点" */
const structureIdSet = computed(() => new Set((structureNodes.value || []).map((n) => n.id)))

// ==================== 生命周期 ====================

onMounted(() => {
  initScene()
  if (props.glbUrl) loadModel(props.glbUrl)
  if (props.structureUrl) loadStructure(props.structureUrl)
})

onBeforeUnmount(() => {
  dispose()
})

watch(() => props.glbUrl, (url) => {
  if (url) loadModel(url)
})

watch(() => props.structureUrl, (url) => {
  if (url) loadStructure(url)
})

// ==================== 场景 ====================

function initScene() {
  const host = canvasHost.value
  if (!host) return

  scene = new THREE.Scene()
  scene.background = new THREE.Color(0xf5f5f5)

  camera = new THREE.PerspectiveCamera(45, host.clientWidth / Math.max(host.clientHeight, 1), 0.001, 10000)
  camera.position.set(1, 1, 1)

  renderer = new THREE.WebGLRenderer({ antialias: true })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  renderer.setSize(host.clientWidth, host.clientHeight)
  // 剖切依赖本开关：不开的话材质的 clippingPlanes 会被忽略
  renderer.localClippingEnabled = true
  host.appendChild(renderer.domElement)

  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.08

  // 光照：半球光打底 + 两个方向光保证工业件也能看清棱线
  scene.add(new THREE.HemisphereLight(0xffffff, 0x888888, 1.1))
  const key = new THREE.DirectionalLight(0xffffff, 1.0)
  key.position.set(1, 2, 3)
  scene.add(key)
  const fill = new THREE.DirectionalLight(0xffffff, 0.4)
  fill.position.set(-2, -1, -1)
  scene.add(fill)

  raycaster = new THREE.Raycaster()
  renderer.domElement.addEventListener('pointerdown', onPointerDown)
  // 滚轮缩放会直接改写相机距离，过渡动画必须立刻让位
  renderer.domElement.addEventListener('wheel', cancelViewTween, { passive: true })

  resizeObserver = new ResizeObserver(() => onResize())
  resizeObserver.observe(host)

  initAxisGizmo()
  animate()
}

function animate() {
  animationId = requestAnimationFrame(animate)
  stepViewTween()
  if (controls) controls.update()
  if (!renderer || !scene || !camera) return

  // 主场景每次都要把视口复位：上一帧为了画罗盘改过 viewport，
  // 不复位的话主画面会缩到角落里
  const size = renderer.getSize(new THREE.Vector2())
  renderer.setViewport(0, 0, size.x, size.y)
  renderer.render(scene, camera)

  renderAxisGizmo()
}

/**
 * 左下角的 XYZ 方位罗盘。
 *
 * <p>为什么不直接把 AxesHelper 加进主场景：它是以世界原点为中心、按模型尺寸画的三根线，
 * 装配体一加载就会穿过零件内部，既看不清也挡视线。这里用独立小场景 + 独立 viewport
 * 叠在角落，任何时候都只有 92px 大小，且只反映"当前视角下 XYZ 指向哪边"。
 */
function initAxisGizmo() {
  axisScene = new THREE.Scene()
  axisCamera = new THREE.PerspectiveCamera(50, 1, 0.1, 10)
  // 下面每帧都会按主相机朝向重算位置，这里只是给个初值
  axisCamera.position.set(0, 0, AXIS_GIZMO_DIST)

  // 白色圆底：不然坐标轴会直接压在模型上，深浅颜色一撞就看不见了。
  // 位置在 renderAxisGizmo 里逐帧贴到相机光轴上 —— 写死世界坐标的话，视角一转圆盘就偏出轴心。
  axisBg = new THREE.Sprite(new THREE.SpriteMaterial({
    map: makeCircleTexture(),
    transparent: true,
    depthTest: false
  }))
  // 圆盘按"看起来和放在原点时一样大"换算：视径 ∝ 尺寸 / 距离
  axisBg.scale.set(AXIS_BG_SCALE, AXIS_BG_SCALE, 1)
  axisBg.position.set(0, 0, -AXIS_BG_OFFSET)
  axisScene.add(axisBg)

  const axes = [
    { dir: new THREE.Vector3(1, 0, 0), color: 0xd4382f, label: 'X' },
    { dir: new THREE.Vector3(0, 1, 0), color: 0x2f9e4f, label: 'Y' },
    { dir: new THREE.Vector3(0, 0, 1), color: 0x2f6fd4, label: 'Z' }
  ]
  for (const { dir, color, label } of axes) {
    const arrow = new THREE.ArrowHelper(dir, new THREE.Vector3(0, 0, 0), 1, color, 0.26, 0.16)
    axisScene.add(arrow)
    const text = new THREE.Sprite(new THREE.SpriteMaterial({
      map: makeLabelTexture(label, '#' + color.toString(16).padStart(6, '0')),
      transparent: true,
      depthTest: false
    }))
    text.position.copy(dir).multiplyScalar(1.28)
    text.scale.set(0.52, 0.52, 1)
    axisScene.add(text)
  }
}

/** 圆底纹理（带一点点边距，避免贴边被裁） */
function makeCircleTexture() {
  const c = document.createElement('canvas')
  c.width = c.height = 128
  const ctx = c.getContext('2d')
  ctx.beginPath()
  ctx.arc(64, 64, 58, 0, Math.PI * 2)
  ctx.fillStyle = 'rgba(255,255,255,0.92)'
  ctx.fill()
  ctx.lineWidth = 3
  ctx.strokeStyle = 'rgba(0,0,0,0.08)'
  ctx.stroke()
  return new THREE.CanvasTexture(c)
}

/** 轴字母纹理 */
function makeLabelTexture(text, color) {
  const c = document.createElement('canvas')
  c.width = c.height = 64
  const ctx = c.getContext('2d')
  ctx.fillStyle = color
  ctx.font = 'bold 46px Arial, sans-serif'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillText(text, 32, 34)
  return new THREE.CanvasTexture(c)
}

function renderAxisGizmo() {
  if (!axisVisible.value || !axisScene || !camera) return
  const size = renderer.getSize(new THREE.Vector2())
  const s = AXIS_GIZMO_SIZE
  /*
   * 小相机只继承朝向还不够，位置必须跟着朝向一起转。
   *
   * 原来位置写死在 (0,0,3)：只要主相机一转（初始 fitView 就已经转了 ~35°），
   * 罗盘相机虽然朝向一致，却不再看着原点 —— 三根轴连同字母一起飘出 92px 的视锥，
   * 表现为"视角一动坐标轴就少一半甚至整个没了"。正确做法是让相机站在
   * 自身 +Z 方向上、距离原点固定的一段位置，这样它永远正对罗盘中心。
   */
  axisCamera.quaternion.copy(camera.quaternion)
  axisCamera.position
    .set(0, 0, 1)
    .applyQuaternion(camera.quaternion)
    .multiplyScalar(AXIS_GIZMO_DIST)
  // 白底圆盘同样要贴在光轴上：写死世界坐标时视角一转它就偏离轴心，
  // 圆心跑到视口外，看起来像"坐标轴被裁掉了半边"
  if (axisBg) axisBg.position.set(0, 0, -AXIS_BG_OFFSET).applyQuaternion(camera.quaternion)
  renderer.setScissorTest(true)
  renderer.setViewport(12, 12, s, s)
  renderer.setScissor(12, 12, s, s)
  const prevAutoClear = renderer.autoClear
  renderer.autoClear = false          // 不清颜色，罗盘直接叠在主画面上
  renderer.clearDepth()               // 但要清深度，否则会被模型挡住
  renderer.render(axisScene, axisCamera)
  renderer.autoClear = prevAutoClear
  renderer.setScissorTest(false)
  renderer.setScissor(0, 0, size.x, size.y)
  renderer.setViewport(0, 0, size.x, size.y)
}

function onResize() {
  const host = canvasHost.value
  if (!host || !renderer || !camera) return
  const w = host.clientWidth
  const h = Math.max(host.clientHeight, 1)
  camera.aspect = w / h
  camera.updateProjectionMatrix()
  renderer.setSize(w, h)
}

// ==================== 模型加载 ====================

function loadModel(url) {
  loading.value = true
  error.value = ''
  loadTip.value = '正在加载模型...'

  const loader = new GLTFLoader()
  const draco = new DRACOLoader()
  // 解码器需随前端部署在 /draco/ 下（从 node_modules/three/examples/jsm/libs/draco 拷贝）。
  // 不放 CDN 有两个原因：国内访问不稳定；私有化部署场景根本没有外网。
  draco.setDecoderPath('/draco/')
  loader.setDRACOLoader(draco)

  loader.load(
    url,
    (gltf) => {
      if (model) {
        scene.remove(model)
        disposeObject(model)
      }
      model = gltf.scene
      scene.add(model)

      prepareMaterials(model, gltf)
      indexNodes(model)
      fitView()
      loading.value = false
    },
    (evt) => {
      if (evt.total > 0) {
        const pct = Math.round((evt.loaded / evt.total) * 100)
        loadTip.value = `正在加载模型... ${pct}%`
      }
    },
    (err) => {
      loading.value = false
      error.value = '模型加载失败：' + (err?.message || '未知错误')
      console.error('[CadViewer] 加载失败', err)
    }
  )
}

/**
 * 材质准备：让高亮只可能影响被选中的那一个零件。
 *
 * <p>为什么必须做：STEP 转出来的 GLB 常常一个 material 都不带（实测某装配体 24 个零件网格、0 个材质），
 * 此时 GLTFLoader 会把**同一个**默认材质实例挂到所有 mesh 上。而高亮是靠改 `material.emissive` 实现的，
 * 材质一共享，选中一颗螺钉就把整个装配体染成蓝色 —— 用户反馈的"整片全蓝、看不到选中的零件"就是这个。
 *
 * <p>所以先按 mesh 拆开材质引用（谁的材质被别人用过了就 clone 一份），后续 mutate 才安全。
 * 顺带把"全模型共用一个默认材质"的外观调成中性灰：默认材质 metalness=1 又没有环境贴图，
 * 渲染出来几乎是黑的，零件轮廓和蓝色高亮都没法辨认。
 */
function prepareMaterials(root, gltf) {
  const ownerByUuid = new Map()
  root.traverse((obj) => {
    if (!obj.isMesh || !obj.material) return
    const isArray = Array.isArray(obj.material)
    const mats = isArray ? obj.material : [obj.material]
    let changed = false
    const next = mats.map((m) => {
      const owner = ownerByUuid.get(m.uuid)
      if (!owner) {
        ownerByUuid.set(m.uuid, obj)
        return m
      }
      // 同一个 mesh 的多个 primitive 复用一份材质是无害的，跨 mesh 才需要拆
      if (owner === obj) return m
      changed = true
      return m.clone()
    })
    if (changed) obj.material = isArray ? next : next[0]
  })

  const declared = (gltf?.parser?.json?.materials || []).length
  if (declared) return
  root.traverse((obj) => {
    if (!obj.isMesh || !obj.material) return
    const mats = Array.isArray(obj.material) ? obj.material : [obj.material]
    mats.forEach((m) => {
      if (!m.isMeshStandardMaterial) return
      m.color.setHex(0xc9ced6)
      m.metalness = 0.15
      m.roughness = 0.55
      m.needsUpdate = true
    })
  })
}

function loadStructure(url) {
  fetch(url)
    .then((r) => (r.ok ? r.json() : Promise.reject(new Error('HTTP ' + r.status))))
    .then((data) => {
      structureNodes.value = Array.isArray(data?.nodes) ? data.nodes : []
    })
    .catch((e) => {
      console.warn('[CadViewer] 结构树加载失败', e)
      structureNodes.value = []
    })
}

/**
 * 建立「结构树 id → three 对象」索引。
 *
 * glTF 里 node 的 name 由转换器写成结构树节点的 id —— 这是两边唯一的锚点。
 * 所以转换器侧压缩时必须保留 node name（gltfpack 默认会删掉它，务必加 -kn）。
 * 找不到 name 时退回到按 gltfIndex 匹配，兼容只给了下标的旧产物。
 */
function indexNodes(root) {
  nodeIndex.clear()
  root.traverse((obj) => {
    if (obj.name) nodeIndex.set(obj.name, obj)
  })
}

/**
 * 结构树节点 id → three 对象。
 *
 * <p>首选按 id 取（GLB 节点名就是 id）；取不到时按结构节点的可读名再试一次，
 * 兼容节点名被写成零件名的产物 —— 否则树里点一行，模型毫无反应。
 */
function resolveNodeObject(nodeId) {
  const direct = nodeIndex.get(nodeId)
  if (direct) return direct
  const meta = structureNodes.value.find((n) => n.id === nodeId)
  return (meta?.name && nodeIndex.get(meta.name)) || null
}

// ==================== 视角 ====================

function fitView() {
  if (!model || !camera || !controls) return
  const box = new THREE.Box3().setFromObject(model)
  if (box.isEmpty()) return

  const size = box.getSize(new THREE.Vector3())
  const center = box.getCenter(new THREE.Vector3())
  const maxDim = Math.max(size.x, size.y, size.z) || 1

  // glTF 以米为单位，而 STEP 多是毫米 —— 换算错了模型会差 1000 倍，
  // 表现为"怎么拉都看不全"。这里按包围盒自适应，天然免疫单位问题。
  const dist = maxDim * 2.2
  camera.near = Math.max(dist / 1000, 0.0001)
  camera.far = dist * 100
  camera.position.set(center.x + dist, center.y + dist * 0.8, center.z + dist)
  camera.updateProjectionMatrix()
  controls.target.copy(center)
  controls.update()
  homePosition = camera.position.clone()
  homeTarget = controls.target.clone()
  viewTween = null
}

function resetView() {
  if (!homePosition || !controls) return
  viewTween = null
  camera.position.copy(homePosition)
  if (homeTarget) controls.target.copy(homeTarget)
  controls.update()
}

/**
 * 视角过渡：只平移注视点与相机，**不改相机到目标的距离**。
 *
 * <p>「选中零件」和「看清零件」是两件事。之前选中后按零件包围盒重设相机距离，
 * 等于强行拉到特写：一颗几毫米的螺钉会把整个视野塞满，用户立刻失去
 * "这颗螺钉装在哪儿"的上下文。这里保留当前的视距与朝向，只把画面挪到它身上。
 */
function flyTo(center, offset, duration = 320) {
  if (!controls || !camera) return
  viewTween = {
    fromTarget: controls.target.clone(),
    toTarget: center.clone(),
    fromPos: camera.position.clone(),
    toPos: center.clone().add(offset),
    start: performance.now(),
    duration
  }
}

function stepViewTween() {
  if (!viewTween || !controls || !camera) return
  const t = Math.min(1, (performance.now() - viewTween.start) / viewTween.duration)
  // easeInOutQuad：起步和收尾都不生硬，中间段仍然是匀速感
  const k = t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2
  controls.target.lerpVectors(viewTween.fromTarget, viewTween.toTarget, k)
  camera.position.lerpVectors(viewTween.fromPos, viewTween.toPos, k)
  if (t >= 1) viewTween = null
}

function cancelViewTween() {
  viewTween = null
}

// ==================== 显示开关 ====================

function toggleWireframe() {
  wireframe.value = !wireframe.value
  applyToMaterials((m) => { m.wireframe = wireframe.value })
}

function toggleTransparent() {
  transparent.value = !transparent.value
  applyToMaterials((m) => {
    m.transparent = transparent.value
    m.opacity = transparent.value ? 0.45 : 1
    m.depthWrite = !transparent.value
  })
}

/** 爆炸视图：各顶层子节点沿"自身中心 → 整体中心"的反方向外推 */
function toggleExplode() {
  if (!model) return
  exploded.value = !exploded.value
  const factor = exploded.value ? 1 : 0

  const box = new THREE.Box3().setFromObject(model)
  const center = box.getCenter(new THREE.Vector3())
  const radius = Math.max(box.getSize(new THREE.Vector3()).length(), 0.0001)

  for (const child of model.children) {
    if (!child.userData.__home) {
      child.userData.__home = child.position.clone()
    }
    const home = child.userData.__home
    if (factor === 0) {
      child.position.copy(home)
      continue
    }
    const cb = new THREE.Box3().setFromObject(child).getCenter(new THREE.Vector3())
    const dir = cb.clone().sub(center)
    if (dir.lengthSq() < 1e-12) dir.set(0, 1, 0)
    dir.normalize().multiplyScalar(radius * 0.35 * factor)
    child.position.copy(home).add(dir)
  }
}

function toggleClip() {
  clipEnabled.value = !clipEnabled.value
  applyClip()
}

function applyClip() {
  if (!model) return
  if (!clipEnabled.value) {
    applyToMaterials((m) => { m.clippingPlanes = null })
    return
  }
  const box = new THREE.Box3().setFromObject(model)
  const min = box.min.z
  const max = box.max.z
  const at = min + (max - min) * (clipRatio.value / 100)
  clipPlane = new THREE.Plane(new THREE.Vector3(0, 0, -1), at)
  applyToMaterials((m) => { m.clippingPlanes = [clipPlane] })
}

function applyToMaterials(fn) {
  if (!model) return
  model.traverse((obj) => {
    if (!obj.isMesh || !obj.material) return
    const mats = Array.isArray(obj.material) ? obj.material : [obj.material]
    for (const m of mats) {
      fn(m)
      m.needsUpdate = true
    }
  })
}

// ==================== 拾取与高亮 ====================

function onPointerDown(event) {
  if (!renderer || !camera || !model) return
  // 用户开始操作就放弃过渡动画，避免相机一边被拖拽一边被动画改
  cancelViewTween()
  const rect = renderer.domElement.getBoundingClientRect()
  pointer.x = ((event.clientX - rect.left) / rect.width) * 2 - 1
  pointer.y = -((event.clientY - rect.top) / rect.height) * 2 + 1

  raycaster.setFromCamera(pointer, camera)
  const hits = raycaster.intersectObject(model, true)
  if (!hits.length) return

  if (measuring.value) {
    addMeasurePoint(hits[0].point)
    return
  }

  // 命中的是 mesh，要往上找到"结构树登记过的那一层"（即带 id 的 node）
  const nodeId = resolveHitNodeId(hits[0].object)
  if (!nodeId) return
  highlight(nodeId)
}

/**
 * 命中的 mesh → 结构树节点 id。
 *
 * <p>不能停在"第一个有名字的祖先"上：零件由成百上千个 primitive 拼成时，
 * GLTFLoader 会把它拆成一堆子网格，名字是 `230019-01_2102` 这种「网格名 + 图元号」，
 * 停在那一层拿到的名字在结构树里根本不存在 —— 高亮会落到一个假 id 上，
 * 结构树永远联动不上。所以这里只认"名字正好是结构树 id"的那一层。
 *
 * <p>个别产物的 GLB 节点名写的是可读名而不是 id，再按结构树节点的 name 反查一次兜底。
 */
function resolveHitNodeId(obj) {
  const ids = structureIdSet.value
  let cur = obj
  while (cur) {
    if (cur.name && ids.has(cur.name)) return cur.name
    cur = cur.parent
  }
  cur = obj
  while (cur) {
    const meta = (structureNodes.value || []).find((n) => n.name && n.name === cur.name)
    if (meta) return meta.id
    cur = cur.parent
  }
  return ''
}

function highlight(nodeId) {
  restoreMaterials()
  selectedId.value = nodeId

  // 名称先取：即使这个 id 在 GLB 里找不到（转换器改名/压缩时节点被并掉），
  // 状态栏也应该如实告诉用户当前选的是哪个零件，而不是一片空白
  const meta = structureNodes.value.find((n) => n.id === nodeId)
  selectedName.value = meta ? (meta.name || meta.number || nodeId) : nodeId

  const node = resolveNodeObject(nodeId)
  if (!node) {
    console.warn('[CadViewer] 结构树节点在 GLB 中没有对应节点，无法高亮：', nodeId)
    emit('select', meta || { id: nodeId })
    return
  }

  // 这里直接改材质是安全的：prepareMaterials 已经把跨 mesh 的共享材质拆开了，
  // 否则改一个零件的 emissive 会把整棵装配体一起染蓝
  node.traverse((obj) => {
    if (!obj.isMesh || !obj.material) return
    const mats = Array.isArray(obj.material) ? obj.material : [obj.material]
    mats.forEach((m, i) => {
      const key = obj.uuid + ':' + i
      if (!originalMaterials.has(key)) originalMaterials.set(key, m.clone())
      if (m.emissive) {
        m.emissive.setHex(0x1668dc)
        m.emissiveIntensity = 0.6
      }
      m.needsUpdate = true
    })
  })
  emit('select', meta || { id: nodeId })
}

function restoreMaterials() {
  if (!model) return
  model.traverse((obj) => {
    if (!obj.isMesh || !obj.material) return
    const mats = Array.isArray(obj.material) ? obj.material : [obj.material]
    mats.forEach((m, i) => {
      const key = obj.uuid + ':' + i
      const original = originalMaterials.get(key)
      if (original && m.emissive) {
        m.emissive.copy(original.emissive)
        m.emissiveIntensity = original.emissiveIntensity
        m.needsUpdate = true
      }
    })
  })
  originalMaterials.clear()
}

/**
 * 结构树 → 模型：选中结构树节点时高亮，并把注视点移到该零件上。
 *
 * <p>只平移、不缩放：取出当前"相机 → 注视点"的向量当作偏移量（视距+朝向一起带着走），
 * 平移完画面里零件还是原来那个大小，用户能顺着上下文看清它装在什么位置。
 */
function onStructureSelect(node) {
  if (!node) return
  highlight(node.id)

  const target = resolveNodeObject(node.id)
  if (!target || !controls || !camera) return
  const box = new THREE.Box3().setFromObject(target)
  if (box.isEmpty()) return

  const center = box.getCenter(new THREE.Vector3())
  flyTo(center, camera.position.clone().sub(controls.target))
}

// ==================== 测量 ====================

function toggleMeasure() {
  measuring.value = !measuring.value
  if (!measuring.value) clearMeasure()
}

function addMeasurePoint(point) {
  if (measurePoints.value.length === 0) {
    // 重新开始量：先把上一次的读数撤掉，否则用户会拿旧数字当新距离
    measureResult.value = ''
  }
  measurePoints.value.push(point.clone())
  if (measurePoints.value.length < 2) {
    // 先给第一个点打个标记：不然用户不确定自己到底点中没有
    markMeasurePoint(measurePoints.value[0])
    return
  }

  const [a, b] = measurePoints.value
  // glTF 单位是米，工程用户看毫米更直观
  const distMm = a.distanceTo(b) * 1000
  measureResult.value = distMm >= 1000
    ? (distMm / 1000).toFixed(3) + ' m'
    : distMm.toFixed(2) + ' mm'

  drawMeasureAnnotation(a, b)
  measurePoints.value = []
}

/** 标注线/端点的粗细：随模型尺寸走，小零件上才不会粗得像根柱、大装配上又不至于看不见 */
function measureRadius() {
  if (!model) return 0.0004
  const box = new THREE.Box3().setFromObject(model)
  const size = new THREE.Vector3()
  box.getSize(size)
  return Math.max(size.length() * 0.006, 0.0002)
}

function ensureMeasureGroup() {
  if (!measureGroup) {
    measureGroup = new THREE.Group()
    scene.add(measureGroup)
  }
  return measureGroup
}

function clearMeasureGroup() {
  if (!measureGroup) return
  scene.remove(measureGroup)
  measureGroup.traverse((child) => {
    if (child.geometry) child.geometry.dispose()
    if (child.material) child.material.dispose()
  })
  measureGroup = null
}

/** 待选第二点时，在第一点处放一个小球 */
function markMeasurePoint(p) {
  clearMeasureGroup()
  const group = ensureMeasureGroup()
  const sphere = new THREE.Mesh(
    new THREE.SphereGeometry(measureRadius() * 3, 16, 16),
    measureMaterial()
  )
  sphere.position.copy(p)
  sphere.renderOrder = 1001
  group.add(sphere)
}

/**
 * 画测量标注：粗线段 + 两端小球。
 *
 * <p>原来用 `THREE.Line`：线宽在 WebGL 里恒为 1px（`linewidth` 基本被各家驱动忽略），
 * 深色模型上几乎看不出那条斜线 —— 用户反馈"测完只有一个读数，不知道从哪量到哪"。
 * 换成圆柱模拟的粗线，并把 `depthTest` 关掉，保证它永远浮在模型上面。
 */
function drawMeasureAnnotation(a, b) {
  clearMeasureGroup()
  const dir = new THREE.Vector3().subVectors(b, a)
  const length = dir.length()
  if (length <= 1e-9) return

  const group = ensureMeasureGroup()
  const material = measureMaterial()
  const radius = measureRadius()

  const segment = new THREE.Mesh(new THREE.CylinderGeometry(radius, radius, length, 12), material)
  segment.position.copy(a).add(b).multiplyScalar(0.5)
  segment.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), dir.clone().normalize())
  segment.renderOrder = 1000
  group.add(segment)

  const sphereGeo = new THREE.SphereGeometry(radius * 3, 16, 16)
  for (const p of [a, b]) {
    const dot = new THREE.Mesh(sphereGeo, material)
    dot.position.copy(p)
    dot.renderOrder = 1001
    group.add(dot)
  }
}

function measureMaterial() {
  return new THREE.MeshBasicMaterial({ color: 0xff3b30, depthTest: false, transparent: true, opacity: 0.95 })
}

function clearMeasure() {
  measurePoints.value = []
  measureResult.value = ''
  clearMeasureGroup()
}

// ==================== 清理 ====================

function disposeObject(obj) {
  obj.traverse((child) => {
    if (child.geometry) child.geometry.dispose()
    if (child.material) {
      const mats = Array.isArray(child.material) ? child.material : [child.material]
      mats.forEach((m) => m.dispose())
    }
  })
}

function dispose() {
  if (animationId) cancelAnimationFrame(animationId)
  if (resizeObserver) resizeObserver.disconnect()
  clearMeasureGroup()
  if (renderer) {
    renderer.domElement.removeEventListener('pointerdown', onPointerDown)
    renderer.domElement.removeEventListener('wheel', cancelViewTween)
    renderer.domElement.remove()
    renderer.dispose()
  }
  if (model) disposeObject(model)
  if (axisScene) {
    // 罗盘的材质都带贴图，dispose 材质不会释放 texture，得单独收
    axisScene.traverse((child) => {
      if (child.material) {
        const mats = Array.isArray(child.material) ? child.material : [child.material]
        mats.forEach((m) => {
          if (m.map) m.map.dispose()
          m.dispose()
        })
      }
      if (child.geometry) child.geometry.dispose()
    })
    axisScene = null
    axisCamera = null
  }
  nodeIndex.clear()
  originalMaterials.clear()
}

function formatNumber(n) {
  if (n == null) return '-'
  if (n >= 1000000) return (n / 1000000).toFixed(1) + ' M'
  if (n >= 1000) return (n / 1000).toFixed(1) + ' K'
  return String(n)
}
</script>

<style scoped>
.cad-viewer {
  position: relative;
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  min-height: 420px;
  background: #fff;
}
.cv-label { font-size: 12px; color: #666; }
.cv-body {
  position: relative;
  flex: 1;
  display: flex;
  min-height: 0;
}
/* 画布的定位上下文：右侧按钮列 / 左下剖切滑块 / 左上测量提示都浮在它上面 */
.cv-canvas-wrap {
  position: relative;
  flex: 1;
  min-width: 0;
  display: flex;
}
.cv-canvas {
  flex: 1;
  min-width: 0;
  min-height: 360px;
}
/* 操作按钮列：右下浮层，垂直排列。半透明白底 + 细边框，
   压在模型上也不至于遮住太多视野 */
.cv-toolbar-float {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 5;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 6px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08);
}
.cv-tool {
  width: 78px;
}
.cv-clip-float,
.cv-measure-float {
  position: absolute;
  left: 12px;
  z-index: 5;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  font-size: 12px;
  color: #666;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08);
}
/* 剖切滑块居底：左下角留给 XYZ 罗盘，放同一边会互相压住 */
.cv-clip-float {
  bottom: 12px;
  left: 50%;
  transform: translateX(-50%);
  width: 260px;
}
.cv-measure-float {
  top: 12px;
}
/* 测量中：小圆点提示"正在等第二次点击" */
.cv-measure-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #fa541c;
  box-shadow: 0 0 0 3px rgba(250, 84, 28, 0.18);
  flex-shrink: 0;
}
/* 测量结果卡片：顶部居中、大字号读数 —— 读数要一眼能看到，不能沉在底部状态栏里 */
.cv-measure-result {
  position: absolute;
  top: 12px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 6;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 10px 8px 16px;
  background: rgba(255, 255, 255, 0.97);
  border: 1px solid #ffccc7;
  border-radius: 10px;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.12);
}
.cv-measure-result-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.cv-measure-caption {
  font-size: 12px;
  color: #8c8c8c;
  line-height: 1;
}
.cv-measure-value {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 20px;
  font-weight: 700;
  color: #cf1322;
  line-height: 1.15;
  font-variant-numeric: tabular-nums;
}
.cv-canvas :deep(canvas) { display: block; }
/* 结构树在左：分隔线要在右边（border-left 会把线画在画布与树之间那一侧之外） */
.cv-structure {
  width: 260px;
  flex-shrink: 0;
  border-right: 1px solid #f0f0f0;
  overflow: auto;
  background: #fff;
}
.cv-mask {
  position: absolute;
  /* 顶部工具栏已经取消（改为画布右侧浮层），遮罩不再需要在顶部留 56px */
  inset: 0 0 32px 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.94);
}
.cv-footer {
  padding: 6px 12px;
  border-top: 1px solid #f0f0f0;
  font-size: 12px;
  color: #666;
  background: #fafafa;
}
.cv-selected { color: #1668dc; }
</style>
