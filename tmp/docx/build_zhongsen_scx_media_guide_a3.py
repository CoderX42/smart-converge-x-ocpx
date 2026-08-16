from __future__ import annotations

from pathlib import Path

from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_CELL_VERTICAL_ALIGNMENT, WD_ROW_HEIGHT_RULE, WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Inches, Mm, Pt, RGBColor


ROOT = Path("/Users/karl/code/yw-tech/smart-converge-x")
OUT = ROOT / "output" / "docx" / "中森跃动OCPX媒体接口文档_v1.1_下游接入版.docx"
OUT.parent.mkdir(parents=True, exist_ok=True)

FONT_LATIN = "Arial Unicode MS"
FONT_CJK = "Arial Unicode MS"
FONT_CODE = "Menlo"
INK = RGBColor(35, 39, 45)
RED = RGBColor(255, 0, 0)
GRAY = RGBColor(132, 139, 148)
GRID = "C7CBD1"
CODE_FILL = "FAFAFA"


def set_run(run, size=11, bold=False, color=INK, code=False):
    latin = FONT_CODE if code else FONT_LATIN
    run.font.name = latin
    run.font.size = Pt(size)
    run.bold = bold
    run.font.color.rgb = color
    rpr = run._element.get_or_add_rPr()
    fonts = rpr.rFonts
    fonts.set(qn("w:ascii"), latin)
    fonts.set(qn("w:hAnsi"), latin)
    fonts.set(qn("w:eastAsia"), FONT_CJK)
    fonts.set(qn("w:cs"), FONT_CJK)
    fonts.set(qn("w:hint"), "eastAsia")
    lang = rpr.find(qn("w:lang"))
    if lang is None:
        lang = OxmlElement("w:lang")
        rpr.append(lang)
    lang.set(qn("w:val"), "zh-CN")
    lang.set(qn("w:eastAsia"), "zh-CN")
    return run


def set_cell_margins(cell, top=65, bottom=65, start=100, end=100):
    tc_pr = cell._tc.get_or_add_tcPr()
    tc_mar = tc_pr.find(qn("w:tcMar"))
    if tc_mar is None:
        tc_mar = OxmlElement("w:tcMar")
        tc_pr.append(tc_mar)
    for edge, value in (("top", top), ("bottom", bottom), ("start", start), ("end", end)):
        item = tc_mar.find(qn(f"w:{edge}"))
        if item is None:
            item = OxmlElement(f"w:{edge}")
            tc_mar.append(item)
        item.set(qn("w:w"), str(value))
        item.set(qn("w:type"), "dxa")


def set_cell_width(cell, width_dxa):
    tc_pr = cell._tc.get_or_add_tcPr()
    tc_w = tc_pr.find(qn("w:tcW"))
    if tc_w is None:
        tc_w = OxmlElement("w:tcW")
        tc_pr.append(tc_w)
    tc_w.set(qn("w:type"), "dxa")
    tc_w.set(qn("w:w"), str(width_dxa))


def set_table_borders(table):
    tbl_pr = table._tbl.tblPr
    borders = tbl_pr.find(qn("w:tblBorders"))
    if borders is None:
        borders = OxmlElement("w:tblBorders")
        tbl_pr.append(borders)
    for edge in ("top", "left", "bottom", "right", "insideH", "insideV"):
        border = borders.find(qn(f"w:{edge}"))
        if border is None:
            border = OxmlElement(f"w:{edge}")
            borders.append(border)
        border.set(qn("w:val"), "single")
        border.set(qn("w:sz"), "5")
        border.set(qn("w:space"), "0")
        border.set(qn("w:color"), GRID)


