#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SCX Nginx 可视化配置面板（仅管理 /etc/nginx/conf.d/scx.conf）
不接管系统 Nginx，不装宝塔。

用法（服务器 root）：
  python3 /opt/smart-converge-x/scripts/deploy/scx-nginx-panel.py
  # 或 systemd：systemctl start scx-nginx-panel

默认：http://127.0.0.1:9088/  （建议 SSH 隧道访问，勿裸奔公网）
  ssh -N -L 9088:127.0.0.1:9088 root@42.81.102.181
  浏览器打开 http://127.0.0.1:9088/

环境变量：
  SCX_NGINX_PANEL_HOST=127.0.0.1
  SCX_NGINX_PANEL_PORT=9088
  SCX_NGINX_PANEL_TOKEN=可选访问令牌（设置后请求需 ?token= 或 Header X-SCX-Token）
  SCX_NGINX_CONF=/etc/nginx/conf.d/scx.conf
  SCX_NGINX_TEMPLATE=/opt/smart-converge-x/scripts/deploy/nginx-scx.conf
"""
from __future__ import annotations

import html
import json
import os
import re
import subprocess
import traceback
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

HOST = os.environ.get("SCX_NGINX_PANEL_HOST", "127.0.0.1")
PORT = int(os.environ.get("SCX_NGINX_PANEL_PORT", "9088"))
TOKEN = os.environ.get("SCX_NGINX_PANEL_TOKEN", "").strip()
CONF_PATH = Path(os.environ.get("SCX_NGINX_CONF", "/etc/nginx/conf.d/scx.conf"))
TEMPLATE_PATH = Path(
    os.environ.get(
        "SCX_NGINX_TEMPLATE",
        "/opt/smart-converge-x/scripts/deploy/nginx-scx.conf",
    )
)
BACKUP_DIR = Path(os.environ.get("SCX_NGINX_BACKUP_DIR", "/etc/nginx/conf.d"))


def run(cmd: list[str], timeout: int = 30) -> tuple[int, str, str]:
    p = subprocess.run(
        cmd,
        capture_output=True,
        text=True,
        timeout=timeout,
    )
    return p.returncode, p.stdout or "", p.stderr or ""


def parse_conf(text: str) -> dict:
    def m1(pat: str, default: str = "") -> str:
        m = re.search(pat, text, re.M)
        return m.group(1).strip() if m else default

    listen = m1(r"^\s*listen\s+(\S+);", "80")
    server_name = m1(r"^\s*server_name\s+([^;]+);", "_")
    root = m1(r"^\s*root\s+([^;]+);", "/var/www/scx")
    body = m1(r"^\s*client_max_body_size\s+([^;]+);", "100m")

    # api proxy_pass
    api_pp = m1(
        r"location\s+/api/\s*\{[^}]*?proxy_pass\s+([^;]+);",
        "http://127.0.0.1:9999/",
    )
    # normalize multi-line
    if not api_pp:
        m = re.search(
            r"location\s+/api/\s*\{([\s\S]*?)\n\s*\}",
            text,
        )
        if m:
            api_pp = m1(r"proxy_pass\s+([^;]+);", "http://127.0.0.1:9999/", m.group(1)) if False else (
                re.search(r"proxy_pass\s+([^;]+);", m.group(1)).group(1).strip()
                if re.search(r"proxy_pass\s+([^;]+);", m.group(1))
                else "http://127.0.0.1:9999/"
            )

    enable_boot = "1" if re.search(r"location\s+/boot/\s*\{", text) else "0"
    enable_ws = "1" if re.search(r"location\s+/boot/ws\s*\{", text) else "0"

    # timeouts from /api/ block if present
    api_block = ""
    m = re.search(r"location\s+/api/\s*\{([\s\S]*?)\n\s*\}", text)
    if m:
        api_block = m.group(1)
    connect = re.search(r"proxy_connect_timeout\s+([^;]+);", api_block or text)
    send = re.search(r"proxy_send_timeout\s+([^;]+);", api_block or text)
    read = re.search(r"proxy_read_timeout\s+([^;]+);", api_block or text)

    # infer mode
    mode = "strip_api"  # proxy_pass http://host:port/
    if api_pp.rstrip("/").endswith("/boot") or api_pp.endswith("/boot/"):
        mode = "strip_api_to_boot"

    backend = "127.0.0.1:9999"
    bm = re.search(r"https?://([^/\s]+)", api_pp)
    if bm:
        backend = bm.group(1)

    return {
        "listen": listen.replace("default_server", "").strip() or "80",
        "server_name": server_name,
        "root": root,
        "client_max_body_size": body,
        "backend": backend,
        "api_mode": mode,
        "enable_boot": enable_boot,
        "enable_ws": enable_ws,
        "proxy_connect_timeout": (connect.group(1) if connect else "60s"),
        "proxy_send_timeout": (send.group(1) if send else "300s"),
        "proxy_read_timeout": (read.group(1) if read else "300s"),
        "raw": text,
    }


def render_conf(f: dict) -> str:
    listen = f.get("listen") or "80"
    server_name = f.get("server_name") or "_"
    root = f.get("root") or "/var/www/scx"
    body = f.get("client_max_body_size") or "100m"
    backend = f.get("backend") or "127.0.0.1:9999"
    mode = f.get("api_mode") or "strip_api"
    enable_boot = f.get("enable_boot") == "1"
    enable_ws = f.get("enable_ws") == "1"
    t_conn = f.get("proxy_connect_timeout") or "60s"
    t_send = f.get("proxy_send_timeout") or "300s"
    t_read = f.get("proxy_read_timeout") or "300s"

    if mode == "strip_api_to_boot":
        api_pass = f"http://{backend}/boot/"
        api_comment = (
            "  # /api/xxx → /boot/xxx （旧模式；若前端已发 /api/boot/xxx 会变成 /boot/boot）\n"
        )
    else:
        api_pass = f"http://{backend}/"
        api_comment = (
            "  # /api/boot/xxx → /boot/xxx（推荐，与当前前端 VITE_OPEN_BOOT 一致）\n"
        )

    boot_block = ""
    if enable_boot:
        boot_block = f"""
    location /boot/ {{
        location ~* ^/boot/actuator {{
            return 403;
        }}
        proxy_pass http://{backend}/boot/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header REMOTE-HOST $remote_addr;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_connect_timeout {t_conn};
        proxy_send_timeout {t_send};
        proxy_read_timeout {t_read};
        client_max_body_size {body};
    }}
