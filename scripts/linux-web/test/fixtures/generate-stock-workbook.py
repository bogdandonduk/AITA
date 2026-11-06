#!/usr/bin/env python3
"""Generate synthetic XLSX locally for the import boundary/performance check. No user data."""
import argparse
from pathlib import Path
from xml.sax.saxutils import escape
from zipfile import ZipFile, ZIP_DEFLATED


def workbook(destination: Path, count: int) -> None:
    headers = ["Name", "Barcode", "Quantity", "Unit", "Sale price", "Supply price", "Category", "Description"]
    rows = [headers] + [
        [f"Тестовый товар {i}", f"000{i:010d}", "1.5", "kg", "1500", "1000", "", "Только тест · " * 18]
        for i in range(1, count + 1)
    ]
    sheet = '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>'
    for number, row in enumerate(rows, 1):
        sheet += f'<row r="{number}">' + "".join(
            f'<c r="{chr(65 + column)}{number}" t="inlineStr"><is><t>{escape(value)}</t></is></c>'
            for column, value in enumerate(row)
        ) + '</row>'
    sheet += '</sheetData></worksheet>'
    destination.parent.mkdir(parents=True, exist_ok=True)
    with ZipFile(destination, "w", compression=ZIP_DEFLATED) as output:
        output.writestr('[Content_Types].xml', '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>')
        output.writestr('_rels/.rels', '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>')
        output.writestr('xl/workbook.xml', '<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Synthetic stock" sheetId="1" r:id="rId1"/></sheets></workbook>')
        output.writestr('xl/_rels/workbook.xml.rels', '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>')
        output.writestr('xl/worksheets/sheet1.xml', sheet)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('destination', type=Path)
    parser.add_argument('--rows', type=int, default=3000)
    args = parser.parse_args()
    if not 1 <= args.rows <= 3001:
        parser.error('Use 1–3001 synthetic data rows')
    workbook(args.destination, args.rows)
