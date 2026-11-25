#!/usr/bin/env python3
"""
Syntax / consistency check for the mod WITHOUT Minecraft, NeoForge or Rimulib installed.

Runs javac over src/main/java. Every line that merely says "that library is not installed" is hidden; what is left are the
mistakes that matter: syntax errors, wrong argument lists, duplicate variables, type mismatches, and "cannot find symbol" that
points at OUR OWN classes (a misspelt method or variable). It cannot check calls into Minecraft / NeoForge / Rimulib: those need
the real compile (HANDOFF phase 4).

LIMITS (tested by injecting mistakes): it reliably catches syntax errors, duplicate variables, missing returns, flow errors and type
errors between our own types. It CANNOT catch a misspelt call or wrong argument list when any argument's type comes from a library
(javac stays silent about those when it cannot resolve an argument), and javac stops after the first parse error, so fix syntax
errors first and run it again.

  python3 tools/check_syntax.py            (needs a JDK: apt-get install -y openjdk-21-jdk-headless)
"""
import glob, os, re, subprocess, sys, tempfile

root = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
files = glob.glob(os.path.join(root, 'src/main/java/**/*.java'), recursive=True)
own = {os.path.splitext(os.path.basename(f))[0] for f in files}
out = tempfile.mkdtemp()
p = subprocess.run(['javac', '-proc:none', '-Xmaxerrs', '100000', '-d', out] + files, capture_output=True, text=True)
text = p.stderr

# split into error blocks
blocks, cur = [], None
for line in text.splitlines():
    if re.match(r'^\S+\.java:\d+: error:', line):
        cur = [line]; blocks.append(cur)
    elif cur is not None:
        cur.append(line)

_ext_cache = {}
def extends_library(cls):
    """True if our class extends a class we do not define (so unknown members may simply be inherited)."""
    if cls in _ext_cache: return _ext_cache[cls]
    result = False
    for f in files:
        if os.path.splitext(os.path.basename(f))[0] == cls:
            src = open(f, encoding='utf-8').read()
            m = re.search(r'\bclass\s+%s\b[^{]*?\bextends\s+([\w.]+)' % re.escape(cls), src)
            result = bool(m) and m.group(1).split('.')[-1] not in own
    _ext_cache[cls] = result
    return result

real, noise = [], 0
for b in blocks:
    head = b[0]
    msg = head.split('error:', 1)[1].strip()
    body = '\n'.join(b)
    if 'does not exist' in msg or 'does not override' in msg or msg.startswith('static import only from classes'):
        noise += 1; continue
    if msg == 'cannot find symbol':
        sym = re.search(r'symbol:\s+(\w+)\s+(\S+)', body)
        loc = re.search(r'location:\s+(.*)', body)
        kind = sym.group(1) if sym else ''
        loc_text = loc.group(1) if loc else ''
        name = sym.group(2) if sym else ''
        if kind == 'class' or (kind == 'variable' and re.match(r'^[A-Z][a-z]', name)):
            noise += 1; continue                      # a library type (an import) used by name
        # the symbol must be a member of one of OUR classes that does not inherit from a library class
        m = re.search(r'class (\w+)', loc_text)
        cls = m.group(1) if m else None
        if cls in own and not extends_library(cls) and 'of type' not in loc_text:
            real.append(b)
        else:
            noise += 1                                # inherited from a library class, or a call on a library object
        continue
    if msg.startswith('incompatible types') and ('cannot be converted' in msg) and not re.search(r'\bdev\.ramiel|\bint\b|\bdouble\b|\bfloat\b|\blong\b|\bboolean\b|\bString\b', msg):
        noise += 1; continue
    real.append(b)

print('javac messages: %d   hidden as "library not installed": %d   to look at: %d' % (len(blocks), noise, len(real)))
for b in real:
    print('\n'.join(b[:5])); print()
sys.exit(1 if real else 0)
