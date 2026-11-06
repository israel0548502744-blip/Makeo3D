"""Wraps Hebrew Kotlin string literals in tr(...). Templates become tr("..{0}..", args).
Usage: wrap.py --dry FILES (prints keys as JSON) | wrap.py FILES (rewrites in place, writes keys.json)"""
import json, re, sys
HEB = re.compile('[֐-׿]')
ESC = {'n': '\n', 't': '\t', 'r': '\r', '"': '"', "'": "'", '\\': '\\', '$': '$', 'b': '\b'}

def unescape(s):
    out, i = [], 0
    while i < len(s):
        c = s[i]
        if c == '\\':
            n = s[i + 1]
            if n == 'u':
                out.append(chr(int(s[i + 2:i + 6], 16))); i += 6; continue
            out.append(ESC.get(n, n)); i += 2; continue
        out.append(c); i += 1
    return ''.join(out)

def skip_string(src, i):
    """src[i] == '"' (not raw). Returns (end index after closing quote, parts)."""
    parts, buf, j = [], [], i + 1
    while True:
        c = src[j]
        if c == '\\':
            buf.append(src[j:j + 2]); j += 2; continue
        if c == '"':
            if buf: parts.append(('text', ''.join(buf)))
            return j + 1, parts
        if c == '$' and j + 1 < len(src) and src[j + 1] == '{':
            if buf: parts.append(('text', ''.join(buf))); buf = []
            k = skip_code(src, j + 2, '}')
            parts.append(('expr', src[j + 2:k])); j = k + 1; continue
        if c == '$' and j + 1 < len(src) and (src[j + 1].isalpha() or src[j + 1] == '_'):
            if buf: parts.append(('text', ''.join(buf))); buf = []
            m = re.match(r'[A-Za-z_][A-Za-z0-9_]*', src[j + 1:])
            parts.append(('expr', m.group())); j += 1 + m.end(); continue
        buf.append(c); j += 1

def skip_code(src, i, closer):
    """Returns index of the matching closer from i (balanced braces, strings, comments)."""
    depth = 0
    while i < len(src):
        c = src[i]
        if src.startswith('//', i):
            i = src.index('\n', i)
        elif src.startswith('/*', i):
            i = src.index('*/', i) + 2; continue
        elif src.startswith('"""', i):
            i = src.index('"""', i + 3) + 3; continue
        elif c == '"':
            i, _ = skip_string(src, i); continue
        elif c == "'":
            i = src.index("'", i + 2 if src[i + 1] == '\\' else i + 1) + 1; continue
        elif c == '{': depth += 1
        elif c == '}':
            if depth == 0 and closer == '}': return i
            depth -= 1
        i += 1
    return i

keys = {}

def literal_repl(parts, line):
    texts = ''.join(p[1] for p in parts if p[0] == 'text')
    if not HEB.search(texts) or re.search(r'const val|\.contains\(|Regex\(|@\w+\(', line):
        # keep, but transform nested expressions
        out = ['"']
        for k, v in parts:
            out.append(v if k == 'text' else '${' + transform(v) + '}')
        out.append('"'); return ''.join(out)
    fmt, args = [], []
    for k, v in parts:
        if k == 'text': fmt.append(v.replace('{', '\\u007B') if False else v)
        else:
            fmt.append('{%d}' % len(args)); args.append(transform(v))
    lit = ''.join(fmt)
    keys[unescape(lit)] = keys.get(unescape(lit), '')
    return 'tr("' + lit + '"' + ''.join(', ' + a for a in args) + ')'

def transform(src):
    out, i, last = [], 0, 0
    while i < len(src):
        c = src[i]
        if src.startswith('//', i):
            e = src.find('\n', i); i = len(src) if e < 0 else e; continue
        if src.startswith('/*', i):
            i = src.index('*/', i) + 2; continue
        if src.startswith('"""', i):
            i = src.index('"""', i + 3) + 3; continue
        if c == "'":
            i = src.index("'", i + 2 if src[i + 1] == '\\' else i + 1) + 1; continue
        if c == '"':
            end, parts = skip_string(src, i)
            ls = src.rfind('\n', 0, i) + 1; le = src.find('\n', i); line = src[ls:le if le >= 0 else len(src)]
            out.append(src[last:i]); out.append(literal_repl(parts, line)); last = i = end; continue
        i += 1
    out.append(src[last:]); return ''.join(out)

dry = sys.argv[1] == '--dry'
files = sys.argv[2:] if dry else sys.argv[1:]
for f in files:
    s = open(f).read()
    t = transform(s)
    if not dry and t != s:
        open(f, 'w').write(t)
json.dump(sorted(keys), open('keys.json', 'w'), ensure_ascii=False, indent=0)
print(len(keys), 'keys')
