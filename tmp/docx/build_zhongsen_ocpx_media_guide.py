from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont
from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_CELL_VERTICAL_ALIGNMENT, WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK, WD_LINE_SPACING
from docx.oxml import OxmlElement
from docx.oxml.ns import nsdecls, qn
from docx.shared import Inches, Pt, RGBColor


ROOT = Path("/Users/karl/code/yw-tech/smart-converge-x")
TMP_DIR = ROOT / "tmp" / "docx" / "zhongsen_ocpx_media_guide"
OUT_DIR = ROOT / "output" / "docx"
DOCX_PATH = OUT_DIR / "中森跃动OCPX媒体接口文档_v1.0.docx"
FLOW_PATH = TMP_DIR / "ocpx-flow.png"

TMP_DIR.mkdir(parents=True, exist_ok=True)
OUT_DIR.mkdir(parents=True, exist_ok=True)


# compact_reference_guide preset, with one named cross-platform CJK override:
# Heiti SC is the local macOS family name reported by fontconfig. Runs also set
# an East Asian language hint so LibreOffice does not drop Chinese glyphs.
FONT_LATIN = "Heiti SC"
FONT_CJK = "Heiti SC"
FONT_CODE = "Menlo"
FONT_CODE_CJK = "Heiti SC"

BLUE = RGBColor(46, 116, 181)
DARK_BLUE = RGBColor(31, 77, 120)
INK = RGBColor(11, 37, 69)
BODY = RGBColor(31, 41, 55)
MUTED = RGBColor(94, 106, 120)
LIGHT_FILL = "E8EEF5"
SOFT_FILL = "F4F6F9"
CODE_FILL = "F7F9FB"
WHITE = RGBColor(255, 255, 255)
WARN = RGBColor(122, 90, 0)

PAGE_WIDTH_DXA = 12240
PAGE_HEIGHT_DXA = 15840
MARGIN_DXA = 1440
CONTENT_WIDTH_DXA = 9360
TABLE_INDENT_DXA = 120
CELL_TOP_BOTTOM_DXA = 80
CELL_START_END_DXA = 120


def set_run_font(
    run,
    *,
    size: float | None = None,
    bold: bool | None = None,
    italic: bool | None = None,
    color: RGBColor | None = None,
    code: bool = False,
):
    latin = FONT_CODE if code else FONT_LATIN
    cjk = FONT_CODE_CJK if code else FONT_CJK
    run.font.name = latin
    rpr = run._element.get_or_add_rPr()
    rfonts = rpr.rFonts
    rfonts.set(qn("w:ascii"), latin)
    rfonts.set(qn("w:hAnsi"), latin)
    rfonts.set(qn("w:eastAsia"), cjk)
    rfonts.set(qn("w:cs"), cjk)
    rfonts.set(qn("w:hint"), "eastAsia")
    lang = rpr.find(qn("w:lang"))
    if lang is None:
        lang = OxmlElement("w:lang")
        rpr.append(lang)
    lang.set(qn("w:val"), "zh-CN")
    lang.set(qn("w:eastAsia"), "zh-CN")
    if size is not None:
        run.font.size = Pt(size)
    if bold is not None:
        run.bold = bold
    if italic is not None:
        run.italic = italic
    if color is not None:
        run.font.color.rgb = color
    return run


def set_cell_shading(cell, fill: str):
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = tc_pr.find(qn("w:shd"))
    if shd is None:
        shd = OxmlElement("w:shd")
        tc_pr.append(shd)
    shd.set(qn("w:fill"), fill)


def set_cell_margins(cell):
    tc_pr = cell._tc.get_or_add_tcPr()
    tc_mar = tc_pr.first_child_found_in("w:tcMar")
    if tc_mar is None:
        tc_mar = OxmlElement("w:tcMar")
        tc_pr.append(tc_mar)
    for edge, value in (
        ("top", CELL_TOP_BOTTOM_DXA),
        ("bottom", CELL_TOP_BOTTOM_DXA),
        ("start", CELL_START_END_DXA),
        ("end", CELL_START_END_DXA),
    ):
        node = tc_mar.find(qn(f"w:{edge}"))
        if node is None:
            node = OxmlElement(f"w:{edge}")
            tc_mar.append(node)
        node.set(qn("w:w"), str(value))
        node.set(qn("w:type"), "dxa")


def set_cell_width(cell, width_dxa: int):
    tc_pr = cell._tc.get_or_add_tcPr()
    tc_w = tc_pr.find(qn("w:tcW"))
    if tc_w is None:
        tc_w = OxmlElement("w:tcW")
        tc_pr.append(tc_w)
    tc_w.set(qn("w:type"), "dxa")
    tc_w.set(qn("w:w"), str(width_dxa))
    cell.width = Inches(width_dxa / 1440)


def set_table_borders(table, color="B8C2CC", size="6"):
    tbl_pr = table._tbl.tblPr
    borders = tbl_pr.find(qn("w:tblBorders"))
    if borders is None:
        borders = OxmlElement("w:tblBorders")
        tbl_pr.append(borders)
    for edge in ("top", "left", "bottom", "right", "insideH", "insideV"):
        tag = borders.find(qn(f"w:{edge}"))
        if tag is None:
            tag = OxmlElement(f"w:{edge}")
            borders.append(tag)
        tag.set(qn("w:val"), "single")
        tag.set(qn("w:sz"), size)
        tag.set(qn("w:space"), "0")
        tag.set(qn("w:color"), color)