def set_table_geometry(table, widths):
    table.autofit = False
    table.alignment = WD_TABLE_ALIGNMENT.LEFT
    tbl_pr = table._tbl.tblPr
    total = sum(widths)
    tbl_w = tbl_pr.find(qn("w:tblW"))
    if tbl_w is None:
        tbl_w = OxmlElement("w:tblW")
        tbl_pr.append(tbl_w)
    tbl_w.set(qn("w:type"), "dxa")
    tbl_w.set(qn("w:w"), str(total))
    tbl_ind = tbl_pr.find(qn("w:tblInd"))
    if tbl_ind is None:
        tbl_ind = OxmlElement("w:tblInd")
        tbl_pr.append(tbl_ind)
    tbl_ind.set(qn("w:type"), "dxa")
    tbl_ind.set(qn("w:w"), "100")
    layout = tbl_pr.find(qn("w:tblLayout"))
    if layout is None:
        layout = OxmlElement("w:tblLayout")
        tbl_pr.append(layout)
    layout.set(qn("w:type"), "fixed")
    grid = table._tbl.tblGrid
    for child in list(grid):
        grid.remove(child)
    for width in widths:
        col = OxmlElement("w:gridCol")
        col.set(qn("w:w"), str(width))
        grid.append(col)
    for row in table.rows:
        tr_pr = row._tr.get_or_add_trPr()
        tr_pr.append(OxmlElement("w:cantSplit"))
        for idx, cell in enumerate(row.cells):
            set_cell_width(cell, widths[idx])
            set_cell_margins(cell)
            cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER


def repeat_header(row):
    tr_pr = row._tr.get_or_add_trPr()
    node = OxmlElement("w:tblHeader")
    node.set(qn("w:val"), "true")
    tr_pr.append(node)


def shade_paragraph(paragraph, fill=CODE_FILL):
    ppr = paragraph._p.get_or_add_pPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:fill"), fill)
    ppr.append(shd)


def keep(paragraph, next_=False):
    ppr = paragraph._p.get_or_add_pPr()
    ppr.append(OxmlElement("w:keepLines"))
    if next_:
        ppr.append(OxmlElement("w:keepNext"))


def configure_section(section, top_mm):
    section.page_width = Mm(297)
    section.page_height = Mm(420)
    section.left_margin = Mm(31.75)
    section.right_margin = Mm(31.75)
    section.top_margin = Mm(top_mm)
    section.bottom_margin = Mm(13)
    section.header_distance = Mm(5)
    section.footer_distance = Mm(5)


def add_heading(doc, text, size=14, before=8, after=5):
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(before)
    p.paragraph_format.space_after = Pt(after)
    p.paragraph_format.line_spacing = 1.15
    set_run(p.add_run(text), size=size, bold=True)
    keep(p, next_=True)
    return p


def add_body(doc, text, size=11, after=6, bold_prefix=None):
    p = doc.add_paragraph()
    p.paragraph_format.space_after = Pt(after)
    p.paragraph_format.line_spacing = 1.45
    if bold_prefix and text.startswith(bold_prefix):
        set_run(p.add_run(bold_prefix), size=size, bold=True)
        set_run(p.add_run(text[len(bold_prefix):]), size=size)
    else:
        set_run(p.add_run(text), size=size)
    return p


def add_bullet(doc, text):
    p = doc.add_paragraph(style="List Bullet")
    p.paragraph_format.left_indent = Mm(5.5)
    p.paragraph_format.first_line_indent = Mm(-5.5)
    p.paragraph_format.space_after = Pt(5)
    p.paragraph_format.line_spacing = 1.15
    set_run(p.add_run(text), size=11, bold=True)
    keep(p)
    return p


def add_code(doc, lines, size=8.5):
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Mm(3)
    p.paragraph_format.right_indent = Mm(3)
    p.paragraph_format.space_before = Pt(3)
    p.paragraph_format.space_after = Pt(6)
    p.paragraph_format.line_spacing = 1.25
    shade_paragraph(p)
    for idx, line in enumerate(lines):
        if idx:
            set_run(p.add_run("\n"), size=size, code=True)
        set_run(p.add_run(line), size=size, code=True)
    keep(p)
    return p


def cell_text(cell, text, size=9, bold=False, color=INK, code=False):
    cell.text = ""
    p = cell.paragraphs[0]
    p.paragraph_format.space_before = Pt(0)
    p.paragraph_format.space_after = Pt(0)
    p.paragraph_format.line_spacing = 1.12
    set_run(p.add_run(text), size=size, bold=bold, color=color, code=code)
    return p


