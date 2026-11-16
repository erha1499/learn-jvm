"""把 content/ 下的手册拼成一篇，单向同步到一篇飞书云文档（整篇覆盖：仓库 → 飞书）。

正文以仓库为准，飞书只是镜像；在飞书里直接做的修改会在下次同步时被覆盖。

用法（在仓库根目录运行）：
    FEISHU_DOC=<飞书文档链接或 token> python3 scripts/sync_feishu.py
    python3 scripts/sync_feishu.py --dry-run     # 只生成 .sync/ 下的文件，不上传

需要先安装并登录 lark-cli（lark-cli auth login）。
转换规则：
    - 去掉一级标题（飞书文档自己的标题就是书名），顶部加一段“以仓库为准”的提示
    - “面试这样说”等引用块转成飞书的提示框（callout）
    - 配图是 SVG，先在 macOS 上用 qlmanage 转成浅色 PNG（.sync/img/），再交给 lark-cli 上传
"""
import json
import os
import re
import subprocess
import sys
from pathlib import Path

from handbook import load

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / '.sync' / 'feishu.md'
IMG_OUT = ROOT / '.sync' / 'img'
REPO = 'https://github.com/erha1499/learn-jvm'
SITE = 'https://erha1499.github.io/learn-jvm/'
BANNER = ('<callout emoji="📌">\n'
          '本文由 GitHub 仓库 [learn-jvm](%s) 的 content/ 单向同步而来，请不要在飞书里直接修改，'
          '改动会在下次同步时被覆盖。交互版：[在线阅读](%s)\n'
          '</callout>') % (REPO, SITE)
WIDTH = 1720


def svg_to_png(name):
    """去掉 SVG 里的深色样式，用 qlmanage 渲染成正方形缩略图，再按 viewBox 的比例裁掉上下留白。"""
    src = (ROOT / 'content' / 'img' / name).read_text(encoding='utf-8')
    light = re.sub(r'@media \(prefers-color-scheme: dark\)\{.*?\n\}\n', '', src, flags=re.S)
    assert 'prefers-color-scheme' not in light, name + ' 的深色样式没有去掉'
    _, _, w, h = (float(x) for x in re.search(r'viewBox="([^"]+)"', src).group(1).split())
    IMG_OUT.mkdir(parents=True, exist_ok=True)
    tmp = IMG_OUT / name
    tmp.write_text(light, encoding='utf-8')
    subprocess.run(['qlmanage', '-t', '-s', str(WIDTH), '-o', str(IMG_OUT), str(tmp)], check=True, capture_output=True)
    png = IMG_OUT / (name + '.png')
    subprocess.run(['sips', '--cropToHeightWidth', str(round(WIDTH * h / w)), str(WIDTH), str(png)], check=True, capture_output=True)
    final = IMG_OUT / name.replace('.svg', '.png')
    png.replace(final)
    tmp.unlink()
    return final


def convert(md):
    lines = md.split('\n')
    if lines and lines[0].startswith('# '):
        lines = lines[1:]
    out, in_code, quotes, images = [BANNER], False, 0, 0
    for line in lines:
        if line.startswith('```'):
            in_code = not in_code
            out.append(line)
            continue
        if in_code:
            out.append(line)
            continue
        if line.startswith('> '):
            body = line[2:]
            if '<' in re.sub(r'`[^`]*`', '', body):
                sys.exit('引用块里有尖括号，转成提示框可能出错，请检查：' + body[:60])
            out.append('<callout emoji="💬">\n' + body + '\n</callout>')
            quotes += 1
            continue
        m = re.match(r'^!\[([^\]]*)\]\(img/([\w.-]+\.svg)\)$', line)
        if m:
            png = svg_to_png(m.group(2))
            line = '![%s](@./%s)' % (m.group(1), png.relative_to(ROOT))
            images += 1
        out.append(line)
    return '\n'.join(out), quotes, images


def main():
    dry = '--dry-run' in sys.argv
    if sys.platform != 'darwin':
        sys.exit('配图转换依赖 macOS 的 qlmanage 和 sips，请在 macOS 上运行')
    text, quotes, images = convert(load())
    OUT.parent.mkdir(exist_ok=True)
    OUT.write_text(text, encoding='utf-8')
    print('已生成 %s：%d 个提示框，%d 张图片' % (OUT.relative_to(ROOT), quotes + 1, images))
    if dry:
        return
    doc = os.environ.get('FEISHU_DOC')
    if not doc:
        sys.exit('请用环境变量 FEISHU_DOC 指定飞书文档的链接或 token')
    cmd = ['lark-cli', 'docs', '+update', '--as', 'user', '--doc', doc, '--command', 'overwrite',
           '--doc-format', 'markdown', '--content', '@./' + str(OUT.relative_to(ROOT))]
    res = subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True)
    try:
        data = json.loads(res.stdout)
    except ValueError:
        sys.exit('lark-cli 返回了非 JSON 内容：\n' + (res.stdout or res.stderr)[-2000:])
    if not data.get('ok'):
        sys.exit('同步失败：\n' + json.dumps(data, ensure_ascii=False, indent=2)[-2000:])
    doc_info = data['data']['document']
    uploaded = sum(1 for b in doc_info.get('new_blocks', []) if b.get('block_type') == 'image')
    print('同步完成：%s（修订号 %s，上传图片 %d 张）' % (doc_info.get('url'), doc_info.get('revision_id'), uploaded))
    for w in data['data'].get('warnings') or []:
        print('警告：', w)


if __name__ == '__main__':
    main()