def set_table_geometry(table, widths_dxa: list[int], indent_dxa: int = TABLE_INDENT_DXA):
    assert sum(widths_dxa) == CONTENT_WIDTH_DXA, widths_dxa
    table.autofit = False
    table.alignment = WD_TABLE_ALIGNMENT.LEFT
    tbl_pr = table._tbl.tblPr

    tbl_w = tbl_pr.find(qn("w:tblW"))
    if tbl_w is None:
        tbl_w = OxmlElement("w:tblW")
        tbl_pr.append(tbl_w)
    tbl_w.set(qn("w:type"), "dxa")
    tbl_w.set(qn("w:w"), str(CONTENT_WIDTH_DXA))

    tbl_ind = tbl_pr.find(qn("w:tblInd"))
    if tbl_ind is None:
        tbl_ind = OxmlElement("w:tblInd")
        tbl_pr.append(tbl_ind)
    tbl_ind.set(qn("w:type"), "dxa")
    tbl_ind.set(qn("w:w"), str(indent_dxa))

    layout = tbl_pr.find(qn("w:tblLayout"))
    if layout is None:
        layout = OxmlElement("w:tblLayout")
        tbl_pr.append(layout)
    layout.set(qn("w:type"), "fixed")

    grid = table._tbl.tblGrid
    for child in list(grid):
        grid.remove(child)
    for width in widths_dxa:
        grid_col = OxmlElement("w:gridCol")
        grid_col.set(qn("w:w"), str(width))
        grid.append(grid_col)

    for row in table.rows:
        tr_pr = row._tr.get_or_add_trPr()
        cant_split = OxmlElement("w:cantSplit")
        tr_pr.append(cant_split)
        for idx, cell in enumerate(row.cells):
            set_cell_width(cell, widths_dxa[idx])
            set_cell_margins(cell)
            cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER


def set_repeat_header(row):
    tr_pr = row._tr.get_or_add_trPr()
    tbl_header = OxmlElement("w:tblHeader")
    tbl_header.set(qn("w:val"), "true")
    tr_pr.append(tbl_header)


def set_paragraph_keep(paragraph, *, keep_next=False, keep_lines=True):
    p_pr = paragraph._p.get_or_add_pPr()
    if keep_next:
        p_pr.append(OxmlElement("w:keepNext"))
    if keep_lines:
        p_pr.append(OxmlElement("w:keepLines"))


def paragraph_shading(paragraph, fill: str):
    p_pr = paragraph._p.get_or_add_pPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:fill"), fill)
    p_pr.append(shd)


def paragraph_border_left(paragraph, color="2E74B5", size="14", space="12"):
    p_pr = paragraph._p.get_or_add_pPr()
    p_bdr = p_pr.find(qn("w:pBdr"))
    if p_bdr is None:
        p_bdr = OxmlElement("w:pBdr")
        p_pr.append(p_bdr)
    left = OxmlElement("w:left")
    left.set(qn("w:val"), "single")
    left.set(qn("w:sz"), size)
    left.set(qn("w:space"), space)
    left.set(qn("w:color"), color)
    p_bdr.append(left)


def add_page_number(paragraph):
    paragraph.add_run("第 ")
    fld = OxmlElement("w:fldSimple")
    fld.set(qn("w:instr"), "PAGE")
    run = OxmlElement("w:r")
    rpr = OxmlElement("w:rPr")
    rfonts = OxmlElement("w:rFonts")
    rfonts.set(qn("w:ascii"), FONT_LATIN)
    rfonts.set(qn("w:hAnsi"), FONT_LATIN)
    rfonts.set(qn("w:eastAsia"), FONT_CJK)
    rfonts.set(qn("w:cs"), FONT_CJK)
    rfonts.set(qn("w:hint"), "eastAsia")
    rpr.append(rfonts)
    color = OxmlElement("w:color")
    color.set(qn("w:val"), "667085")
    rpr.append(color)
    sz = OxmlElement("w:sz")
    sz.set(qn("w:val"), "18")
    rpr.append(sz)
    run.append(rpr)
    text = OxmlElement("w:t")
    text.text = "1"
    run.append(text)
    fld.append(run)
    paragraph._p.append(fld)
    paragraph.add_run(" 页")