def add_parameter_table(doc, rows, include_header):
    table = doc.add_table(rows=1 if include_header else 0, cols=4)
    widths = [2520, 2660, 2350, 5660]
    if include_header:
        header = table.rows[0]
        for cell, text in zip(header.cells, ("参数名", "参数宏", "是否必填", "参数说明")):
            cell_text(cell, text, size=9.5, bold=True)
        header.height = Mm(8)
        header.height_rule = WD_ROW_HEIGHT_RULE.AT_LEAST
        repeat_header(header)
    for name, macro, required, description, required_red, min_mm in rows:
        row = table.add_row()
        row.height = Mm(min_mm)
        row.height_rule = WD_ROW_HEIGHT_RULE.AT_LEAST
        cell_text(row.cells[0], name, code=True)
        cell_text(row.cells[1], macro, code=True)
        cell_text(row.cells[2], required, color=RED if required_red else INK)
        cell_text(row.cells[3], description)
    set_table_geometry(table, widths)
    set_table_borders(table)
    return table


def add_event_table(doc):
    events = [
        ("1", "激活（ACTIVATE）"),
        ("2", "唤起（EVOKE）"),
        ("3", "登录（LOGIN）"),
        ("4", "注册（REGISTER）"),
        ("5", "付费（PAYMENT）"),
        ("6", "有效获客（CUSTOMER_ACQUISITION）"),
        ("7", "次留（NEXT_STAY）"),
        ("8", "关键页面访问（VISITS）"),
        ("9", "认证（AUTHENTICATION）"),
        ("10", "下单（ORDER）"),
        ("11", "完单（FINISH_THE_ORDER）"),
        ("12", "兼容保留：EVOKE_C"),
        ("13", "兼容保留：EVOKE_FIRST"),
        ("14", "兼容保留：EVOKE_C_VALID"),
        ("15", "兼容保留：LAHUO"),
        ("16", "兼容保留：LAHUI"),
        ("17", "兼容保留：HUIFANG"),
        ("18", "首次激活（FIRST_ACTIVATION）"),
        ("19", "首次购买（FIRST_ORDER）"),
    ]
    table = doc.add_table(rows=1, cols=2)
    widths = [4060, 9130]
    for cell, text in zip(table.rows[0].cells, ("值", "对应事件")):
        cell_text(cell, text, size=10, bold=True)
    repeat_header(table.rows[0])
    table.rows[0].height = Mm(9)
    table.rows[0].height_rule = WD_ROW_HEIGHT_RULE.AT_LEAST
    for value, label in events:
        row = table.add_row()
        row.height = Mm(15.2)
        row.height_rule = WD_ROW_HEIGHT_RULE.AT_LEAST
        cell_text(row.cells[0], value, size=9.5)
        cell_text(row.cells[1], label, size=9.5)
    set_table_geometry(table, widths)
    set_table_borders(table)
    return table


doc = Document()
doc.core_properties.title = "中森跃动 OCPX 媒体接口文档 1.1"
doc.core_properties.subject = "通用下游媒体接入"
doc.core_properties.author = "中森跃动"
doc.core_properties.keywords = "OCPX,媒体接口,Feedback,Callback"

normal = doc.styles["Normal"]
normal.font.name = FONT_LATIN
normal.font.size = Pt(11)
normal._element.rPr.rFonts.set(qn("w:eastAsia"), FONT_CJK)
normal.paragraph_format.space_after = Pt(0)

configure_section(doc.sections[0], 27)

title = doc.add_paragraph()
title.alignment = WD_ALIGN_PARAGRAPH.CENTER
title.paragraph_format.space_after = Pt(28)
set_run(title.add_run("中森跃动 ocpx 媒体接口文档 1.1"), size=24, bold=True)
keep(title)

p = doc.add_paragraph()
p.paragraph_format.space_after = Pt(25)
set_run(p.add_run("具体文档"), size=18, bold=True)
keep(p, next_=True)

