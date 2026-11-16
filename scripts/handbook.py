"""把 content/ 下按单元拆开的 Markdown 拼回一整本手册，网页构建和飞书同步共用。

content/README.md 是书名和导读，NN-*.md 是各单元和附录，按文件名排序。
拼接时单元文件的标题各降一级，导读里指向单元文件的链接还原成纯文字。
"""
import re
from pathlib import Path

CONTENT = Path(__file__).resolve().parent.parent / 'content'
MD_LINK = re.compile(r'\[([^\]]+)\]\(\d\d-[\w-]+\.md\)')
HEADING = re.compile(r'^#{1,5} ')


def chapters():
    return sorted(CONTENT.glob('[0-9][0-9]-*.md'))


def demote(text):
    out, code = [], False
    for line in text.split('\n'):
        if line.startswith('```'):
            code = not code
        elif not code and HEADING.match(line):
            line = '#' + line
        out.append(line)
    return '\n'.join(out)


def load():
    parts = [MD_LINK.sub(r'\1', (CONTENT / 'README.md').read_text(encoding='utf-8').rstrip('\n'))]
    parts += [demote(p.read_text(encoding='utf-8').rstrip('\n')) for p in chapters()]
    return '\n\n'.join(parts) + '\n'