def create_numbering(doc: Document):
    numbering = doc.part.numbering_part.element
    existing_abstract = [int(x.get(qn("w:abstractNumId"))) for x in numbering.findall(qn("w:abstractNum"))]
    existing_num = [int(x.get(qn("w:numId"))) for x in numbering.findall(qn("w:num"))]
    next_abs = max(existing_abstract or [0]) + 1
    next_num = max(existing_num or [0]) + 1

    def add_definition(num_format: str, lvl_text: str, abstract_id: int, num_id: int):
        abstract = OxmlElement("w:abstractNum")
        abstract.set(qn("w:abstractNumId"), str(abstract_id))
        multi = OxmlElement("w:multiLevelType")
        multi.set(qn("w:val"), "singleLevel")
        abstract.append(multi)
        lvl = OxmlElement("w:lvl")
        lvl.set(qn("w:ilvl"), "0")
        start = OxmlElement("w:start")
        start.set(qn("w:val"), "1")
        lvl.append(start)
        num_fmt = OxmlElement("w:numFmt")
        num_fmt.set(qn("w:val"), num_format)
        lvl.append(num_fmt)
        lvl_text_el = OxmlElement("w:lvlText")
        lvl_text_el.set(qn("w:val"), lvl_text)
        lvl.append(lvl_text_el)
        jc = OxmlElement("w:lvlJc")
        jc.set(qn("w:val"), "left")
        lvl.append(jc)
        ppr = OxmlElement("w:pPr")
        tabs = OxmlElement("w:tabs")
        tab = OxmlElement("w:tab")
        tab.set(qn("w:val"), "num")
        tab.set(qn("w:pos"), "540")
        tabs.append(tab)
        ppr.append(tabs)
        ind = OxmlElement("w:ind")
        ind.set(qn("w:left"), "540")
        ind.set(qn("w:hanging"), "270")
        ppr.append(ind)
        spacing = OxmlElement("w:spacing")
        spacing.set(qn("w:after"), "80")
        spacing.set(qn("w:line"), "300")
        spacing.set(qn("w:lineRule"), "auto")
        ppr.append(spacing)
        lvl.append(ppr)
        rpr = OxmlElement("w:rPr")
        rfonts = OxmlElement("w:rFonts")
        rfonts.set(qn("w:ascii"), FONT_LATIN)
        rfonts.set(qn("w:hAnsi"), FONT_LATIN)
        rfonts.set(qn("w:eastAsia"), FONT_CJK)
        rfonts.set(qn("w:cs"), FONT_CJK)
        rfonts.set(qn("w:hint"), "eastAsia")
        rpr.append(rfonts)
        lvl.append(rpr)
        abstract.append(lvl)
        numbering.append(abstract)

        num = OxmlElement("w:num")
        num.set(qn("w:numId"), str(num_id))
        abs_id = OxmlElement("w:abstractNumId")
        abs_id.set(qn("w:val"), str(abstract_id))
        num.append(abs_id)
        numbering.append(num)

    bullet_num = next_num
    decimal_num = next_num + 1
    add_definition("bullet", "•", next_abs, bullet_num)
    add_definition("decimal", "%1.", next_abs + 1, decimal_num)
    return bullet_num, decimal_num


def apply_numbering(paragraph, num_id: int):
    p_pr = paragraph._p.get_or_add_pPr()
    num_pr = OxmlElement("w:numPr")
    ilvl = OxmlElement("w:ilvl")
    ilvl.set(qn("w:val"), "0")
    num_id_el = OxmlElement("w:numId")
    num_id_el.set(qn("w:val"), str(num_id))
    num_pr.append(ilvl)
    num_pr.append(num_id_el)
    p_pr.append(num_pr)


def add_list_item(doc, text: str, num_id: int, *, bold_prefix: str | None = None):
    p = doc.add_paragraph()
    p.paragraph_format.space_after = Pt(4)
    p.paragraph_format.line_spacing = 1.25
    apply_numbering(p, num_id)
    if bold_prefix and text.startswith(bold_prefix):
        set_run_font(p.add_run(bold_prefix), size=11, bold=True, color=INK)
        set_run_font(p.add_run(text[len(bold_prefix) :]), size=11, color=BODY)
    else:
        set_run_font(p.add_run(text), size=11, color=BODY)
    return p


def add_body(doc, text: str, *, bold_prefix: str | None = None, color=BODY):
    p = doc.add_paragraph(style="Normal")
    if bold_prefix and text.startswith(bold_prefix):
        set_run_font(p.add_run(bold_prefix), size=11, bold=True, color=INK)
        set_run_font(p.add_run(text[len(bold_prefix) :]), size=11, color=color)
    else:
        set_run_font(p.add_run(text), size=11, color=color)
    return p


def add_callout(doc, label: str, text: str, *, warning=False):
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Inches(0.12)
    p.paragraph_format.right_indent = Inches(0.05)
    p.paragraph_format.space_before = Pt(6)
    p.paragraph_format.space_after = Pt(10)
    p.paragraph_format.line_spacing = 1.20
    paragraph_shading(p, "FFF8E8" if warning else SOFT_FILL)
    paragraph_border_left(p, "B88700" if warning else "2E74B5")
    set_run_font(p.add_run(label + "  "), size=10.5, bold=True, color=WARN if warning else DARK_BLUE)
    set_run_font(p.add_run(text), size=10.5, color=BODY)
    set_paragraph_keep(p)
    return p


def add_code_block(doc, lines: list[str]):
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Inches(0.14)
    p.paragraph_format.right_indent = Inches(0.10)
    p.paragraph_format.space_before = Pt(5)
    p.paragraph_format.space_after = Pt(9)
    p.paragraph_format.line_spacing = 1.10
    paragraph_shading(p, CODE_FILL)
    for idx, line in enumerate(lines):
        if idx:
            p.add_run().add_break()
        set_run_font(p.add_run(line), size=8.5, color=INK, code=True)
    set_paragraph_keep(p)
    return p


def add_heading(doc, text: str, level: int):
    p = doc.add_paragraph(text, style=f"Heading {level}")
    set_paragraph_keep(p, keep_next=True)
    return p


def add_table(doc, headers: list[str], rows: list[list[str]], widths_dxa: list[int], *, font_size=9.2):
    table = doc.add_table(rows=1, cols=len(headers))
    table.style = "Table Grid"
    header = table.rows[0]
    for idx, value in enumerate(headers):
        cell = header.cells[idx]
        cell.text = ""
        set_cell_shading(cell, LIGHT_FILL)
        p = cell.paragraphs[0]
        p.paragraph_format.space_after = Pt(0)
        p.paragraph_format.line_spacing = 1.0
        set_run_font(p.add_run(value), size=9.2, bold=True, color=INK)
    set_repeat_header(header)
    for row_values in rows:
        row = table.add_row()
        for idx, value in enumerate(row_values):
            cell = row.cells[idx]
            cell.text = ""
            p = cell.paragraphs[0]
            p.paragraph_format.space_after = Pt(0)
            p.paragraph_format.line_spacing = 1.08
            code_col = idx in (0, 1) and any(ch in value for ch in ("_", "=", "__"))
            set_run_font(p.add_run(value), size=font_size, color=BODY, code=code_col)
    set_table_geometry(table, widths_dxa)
    set_table_borders(table)
    after = doc.add_paragraph()
    after.paragraph_format.space_before = Pt(0)
    after.paragraph_format.space_after = Pt(2)
    return table


