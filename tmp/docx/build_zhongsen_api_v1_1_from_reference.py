from __future__ import annotations

from pathlib import Path

from docx import Document
from docx.enum.table import WD_CELL_VERTICAL_ALIGNMENT, WD_ROW_HEIGHT_RULE, WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Inches, Pt, RGBColor


ROOT = Path("/Users/karl/code/yw-tech/smart-converge-x")
REFERENCE = Path("/Users/karl/Desktop/OCPX文档/API文档(1).docx")
OUT = ROOT / "output" / "docx" / "中森跃动OCPX_API文档_v1.1.docx"
OUT.parent.mkdir(parents=True, exist_ok=True)

FONT = "Arial Unicode MS"
FONT_CODE = "Menlo"
INK = RGBColor(26, 26, 26)
BODY = RGBColor(51, 51, 51)
BLUE = RGBColor(0, 153, 255)
RED = RGBColor(255, 0, 0)
GRID = "C9CDD2"
CODE_FILL = "FAFAFA"


def clear_body(document: Document) -> None:
    body = document._body._element
    for child in list(body):
        if child.tag != qn("w:sectPr"):
            body.remove(child)


def set_run(run, size=11, bold=False, color=BODY, code=False, underline=False):
    family = FONT_CODE if code else FONT
    run.font.name = family
    run.font.size = Pt(size)
    run.bold = bold
    run.font.color.rgb = color
    run.underline = underline
    rpr = run._element.get_or_add_rPr()
    fonts = rpr.rFonts
    for attr in ("ascii", "hAnsi", "eastAsia", "cs"):
        fonts.set(qn(f"w:{attr}"), family)
    fonts.set(qn("w:hint"), "eastAsia")
    lang = rpr.find(qn("w:lang"))
    if lang is None:
        lang = OxmlElement("w:lang")
        rpr.append(lang)
    lang.set(qn("w:val"), "zh-CN")
    lang.set(qn("w:eastAsia"), "zh-CN")
    return run


def keep(paragraph, next_=False):
    ppr = paragraph._p.get_or_add_pPr()
    ppr.append(OxmlElement("w:keepLines"))
    if next_:
        ppr.append(OxmlElement("w:keepNext"))


def set_num(paragraph, num_id=1, level=0):
    ppr = paragraph._p.get_or_add_pPr()
    num_pr = ppr.find(qn("w:numPr"))
    if num_pr is None:
        num_pr = OxmlElement("w:numPr")
        ppr.append(num_pr)
    ilvl = OxmlElement("w:ilvl")
    ilvl.set(qn("w:val"), str(level))
    num_id_node = OxmlElement("w:numId")
    num_id_node.set(qn("w:val"), str(num_id))
    num_pr.append(ilvl)
    num_pr.append(num_id_node)


def shade_paragraph(paragraph, fill=CODE_FILL):
    ppr = paragraph._p.get_or_add_pPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:fill"), fill)
    ppr.append(shd)


def add_left_border(paragraph, color="0099FF", size="12", space="6"):
    ppr = paragraph._p.get_or_add_pPr()
    borders = ppr.find(qn("w:pBdr"))
    if borders is None:
        borders = OxmlElement("w:pBdr")
        ppr.append(borders)
    left = OxmlElement("w:left")
    left.set(qn("w:val"), "single")
    left.set(qn("w:sz"), size)
    left.set(qn("w:space"), space)
    left.set(qn("w:color"), color)
    borders.append(left)


def add_body(document, text, *, size=11, bold=False, color=BODY, after=5, before=0):
    p = document.add_paragraph()
    p.paragraph_format.space_before = Pt(before)
    p.paragraph_format.space_after = Pt(after)
    p.paragraph_format.line_spacing = 1.3
    set_run(p.add_run(text), size=size, bold=bold, color=color)
    return p


def add_label_value(document, label, value, *, bullet=True, after=6):
    p = document.add_paragraph()
    if bullet:
        set_num(p, 1)
    p.paragraph_format.space_after = Pt(after)
    p.paragraph_format.line_spacing = 1.25
    set_run(p.add_run(label), size=11, bold=True, color=INK)
    set_run(p.add_run(value), size=11, color=BODY)
    keep(p)
    return p


