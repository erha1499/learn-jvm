"""把 content/ 下的手册（导读 + 各单元 + 附录）转成交互网页，并在正文里插入演示的挂载点。

用法（在仓库根目录）：
    python3 site/build.py
生成：
    docs/index.html   交互学习手册
    docs/img/         正文配图（从 content/img 复制）
"""
import html
import json
import re
import shutil
import sys
from pathlib import Path

SITE = Path(__file__).resolve().parent
ROOT = SITE.parent
sys.path.insert(0, str(ROOT / 'scripts'))
from handbook import load  # noqa: E402

DOC = load()
SHELL = (SITE / 'course.shell.html').read_text(encoding='utf-8')

# ---------- 网页版的少量文字调整（改动前断言原文存在，正文改了措辞就会报错提醒） ----------
REPL = [
    ('网页版在关键位置嵌了可以一步一步执行的演示', '正文关键处嵌了可以一步一步执行的演示'),
]
for a, b in REPL:
    assert a in DOC, a
    DOC = DOC.replace(a, b, 1)

LINES = DOC.split('\n')


def chunks():
    """按二级标题切开整本书：导读、各单元、附录。代码块里的行不算标题。"""
    heads, code = [], False
    for i, line in enumerate(LINES):
        if line.startswith('```'):
            code = not code
        elif not code and line.startswith('## '):
            heads.append(i)
    heads.append(len(LINES))
    return [LINES[heads[k]:heads[k + 1]] for k in range(len(heads) - 1)]


# ---------- 行内转换 ----------
ESC_RE = re.compile(r'\\([\\`*_{}\[\]()#+\-.!~|<>])')
LINK_RE = re.compile(r'(?<!\\)\[([^\]]+?)\]\((https?://[^)\s]+)\)')


def inline(s):
    keep = []

    def stash(h):
        keep.append(h)
        return '\x00%d\x00' % (len(keep) - 1)

    s = re.sub(r'`([^`]+)`', lambda m: stash('<code>' + html.escape(m.group(1), quote=False) + '</code>'), s)
    s = LINK_RE.sub(lambda m: stash('<a href="%s" target="_blank" rel="noopener">%s</a>' % (
        html.escape(m.group(2)), html.escape(ESC_RE.sub(r'\1', m.group(1)), quote=False))), s)
    s = ESC_RE.sub(r'\1', s)
    s = html.escape(s, quote=False)
    s = re.sub(r'\*\*(.+?)\*\*', r'<strong>\1</strong>', s)
    for _ in range(2):
        s = re.sub(r'\x00(\d+)\x00', lambda m: keep[int(m.group(1))], s)
    return s


def plain(s):
    return re.sub(r'<[^>]+>', '', inline(s))


# ---------- 块级解析 ----------
def parse(lines):
    blocks, i, n = [], 0, len(lines)
    while i < n:
        L = lines[i]
        if not L.strip():
            i += 1
            continue
        if L.startswith('```'):
            lang = L[3:].strip()
            j = i + 1
            body = []
            while not lines[j].startswith('```'):
                body.append(lines[j])
                j += 1
            blocks.append({'t': 'code', 'lang': lang, 'text': '\n'.join(body)})
            i = j + 1
            continue
        m = re.match(r'^(#{2,4}) (.*)$', L)
        if m:
            blocks.append({'t': 'h', 'lv': len(m.group(1)), 'text': m.group(2)})
            i += 1
            continue
        if L.startswith('|') and i + 1 < n and re.match(r'^\|\s*-', lines[i + 1]):
            rows = []
            j = i
            while j < n and lines[j].startswith('|'):
                if not re.match(r'^\|\s*-', lines[j]):
                    rows.append([c.strip() for c in lines[j].strip().strip('|').split('|')])
                j += 1
            blocks.append({'t': 'table', 'rows': rows, 'raw': L})
            i = j
            continue
        if L.startswith('> '):
            blocks.append({'t': 'quote', 'text': L[2:]})
            i += 1
            continue
        if re.match(r'^(- |\d+\. )', L):
            items = []
            ordered = bool(re.match(r'^\d+\. ', L))
            check = L.startswith('- [ ] ')
            j = i
            while j < n and (re.match(r'^(- |\d+\. )', lines[j]) or re.match(r'^ {2,4}- ', lines[j])):
                cur = lines[j]
                if re.match(r'^ {2,4}- ', cur):
                    items[-1]['sub'].append(cur.strip()[2:])
                else:
                    txt = re.sub(r'^(- \[ \] |- |\d+\. )', '', cur)
                    items.append({'text': txt, 'sub': []})
                j += 1
            blocks.append({'t': 'list', 'ordered': ordered, 'check': check, 'items': items})
            i = j
            continue
        if L.startswith('!['):
            blocks.append({'t': 'embed', 'text': L})
            i += 1
            continue
        blocks.append({'t': 'p', 'text': L})
        i += 1
    return blocks