def page_break(doc):
    p = doc.add_paragraph()
    p.add_run().add_break(WD_BREAK.PAGE)


def create_flow_diagram(path: Path):
    canvas = Image.new("RGB", (1500, 360), "white")
    draw = ImageDraw.Draw(canvas)
    try:
        title_font = ImageFont.truetype("/System/Library/Fonts/Hiragino Sans GB.ttc", 30)
        body_font = ImageFont.truetype("/System/Library/Fonts/Hiragino Sans GB.ttc", 23)
    except OSError:
        title_font = ImageFont.truetype("/System/Library/Fonts/STHeiti Medium.ttc", 30)
        body_font = ImageFont.truetype("/System/Library/Fonts/STHeiti Light.ttc", 23)

    boxes = [
        ("媒体 / DSP", "曝光或点击"),
        ("中森跃动", "接收、去重、转发"),
        ("广告主 / DSP", "识别转化并回传"),
        ("中森跃动", "归因、去重、规则处理"),
        ("媒体 / DSP", "接收转化回调"),
    ]
    x_positions = [30, 326, 622, 918, 1214]
    w, h, y = 255, 180, 82
    fills = ["#F4F6F9", "#E8EEF5", "#F4F6F9", "#E8EEF5", "#F4F6F9"]
    for i, ((title, body), x) in enumerate(zip(boxes, x_positions)):
        draw.rounded_rectangle((x, y, x + w, y + h), radius=18, fill=fills[i], outline="#8EA7BF", width=3)
        title_bbox = draw.textbbox((0, 0), title, font=title_font)
        draw.text((x + (w - (title_bbox[2] - title_bbox[0])) / 2, y + 34), title, font=title_font, fill="#0B2545")
        body_bbox = draw.textbbox((0, 0), body, font=body_font)
        draw.text((x + (w - (body_bbox[2] - body_bbox[0])) / 2, y + 104), body, font=body_font, fill="#475467")
        if i < len(boxes) - 1:
            ax1, ax2, ay = x + w + 10, x_positions[i + 1] - 10, y + h // 2
            draw.line((ax1, ay, ax2, ay), fill="#2E74B5", width=5)
            draw.polygon([(ax2, ay), (ax2 - 16, ay - 10), (ax2 - 16, ay + 10)], fill="#2E74B5")
    canvas.save(path, optimize=True)


def configure_document(doc: Document):
    section = doc.sections[0]
    section.page_width = Inches(8.5)
    section.page_height = Inches(11)
    section.top_margin = Inches(1)
    section.bottom_margin = Inches(1)
    section.left_margin = Inches(1)
    section.right_margin = Inches(1)
    section.header_distance = Inches(0.492)
    section.footer_distance = Inches(0.492)
    section.different_first_page_header_footer = True

    styles = doc.styles
    normal = styles["Normal"]
    normal.font.name = FONT_LATIN
    normal._element.rPr.rFonts.set(qn("w:ascii"), FONT_LATIN)
    normal._element.rPr.rFonts.set(qn("w:hAnsi"), FONT_LATIN)
    normal._element.rPr.rFonts.set(qn("w:eastAsia"), FONT_CJK)
    normal._element.rPr.rFonts.set(qn("w:cs"), FONT_CJK)
    normal._element.rPr.rFonts.set(qn("w:hint"), "eastAsia")
    normal_lang = OxmlElement("w:lang")
    normal_lang.set(qn("w:val"), "zh-CN")
    normal_lang.set(qn("w:eastAsia"), "zh-CN")
    normal._element.rPr.append(normal_lang)
    normal.font.size = Pt(11)
    normal.font.color.rgb = BODY
    normal.paragraph_format.space_before = Pt(0)
    normal.paragraph_format.space_after = Pt(6)
    normal.paragraph_format.line_spacing = 1.25
    normal.paragraph_format.widow_control = True

    heading_tokens = {
        1: (16, BLUE, 18, 10),
        2: (13, BLUE, 14, 7),
        3: (12, DARK_BLUE, 10, 5),
    }
    for level, (size, color, before, after) in heading_tokens.items():
        style = styles[f"Heading {level}"]
        style.font.name = FONT_LATIN
        style._element.rPr.rFonts.set(qn("w:ascii"), FONT_LATIN)
        style._element.rPr.rFonts.set(qn("w:hAnsi"), FONT_LATIN)
        style._element.rPr.rFonts.set(qn("w:eastAsia"), FONT_CJK)
        style._element.rPr.rFonts.set(qn("w:cs"), FONT_CJK)
        style._element.rPr.rFonts.set(qn("w:hint"), "eastAsia")
        style_lang = OxmlElement("w:lang")
        style_lang.set(qn("w:val"), "zh-CN")
        style_lang.set(qn("w:eastAsia"), "zh-CN")
        style._element.rPr.append(style_lang)
        style.font.size = Pt(size)
        style.font.bold = True
        style.font.color.rgb = color
        style.paragraph_format.space_before = Pt(before)
        style.paragraph_format.space_after = Pt(after)
        style.paragraph_format.keep_with_next = True
        style.paragraph_format.keep_together = True

    header = section.header
    hp = header.paragraphs[0]
    hp.alignment = WD_ALIGN_PARAGRAPH.LEFT
    hp.paragraph_format.space_after = Pt(0)
    set_run_font(hp.add_run("中森跃动  |  OCPX MEDIA API"), size=8.5, bold=True, color=MUTED)
    first_header = section.first_page_header
    first_header.paragraphs[0].text = ""

    footer = section.footer
    fp = footer.paragraphs[0]
    fp.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    fp.paragraph_format.space_before = Pt(0)
    fp.paragraph_format.space_after = Pt(0)
    set_run_font(fp.add_run("中森跃动 OCPX 媒体接口文档  |  "), size=8.5, color=MUTED)
    add_page_number(fp)
    for run in fp.runs:
        set_run_font(run, size=8.5, color=MUTED)

    doc.core_properties.title = "中森跃动 OCPX 媒体接口文档"
    doc.core_properties.subject = "媒体/DSP 曝光、点击监测与转化回传接入规范"
    doc.core_properties.author = "中森跃动"
    doc.core_properties.last_modified_by = "中森跃动"
    doc.core_properties.keywords = "OCPX, 媒体接口, 监测, 回传, 中森跃动"