"""

    ws_block = ""
    if enable_ws:
        ws_block = f"""
    location /boot/ws {{
        proxy_pass http://{backend}/boot/ws;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "Upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 300s;
        proxy_read_timeout 300s;
        proxy_send_timeout 300s;
    }}
"""

    return f"""# SCX Nginx — generated by scx-nginx-panel {datetime.now().isoformat(timespec="seconds")}
# 勿手改后被面板覆盖时请先备份。路径约定见 scripts/deploy/README.md

server {{
    listen {listen};
    server_name {server_name};

    root {root};
    index index.html;

    client_max_body_size {body};

    add_header X-Content-Type-Options "nosniff" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;
    add_header X-Frame-Options "SAMEORIGIN" always;

    gzip on;
    gzip_vary on;
    gzip_comp_level 6;
    gzip_min_length 1024;
    gzip_proxied any;
    gzip_types
        text/plain text/css text/xml text/javascript
        application/json application/javascript application/xml
        application/rss+xml application/atom+xml image/svg+xml;
    gzip_disable "MSIE [1-6]\\.";

    location ~* /actuator {{
        return 403;
    }}
    location ^~ /config/ {{
        return 404;
    }}
    location ^~ /logs/ {{
        return 404;
    }}
    location ~ /\\. {{
        deny all;
    }}
    location ~* \\.(htaccess|htpasswd|ini|log|sh|sql|php|py|bak|swp|env)$ {{
        deny all;
    }}

    # 管理后台 API
{api_comment}    location /api/ {{
        proxy_pass {api_pass};
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header REMOTE-HOST $remote_addr;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_connect_timeout {t_conn};
        proxy_send_timeout {t_send};
        proxy_read_timeout {t_read};
        client_max_body_size {body};
    }}
{ws_block}{boot_block}
    location = /index.html {{
        add_header Cache-Control "no-cache, no-store, must-revalidate" always;
        add_header Pragma "no-cache" always;
        try_files $uri =404;
    }}
    location = /_app.config.js {{
        add_header Cache-Control "no-cache, no-store, must-revalidate" always;
        add_header Pragma "no-cache" always;
        default_type application/javascript;
        try_files $uri =404;
    }}

    location ^~ /css/ {{
        expires 30d;
        add_header Cache-Control "public, max-age=2592000, immutable";
        access_log off;
        try_files $uri =404;
    }}
    location ^~ /js/ {{
        expires 30d;
        add_header Cache-Control "public, max-age=2592000, immutable";
        access_log off;
        try_files $uri =404;
    }}
    location ^~ /jse/ {{
        expires 30d;
        add_header Cache-Control "public, max-age=2592000, immutable";
        access_log off;
        try_files $uri =404;
    }}
    location ^~ /static/ {{
        expires 30d;
        add_header Cache-Control "public, max-age=2592000, immutable";
        access_log off;
        try_files $uri =404;
    }}

    location ~* \\.(?:js|mjs|css|map)$ {{
        expires 7d;
        access_log off;
        add_header Cache-Control "public, no-transform";
        try_files $uri =404;
    }}
    location ~* \\.(?:gif|jpg|jpeg|png|bmp|webp|ico|svg)$ {{
        expires 30d;
        access_log off;
        add_header Cache-Control "public, no-transform";
        try_files $uri =404;
    }}
    location ~* \\.(?:woff|woff2|ttf|eot)$ {{
        expires 365d;
        access_log off;
        add_header Cache-Control "public, immutable";
        try_files $uri =404;
    }}

    location / {{
        add_header Cache-Control "no-cache" always;
        try_files $uri $uri/ /index.html;
    }}
}}
"""


def nginx_test_with(conf_text: str) -> tuple[bool, str]:
    """Write temp conf include test if possible; else test after write is caller's job."""
    # Use nginx -t only works for full config; we write to target path temporarily in apply
    code, out, err = run(["nginx", "-t"])
    return code == 0, (out + err).strip()


