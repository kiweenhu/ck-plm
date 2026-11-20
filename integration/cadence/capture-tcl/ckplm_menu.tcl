# =============================================================================
# CK-PLM 集成 —— OrCAD Capture Tcl 插件
#
# 功能：在 Capture 菜单栏注册 "CK-PLM" 菜单
#       检出 / 检入 / 取消检出 / 上传设计 / BOM 同步 / 下载设计
#
# 安装：Capture → Tools → Tcl/Tk Scripts → Run Script... 选择本文件；
#       或放入 %CDSHOME%\capture\tclscripts\ckplm_menu.tcl 随 Capture 启动自动加载。
#       （自动加载方式建议先手动跑通一次再启用）
#
# 前置：本机已配置好 ckplm_cli.py（python ckplm_cli.py config --server ... --username ...）
#
# 注意：不同 Capture 版本的 Tcl 菜单 API 略有差异，适配点集中在
#       ckplm::register_menu 与 ckplm::current_design 两个过程，
#       若菜单没出来或取不到设计名，改这两处即可。
# =============================================================================

package require Tk

namespace eval ckplm {
    # ==================== 配置 ====================
    variable cliPath    "D:/AI-Coding/CK-PLM/integration/cadence/ckplm_cli.py"
    variable pythonBin  "python"
    variable menuName   "CK-PLM"

    variable actions {
        checkout  "检出(&C)"
        checkin   "检入(&I)"
        undo      "取消检出(&U)"
        upload    "上传设计(&D)..."
        bomsync   "BOM 同步(&B)..."
        download  "下载最新设计(&L)..."
    }
}

# =============================================================================
# 适配层 1：菜单注册
# =============================================================================
proc ckplm::register_menu {} {
    variable menuName
    variable actions

    # 先清掉可能存在的旧菜单（脚本重跑时不叠加）
    catch { RemoveMenu $menuName }

    RegisterAction "CKPLM.Checkout"  "ckplm::do_checkout"  "" "" ""
    RegisterAction "CKPLM.Checkin"   "ckplm::do_checkin"   "" "" ""
    RegisterAction "CKPLM.Undo"      "ckplm::do_undo"      "" "" ""
    RegisterAction "CKPLM.Upload"    "ckplm::do_upload"    "" "" ""
    RegisterAction "CKPLM.BomSync"   "ckplm::do_bomsync"   "" "" ""
    RegisterAction "CKPLM.Download"  "ckplm::do_download"  "" "" ""

    AddMenu $menuName
    AddMenuItem $menuName [dict get $actions checkout]  "CKPLM.Checkout"
    AddMenuItem $menuName [dict get $actions checkin]   "CKPLM.Checkin"
    AddMenuItem $menuName [dict get $actions undo]      "CKPLM.Undo"
    AddSeparator $menuName
    AddMenuItem $menuName [dict get $actions upload]    "CKPLM.Upload"
    AddMenuItem $menuName [dict get $actions download]  "CKPLM.Download"
    AddSeparator $menuName
    AddMenuItem $menuName [dict get $actions bomsync]   "CKPLM.BomSync"
}

# =============================================================================
# 适配层 2：当前设计信息
# =============================================================================
proc ckplm::current_design {} {
    # 不同版本取设计名的候选命令，从上到下尝试
    foreach cmd {GetProjectName capGetProjectName GetSelectedProjectName} {
        if {![catch {uplevel #0 $cmd} name] && $name ne ""} {
            return $name
        }
    }
    # 兜底：弹窗让工程师输入原理图编号（PLM 侧编号）
    return [tk_input "输入原理图编号" "PLM 原理图编号（如 SCH-2026-0001）："]
}

proc ckplm::tk_input {title prompt} {
    set result ""
    toplevel .ckplminput
    wm title .ckplminput $title
    label .ckplminput.l -text $prompt
    entry .ckplminput.e -textvariable ckplm::inputval -width 40
    button .ckplminput.ok -text "确定" -command {
        set ckplm::result $ckplm::inputval
        destroy .ckplminput
    }
    pack .ckplminput.l .ckplminput.e .ckplminput.ok -pady 4 -fill x
    grab .ckplminput
    tkwait window .ckplminput
    return $ckplm::result
}

# =============================================================================
# CLI 调用
# =============================================================================
proc ckplm::run_cli {args} {
    variable cliPath
    variable pythonBin
    set cmd [auto_execok $pythonBin]
    if {$cmd eq ""} { ckplm::msg error "找不到 python，请检查 PATH 或修改 ckplm::pythonBin" ; return "" }
    if {[catch {exec $cmd $cliPath {*}$args} output]} {
        ckplm::msg error "CLI 执行失败：\n$output"
        return ""
    }
    return $output
}

proc ckplm::msg {level text} {
    tk_messageBox -type ok -icon $level -message $text -title "CK-PLM"
}

# =============================================================================
# 菜单动作
# =============================================================================
proc ckplm::do_checkout {} {
    set sch [ckplm::current_design]
    if {$sch eq ""} { return }
    set comment [ckplm::tk_input "检出" "检出注释（可空）："]
    set output [ckplm::run_cli checkout --schematic $sch --comment $comment]
    if {$output ne ""} { ckplm::msg info "检出成功：$sch" }
}

proc ckplm::do_checkin {} {
    set sch [ckplm::current_design]
    if {$sch eq ""} { return }
    set output [ckplm::run_cli checkin --schematic $sch]
    if {$output ne ""} { ckplm::msg info "检入成功：$sch" }
}

proc ckplm::do_undo {} {
    set sch [ckplm::current_design]
    if {$sch eq ""} { return }
    set output [ckplm::run_cli undo --schematic $sch]
    if {$output ne ""} { ckplm::msg info "已取消检出：$sch" }
}

proc ckplm::do_upload {} {
    set sch [ckplm::current_design]
    if {$sch eq ""} { return }
    set file [tk_getOpenFile -title "选择 .DSN 设计文件" -filetypes {
        {{"OrCAD 设计" ".dsn" ".DSN"}} {{"所有文件" *}}
    }]
    if {$file eq ""} { return }
    set output [ckplm::run_cli upload-dsn --schematic $sch --file $file]
    if {$output ne ""} { ckplm::msg info "设计已上传：$sch\n记得执行检入使文件生效" }
}

proc ckplm::do_download {} {
    set sch [ckplm::current_design]
    if {$sch eq ""} { return }
    set output [ckplm::run_cli download --schematic $sch]
    if {$output ne ""} { ckplm::msg info "下载完成（见 CLI 输出目录）" }
}

proc ckplm::do_bomsync {} {
    set sch [ckplm::current_design]
    if {$sch eq ""} { return }
    set file [tk_getOpenFile -title "选择 Capture 导出的 BOM 文件" -filetypes {
        {{"BOM 文件" ".bom" ".csv" ".bsv" ".txt"}} {{"所有文件" *}}
    }]
    if {$file eq ""} { return }
    set output [ckplm::run_cli bom-sync --schematic $sch --bom $file]
    if {$output ne ""} {
        # 差异摘要在 CLI 的 JSON 输出里，这里弹窗提示已同步
        ckplm::msg info "BOM 已同步：$sch\n新增/移除/变化明细见 CK-PLM 页面"
    }
}

# =============================================================================
# 注册菜单
# =============================================================================
ckplm::register_menu