add_heading(doc, "1. 概览", before=0, after=8)
add_body(doc, "媒体或三方 DSP 在广告曝光、点击时调用我方下发的监测地址并替换地址中的宏，我方收集投放行为并向已配置的广告主转发。", after=5)
add_body(doc, "广告主回传转化后，我方完成归因、去重与扣量判定，再调用原请求中的 callback_url，将转化结果回传媒体或三方 DSP。", after=14)

add_heading(doc, "2. 我方监测地址获取", before=0, after=7)
add_body(doc, "媒体完成文档接入后，由中森跃动运营人员在我方管理端创建监测任务并提供曝光、点击两条完整地址。媒体只替换宏，不得修改四个路由参数及 event 固定值。", after=4)
add_code(doc, ["https://{HOST}/ocpx/feedback?request_id=__REQUEST_ID__&callback_url=__CALLBACK_URL__&event=IMP&mId={mediaId}&tId={taskId}&aId={advertiserId}&lId={linkId}"], size=7.7)

add_heading(doc, "3. 曝光/点击接口（媒体或三方 DSP 发生曝光/点击时调用）", before=0, after=7)
add_bullet(doc, "接口类型：http/https（实际联调与生产均以运营下发的完整地址为准）")
add_bullet(doc, "请求类型：GET")
add_bullet(doc, "接口路径：/ocpx/feedback；event=IMP 表示曝光，event=CLK 表示点击")
add_bullet(doc, "请求参数：")

page1_rows = [
    ("mId", "平台下发固定值", "是", "媒体 ID，最长 32 字符；不得替换", True, 8),
    ("tId", "平台下发固定值", "是", "监测任务 ID，最长 32 字符；不得替换", True, 8),
    ("aId", "平台下发固定值", "是", "广告主 ID；不同于媒体侧 adv_id", True, 8),
    ("lId", "平台下发固定值", "是", "推广链接 ID，最长 32 字符；不得替换", True, 8),
    ("event", "平台下发固定值", "是", "仅使用 IMP（曝光）或 CLK（点击）", True, 8),
    ("request_id", "__REQUEST_ID__", "建议必传", "媒体侧请求唯一 ID，最长 128 字符；重试必须保持不变", False, 8),
    ("callback_url", "__CALLBACK_URL__", "条件必填", "需要接收转化回传时必传；完整 URL 值必须 urlencode", True, 9),
    ("idfa", "__IDFA__", "否", "iOS 设备 IDFA；能获取时建议传", False, 8),
    ("idfa_md5", "__IDFA_MD5__", "否", "IDFA 的小写 MD5", False, 8),
    ("imei", "__IMEI__", "否", "Android 设备 IMEI；能获取时建议传", False, 8),
    ("imei_md5", "__IMEI_MD5__", "否", "IMEI 的小写 MD5", False, 8),
    ("oaid", "__OAID__", "否", "Android OAID；能获取时建议传", False, 8),
    ("oaid_md5", "__OAID_MD5__", "否", "OAID 的小写 MD5", False, 8),
]
add_parameter_table(doc, page1_rows, include_header=True)

section2 = doc.add_section(WD_SECTION.NEW_PAGE)
configure_section(section2, 7)