def add_url(document, text, *, after=7, color=BLUE, size=9):
    p = document.add_paragraph()
    p.paragraph_format.left_indent = Pt(0)
    p.paragraph_format.right_indent = Pt(0)
    p.paragraph_format.space_after = Pt(after)
    p.paragraph_format.line_spacing = 1.25
    set_run(p.add_run(text), size=size, color=color, code=True)
    keep(p)
    return p


def add_code(document, lines, *, after=7):
    p = document.add_paragraph()
    p.paragraph_format.left_indent = Pt(6)
    p.paragraph_format.right_indent = Pt(6)
    p.paragraph_format.space_before = Pt(2)
    p.paragraph_format.space_after = Pt(after)
    p.paragraph_format.line_spacing = 1.2
    shade_paragraph(p)
    for index, line in enumerate(lines):
        if index:
            set_run(p.add_run("\n"), size=8.5, code=True)
        set_run(p.add_run(line), size=8.5, color=BODY, code=True)
    keep(p)
    return p


def add_heading(document, text, level=4, *, before=10, after=7, center=False):
    style = f"Heading {level}"
    p = document.add_paragraph(style=style)
    p.paragraph_format.space_before = Pt(before)
    p.paragraph_format.space_after = Pt(after)
    p.paragraph_format.line_spacing = 1.35
    if center:
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    size = 14 if level == 3 else 12 if level == 4 else 11
    set_run(p.add_run(text), size=size, bold=True, color=INK)
    keep(p, next_=True)
    return p


def set_cell_margins(cell, top=90, bottom=90, start=90, end=90):
    tc_pr = cell._tc.get_or_add_tcPr()
    tc_mar = tc_pr.find(qn("w:tcMar"))
    if tc_mar is None:
        tc_mar = OxmlElement("w:tcMar")
        tc_pr.append(tc_mar)
    for edge, value in (("top", top), ("bottom", bottom), ("start", start), ("end", end)):
        node = tc_mar.find(qn(f"w:{edge}"))
        if node is None:
            node = OxmlElement(f"w:{edge}")
            tc_mar.append(node)
        node.set(qn("w:w"), str(value))
        node.set(qn("w:type"), "dxa")


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
    tbl_ind.set(qn("w:w"), "0")
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
        for index, cell in enumerate(row.cells):
            tc_pr = cell._tc.get_or_add_tcPr()
            tc_w = tc_pr.find(qn("w:tcW"))
            if tc_w is None:
                tc_w = OxmlElement("w:tcW")
                tc_pr.append(tc_w)
            tc_w.set(qn("w:type"), "dxa")
            tc_w.set(qn("w:w"), str(widths[index]))
            set_cell_margins(cell, start=0)
            cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER


def repeat_header(row):
    tr_pr = row._tr.get_or_add_trPr()
    node = OxmlElement("w:tblHeader")
    node.set(qn("w:val"), "true")
    tr_pr.append(node)


def cell_text(cell, text, *, bold=False, color=BODY, code=False, size=9):
    cell.text = ""
    p = cell.paragraphs[0]
    p.paragraph_format.space_before = Pt(0)
    p.paragraph_format.space_after = Pt(0)
    p.paragraph_format.left_indent = Pt(4.5)
    p.paragraph_format.line_spacing = 1.18
    set_run(p.add_run(text), size=size, bold=bold, color=color, code=code)


def add_parameter_table(document, rows):
    table = document.add_table(rows=1, cols=5)
    table.style = "Table Grid"
    widths = [1645, 2011, 1027, 959, 3397]
    headers = ("参数名", "参数宏", "最大长度", "是否必填", "参数说明")
    for cell, text in zip(table.rows[0].cells, headers):
        cell_text(cell, text, bold=True, color=INK)
    repeat_header(table.rows[0])
    table.rows[0].height = Pt(26)
    table.rows[0].height_rule = WD_ROW_HEIGHT_RULE.AT_LEAST
    for name, macro, max_len, required, description in rows:
        row = table.add_row()
        row.height = Pt(25)
        row.height_rule = WD_ROW_HEIGHT_RULE.AT_LEAST
        cell_text(row.cells[0], name, code=True)
        cell_text(row.cells[1], macro, code=True)
        cell_text(row.cells[2], max_len)
        cell_text(row.cells[3], required, color=RED if required in {"是", "条件必填"} else BODY)
        cell_text(row.cells[4], description)
    set_table_geometry(table, widths)
    set_table_borders(table)
    return table