def apply_conf(text: str) -> tuple[bool, str]:
    CONF_PATH.parent.mkdir(parents=True, exist_ok=True)
    bak = None
    if CONF_PATH.exists():
        bak = BACKUP_DIR / f"scx.conf.bak.{datetime.now().strftime('%Y%m%d%H%M%S')}"
        bak.write_text(CONF_PATH.read_text(encoding="utf-8"), encoding="utf-8")
    CONF_PATH.write_text(text, encoding="utf-8")
    code, out, err = run(["nginx", "-t"])
    msg = (out + err).strip()
    if code != 0:
        if bak and bak.exists():
            CONF_PATH.write_text(bak.read_text(encoding="utf-8"), encoding="utf-8")
            run(["nginx", "-t"])
        return False, f"nginx -t 失败，已回滚\n{msg}"
    # reload
    code2, out2, err2 = run(["systemctl", "reload", "nginx"])
    if code2 != 0:
        # try nginx -s reload
        code3, out3, err3 = run(["nginx", "-s", "reload"])
        if code3 != 0:
            return False, f"reload 失败\n{out2}{err2}\n{out3}{err3}"
    return True, f"已写入 {CONF_PATH}" + (f"\n备份: {bak}" if bak else "") + f"\n{msg}"


PAGE = r"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<title>SCX Nginx 配置面板</title>
<style>
:root {
  --bg: #0f1419; --card: #1a2332; --border: #2d3a4d; --text: #e7ecf3;
  --muted: #8b9bb4; --accent: #3b82f6; --ok: #22c55e; --err: #ef4444; --warn: #f59e0b;
}
* { box-sizing: border-box; }
body {
  margin: 0; font-family: ui-sans-serif, system-ui, -apple-system, "Segoe UI", sans-serif;
  background: radial-gradient(1200px 600px at 10% -10%, #1e3a5f 0%, transparent 50%), var(--bg);
  color: var(--text); min-height: 100vh;
}
header {
  padding: 20px 28px; border-bottom: 1px solid var(--border);
  display: flex; align-items: center; justify-content: space-between; gap: 16px; flex-wrap: wrap;
}
header h1 { margin: 0; font-size: 1.25rem; font-weight: 650; letter-spacing: .02em; }
header .sub { color: var(--muted); font-size: .875rem; }
main { max-width: 1100px; margin: 0 auto; padding: 24px; display: grid; gap: 20px; }
.card {
  background: var(--card); border: 1px solid var(--border); border-radius: 14px;
  padding: 20px 22px; box-shadow: 0 8px 30px rgba(0,0,0,.25);
}
.card h2 { margin: 0 0 14px; font-size: 1rem; color: #cfe0ff; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 14px; }
label { display: block; font-size: .78rem; color: var(--muted); margin-bottom: 6px; }
input, select, textarea {
  width: 100%; background: #0d121a; border: 1px solid var(--border); color: var(--text);
  border-radius: 8px; padding: 10px 12px; font-size: .9rem;
}
input:focus, select:focus, textarea:focus { outline: 2px solid rgba(59,130,246,.45); border-color: var(--accent); }
textarea { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; min-height: 360px; line-height: 1.45; }
.row { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; margin-top: 16px; }
button {
  border: 0; border-radius: 9px; padding: 10px 16px; font-weight: 600; cursor: pointer; font-size: .9rem;
}
.btn-primary { background: var(--accent); color: #fff; }
.btn-secondary { background: #243247; color: var(--text); }
.btn-ok { background: #15803d; color: #fff; }
.btn-danger { background: #b91c1c; color: #fff; }
button:disabled { opacity: .55; cursor: not-allowed; }
.msg { white-space: pre-wrap; font-family: ui-monospace, monospace; font-size: .82rem;
  background: #0d121a; border-radius: 8px; padding: 12px; border: 1px solid var(--border); max-height: 240px; overflow: auto; }
.msg.ok { border-color: rgba(34,197,94,.5); color: #bbf7d0; }
.msg.err { border-color: rgba(239,68,68,.5); color: #fecaca; }
.badge { display: inline-flex; gap: 8px; align-items: center; font-size: .8rem; color: var(--muted); }
.dot { width: 8px; height: 8px; border-radius: 50%; background: var(--ok); }
.hint { font-size: .8rem; color: var(--muted); line-height: 1.5; margin-top: 8px; }
.check { display: flex; align-items: center; gap: 8px; margin-top: 22px; }
.check input { width: auto; }
tabs { display: flex; gap: 8px; margin-bottom: 12px; }
.tab { padding: 8px 12px; border-radius: 8px; background: #243247; cursor: pointer; font-size: .85rem; }
.tab.active { background: var(--accent); color: #fff; }
.hidden { display: none; }
</style>
</head>
<body>
<header>
  <div>
    <h1>SCX Nginx 可视化配置</h1>
    <div class="sub">只管理 <code>scx.conf</code> · 不接管系统 Nginx · 替代宝塔站点编辑</div>
  </div>
  <div class="badge"><span class="dot" id="statusDot"></span><span id="statusText">检测中…</span></div>
</header>
<main>
  <div class="card">
    <div class="tabs" style="display:flex;gap:8px;margin-bottom:14px">
      <div class="tab active" data-tab="form">表单配置</div>
      <div class="tab" data-tab="raw">高级：原始配置</div>
    </div>

    <div id="panel-form">
      <h2>站点与反代</h2>
      <div class="grid">
        <div><label>监听端口 listen</label><input id="listen" value="80"/></div>
        <div><label>域名 server_name（多个空格分隔，_ 表示任意）</label><input id="server_name" value="_"/></div>
        <div><label>前端静态目录 root</label><input id="root" value="/var/www/scx"/></div>
        <div><label>上传限制 client_max_body_size</label><input id="client_max_body_size" value="100m"/></div>
        <div><label>后端地址 backend（host:port）</label><input id="backend" value="127.0.0.1:9999"/></div>
        <div>
          <label>/api 转发模式</label>
          <select id="api_mode">
            <option value="strip_api">推荐：/api/xxx → 后端 /xxx（前端发 /api/boot/...）</option>
            <option value="strip_api_to_boot">旧：/api/xxx → 后端 /boot/xxx</option>
          </select>
        </div>
        <div><label>proxy_connect_timeout</label><input id="proxy_connect_timeout" value="60s"/></div>
        <div><label>proxy_send_timeout</label><input id="proxy_send_timeout" value="300s"/></div>
        <div><label>proxy_read_timeout</label><input id="proxy_read_timeout" value="300s"/></div>
      </div>
      <div class="grid" style="margin-top:8px">
        <label class="check"><input type="checkbox" id="enable_boot" checked/> 启用 /boot/ 直连（Swagger 等）</label>
        <label class="check"><input type="checkbox" id="enable_ws" checked/> 启用 /boot/ws WebSocket</label>
      </div>
      <p class="hint">生成配置会覆盖 <code id="confPath"></code>。应用前自动 <code>nginx -t</code>，失败则回滚备份。</p>
      <div class="row">
        <button class="btn-secondary" type="button" id="btnReload">重新加载当前 conf</button>
        <button class="btn-secondary" type="button" id="btnPreview">预览生成结果</button>
        <button class="btn-primary" type="button" id="btnApplyForm">生成并应用</button>
        <button class="btn-ok" type="button" id="btnTest">仅 nginx -t</button>
        <button class="btn-secondary" type="button" id="btnReloadNginx">重载 Nginx</button>
      </div>
    </div>

    <div id="panel-raw" class="hidden">
      <h2>原始 scx.conf</h2>
      <textarea id="raw" spellcheck="false"></textarea>
      <div class="row">
        <button class="btn-secondary" type="button" id="btnReloadRaw">从磁盘重载</button>
        <button class="btn-primary" type="button" id="btnApplyRaw">保存并应用原始配置</button>
      </div>
    </div>
  </div>

  <div class="card">
    <h2>输出</h2>
    <div class="msg" id="msg">就绪。</div>
  </div>
</main>
<script>
const $ = (id) => document.getElementById(id);
const token = new URLSearchParams(location.search).get('token') || localStorage.getItem('scx_nginx_token') || '';
if (new URLSearchParams(location.search).get('token')) localStorage.setItem('scx_nginx_token', token);
function headers() {
  const h = {'Content-Type': 'application/json'};
  if (token) h['X-SCX-Token'] = token;
  return h;
}
function qs(path) {
  return token ? path + (path.includes('?') ? '&' : '?') + 'token=' + encodeURIComponent(token) : path;
}
function setMsg(text, ok) {
  const el = $('msg');
  el.textContent = text;
  el.className = 'msg ' + (ok === true ? 'ok' : ok === false ? 'err' : '');
}
async function api(path, opts={}) {
  const r = await fetch(qs(path), Object.assign({headers: headers()}, opts));
  const data = await r.json().catch(() => ({ok:false, message: r.statusText}));
  if (!r.ok) throw new Error(data.message || r.statusText);
  return data;
}
function formData() {
  return {
    listen: $('listen').value.trim(),
    server_name: $('server_name').value.trim(),
    root: $('root').value.trim(),
    client_max_body_size: $('client_max_body_size').value.trim(),
    backend: $('backend').value.trim(),
    api_mode: $('api_mode').value,
    enable_boot: $('enable_boot').checked ? '1' : '0',
    enable_ws: $('enable_ws').checked ? '1' : '0',
    proxy_connect_timeout: $('proxy_connect_timeout').value.trim(),
    proxy_send_timeout: $('proxy_send_timeout').value.trim(),
    proxy_read_timeout: $('proxy_read_timeout').value.trim(),
  };
}
function fill(f) {
  $('listen').value = f.listen || '80';
  $('server_name').value = f.server_name || '_';
  $('root').value = f.root || '/var/www/scx';
  $('client_max_body_size').value = f.client_max_body_size || '100m';
  $('backend').value = f.backend || '127.0.0.1:9999';
  $('api_mode').value = f.api_mode || 'strip_api';
  $('enable_boot').checked = f.enable_boot !== '0';
  $('enable_ws').checked = f.enable_ws !== '0';
  $('proxy_connect_timeout').value = f.proxy_connect_timeout || '60s';
  $('proxy_send_timeout').value = f.proxy_send_timeout || '300s';
  $('proxy_read_timeout').value = f.proxy_read_timeout || '300s';
  if (f.raw) $('raw').value = f.raw;
}
function showTab(name) {
  document.querySelectorAll('.tab').forEach(x => x.classList.toggle('active', x.dataset.tab === name));
  $('panel-form').classList.toggle('hidden', name !== 'form');
  $('panel-raw').classList.toggle('hidden', name !== 'raw');
}
async function load() {
  try {
    const d = await api('/api/status');
    $('confPath').textContent = d.conf_path;
    $('statusText').textContent = d.nginx_ok ? 'Nginx 配置检测通过' : 'Nginx 检测异常';
    $('statusDot').style.background = d.nginx_ok ? 'var(--ok)' : 'var(--err)';
    fill(d.config);
    setMsg('已加载当前配置。\\n' + (d.nginx_msg || ''), d.nginx_ok);
  } catch (e) {
    setMsg(String(e.message || e), false);
    $('statusText').textContent = '加载失败';
    $('statusDot').style.background = 'var(--err)';
  }
}
document.querySelectorAll('.tab').forEach(t => t.addEventListener('click', () => showTab(t.dataset.tab)));
$('btnReload').onclick = load;
$('btnReloadRaw').onclick = load;
$('btnPreview').onclick = async () => {
  try {
    const d = await api('/api/preview', {method:'POST', body: JSON.stringify(formData())});
    $('raw').value = d.conf;
    setMsg('预览已生成，可在「高级」页查看。', true);
    showTab('raw');
  } catch(e) { setMsg(String(e.message||e), false); }
};
$('btnApplyForm').onclick = async () => {
  if (!confirm('将根据表单生成配置并 reload Nginx，是否继续？')) return;
  try {
    const d = await api('/api/apply', {method:'POST', body: JSON.stringify(Object.assign({mode:'form'}, formData()))});
    setMsg(d.message, d.ok);
    if (d.conf) $('raw').value = d.conf;
    await load();
  } catch(e) { setMsg(String(e.message||e), false); }
};
$('btnApplyRaw').onclick = async () => {
  if (!confirm('将保存原始配置并 reload Nginx，是否继续？')) return;
  try {
    const d = await api('/api/apply', {method:'POST', body: JSON.stringify({mode:'raw', raw: $('raw').value})});
    setMsg(d.message, d.ok);
    await load();
  } catch(e) { setMsg(String(e.message||e), false); }
};
$('btnTest').onclick = async () => {
  try {
    const d = await api('/api/test');
    setMsg(d.message, d.ok);
  } catch(e) { setMsg(String(e.message||e), false); }
};
$('btnReloadNginx').onclick = async () => {
  try {
    const d = await api('/api/reload', {method:'POST', body:'{}'});
    setMsg(d.message, d.ok);
  } catch(e) { setMsg(String(e.message||e), false); }
};
load();
</script>
</body>
</html>
"""


class Handler(BaseHTTPRequestHandler):
    server_version = "SCXNginxPanel/1.0"

    def log_message(self, fmt, *args):
        print("[%s] %s" % (datetime.now().isoformat(timespec="seconds"), fmt % args))

    def _auth_ok(self) -> bool:
        if not TOKEN:
            return True
        if self.headers.get("X-SCX-Token") == TOKEN:
            return True
        q = parse_qs(urlparse(self.path).query)
        return q.get("token", [""])[0] == TOKEN

    def _send(self, code: int, body: bytes, content_type: str = "text/html; charset=utf-8"):
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def _json(self, code: int, obj: dict):
        data = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self._send(code, data, "application/json; charset=utf-8")

    def _read_json(self) -> dict:
        n = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(n) if n else b"{}"
        if not raw:
            return {}
        return json.loads(raw.decode("utf-8"))

    def do_GET(self):
        if not self._auth_ok():
            return self._json(401, {"ok": False, "message": "Unauthorized: 需要 token"})
        path = urlparse(self.path).path
        if path in ("/", "/index.html"):
            return self._send(200, PAGE.encode("utf-8"))
        if path == "/api/status":
            text = ""
            if CONF_PATH.exists():
                text = CONF_PATH.read_text(encoding="utf-8")
            elif TEMPLATE_PATH.exists():
                text = TEMPLATE_PATH.read_text(encoding="utf-8")
            cfg = parse_conf(text) if text else parse_conf(render_conf({}))
            code, out, err = run(["nginx", "-t"])
            return self._json(
                200,
                {
                    "ok": True,
                    "conf_path": str(CONF_PATH),
                    "config": cfg,
                    "nginx_ok": code == 0,
                    "nginx_msg": (out + err).strip(),
                },
            )
        if path == "/api/test":
            code, out, err = run(["nginx", "-t"])
            return self._json(
                200,
                {"ok": code == 0, "message": (out + err).strip() or ("ok" if code == 0 else "fail")},
            )
        return self._json(404, {"ok": False, "message": "not found"})

    def do_POST(self):
        if not self._auth_ok():
            return self._json(401, {"ok": False, "message": "Unauthorized: 需要 token"})
        path = urlparse(self.path).path
        try:
            body = self._read_json()
            if path == "/api/preview":
                conf = render_conf(body)
                return self._json(200, {"ok": True, "conf": conf})
            if path == "/api/apply":
                mode = body.get("mode") or "form"
                if mode == "raw":
                    conf = body.get("raw") or ""
                else:
                    conf = render_conf(body)
                if not conf.strip():
                    return self._json(400, {"ok": False, "message": "配置为空"})
                ok, msg = apply_conf(conf)
                return self._json(200 if ok else 400, {"ok": ok, "message": msg, "conf": conf})
            if path == "/api/reload":
                code, out, err = run(["nginx", "-t"])
                if code != 0:
                    return self._json(400, {"ok": False, "message": (out + err).strip()})
                code2, out2, err2 = run(["systemctl", "reload", "nginx"])
                if code2 != 0:
                    code2, out2, err2 = run(["nginx", "-s", "reload"])
                return self._json(
                    200 if code2 == 0 else 400,
                    {"ok": code2 == 0, "message": (out2 + err2).strip() or "reloaded"},
                )
            return self._json(404, {"ok": False, "message": "not found"})
        except Exception as e:
            return self._json(
                500,
                {"ok": False, "message": f"{e}\n{traceback.format_exc()}"},
            )


def main():
    if os.geteuid() != 0:
        print("警告: 非 root 可能无法写入 /etc/nginx 或 reload")
    httpd = ThreadingHTTPServer((HOST, PORT), Handler)
    print(f"SCX Nginx Panel: http://{HOST}:{PORT}/")
    if TOKEN:
        print(f"Token 已启用，访问: http://{HOST}:{PORT}/?token=***")
    else:
        print("未设置 SCX_NGINX_PANEL_TOKEN（建议生产设置令牌，并仅监听 127.0.0.1）")
    print(f"配置文件: {CONF_PATH}")
    httpd.serve_forever()


if __name__ == "__main__":
    main()