page2_rows = [
    ("caid", "__CAID__", "否", "iOS CAID；按合作方约定填写", False, 7.5),
    ("caid_list", "__CAID_LIST__", "否", "CAID JSON 数组字符串；作为 Query 参数时必须 urlencode", False, 12),
    ("caid1", "__CAID1__", "否", "兼容 CAID 字段", False, 7.5),
    ("aaid", "__AAID__", "否", "AAID 设备标识", False, 7.5),
    ("androidid", "__ANDROIDID__", "否", "Android ID；字段名为 androidid，不是 android_id", False, 9),
    ("androidid_md5", "__ANDROIDID_MD5__", "否", "Android ID 的小写 MD5", False, 7.5),
    ("mac", "__MAC__", "否", "设备 MAC 地址；按隐私合规要求提供", False, 7.5),
    ("mac_md5", "__MAC_MD5__", "否", "MAC 地址的小写 MD5", False, 7.5),
    ("ip", "__IP__", "否", "客户端 IP", False, 7.5),
    ("ua", "__UA__", "否", "User-Agent；作为 Query 参数时必须 urlencode", False, 7.5),
    ("os", "__OS__", "否", "操作系统：0=Android，1=iOS", False, 7.5),
    ("model", "__MODEL__", "否", "手机机型", False, 7.5),
    ("ts", "__TS__", "否", "媒体原始时间戳字符串（建议 Unix 秒）", False, 7.5),
    ("mts", "__MTS__", "否", "毫秒时间戳字符串", False, 7.5),
    ("adv_id", "__ADVERTISER_ID__", "否", "媒体侧广告主 ID；与平台固定 aId 不同", False, 7.5),
    ("group_id", "__ORDER_GROUP_ID__", "否", "媒体侧广告组 ID", False, 7.5),
    ("plan_id", "__ORDER_PLAN_ID__", "否", "媒体侧广告计划 ID", False, 7.5),
    ("creative_id", "__CREATIVE_ID__", "否", "媒体侧广告创意 ID", False, 7.5),
    ("impresss_id", "自定义宏", "否", "曝光标识；当前协议字段名保留三个字母 s", False, 7.5),
]
add_parameter_table(doc, page2_rows, include_header=True)

add_bullet(doc, "响应：")
add_body(doc, "HTTP 状态码 200 表示我方平台的 Feedback 事实事务已成功提交；不等同于广告主已接受转化。", size=10, after=3)
add_code(doc, ["{", '  "ret": 0,', '  "code": 200,', '  "msg": "提交成功",', '  "data": {}', "}"], size=8)

add_heading(doc, "4. 用户行为回传", before=6, after=6)
add_body(doc, "我方完成行为归因、去重和扣量判定后，读取曝光/点击请求中的 callback_url，在其后追加 event_type={event_type}；若上游提供金额，再追加 currency_amount={currency_amount}，然后以 HTTP GET 回传。", size=10.5, after=4)
p = doc.add_paragraph()
p.paragraph_format.space_after = Pt(4)
p.paragraph_format.line_spacing = 1.3
set_run(p.add_run("注意："), size=10.5, bold=True)
set_run(p.add_run("currency_amount 保持上游原始十进制单位，我方平台不自动换算为“分”；媒体不得按快手 amount 字段解析。"), size=10.5, color=RED)

add_body(doc, "例如，媒体上报的原始 callback_url 为：", size=10, after=2)
add_code(doc, ["https://media.example.com/ocpx/convert?request_id=clk_202608160001"], size=8)
add_body(doc, "激活回调：", size=10, after=1)
add_code(doc, ["https://media.example.com/ocpx/convert?request_id=clk_202608160001&event_type=1"], size=8)
add_body(doc, "付费回调（有金额时）：", size=10, after=1)
add_code(doc, ["https://media.example.com/ocpx/convert?request_id=clk_202608160001&event_type=5&currency_amount=123.45"], size=8)

add_bullet(doc, "请求参数：")
add_body(doc, "event_type　用户行为类型，整数 1～19，必传。", size=10, after=2, bold_prefix="event_type")
add_body(doc, "currency_amount　支付相关金额，可选，非负十进制数。", size=10, after=2, bold_prefix="currency_amount")
add_body(doc, "媒体回调地址必须为可解析的公网 HTTP/HTTPS URL；应在 5 秒内返回 HTTP 200 或 204，响应体内容会被忽略。", size=10, after=4)
add_body(doc, "event_type 值说明：", size=10.5, after=4, bold_prefix="event_type 值说明：")

section3 = doc.add_section(WD_SECTION.NEW_PAGE)
configure_section(section3, 25)
add_event_table(doc)

p = doc.add_paragraph()
p.paragraph_format.space_before = Pt(8)
p.paragraph_format.space_after = Pt(0)
p.paragraph_format.line_spacing = 1.3
set_run(p.add_run("说明："), size=9.5, bold=True)
set_run(p.add_run("12～17 为当前兼容保留编号，不属于管理端可选择的目标转化事件；媒体如需依赖其业务含义，须在联调时另行确认。20～29 不在当前通用回调契约范围内。"), size=9.5, color=RED)

doc.save(OUT)
print(OUT)