def add_event_table(document):
    events = [
        ("1", "激活（ACTIVATE）"),
        ("2", "唤起 / 拉活（EVOKE）"),
        ("3", "登录（LOGIN）"),
        ("4", "注册（REGISTER）"),
        ("5", "付费 / 购买（PAYMENT）"),
        ("6", "有效获客 / 拉新（CUSTOMER_ACQUISITION）"),
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
        ("18", "首次激活 / 首活（FIRST_ACTIVATION）"),
        ("19", "首次购买 / 首购（FIRST_ORDER）"),
    ]
    table = document.add_table(rows=1, cols=4)
    table.style = "Table Grid"
    for cell, text in zip(table.rows[0].cells, ("值", "对应事件", "值", "对应事件")):
        cell_text(cell, text, bold=True, color=INK)
    repeat_header(table.rows[0])
    left = events[:10]
    right = events[10:]
    for index in range(10):
        row = table.add_row()
        row.height = Pt(22)
        row.height_rule = WD_ROW_HEIGHT_RULE.AT_LEAST
        left_value, left_label = left[index]
        cell_text(row.cells[0], left_value, code=True)
        cell_text(row.cells[1], left_label)
        if index < len(right):
            right_value, right_label = right[index]
            cell_text(row.cells[2], right_value, code=True)
            cell_text(row.cells[3], right_label)
        else:
            cell_text(row.cells[2], "")
            cell_text(row.cells[3], "")
    set_table_geometry(table, [900, 3619, 900, 3620])
    set_table_borders(table)
    return table


doc = Document(REFERENCE)
clear_body(doc)

section = doc.sections[0]
section.page_width = Inches(8.2681)
section.page_height = Inches(11.6931)
section.left_margin = Inches(0.5)
section.right_margin = Inches(0.5)
section.top_margin = Inches(0.5)
section.bottom_margin = Inches(0.5)
section.header_distance = Inches(0.4944)
section.footer_distance = Inches(0.5924)

for style_name, size, bold in (("Normal", 11, False), ("Heading 3", 14, True), ("Heading 4", 12, True), ("Heading 6", 11, True)):
    style = doc.styles[style_name]
    style.font.name = FONT
    style.font.size = Pt(size)
    style.font.bold = bold
    style.font.color.rgb = INK if bold else BODY
    style._element.get_or_add_rPr().rFonts.set(qn("w:ascii"), FONT)
    style._element.get_or_add_rPr().rFonts.set(qn("w:hAnsi"), FONT)
    style._element.get_or_add_rPr().rFonts.set(qn("w:eastAsia"), FONT)

doc.core_properties.title = "中森跃动 OCPX API 文档 v1.1"
doc.core_properties.subject = "媒体点击、曝光监测与转化回调接口"
doc.core_properties.author = "中森跃动"
doc.core_properties.keywords = "OCPX,API,媒体接口,Feedback,Callback,v1.1"
doc.core_properties.comments = "基于当前通用媒体 Feedback 与 Callback 契约整理"

add_heading(doc, "中森跃动 OCPX API 文档 v1.1", level=3, before=0, after=24, center=True)

add_heading(doc, "1. 点击/曝光监测链接", level=4, before=0, after=13)
add_body(doc, "示例：", after=4)
template_url = (
    "https://{HOST}/ocpx/feedback?request_id=__REQUEST_ID__&callback_url=__CALLBACK_URL__&ip=__IP__"
    "&androidid=__ANDROIDID__&androidid_md5=__ANDROIDID_MD5__&imei=__IMEI__"
    "&imei_md5=__IMEI_MD5__&idfa=__IDFA__&idfa_md5=__IDFA_MD5__&caid=__CAID__"
    "&caid_list=__CAID_LIST__&oaid=__OAID__&oaid_md5=__OAID_MD5__&aaid=__AAID__"
    "&ua=__UA__&os=__OS__&model=__MODEL__"
    "&mId={mediaId}&tId={taskId}&aId={advertiserId}&lId={linkId}&event=CLK"
)
add_url(doc, template_url, after=10)

