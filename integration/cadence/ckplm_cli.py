#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
CK-PLM 桥接 CLI —— OrCAD Capture 与 CK-PLM 之间的本地桥。

设计要点：
- 仅用 Python 标准库（urllib），不要求工程师机器装任何 pip 包
- token 缓存在 ~/.ckplm/token.json，过期自动重登
- BOM 输入是 Capture 自带 "Tools → Bills of Materials" 导出的 CSV/BSV 文件，
  列映射可用 --map 显式指定，缺省自动按表头识别

典型用法：
  python ckplm_cli.py login
  python ckplm_cli.py checkout --schematic SCH-2026-0001
  python ckplm_cli.py upload-dsn --schematic SCH-2026-0001 --file D:\\proj\\design.dsn
  python ckplm_cli.py bom-sync --schematic SCH-2026-0001 --bom D:\\proj\\design.bom
  python ckplm_cli.py checkin --schematic SCH-2026-0001
"""

import argparse
import csv
import json
import os
import sys
import tempfile
import urllib.error
import urllib.request
import uuid

CONFIG_DIR = os.path.join(os.path.expanduser("~"), ".ckplm")
CONFIG_FILE = os.path.join(CONFIG_DIR, "cli.json")
TOKEN_FILE = os.path.join(CONFIG_DIR, "token.json")

# Capture 导出 BOM 的常见表头（大小写/空格不敏感，按子串匹配）
HEADER_ALIASES = {
    "reference": ["reference designator", "refdes", "reference", "位号", "器件编号"],
    "quantity": ["quantity", "qty", "数量"],
    "value": ["value", "值", "型号"],
    "partnumber": ["part number", "partnumber", "part_no", "元件编号", "器件编码", "物料编码"],
    "footprint": ["pcb footprint", "footprint", "封装"],
}


def load_config():
    if not os.path.exists(CONFIG_FILE):
        return {}
    with open(CONFIG_FILE, "r", encoding="utf-8") as f:
        return json.load(f)


def save_config(cfg):
    os.makedirs(CONFIG_DIR, exist_ok=True)
    with open(CONFIG_FILE, "w", encoding="utf-8") as f:
        json.dump(cfg, f, ensure_ascii=False, indent=2)


def http_json(method, url, token=None, body=None, timeout=60):
    req = urllib.request.Request(url, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8")
    try:
        with urllib.request.urlopen(req, data=data, timeout=timeout) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        try:
            return json.loads(e.read().decode("utf-8"))
        except Exception:
            return {"code": e.code, "message": "HTTP " + str(e.code)}


def http_upload(url, token, filepath, extra_fields=None, timeout=300):
    """multipart/form-data 上传文件（标准库实现）。"""
    boundary = "----ckplm" + uuid.uuid4().hex
    filename = os.path.basename(filepath)
    with open(filepath, "rb") as f:
        filedata = f.read()

    parts = []
    for k, v in (extra_fields or {}).items():
        parts.append(
            ("--" + boundary + "\r\n" +
             'Content-Disposition: form-data; name="%s"\r\n\r\n%s\r\n' % (k, v)).encode("utf-8"))
    parts.append(
        ("--" + boundary + "\r\n" +
         'Content-Disposition: form-data; name="file"; filename="%s"\r\n' % filename +
         "Content-Type: application/octet-stream\r\n\r\n").encode("utf-8"))
    parts.append(filedata)
    parts.append(("\r\n--" + boundary + "--\r\n").encode("utf-8"))

    req = urllib.request.Request(url, method="POST")
    req.add_header("Content-Type", "multipart/form-data; boundary=" + boundary)
    req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, data=b"".join(parts), timeout=timeout) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        try:
            return json.loads(e.read().decode("utf-8"))
        except Exception:
            return {"code": e.code, "message": "HTTP " + str(e.code)}


def get_token(args, force=False):
    cfg = load_config()
    if not cfg.get("server"):
        die("未配置服务器地址，先执行: python ckplm_cli.py config --server http://host:port")

    if not force and os.path.exists(TOKEN_FILE):
        with open(TOKEN_FILE, "r", encoding="utf-8") as f:
            tok = json.load(f)
        if tok.get("token"):
            return tok["token"]

    resp = http_json("POST", cfg["server"].rstrip("/") + "/api/auth/login",
                     body={"username": cfg.get("username"), "password": cfg.get("password")})
    token = (resp.get("data") or {}).get("token")
    if not token:
        die("登录失败: " + json.dumps(resp, ensure_ascii=False))
    os.makedirs(CONFIG_DIR, exist_ok=True)
    with open(TOKEN_FILE, "w", encoding="utf-8") as f:
        json.dump({"token": token}, f)
    return token


def api(args, method, path, body=None):
    token = get_token(args)
    cfg = load_config()
    return http_json(method, cfg["server"].rstrip("/") + path, token=token, body=body)


def die(msg, code=1):
    print("[错误] " + msg, file=sys.stderr)
    sys.exit(code)


def out(resp):
    print(json.dumps(resp, ensure_ascii=False, indent=2))


# ==================== BOM 解析 ====================

def match_header(header, kind):
    h = header.strip().lower().replace("_", " ").replace("-", " ")
    for alias in HEADER_ALIASES[kind]:
        if alias in h:
            return True
    return False


def parse_bom(path, colmap=None):
    """解析 Capture 导出的 BOM 文件（CSV/BSV/TXT，自动嗅探分隔符）。"""
    sniff = open(path, "r", encoding="utf-8-sig", errors="replace").readline()
    delim = ";" if sniff.count(";") > sniff.count(",") else ","
    rows = list(csv.reader(open(path, "r", encoding="utf-8-sig", errors="replace"), delimiter=delim))
    if not rows:
        die("BOM 文件为空: " + path)

    header = [h.strip() for h in rows[0]]
    idx = {}
    for kind, keys in (colmap or {}).items():
        idx[kind] = keys
    if not idx:
        for kind in HEADER_ALIASES:
            for i, h in enumerate(header):
                if match_header(h, kind):
                    idx[kind] = i
                    break

    if "reference" not in idx:
        die("无法识别位号列，请用 --map 指定，表头: " + ", ".join(header))

    items = []
    for row in rows[1:]:
        if not row or not row[0].strip():
            continue
        ref = row[idx["reference"]].strip() if idx.get("reference") is not None and idx["reference"] < len(row) else ""
        if not ref:
            continue
        def grab(kind):
            i = idx.get(kind)
            if i is None or i >= len(row):
                return ""
            return row[i].strip()
        qty = grab("quantity")
        items.append({
            "reference": ref,
            "quantity": int(float(qty)) if qty.replace(".", "", 1).isdigit() else 1,
            "value": grab("value"),
            "partNumber": grab("partnumber"),
            "footprint": grab("footprint"),
        })
    return items


# ==================== 命令 ====================

def cmd_config(a):
    cfg = load_config()
    if a.server: cfg["server"] = a.server
    if a.username: cfg["username"] = a.username
    if a.password is not None: cfg["password"] = a.password
    save_config(cfg)
    print("已保存配置:", json.dumps({k: ("***" if k == "password" else v) for k, v in cfg.items()},
                                    ensure_ascii=False))


def cmd_login(a):
    token = get_token(a, force=True)
    print("登录成功，token 已缓存到 " + TOKEN_FILE)


def cmd_whoami(a):
    out(api(a, "GET", "/api/ecad/projects"))


def cmd_create(a):
    body = {"name": a.name, "projectOid": a.project}
    if a.number:
        body["number"] = a.number
    qs = ""
    if a.project:
        qs = "?projectId=" + a.project
    out(api(a, "POST", "/api/ecad/schematics" + qs, body=body))


def cmd_checkout(a):
    out(api(a, "POST", "/api/ecad/schematics/%s/checkout" % a.schematic,
            body={"comment": a.comment or ""}))


def cmd_checkin(a):
    out(api(a, "POST", "/api/ecad/schematics/%s/checkin" % a.schematic, body={}))


def cmd_undo(a):
    out(api(a, "POST", "/api/ecad/schematics/%s/undo-checkout" % a.schematic, body={}))


def cmd_upload_dsn(a):
    token = get_token(a)
    cfg = load_config()
    fields = {}
    if a.sheets: fields["sheetCount"] = str(a.sheets)
    if a.components: fields["componentCount"] = str(a.components)
    out(http_upload(cfg["server"].rstrip("/") + "/api/ecad/schematics/%s/dsn" % a.schematic,
                    token, a.file, fields))


def cmd_download(a):
    """下载最新迭代的 .DSN（经统一文件接口）。"""
    resp = api(a, "GET", "/api/ecad/schematics/%s/iterations/latest" % a.schematic)
    ckfile = ((resp.get("data") or {}).get("ckfileOid"))
    if not ckfile:
        die("最新迭代没有设计文件（先 checkout + upload-dsn）")
    token = get_token(a)
    cfg = load_config()
    url = cfg["server"].rstrip("/") + "/api/ckfiles/%s/download" % ckfile
    req = urllib.request.Request(url)
    req.add_header("Authorization", "Bearer " + token)
    dest = a.file or os.path.join(tempfile.gettempdir(), "ckplm_" + a.schematic + ".dsn")
    with urllib.request.urlopen(req, timeout=300) as r, open(dest, "wb") as f:
        f.write(r.read())
    print("已下载到 " + dest)


def cmd_bom_sync(a):
    items = parse_bom(a.bom, a.map)
    print("解析到 %d 行 BOM，同步中..." % len(items))
    out(api(a, "POST", "/api/ecad/schematics/%s/bom-sync" % a.schematic, body=items))


def cmd_instances(a):
    out(api(a, "GET", "/api/ecad/schematics/%s/instances" % a.schematic))


def cmd_iterations(a):
    out(api(a, "GET", "/api/ecad/schematics/%s/iterations" % a.schematic))


def main():
    p = argparse.ArgumentParser(description="CK-PLM 桥接 CLI（OrCAD Capture 集成）")
    sub = p.add_subparsers(dest="cmd", required=True)

    sp = sub.add_parser("config", help="配置服务器与账号")
    sp.add_argument("--server"); sp.add_argument("--username"); sp.add_argument("--password")
    sp.set_defaults(fn=cmd_config)

    for name, helptext in [("login", "登录并缓存 token"), ("whoami", "连通性检查")]:
        sp = sub.add_parser(name, help=helptext); sp.set_defaults(fn={"login": cmd_login, "whoami": cmd_whoami}[name])

    sp = sub.add_parser("create", help="创建原理图对象")
    sp.add_argument("--name", required=True)
    sp.add_argument("--project", help="设计项目 oid")
    sp.add_argument("--number", help="编号（缺省自动生成）")
    sp.set_defaults(fn=cmd_create)

    for name, fn, helptext in [
        ("checkout", cmd_checkout, "检出"), ("checkin", cmd_checkin, "检入"),
        ("undo", cmd_undo, "取消检出"), ("download", cmd_download, "下载最新 .DSN"),
        ("instances", cmd_instances, "查看设计实例"), ("iterations", cmd_iterations, "版本历史"),
    ]:
        sp = sub.add_parser(name, help=helptext)
        sp.add_argument("--schematic", required=True, help="原理图编号或 oid")
        if name == "download":
            sp.add_argument("--file", help="目标路径")
        if name == "checkout":
            sp.add_argument("--comment", help="检出注释")
        sp.set_defaults(fn=fn)

    sp = sub.add_parser("upload-dsn", help="上传 .DSN 到检出的迭代")
    sp.add_argument("--schematic", required=True)
    sp.add_argument("--file", required=True)
    sp.add_argument("--sheets", type=int, help="原理图页数")
    sp.add_argument("--components", type=int, help="器件总数")
    sp.set_defaults(fn=cmd_upload_dsn)

    sp = sub.add_parser("bom-sync", help="同步 Capture 导出的 BOM（CSV/BSV）")
    sp.add_argument("--schematic", required=True)
    sp.add_argument("--bom", required=True, help="Capture 导出的 BOM 文件路径")
    sp.add_argument("--map", help="列映射，如 reference=0,value=2,quantity=3,partnumber=4,footprint=5")
    sp.set_defaults(fn=cmd_bom_sync)

    args = p.parse_args()
    if getattr(args, "map", None):
        parsed = {}
        for pair in args.map.split(","):
            k, _, v = pair.partition("=")
            parsed[k.strip()] = int(v)
        args.map = parsed
    args.fn(args)


if __name__ == "__main__":
    main()
