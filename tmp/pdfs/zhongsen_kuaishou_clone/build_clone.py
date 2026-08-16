from __future__ import annotations

import argparse
import io
from copy import deepcopy
from pathlib import Path

from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Inches, Pt
from pypdf import PdfReader, PdfWriter
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas


A3_WIDTH_PT = 841.9
A3_HEIGHT_PT = 1190.25
TITLE = "中森跃动 ocpx 媒体接口文档 1.4"
FONT_PATH = "/System/Library/Fonts/STHeiti Medium.ttc"


def build_pdf(source: Path, output: Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    pdfmetrics.registerFont(TTFont("PingFangSC", FONT_PATH, subfontIndex=0))

    overlay_stream = io.BytesIO()
    overlay = canvas.Canvas(overlay_stream, pagesize=(A3_WIDTH_PT, A3_HEIGHT_PT))
    # The reference title occupies top=94.67..119.87 pt. Clear only that band.
    overlay.setFillColorRGB(1, 1, 1)
    overlay.rect(170, 1059, 502, 48, stroke=0, fill=1)
    overlay.setFillColorRGB(0.19, 0.19, 0.19)
    overlay.setFont("PingFangSC", 23.2)
    overlay.drawCentredString(A3_WIDTH_PT / 2, 1073.0, TITLE)
    overlay.save()
    overlay_stream.seek(0)

    source_reader = PdfReader(str(source))
    title_overlay = PdfReader(overlay_stream).pages[0]
    writer = PdfWriter()
    for index, page in enumerate(source_reader.pages):
        if index == 0:
            page.merge_page(title_overlay, over=True)
        writer.add_page(page)

    metadata = dict(source_reader.metadata or {})
    metadata.update({"/Title": TITLE, "/Author": "中森跃动"})
    writer.add_metadata(metadata)
    with output.open("wb") as stream:
        writer.write(stream)


def set_cellless_page_geometry(section) -> None:
    section.page_width = Pt(A3_WIDTH_PT)
    section.page_height = Pt(A3_HEIGHT_PT)
    section.top_margin = Pt(0)
    section.bottom_margin = Pt(0)
    section.left_margin = Pt(0)
    section.right_margin = Pt(0)
    section.header_distance = Pt(0)
    section.footer_distance = Pt(0)
    section.gutter = Pt(0)


def set_floating_page_image(
    paragraph,
    image_path: Path,
    rel_width_pt: float,
    rel_height_pt: float,
    alt_text: str,
) -> None:
    run = paragraph.add_run()
    inline = run.add_picture(str(image_path), width=Pt(rel_width_pt), height=Pt(rel_height_pt))._inline

    anchor = OxmlElement("wp:anchor")
    anchor.set("distT", "0")
    anchor.set("distB", "0")
    anchor.set("distL", "0")
    anchor.set("distR", "0")
    anchor.set("simplePos", "0")
    anchor.set("relativeHeight", "0")
    anchor.set("behindDoc", "1")
    anchor.set("locked", "1")
    anchor.set("layoutInCell", "1")
    anchor.set("allowOverlap", "1")

    simple_pos = OxmlElement("wp:simplePos")
    simple_pos.set("x", "0")
    simple_pos.set("y", "0")
    anchor.append(simple_pos)

    pos_h = OxmlElement("wp:positionH")
    pos_h.set("relativeFrom", "page")
    pos_h_off = OxmlElement("wp:posOffset")
    pos_h_off.text = "0"
    pos_h.append(pos_h_off)
    anchor.append(pos_h)

    pos_v = OxmlElement("wp:positionV")
    pos_v.set("relativeFrom", "page")
    pos_v_off = OxmlElement("wp:posOffset")
    pos_v_off.text = "0"
    pos_v.append(pos_v_off)
    anchor.append(pos_v)

    anchor.append(deepcopy(inline.extent))
    effect_extent = inline.find(qn("wp:effectExtent"))
    if effect_extent is not None:
        anchor.append(deepcopy(effect_extent))
    wrap_none = OxmlElement("wp:wrapNone")
    anchor.append(wrap_none)
    doc_pr = inline.find(qn("wp:docPr"))
    frame_pr = inline.find(qn("wp:cNvGraphicFramePr"))
    graphic = inline.find(qn("a:graphic"))
    if doc_pr is None or frame_pr is None or graphic is None:
        raise ValueError("Unexpected inline drawing structure")
    doc_pr.set("descr", alt_text)
    anchor.append(deepcopy(doc_pr))
    anchor.append(deepcopy(frame_pr))
    anchor.append(deepcopy(graphic))

    inline.getparent().replace(inline, anchor)


def build_docx(page_images: list[Path], output: Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    doc = Document()
    doc.core_properties.title = TITLE
    doc.core_properties.author = "中森跃动"
    doc.core_properties.subject = "OCPX 媒体接口文档 1.4 一比一版式复刻版"

    section = doc.sections[0]
    set_cellless_page_geometry(section)

    for index, image_path in enumerate(page_images):
        if index > 0:
            section = doc.add_section(WD_SECTION.NEW_PAGE)
            set_cellless_page_geometry(section)
        paragraph = doc.add_paragraph()
        paragraph.alignment = WD_ALIGN_PARAGRAPH.LEFT
        paragraph.paragraph_format.space_before = Pt(0)
        paragraph.paragraph_format.space_after = Pt(0)
        paragraph.paragraph_format.line_spacing = 1
        set_floating_page_image(
            paragraph,
            image_path,
            A3_WIDTH_PT,
            A3_HEIGHT_PT,
            f"中森跃动 OCPX 媒体接口文档第 {index + 1} 页",
        )

    # Remove the default empty paragraph that can precede the first page image.
    body = doc._element.body
    for paragraph in list(body.findall(qn("w:p"))):
        if paragraph.xpath(".//a:blip"):
            continue
        if paragraph.xpath(".//w:br[@w:type='page']"):
            continue
        if not paragraph.xpath(".//w:t") and paragraph is body[0]:
            body.remove(paragraph)
            break

    doc.save(output)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--pdf", type=Path)
    parser.add_argument("--docx", type=Path)
    parser.add_argument("--page-image", action="append", type=Path, default=[])
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if args.pdf:
        build_pdf(args.source, args.pdf)
    if args.docx:
        if len(args.page_image) != 3:
            raise ValueError("DOCX build requires exactly three --page-image values")
        build_docx(args.page_image, args.docx)


if __name__ == "__main__":
    main()