note = doc.add_paragraph()
note.paragraph_format.left_indent = Pt(5)
note.paragraph_format.space_before = Pt(2)
note.paragraph_format.space_after = Pt(6)
note.paragraph_format.line_spacing = 1.35
add_left_border(note)
set_run(note.add_run("注：正式请求链接由中森跃动运营人员提供。"), size=11, color=RED)
set_run(note.add_run("__参数名__ 为媒体侧宏占位符，必须替换为真实值；没有对应值时应替换为空或不传该可选参数，不得把占位符原样发送。"), size=11, bold=True, color=RED, underline=True)

add_label_value(doc, "接口类型：", "HTTPS")
add_label_value(doc, "请求方式：", "GET")
add_label_value(doc, "接口路径：", "/ocpx/feedback；event=IMP 表示曝光，event=CLK 表示点击")
add_label_value(doc, "参数说明：", "")

parameter_rows = [
    ("request_id", "__REQUEST_ID__", "128", "建议必传", "媒体侧请求唯一 ID；重试必须保持不变；缺失时平台按完整请求生成 MD5 回退值"),
    ("callback_url", "__CALLBACK_URL__", "3000", "条件必填", "需要接收转化回调时必传；完整公网 HTTP/HTTPS URL 必须 urlencode"),
    ("idfa", "__IDFA__", "128", "否", "iOS 广告标识；能获取时建议传"),
    ("idfa_md5", "__IDFA_MD5__", "32", "否", "IDFA 的小写 MD5"),
    ("imei", "__IMEI__", "128", "否", "Android IMEI；能获取时建议传"),
    ("imei_md5", "__IMEI_MD5__", "32", "否", "IMEI 的小写 MD5"),
    ("oaid", "__OAID__", "128", "否", "Android OAID；能获取时建议传"),
    ("oaid_md5", "__OAID_MD5__", "32", "否", "OAID 的小写 MD5"),
    ("caid", "__CAID__", "128", "否", "iOS CAID；按合作方约定填写"),
    ("caid_list", "__CAID_LIST__", "2000", "否", "CAID JSON 数组字符串；作为 Query 参数时必须 urlencode"),
    ("caid1", "__CAID1__", "128", "否", "兼容 CAID 字段"),
    ("aaid", "__AAID__", "128", "否", "AAID 设备标识"),
    ("androidid", "__ANDROIDID__", "128", "否", "Android ID；字段名为 androidid，不是 android_id"),
    ("androidid_md5", "__ANDROIDID_MD5__", "32", "否", "Android ID 的小写 MD5"),
    ("mac", "__MAC__", "128", "否", "设备 MAC 地址；按隐私合规要求提供"),
    ("ip", "__IP__", "64", "否", "客户端 IP"),
    ("ua", "__UA__", "2000", "否", "User-Agent；作为 Query 参数时必须 urlencode"),
    ("os", "__OS__", "1", "否", "操作系统：0=Android，1=iOS"),
    ("model", "__MODEL__", "128", "否", "手机机型"),
]
add_parameter_table(doc, parameter_rows)

add_label_value(doc, "请求响应：", "", after=3)
add_body(doc, "HTTP 200 表示 Feedback 事实事务已成功提交；HTTP 400 表示必填路由参数缺失、超长或混入已废弃参数；HTTP 500 表示服务端未成功完成事实事务。", size=9, after=5)
add_code(doc, ["{", '  "ret": 0,', '  "code": 200,', '  "msg": "提交成功",', '  "data": {}', "}"], after=7)