# ---------- 渲染 ----------
def render_list(b):
    tag = 'ol' if b['ordered'] else 'ul'
    out = ['<%s>' % tag]
    for it in b['items']:
        sub = ''
        if it['sub']:
            sub = '<ul>' + ''.join('<li>%s</li>' % inline(s) for s in it['sub']) + '</ul>'
        out.append('<li>%s%s</li>' % (inline(it['text']), sub))
    out.append('</%s>' % tag)
    return ''.join(out)


def render_table(b):
    head, *body = b['rows']
    h = ''.join('<th>%s</th>' % inline(c) for c in head)
    rows = ''.join('<tr>' + ''.join('<td>%s</td>' % inline(c) for c in r) + '</tr>' for r in body)
    return '<div class="tbl"><table><thead><tr>%s</tr></thead><tbody>%s</tbody></table></div>' % (h, rows)


def render_code(b):
    lang = b['lang'] or 'text'
    return '<pre class="codeblock" data-lang="%s"><code>%s</code></pre>' % (lang, html.escape(b['text'], quote=False))


IMG_RE = re.compile(r'^!\[([^\]]*)\]\((img/[\w.-]+)\)$')


def render_embed(b):
    m = IMG_RE.match(b['text'])
    if not m:
        raise SystemExit('看不懂的配图：' + b['text'])
    if not (ROOT / 'content' / m.group(2)).exists():
        raise SystemExit('配图不存在：' + m.group(2))
    return '<figure class="fig"><img src="%s" alt="%s" loading="lazy"></figure>' % (m.group(2), html.escape(m.group(1)))


def render_block(b):
    t = b['t']
    if t == 'p':
        return '<p>%s</p>' % inline(b['text'])
    if t == 'list':
        return render_list(b)
    if t == 'table':
        return render_table(b)
    if t == 'code':
        return render_code(b)
    if t == 'quote':
        return '<blockquote><p>%s</p></blockquote>' % inline(b['text'])
    if t == 'mount':
        return '<div class="lab-mount" data-lab="%s"></div>' % b['lab']
    if t == 'embed':
        return render_embed(b)
    raise SystemExit('未知块：' + t)


# ---------- “深入一层”折叠块 ----------
# Markdown 里写成 `### 深入一层（可跳过）：标题`（拼成整本后是四级标题），网页里渲染成默认收起的 <details>，
# 范围到下一个标题为止。
DEEP_CLOSE = '</div></details>'


def deep_open_html(text):
    m = re.match(r'^深入一层(?:（可跳过）)?[：:]\s*(.+)$', text)
    if not m:
        raise SystemExit('四级标题只用于“深入一层（可跳过）：标题”：' + text)
    return ('<details class="deep"><summary><span class="deep-tag">深入一层</span><span class="deep-t">%s</span>'
            '</summary><div class="deep-b">' % inline(m.group(1)))


# ---------- 插入演示 ----------
RULES = [  # (动作, 匹配方式, 匹配文本, 演示名)
    ('after', 'p', '按这个思路把 `add(3, 4)` 走一遍', 'bytecode'),
    ('drop', 'table', '| 偏移 | 指令 | 局部变量表', None),  # 网页版由上面的演示代替这张时间线表
    ('after', 'p', '“后置 ++ 先取值再自增”', 'ipp'),
    ('after', 'p', '方法 A 调用方法 B', 'callstack'),
    ('after', 'p', '**栈里存的是对象还是指针？**', 'areas'),
    ('after', 'list', '**1**：两个字面量', 'intern'),
    ('after', 'p', '把 ① 挪到最后一行', 'clinit'),
    ('after', 'code', 'findBootstrapClassOrNull', 'delegation'),
    ('after', 'p', 'TLAB 就像每个人先从公共纸箱里领一叠便利贴', 'tlab'),
    ('after', 'p', '`examples/FieldLayout.java` 还用', 'layout'),
    ('after', 'p', '回到 CycleRef：', 'reach'),
    ('after', 'p', '三种算法的取舍', 'algos'),
    ('after', 'list', '**在 Eden 出生**', 'generations'),
    ('after', 'table', '| 方案 | 破坏哪个条件', 'tricolor'),
    ('drop', 'embed', 'G1 的 Region', None),  # 网页版用下面的演示代替这张静态图
    ('after', 'p', '回收速度跟不上分配速度时，G1 也会退化成 Full GC', 'g1'),
]