def build_document():
    create_flow_diagram(FLOW_PATH)
    doc = Document()
    configure_document(doc)
    bullet_num, decimal_num = create_numbering(doc)

    # Cover page: editorial_cover pattern, adapted for a technical reference guide.
    spacer = doc.add_paragraph()
    spacer.paragraph_format.space_after = Pt(86)
    brand = doc.add_paragraph()
    brand.alignment = WD_ALIGN_PARAGRAPH.CENTER
    brand.paragraph_format.space_after = Pt(14)
    set_run_font(brand.add_run("中 森 跃 动"), size=13, bold=True, color=BLUE)

    title = doc.add_paragraph()
    title.alignment = WD_ALIGN_PARAGRAPH.CENTER
    title.paragraph_format.space_after = Pt(9)
    set_run_font(title.add_run("OCPX 媒体接口文档"), size=30, bold=True, color=INK)

    subtitle = doc.add_paragraph()
    subtitle.alignment = WD_ALIGN_PARAGRAPH.CENTER
    subtitle.paragraph_format.space_after = Pt(34)
    set_run_font(subtitle.add_run("媒体 / DSP 接入与转化回传规范"), size=14, color=DARK_BLUE)

    meta = doc.add_table(rows=2, cols=4)
    meta_headers = ["文档版本", "文档状态", "发布日期", "适用范围"]
    meta_values = ["V1.0", "对外联调稿", "2026-08-16", "曝光 / 点击监测与转化回传"]
    for j, text in enumerate(meta_headers):
        cell = meta.rows[0].cells[j]
        cell.text = ""
        set_cell_shading(cell, SOFT_FILL)
        p = cell.paragraphs[0]
        p.paragraph_format.space_after = Pt(0)
        p.alignment = WD_ALIGN_PARAGRAPH.LEFT
        set_run_font(p.add_run(text), size=9.5, bold=True, color=INK)
    set_repeat_header(meta.rows[0])
    for j, text in enumerate(meta_values):
        cell = meta.rows[1].cells[j]
        cell.text = ""
        p = cell.paragraphs[0]
        p.paragraph_format.space_after = Pt(0)
        p.alignment = WD_ALIGN_PARAGRAPH.LEFT
        set_run_font(p.add_run(text), size=9.5, color=BODY)
    set_table_geometry(meta, [1500, 1740, 1800, 4320])
    set_table_borders(meta, color="D7DEE7", size="5")

    cover_note = doc.add_paragraph()
    cover_note.alignment = WD_ALIGN_PARAGRAPH.CENTER
    cover_note.paragraph_format.space_before = Pt(28)
    cover_note.paragraph_format.space_after = Pt(0)
    set_run_font(
        cover_note.add_run("实际监测地址、联调环境与联系人由中森跃动运营人员按项目另行提供。"),
        size=9.5,
        color=MUTED,
    )
    page_break(doc)

    add_heading(doc, "1. 文档说明", 1)
    add_body(
        doc,
        "本文面向接入中森跃动 OCPX 监测与转化回传能力的媒体、广告平台及第三方 DSP。媒体在发生曝光或点击时调用中森跃动分配的监测地址；中森跃动完成数据接收、归因匹配与规则处理后，将转化事件回传至媒体在点击请求中携带的回调地址。",
    )
    add_callout(
        doc,
        "协议边界",
        "本文件说明当前通用 GET 接入方式。专用签名、白名单、特殊事件映射或其他媒体方言，必须在联调时单独确认，不自动继承本文示例。",
    )

    add_heading(doc, "1.1 角色约定", 2)
    role_rows = [
        ["媒体 / DSP", "触发曝光、点击监测请求，并提供可公网访问的转化回调地址。"],
        ["中森跃动", "接收监测请求，完成事件事实、去重、归因与回传规则处理。"],
        ["广告主 / 上游 DSP", "接收曝光或点击并识别后续转化，再回传转化事件。"],
    ]
    add_table(doc, ["角色", "主要职责"], role_rows, [2160, 7200], font_size=10)

    add_heading(doc, "1.2 数据链路", 2)
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_after = Pt(8)
    run = p.add_run()
    run.add_picture(str(FLOW_PATH), width=Inches(6.42))
    doc_pr = run._r.xpath(".//wp:docPr")
    if doc_pr:
        doc_pr[0].set("descr", "媒体曝光点击到中森跃动归因再回传媒体的 OCPX 数据流程图")
    caption = doc.add_paragraph()
    caption.alignment = WD_ALIGN_PARAGRAPH.CENTER
    caption.paragraph_format.space_after = Pt(10)
    set_run_font(caption.add_run("图 1  中森跃动 OCPX 通用媒体链路"), size=9, color=MUTED)

    add_heading(doc, "1.3 快速接入步骤", 2)
    steps = [
        "获取监测地址：由中森跃动运营人员分别提供曝光监测地址和点击监测地址。",
        "配置宏替换：按本文参数表替换媒体宏，重点确认 request_id 与 callback_url。",
        "发送监测请求：曝光或点击发生时，以 HTTP GET 调用对应地址。",
        "接收转化回调：媒体提供公网 HTTP/HTTPS callback_url，并接收 event_type 与可选 currency_amount。",
        "完成联调验收：核对宏值、URL 编码、HTTP 状态、去重行为和事件映射。",
    ]
    for step in steps:
        step_paragraph = add_list_item(doc, step, decimal_num)
        step_paragraph.paragraph_format.space_after = Pt(1)
        step_paragraph.paragraph_format.line_spacing = 1.05

    add_heading(doc, "2. 监测地址获取与使用规则", 1)
    add_body(doc, "媒体或第三方 DSP 完成接入信息确认后，由中森跃动运营人员按监测任务提供两条地址：")
    add_list_item(doc, "曝光监测地址：固定携带 event=IMP。", bullet_num)
    add_list_item(doc, "点击监测地址：固定携带 event=CLK。", bullet_num)
    add_callout(
        doc,
        "重要",
        "地址中的 mId、tId、aId、lId 与 event 为中森跃动生成的固定路由参数。媒体只替换宏值，不得删除、重命名或改写这些参数。",
        warning=True,
    )

    add_heading(doc, "2.1 地址模板", 2)
    add_code_block(
        doc,
        [
            "GET https://{中森跃动分配域名}/ocpx/feedback?",
            "    request_id=__REQUEST_ID__&callback_url=__CALLBACK_URL__&",
            "    event=CLK&mId={固定值}&tId={固定值}&aId={固定值}&lId={固定值}",
        ],
    )
    add_body(doc, "最终地址可能包含双方约定的更多设备或广告层级宏。以上域名与固定值仅用于展示结构，不是可直接调用的生产地址。")

    add_heading(doc, "2.2 通用请求要求", 2)
    general_rows = [
        ["传输协议", "HTTP / HTTPS；生产环境建议仅使用 HTTPS。"],
        ["请求方法", "GET"],
        ["字符编码", "UTF-8"],
        ["Content-Type", "无请求体；参数位于 Query String。"],
        ["超时与重试", "由媒体侧按双方联调策略配置；request_id 必须保持不变，以便幂等识别。"],
    ]
    add_table(doc, ["项目", "要求"], general_rows, [2160, 7200], font_size=10)

    add_heading(doc, "3. 曝光 / 点击接口", 1)
    add_heading(doc, "3.1 固定路由参数", 2)
    route_rows = [
        ["mId", "是", "中森跃动媒体主键；由系统写入监测地址，媒体不得修改。"],
        ["tId", "是", "监测任务主键；由系统写入监测地址，媒体不得修改。"],
        ["aId", "是", "中森跃动广告主主键；不同于媒体侧 advertiser_id。"],
        ["lId", "是", "推广链接主键；由系统写入监测地址，媒体不得修改。"],
        ["event", "是", "事件类型：IMP 表示曝光，CLK 表示点击；由对应监测地址固定。"],
    ]
    add_table(doc, ["参数", "必填", "说明"], route_rows, [1800, 1080, 6480], font_size=9.5)

    add_heading(doc, "3.2 核心媒体参数", 2)
    core_rows = [
        ["request_id", "__REQUEST_ID__", "建议必填", "媒体侧请求唯一标识；重试时保持不变。缺失时系统可能生成回退去重键，但不建议依赖。"],
        ["callback_url", "__CALLBACK_URL__", "条件必填", "通用媒体接收转化回调的公网 HTTP/HTTPS 地址；必须整体 URL Encode。未提供时无法走通用转化回传。"],
    ]
    add_table(doc, ["参数", "参数宏", "要求", "说明"], core_rows, [1740, 2100, 1320, 4200], font_size=9.0)
    add_callout(
        doc,
        "成功语义",
        "监测接口返回 HTTP 200，仅表示中森跃动已完成当前事件事实事务并接受请求；上游广告主投递为后续链路，不等同于广告主已确认接收。",
    )

    add_heading(doc, "3.3 设备标识参数", 2)
    device_rows = [
        ["idfa", "__IDFA__", "否", "iOS IDFA 明文；仅在已合法获取且获得必要授权时传输。"],
        ["idfa_md5", "__IDFA_MD5__", "否", "iOS IDFA 的小写 MD5。"],
        ["caid", "__CAID__", "否", "iOS CAID 单值；具体版本策略由双方确认。"],
        ["caid_list", "__CAID_LIST__", "否", "iOS CAID 版本列表 JSON 字符串；需整体 URL 编码。"],
        ["imei", "__IMEI__", "否", "Android IMEI 明文；仅在合法、必要且已授权时传输。"],
        ["imei_md5", "__IMEI_MD5__", "否", "Android IMEI 的小写 MD5。"],
        ["oaid", "__OAID__", "否", "Android OAID 明文。"],
        ["oaid_md5", "__OAID_MD5__", "否", "Android OAID 的小写 MD5。"],
        ["androidid", "__ANDROIDID__", "否", "Android ID 明文。参数名为 androidid，不是 android_id。"],
        ["androidid_md5", "__ANDROIDID_MD5__", "否", "Android ID 的小写 MD5。"],
        ["aaid", "__AAID__", "否", "阿里设备标识；仅在适用流量场景且已约定时传输。"],
    ]
    add_table(doc, ["参数", "参数宏", "接口必填", "说明"], device_rows, [1740, 2220, 1260, 4140], font_size=8.7)
    add_callout(
        doc,
        "归因建议",
        "接口层不强制所有设备标识同时存在。为提升归因命中率，Android 建议至少提供 OAID、IMEI/IMEI_MD5 或 Android ID 之一；iOS 建议提供 IDFA/IDFA_MD5 或双方确认的 CAID。所有标识均须遵守适用法律、平台政策与用户授权。",
        warning=True,
    )

    add_heading(doc, "3.4 设备环境与广告层级参数", 2)
    env_rows = [
        ["ip", "__IP__", "客户端公网 IP。"],
        ["ua", "__UA__", "User-Agent；必须 URL Encode。"],
        ["os", "__OS__", "操作系统：0 = Android，1 = iOS。"],
        ["model", "__MODEL__", "设备型号。"],
        ["adv_id", "__ADVERTISERID__", "媒体侧广告账户 ID；不要与固定路由参数 aId 混用。"],
        ["plan_id", "__CAMPAIGNID__", "媒体侧广告计划 ID。"],
        ["group_id", "__ADGROUPID__", "媒体侧广告组 / 广告任务 ID。"],
        ["creative_id", "__CREATIVEID__", "媒体侧广告创意 ID。"],
        ["impresss_id", "双方约定", "曝光唯一标识；参数名按当前契约为 impresss_id。"],
    ]
    add_table(doc, ["参数", "建议宏", "说明"], env_rows, [1800, 2280, 5280], font_size=9.0)

    add_heading(doc, "3.5 请求示例", 2)
    add_body(doc, "以下示例仅展示拼接方式。实际域名、固定路由值与宏名称以中森跃动运营人员提供的监测地址为准。")
    add_code_block(
        doc,
        [
            "GET https://tracking.example.invalid/ocpx/feedback?",
            "    request_id=req_20260816_000001&",
            "    oaid=0123456789abcdef&",
            "    os=0&",
            "    callback_url=https%3A%2F%2Fmedia.example.invalid%2Focpx%2Fcallback%3Fclick_id%3Dabc123&",
            "    event=CLK&mId=media_x&tId=task_x&aId=advertiser_x&lId=link_x",
        ],
    )
    add_callout(
        doc,
        "示例域名",
        "example.invalid 专用于文档展示，不可访问。生产联调必须使用中森跃动实际分配的域名与监测地址。",
    )

    add_heading(doc, "3.6 成功响应与状态码", 2)
    add_code_block(
        doc,
        [
            "HTTP/1.1 200 OK",
            '{"ret":0,"code":200,"msg":"提交成功","data":{}}',
        ],
    )
    status_rows = [
        ["200", "当前事件事实事务提交成功；不代表上游广告主已完成接收。"],
        ["400", "缺少固定路由参数，或请求包含已退役的旧路由参数。"],
        ["500", "事件事实、计费或小时汇总事务失败；媒体可按约定策略重试。"],
    ]
    add_table(doc, ["HTTP 状态码", "含义"], status_rows, [2040, 7320], font_size=10)

    add_heading(doc, "4. 转化事件回传", 1)
    add_body(
        doc,
        "广告主侧转化被中森跃动接收并完成归因、去重与规则处理后，中森跃动读取原点击请求中的 callback_url，并以 HTTP GET 向媒体回传转化事件。回调地址已有 Query 参数时使用 & 追加，否则使用 ? 追加。",
    )

    add_heading(doc, "4.1 回调参数", 2)
    callback_rows = [
        ["event_type", "是", "整数", "中森跃动标准转化事件编号，取值见 4.3。"],
        ["currency_amount", "否", "十进制数", "支付类事件可携带金额。按合作方原始十进制金额透传，不自动按“分”换算；币种与单位须在联调时固定。"],
    ]
    add_table(doc, ["参数", "必填", "类型", "说明"], callback_rows, [2100, 1080, 1500, 4680], font_size=9.2)

    add_heading(doc, "4.2 回调示例", 2)
    add_body(doc, "媒体在点击请求中提供的原始回调地址：")
    add_code_block(doc, ["https://media.example.invalid/ocpx/callback?click_id=abc123"])
    add_body(doc, "中森跃动回传“激活”事件：")
    add_code_block(doc, ["GET https://media.example.invalid/ocpx/callback?click_id=abc123&event_type=1"])
    add_body(doc, "中森跃动回传“付费”事件，并携带金额：")
    add_code_block(
        doc,
        ["GET https://media.example.invalid/ocpx/callback?click_id=abc123&event_type=5&currency_amount=123.45"],
    )

    add_heading(doc, "4.3 event_type 取值", 2)
    event_rows = [
        ["1", "激活", "标准事件"],
        ["2", "唤起 / 唤醒", "标准事件"],
        ["3", "登录", "标准事件"],
        ["4", "注册", "标准事件"],
        ["5", "付费", "可携带 currency_amount"],
        ["6", "有效获客", "标准事件"],
        ["7", "次日留存", "标准事件"],
        ["8", "关键页面访问", "标准事件"],
        ["9", "认证", "标准事件"],
        ["10", "下单", "标准事件"],
        ["11", "完单", "标准事件"],
        ["12", "保留", "兼容事件，启用前须确认映射"],
        ["13", "保留", "兼容事件，启用前须确认映射"],
        ["14", "保留", "兼容事件，启用前须确认映射"],
        ["15", "保留", "兼容事件，启用前须确认映射"],
        ["16", "保留", "兼容事件，启用前须确认映射"],
        ["17", "保留", "兼容事件，启用前须确认映射"],
        ["18", "首次激活", "标准事件"],
        ["19", "首次购买", "标准事件"],
    ]
    add_table(doc, ["值", "事件", "备注"], event_rows, [1200, 3000, 5160], font_size=9.3)
    add_callout(
        doc,
        "保留事件",
        "12–17 为兼容保留编号。未形成书面映射前，媒体不得据此直接解释业务含义；中森跃动也不会把参考平台的事件名称自动套用到这些编号。",
        warning=True,
    )

    add_heading(doc, "4.4 媒体回调响应", 2)
    add_body(doc, "媒体回调服务应返回 HTTP 2xx 表示成功接收。中森跃动通用回传不依赖响应 Body 的固定 JSON 结构；如需业务码校验、签名、重试或幂等确认，须作为专用协议另行约定。")

    add_heading(doc, "5. 编码、安全与合规", 1)
    security_items = [
        "callback_url、ua、caid_list 等可能包含保留字符的值必须整体 URL Encode，避免 &、=、? 被误解析为外层参数。",
        "callback_url 仅允许公网 HTTP/HTTPS 地址，不得包含用户名密码、URL Fragment，不得指向 localhost、内网、链路本地或保留域名。",
        "设备标识仅在业务必要、合法且获得适当授权时传输；优先使用符合平台规则的标识或散列值，并遵循数据最小化原则。",
        "不得在文档、聊天记录或工单中明文传递生产密钥、签名材料、完整设备标识或真实用户数据。",
        "媒体侧应使用 request_id 实现幂等；同一事件重试不得生成新的 request_id。",
        "生产域名、IP 白名单、签名或专用适配器配置，以双方确认的联调记录为准。",
    ]
    for item in security_items:
        add_list_item(doc, item, bullet_num)

    add_heading(doc, "6. 联调验收清单", 1)
    checklist_rows = [
        ["监测地址", "已获得曝光与点击两条地址；固定路由参数未被修改。"],
        ["宏替换", "宏均被真实值替换；未替换宏不会被当作真实设备标识。"],
        ["唯一请求", "request_id 在同一事件重试时保持一致。"],
        ["URL 编码", "callback_url、ua、caid_list 等已按 UTF-8 正确编码，且只编码一次。"],
        ["设备参数", "至少提供双方约定的归因标识；采集合规与授权已确认。"],
        ["监测响应", "成功请求收到 HTTP 200 与标准 JSON；失败场景能识别 400/500。"],
        ["回调可达", "callback_url 为公网 HTTP/HTTPS 地址，中森跃动侧可访问。"],
        ["事件映射", "仅使用已确认的 event_type；12–17 未确认前不启用。"],
        ["金额口径", "支付金额的币种、单位与小数精度已书面确认。"],
        ["验收边界", "合成联调、第三方 UAT 与生产验收的证据已分别记录。"],
    ]
    add_table(doc, ["检查项", "验收标准"], checklist_rows, [2160, 7200], font_size=9.4)

    page_break(doc)
    add_heading(doc, "7. 常见问题", 1)
    qa_rows = [
        ["Q1：HTTP 200 是否表示广告主已收到？", "否。HTTP 200 表示中森跃动已接受并提交当前事件事实；后续上游投递是独立链路。"],
        ["Q2：callback_url 为什么需要编码？", "它自身可能包含 ?、&、=。若不整体编码，外层 Query 会被错误拆分。"],
        ["Q3：可以只传曝光或只传点击吗？", "按具体业务与任务配置执行。转化归因通常依赖点击链路，最终要求以联调方案为准。"],
        ["Q4：可以使用内网回调地址吗？", "不可以。通用回调仅允许通过安全校验的公网 HTTP/HTTPS 地址。"],
        ["Q5：事件 12–17 如何使用？", "默认不使用；需由双方书面确认事件名称、去重、金额和验收口径。"],
    ]
    add_table(doc, ["问题", "答复"], qa_rows, [3360, 6000], font_size=9.3)

    add_heading(doc, "8. 文档版本记录", 1)
    version_rows = [
        ["V1.0", "2026-08-16", "初始对外联调稿；明确当前通用 GET 监测、callback_url 回传、event_type 1–19 与安全要求。"],
    ]
    add_table(doc, ["版本", "日期", "变更说明"], version_rows, [1440, 1800, 6120], font_size=9.5)

    add_callout(
        doc,
        "联调资料",
        "实际监测域名、任务地址、测试账号、IP 白名单与技术联系人由中森跃动运营人员按项目提供。本文不包含任何生产凭据。",
    )

    doc.save(DOCX_PATH)
    print(DOCX_PATH)


if __name__ == "__main__":
    build_document()
