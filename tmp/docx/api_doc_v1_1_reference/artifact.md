# API 文档 v1.1 参考模板执行契约

## Reference

- retained source: `/Users/karl/Desktop/OCPX文档/API文档(1).docx`
- SHA-256: `f7802b4ffd7dcdcaa53e470672005106605b5043b0f9000820350567d8461f8a`
- source size: 23 KB
- rendered page count: 5（第 5 页为空白尾页，不作为内容样式）
- section count: 1
- visual evidence: `/Users/karl/code/yw-tech/smart-converge-x/tmp/docx/api_doc_1_reference_render/`
- section evidence: `section_audit.py` 输出；style evidence: `/Users/karl/code/yw-tech/smart-converge-x/tmp/docx/api_doc_1_reference_style.json`

## Page system

- A4 portrait: 8.2681 × 11.6931 in.
- margins: left/right/top/bottom all 0.50 in.
- header distance 0.4944 in; footer distance 0.5924 in.
- one section, no first-page special header, no odd/even split, no visible header/footer furniture.
- source uses natural pagination; final may add pages for a larger parameter table but must keep the same A4 geometry.

## Typography and hierarchy

- title: source `Heading 3`, centered, bold black; final slot is `中森跃动 OCPX API 文档 v1.1`.
- primary numbered sections: source `Heading 4`, bold black, left aligned.
- example subsection: source `Heading 6`, bold black, left aligned.
- body: 9 pt visual baseline, black/dark gray, compact paragraph rhythm.
- reference blue is used for monitoring URLs and complete request examples; red is used only for critical macro/encoding warnings.
- source relies on direct formatting and mixed fonts; final may normalize runs to an embeddable Chinese-capable font while preserving the visible hierarchy and density.

## Lists and tables

- bullet lists are used for protocol facts and event examples.
- main parameter table: 5 columns with source grid widths `1645/2011/1027/959/3397` DXA; headers `参数名/参数宏/最大长度/是否必填/参数说明`.
- table uses `Table Grid`, fixed layout, vertically centered cells, 9 pt text, repeating header, natural row growth; no fixed row height that may clip content.
- final table may add accepted fields required by the current platform contract while preserving the five-column proportions.

## Components and content flow

1. Centered title.
2. `1. 点击/曝光监测链接`: template URL, red macro warning, HTTPS/GET facts, parameter table, response and filled request example.
3. `2. 回调说明`: callback field behavior, event mapping and optional amount.
4. `3. 示例`: click request and conversion callback examples.

## Slot map

- `word/document.xml` body paragraphs and the single parameter table are editable and will be rewritten for the current platform contract.
- title/version stays v1.1 and gains the `中森跃动 OCPX` product-facing name.
- source host, source route tokens, `callback`, and string `action_type` examples must be replaced because they belong to another protocol.
- current public contract must use `/ocpx/feedback`, fixed route IDs `mId/tId/aId/lId`, `callback_url`, and downstream `event_type` plus optional `currency_amount`.
- reference styles, theme, numbering definitions, section geometry, table visual language, and package relationships are preserve-only unless python-docx must serialize the new body.

## Text coverage and package preservation

- inspected body paragraphs, table cells, styles, numbering, fields and relationships.
- no drawings, headers, footers, comments or content controls.
- one hyperlink field exists in the source body and is editable because its example URL belongs to the source protocol.
- package parts present: content types, root rels, core/app/custom properties, document and rels, styles, numbering, settings, font table, theme, footnotes and endnotes.
- preserve source file byte-for-byte; write final output to a different path.

## Fidelity and correctness gates

- source hash must remain unchanged after authoring.
- final must retain A4 0.5-in geometry, centered title, numbered section hierarchy, blue URL examples, red warnings, five-column parameter table and compact body rhythm.
- final must not retain the source host, source-specific route IDs, `action_type` callback contract, old product branding, or any `SCX` visible brand text.
- final must contain v1.1 consistently in title, filename and metadata.
- render every final page; reject missing glyphs, clipped tables, blank trailing pages, broken URLs, or unexplained pagination.