def apply_rules(blocks):
    used = set()
    for act, kind, text, lab in RULES:
        for idx, b in enumerate(blocks):
            hit = ((kind == 'p' and b['t'] == 'p' and b['text'].startswith(text))
                   or (kind == 'code' and b['t'] == 'code' and text in b['text'])
                   or (kind == 'table' and b['t'] == 'table' and b['raw'].startswith(text))
                   or (kind == 'list' and b['t'] == 'list' and b['items'][0]['text'].startswith(text))
                   or (kind == 'embed' and b['t'] == 'embed' and text in b['text']))
            if not hit:
                continue
            if act == 'after':
                blocks.insert(idx + 1, {'t': 'mount', 'lab': lab})
            else:
                del blocks[idx]
            used.add(lab or 'drop:' + text)
            break
    return used


# ---------- 自测答案 ----------
ANSWERS = json.loads((ROOT / 'content' / 'answers.json').read_text(encoding='utf-8'))


def render_unit(lines, num, cn):
    head = lines[0][3:]
    m = re.match(r'^单元(\S+) (.+?)：(.+)$', head)
    if m:
        assert m.group(1) == cn, (head, cn)
        title, sub, label = m.group(2), m.group(3), '单元' + cn
    else:
        m = re.match(r'^附录 (.+)$', head)
        assert m, head
        title, sub, label = m.group(1), '', '附录'
    blocks = parse(lines[1:])
    used = apply_rules(blocks)
    out, toc = [], []
    out.append('<header class="u-head"><div class="u-no">%s</div><h1>%s</h1>%s</header>'
               % (label, html.escape(title), '<p class="u-sub">%s</p>' % html.escape(sub) if sub else ''))
    i, sec_open, deep_open, mode, quiz_n = 0, False, False, None, 0
    while i < len(blocks):
        b = blocks[i]
        if b['t'] == 'h' and b['lv'] == 4:
            if deep_open:
                out.append(DEEP_CLOSE)
            out.append(deep_open_html(b['text']))
            deep_open = True
            i += 1
            continue
        if b['t'] == 'h' and b['lv'] == 3:
            if deep_open:
                out.append(DEEP_CLOSE)
                deep_open = False
            if sec_open:
                out.append('</section>')
            text = b['text']
            mm = re.match(r'^(\d+)\.(\d+) ', text)
            if mm:
                assert int(mm.group(1)) == num, text
                sid = 'u%d-%s' % (num, mm.group(2))
                mode = None
            elif text == '面试这样说':
                sid, mode = 'u%d-interview' % num, 'interview'
            elif text == '自测':
                sid, mode = 'u%d-quiz' % num, 'quiz'
            else:
                raise SystemExit('未知小节：' + text)
            cls = ' class="%s"' % mode if mode else ''
            extra = '<span class="qz-progress"></span>' if mode == 'quiz' else ''
            out.append('<section id="%s"%s><h2>%s%s</h2>' % (sid, cls, inline(text), extra))
            toc.append((sid, plain(text)))
            sec_open = True
            i += 1
            continue
        if not sec_open:
            if b['t'] == 'p' and b['text'].startswith('本单元的核心'):
                out.append('<p class="core">%s</p>' % inline(b['text'][len('本单元的核心：'):]))
            elif b['t'] == 'p' and b['text'] == '**学完这个单元，你能：**':
                goals = blocks[i + 1]
                assert goals['t'] == 'list'
                out.append('<div class="goals"><div class="g-h">学完这个单元，你能</div><ul>%s</ul></div>'
                           % ''.join('<li>%s</li>' % inline(it['text']) for it in goals['items']))
                i += 2
                continue
            else:
                out.append(render_block(b))
            i += 1
            continue
        if mode == 'interview' and b['t'] == 'p' and b['text'].startswith('**问：'):
            q = b['text'].strip('*')
            q = q[len('问：'):] if q.startswith('问：') else q  # 小节标题已经说明是面试题
            a = blocks[i + 1]
            assert a['t'] == 'quote'
            out.append('<details class="qa"><summary>%s</summary><div class="ans">%s</div></details>'
                       % (inline(q), inline(a['text'])))
            i += 2
            continue
        if mode == 'quiz' and b['t'] == 'list' and b['check']:
            items = []
            for it in b['items']:
                quiz_n += 1
                qid = 'u%d-q%d' % (num, quiz_n)
                ans = ANSWERS.get(it['text'])
                if ans is None:
                    raise SystemExit('缺少自测答案：' + it['text'])
                items.append('<li class="qz" data-qid="%s"><div class="qz-q">%s</div><div class="qz-act">'
                             '<button type="button" class="btn sm qz-show" aria-expanded="false">答案</button>'
                             '<label class="qz-ok"><input type="checkbox" class="qz-cb"> 我会了</label></div>'
                             '<div class="qz-a" hidden>%s</div></li>' % (qid, inline(it['text']), ans))
            out.append('<ol class="quizlist">%s</ol>' % ''.join(items))
            i += 1
            continue
        out.append(render_block(b))
        i += 1
    if deep_open:
        out.append(DEEP_CLOSE)
    if sec_open:
        out.append('</section>')
    return '\n'.join(out), toc, used, title


