/**
 * MCAD / ECAD 工具清单
 *
 * 供「MCAD工具」「ECAD工具」两个业务组件（cad-tool-select）共用，
 * 同时被 RenderFields（运行期渲染）与 widgets/renderer.js（设计态预览）引用，避免清单两处维护。
 *
 * 约定：
 *   value —— 稳定短码（建议落库值，避免产品改名导致历史数据失配）
 *   label —— 展示名（厂商 + 产品）
 *
 * 如需增删工具，只改本文件即可，组件与预览自动生效。
 */

/** 机械 CAD（MCAD）：三维建模 / 二维机械制图 */
export const MCAD_TOOLS = [
  { label: 'PTC Creo',            value: 'CREO' },
  { label: 'Siemens NX',          value: 'NX' },
  { label: 'CATIA（达索）',        value: 'CATIA' },
  { label: 'SOLIDWORKS（达索）',   value: 'SOLIDWORKS' },
  { label: 'Autodesk Inventor',   value: 'INVENTOR' },
  { label: 'Solid Edge（西门子）', value: 'SOLID_EDGE' },
  { label: 'AutoCAD',             value: 'AUTOCAD' },
  { label: 'Fusion 360',          value: 'FUSION360' },
  { label: 'Rhino 犀牛',          value: 'RHINO' },
  { label: 'FreeCAD',             value: 'FREECAD' },
  { label: 'BricsCAD',            value: 'BRICSCAD' },
  { label: '中望CAD（ZWCAD）',     value: 'ZWCAD' },
  { label: '浩辰CAD（GstarCAD）',  value: 'GSTARCAD' },
  { label: 'CAXA 电子图板',        value: 'CAXA' }
]

/** 电子 CAD（ECAD）：原理图 / PCB 设计（含常用仿真工具） */
export const ECAD_TOOLS = [
  { label: 'Altium Designer',          value: 'ALTIUM' },
  { label: 'Cadence Allegro',          value: 'ALLEGRO' },
  { label: 'Cadence OrCAD',            value: 'ORCAD' },
  { label: 'Siemens Xpedition',        value: 'XPEDITION' },
  { label: 'Siemens PADS',             value: 'PADS' },
  { label: 'Zuken CR-8000',            value: 'CR8000' },
  { label: 'KiCad',                    value: 'KICAD' },
  { label: 'Autodesk EAGLE',           value: 'EAGLE' },
  { label: 'Fusion 360 Electronics',   value: 'FUSION360_ECAD' },
  { label: '立创EDA（EasyEDA）',        value: 'EASYEDA' },
  { label: 'Proteus',                  value: 'PROTEUS' },
  { label: 'DipTrace',                 value: 'DIPTRACE' },
  { label: 'Target 3001',              value: 'TARGET3001' },
  { label: 'LTspice',                  value: 'LTSPICE' }
]

/** kind → 选项清单 */
export const CAD_TOOL_OPTIONS = {
  MCAD: MCAD_TOOLS,
  ECAD: ECAD_TOOLS
}

/** kind → 默认 placeholder */
export const CAD_TOOL_PLACEHOLDER = {
  MCAD: '请选择 MCAD 工具',
  ECAD: '请选择 ECAD 工具'
}
