"""Convert eBible USFX (World English Bible) into the Bible Daily asset format.
Output (gzip TSV, one line per verse): book_number \t chapter \t verse \t text
Footnotes (<f>), cross references (<x>), headings and Psalm titles are dropped."""
import re, sys, gzip, html
CANON = "GEN EXO LEV NUM DEU JOS JDG RUT 1SA 2SA 1KI 2KI 1CH 2CH EZR NEH EST JOB PSA PRO ECC SNG ISA JER LAM EZK DAN HOS JOL AMO OBA JON MIC NAM HAB ZEP HAG ZEC MAL MAT MRK LUK JHN ACT ROM 1CO 2CO GAL EPH PHP COL 1TH 2TH 1TI 2TI TIT PHM HEB JAS 1PE 2PE 1JN 2JN 3JN JUD REV".split()
src, out = sys.argv[1], sys.argv[2]
s = open(src, encoding="utf8").read()
s = re.sub(r"<f\b[^>]*>.*?</f>", "", s, flags=re.S)
s = re.sub(r"<x\b[^>]*>.*?</x>", "", s, flags=re.S)
s = re.sub(r"<(d|h|toc|id|ide|cl|vp)\b[^>]*>.*?</\1>", "", s, flags=re.S)
rows = []
for m in re.finditer(r'<book id="([A-Z0-9]+)"(.*?)</book>', s, flags=re.S):
    code, body = m.group(1), m.group(2)
    if code not in CANON: continue
    bn = CANON.index(code) + 1
    for cm in re.finditer(r'<c id="(\d+)"\s*/>(.*?)(?=<c id=|\Z)', body, flags=re.S):
        ch = int(cm.group(1))
        for vm in re.finditer(r'<v id="([\d\-]+)"\s*/>(.*?)<ve\s*/>', cm.group(2), flags=re.S):
            t = re.sub(r"<[^>]+>", " ", vm.group(2))
            t = html.unescape(re.sub(r"\s+", " ", t)).strip()
            t = re.sub(r"\s+([,.;:!?’”)])", r"\1", t)
            t = re.sub(r"([“‘(])\s+", r"\1", t)
            vid = vm.group(1)
            rows.append((bn, ch, int(vid.split("-")[0]), t))
with gzip.open(out, "wt", encoding="utf8", compresslevel=9) as f:
    for r in rows: f.write("%d\t%d\t%d\t%s\n" % r)
print("verses", len(rows), "books", len({r[0] for r in rows}), "chapters", len({(r[0], r[1]) for r in rows}))