def render_home(lines):
    """导读：单元顺序表交给页面脚本画成卡片，这里只渲染其余部分。"""
    blocks = parse(lines[1:])
    out, mottos = [], {}
    skip_table = False
    for b in blocks:
        if b['t'] == 'h' and b['text'] == '单元顺序':
            skip_table = True
            continue
        if skip_table and b['t'] == 'table':
            for row in b['rows'][1:]:
                cn = row[0].split(' ')[0]
                mottos[cn] = row[1] + '。'
            skip_table = False
            continue
        if b['t'] == 'h':
            out.append('<h2>%s</h2>' % inline(b['text']))
        else:
            out.append(render_block(b))
    return '\n'.join(out), mottos


CN_NUM = '一二三四五六七八九'
parts = chunks()
assert parts[0][0].startswith('## 导读'), parts[0][0]
home, MOTTO = render_home(parts[0])
units, all_used = [], set()
for k, lines in enumerate(parts[1:], 1):
    cn = '附录' if lines[0].startswith('## 附录') else CN_NUM[k - 1]
    body, toc, used, title = render_unit(lines, k, cn)
    all_used |= used
    units.append({'id': 'u%d' % k, 'cn': cn, 'title': title, 'body': body, 'toc': toc, 'motto': MOTTO.get(cn, '')})
assert units[-1]['cn'] == '附录', '最后一部分应当是附录'
assert all(u['motto'] for u in units[:-1]), '导读的单元顺序表缺少某个单元的一句话核心'

need = {r[3] or 'drop:' + r[2] for r in RULES}
if all_used != need:
    raise SystemExit('有演示没插进去：%s' % (need - all_used))

views = ['<article class="view" id="view-home" data-view="home" hidden><!--HOME-HERO--><div class="prose">%s</div></article>' % home]
for u in units:
    views.append('<article class="view" id="view-%s" data-view="%s" hidden><div class="prose">%s</div></article>'
                 % (u['id'], u['id'], u['body']))
toc_json = json.dumps([{'id': u['id'], 'cn': u['cn'], 'title': u['title'], 'motto': u['motto'],
                        'sections': [{'id': s, 'title': t} for s, t in u['toc']]} for u in units], ensure_ascii=False)


def full_page(fragment):
    """页面源码里 <title>、<link>、<style> 在前，正文在后；拆开后包成完整的 HTML 文档。"""
    cut = fragment.index('</style>') + len('</style>')
    return ('<!doctype html>\n<html lang="zh-CN">\n<head>\n<meta charset="utf-8">\n'
            '<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">\n'
            '<style>[hidden]{display:none!important} img{max-width:100%}</style>\n'
            + fragment[:cut] + '\n</head>\n<body>\n' + fragment[cut:] + '\n</body>\n</html>\n')


page = SHELL.replace('<!--VIEWS-->', '\n'.join(views)).replace('/*TOC_JSON*/null', toc_json)
assert '/*TOC_JSON*/' not in page
out = ROOT / 'docs' / 'index.html'
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(full_page(page), encoding='utf-8')
img_out = ROOT / 'docs' / 'img'
if img_out.exists():
    shutil.rmtree(img_out)
shutil.copytree(ROOT / 'content' / 'img', img_out)
print('写出 docs/index.html（%d 个单元和附录，演示 %d 个，小节 %d 个）和 docs/img/'
      % (len(units) - 1, sum(1 for r in RULES if r[3]), sum(len(u['toc']) for u in units)))