add_label_value(doc, "请求示例：", "以下域名、固定 ID 与设备值仅作格式示例，联调时须替换为运营下发值。", after=4)
filled_url = (
    "https://{HOST}/ocpx/feedback?request_id=clk_202608160001&oaid=6a25babaa6243f485090e20b89cb9b93"
    "&ip=203.0.113.10&os=0&callback_url=https%3A%2F%2Fcallback.media-domain.cn%2Focpx%2Fconvert%3Frequest_id%3Dclk_202608160001"
    "&mId=m001&tId=t001&aId=a001&lId=l001&event=CLK"
)
add_url(doc, filled_url, after=9, size=8.5)

doc.add_page_break()
add_heading(doc, "2. 回调说明", level=4, before=0, after=8)
add_body(doc, "平台完成归因、去重与扣量判定后，读取点击/曝光请求中的 callback_url，并以 HTTP GET 回调媒体。平台会在原地址后追加 event_type 参数；若回传包含金额，再追加 currency_amount 参数。", after=7)
add_body(doc, "原 callback_url 已有 Query 参数时使用 & 追加，否则使用 ? 追加。event_type 为整数 1～19；currency_amount 为可选非负十进制金额，保持上游原始单位，平台不自动换算为“分”。", after=7)
add_body(doc, "event_type 值说明：", bold=True, after=5)
add_event_table(doc)

warning = doc.add_paragraph()
warning.paragraph_format.space_before = Pt(7)
warning.paragraph_format.space_after = Pt(7)
warning.paragraph_format.line_spacing = 1.3
set_run(warning.add_run("说明："), size=9, bold=True, color=INK)
set_run(warning.add_run("12～17 为兼容保留编号，不属于管理端常规可选择的目标转化事件；如需依赖其业务含义，须在联调时另行确认。"), size=9, color=RED)

add_body(doc, "回调地址要求：必须是可解析的公网 HTTP/HTTPS URL，不得包含用户信息或 URL 片段，不得指向 localhost、内网或保留域名；应在 5 秒内返回 HTTP 200 或 204，响应体内容会被忽略。", size=9, after=7)
add_body(doc, "回调拼接示例：", bold=True, after=3)
add_url(doc, "https://callback.media-domain.cn/ocpx/convert?request_id=clk_202608160001&event_type=1", after=4)
add_url(doc, "https://callback.media-domain.cn/ocpx/convert?request_id=clk_202608160001&event_type=5&currency_amount=123.45", after=9)

add_heading(doc, "3. 示例", level=4, before=6, after=8)
add_heading(doc, "3.1 点击示例", level=6, before=0, after=6)
example_values = [
    ("request_id：", "clk_202608160001"),
    ("oaid：", "6a25babaa6243f485090e20b89cb9b93"),
    ("ip：", "203.0.113.10"),
    ("os：", "0"),
    ("callback_url：", "https://callback.media-domain.cn/ocpx/convert?request_id=clk_202608160001"),
    ("mId：", "m001"),
    ("tId：", "t001"),
    ("aId：", "a001"),
    ("lId：", "l001"),
    ("event：", "CLK"),
]
for label, value in example_values:
    add_label_value(doc, label, value, bullet=True, after=3)

add_body(doc, "媒体上报点击的完整请求地址：", bold=True, after=3)
add_url(doc, filled_url, after=7, size=8.5)
add_body(doc, "请求成功响应：", bold=True, after=3)
add_code(doc, ['{"ret":0,"code":200,"msg":"提交成功","data":{}}'], after=8)

add_heading(doc, "3.2 回调示例", level=6, before=5, after=6)
add_body(doc, "用户激活后，平台以 GET 调用媒体 callback_url：", after=3)
add_url(doc, "https://callback.media-domain.cn/ocpx/convert?request_id=clk_202608160001&event_type=1", after=6)
add_body(doc, "用户完成付费且上游提供金额时：", after=3)
add_url(doc, "https://callback.media-domain.cn/ocpx/convert?request_id=clk_202608160001&event_type=5&currency_amount=123.45", after=6)
add_body(doc, "媒体返回 HTTP 200 或 204 即视为接收完成；响应体由媒体自行定义，平台不解析。", after=0)

doc.save(OUT)
print(OUT)
