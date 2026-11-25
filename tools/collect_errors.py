#!/usr/bin/env python3
"""
Turns a messy Gradle / Minecraft log into a SHORT report you can paste into the chat. (Phase 4: first compile and first run.)

  python3 tools/collect_errors.py                        run ./gradlew compileJava, keep the full log in build-log.txt, write build-errors.txt
  python3 tools/collect_errors.py build-log.txt          just read a log you already have
  python3 tools/collect_errors.py --game run/logs/latest.log     read a game log / crash report instead (exceptions, our stack frames, hints)

Build errors are grouped by ROOT CAUSE, because one wrong import produces dozens of follow-on errors:
  1. packages / classes that could not be found (fix these first)
  2. every other error once, with its source line
  3. if javac never ran (plugin, Java or download problems), the Gradle failure text
"""
import collections, os, re, subprocess, sys

OUT = 'build-errors.txt'
MAX_OTHER = 60

def short(path):
    path = path.replace('\\', '/')
    return path.split('src/main/java/')[-1]

def run_gradle():
    here = os.path.dirname(os.path.abspath(__file__))
    root = os.path.join(here, '..')
    cmd = ['gradlew.bat' if os.name == 'nt' else './gradlew', 'compileJava', '--console=plain']
    print('running:', ' '.join(cmd), '(the first run downloads Minecraft and takes several minutes)')
    p = subprocess.run(cmd, cwd=root, capture_output=True, text=True, shell=(os.name == 'nt'))
    text = p.stdout + '\n' + p.stderr
    open(os.path.join(root, 'build-log.txt'), 'w', encoding='utf-8').write(text)
    return text

def parse_build(text):
    blocks, cur = [], None
    for line in text.splitlines():
        m = re.match(r'^(.*\.java):(\d+): (error|warning): (.*)$', line)
        if m:
            cur = {'file': short(m.group(1)), 'line': m.group(2), 'kind': m.group(3), 'msg': m.group(4), 'extra': []}
            if cur['kind'] == 'error': blocks.append(cur)
            else: cur = None
        elif cur is not None and len(cur['extra']) < 5:
            if line.startswith('> ') or line.startswith('* ') or line.startswith('FAILURE'): cur = None
            else: cur['extra'].append(line.rstrip())
    return blocks

def report_build(text):
    out = []
    blocks = parse_build(text)
    pk = collections.Counter(); cls = collections.defaultdict(set); other = []
    for b in blocks:
        msg = b['msg']
        mp = re.match(r'package (\S+) does not exist', msg)
        if mp:
            pk[mp.group(1)] += 1; continue
        if msg == 'cannot find symbol':
            sym = next((e for e in b['extra'] if 'symbol:' in e), '')
            m = re.search(r'symbol:\s+class (\w+)', sym)
            if m: cls[m.group(1)].add(b['file']); continue
        other.append(b)
    out.append('BUILD REPORT: %d javac errors' % len(blocks))
    if pk or cls:
        out.append('\n== 1. FIX FIRST: imports that do not resolve (most other errors follow from these) ==')
        for p, n in pk.most_common(): out.append('  package %-60s %3d x' % (p, n))
        for c, files in sorted(cls.items(), key=lambda kv: -len(kv[1])):
            out.append('  class   %-60s in %d file(s): %s' % (c, len(files), ', '.join(sorted(files)[:3]) + (' ...' if len(files) > 3 else '')))
    if other:
        out.append('\n== 2. other errors (%d%s) ==' % (len(other), ', first %d shown' % MAX_OTHER if len(other) > MAX_OTHER else ''))
        seen = set()
        for b in other:
            key = (b['file'], b['line'], b['msg'])
            if key in seen: continue
            seen.add(key)
            if len(seen) > MAX_OTHER: break
            out.append('%s:%s  %s' % (b['file'], b['line'], b['msg']))
            for e in b['extra'][:4]:
                if e.strip(): out.append('      ' + e.strip())
    if not blocks:
        out.append('\nNo javac errors found.')
        m = re.search(r'\* What went wrong:\n(.*?)(?:\n\* Try:|\Z)', text, re.S)
        if m: out.append('\n== 3. Gradle failure ==\n' + '\n'.join(m.group(1).strip().splitlines()[:14]))
        for l in text.splitlines():
            if re.search(r'Could not (resolve|find|download)|Unsupported (class file|Java)|toolchain|No matching', l): out.append('  ' + l.strip())
        if 'BUILD SUCCESSFUL' in text: out.append('\nBUILD SUCCESSFUL. Next: ./gradlew runClient')
    return '\n'.join(out)

HINTS = [
    (r'ClassNotFoundException: net\.minecraft\.client|NoClassDefFoundError: net/minecraft/client',
     'A CLIENT class was loaded on a dedicated server. Find the dev.ramiel frame below it: that class must only be touched from RamielClient (guarded by the Dist check in RamielMod).'),
    (r'NoSuchMethodError|NoSuchFieldError|AbstractMethodError',
     'A method/field the mod expects does not exist in this NeoForge/Rimulib version. The frame names the caller; check Rimulib >= 1.2.2 first.'),
    (r'InvalidModFileException|Mod file .* has missing or invalid|neoforge\.mods\.toml',
     'mods.toml problem: check src/main/resources/META-INF/neoforge.mods.toml and that Rimulib is in libs/ (dev) or mods/ (play).'),
    (r'Missing or unsupported mandatory dependencies|rimulib',
     'Rimulib is missing or too old: it must be >= 1.2.2 (the NeoForge 1.21.1 build).'),
    (r'ResourceLocationException|Non \[a-z0-9',
     'A resource name has an illegal character (ids must be lowercase a-z 0-9 _ - . /).'),
    (r'Registry .* (?:not|un)initialized|Components not bound yet|Item id not set',
     'Something read a registry object too early (during class load / registration). Use .get() lazily, inside the event or method.'),
    (r'shader|Shader|glsl|GLSL',
     'A shader failed: note the file name. Our only custom shader is assets/ramiel/shaders/fx/ramiel_impact.fsh. Try with shader packs OFF first.'),
    (r'ConcurrentModificationException', 'A list was changed while being looped over; the frame names where.'),
]

def report_game(text):
    lines = text.splitlines()
    out = ['GAME LOG REPORT (%d lines)' % len(lines)]
    # exceptions: a line that is an exception header, followed by its "at" frames and "Caused by" chain
    blocks, i = [], 0
    while i < len(lines):
        if re.search(r'(Exception|Error)\b.*', lines[i]) and not lines[i].startswith('\tat ') and not lines[i].startswith('at ') \
                and re.search(r'^\S*(\[.*\] \[.*\]: )?.*\b(java|net|dev|org|com|cyou)\.[\w.$]+(Exception|Error)', lines[i]):
            j = i + 1
            while j < len(lines) and (lines[j].startswith('\tat ') or lines[j].startswith('\t...') or lines[j].startswith('Caused by') or lines[j].startswith('\tSuppressed')): j += 1
            blocks.append(lines[i:j]); i = j
        else:
            i += 1
    ours = [b for b in blocks if any('dev.ramiel' in l for l in b)]
    chosen = (ours or blocks)[:3]
    if not chosen: out.append('\nNo exceptions found.')
    for k, b in enumerate(chosen):
        out.append('\n== exception %d%s ==' % (k + 1, ' (passes through dev.ramiel)' if b in ours else ''))
        keep = [l for l in b if not l.startswith('\tat ') or 'dev.ramiel' in l or 'cyou.rimuru' in l]
        out += [l[:200] for l in (keep if len(keep) > 3 else b)[:28]]
    blob = text
    hints = [h for pat, h in HINTS if re.search(pat, blob)]
    if hints:
        out.append('\n== likely causes ==')
        out += ['  - ' + h for h in hints]
    ramiel_lines = [l[:220] for l in lines if re.search(r'\bramiel\b', l, re.I) and re.search(r'ERROR|WARN|FATAL', l)][:10]
    if ramiel_lines:
        out.append('\n== ERROR/WARN lines that mention ramiel ==')
        out += ramiel_lines
    return '\n'.join(out)

def main():
    args = sys.argv[1:]
    if args and args[0] == '--game':
        path = args[1] if len(args) > 1 else 'run/logs/latest.log'
        text = open(path, encoding='utf-8', errors='replace').read()
        rep = report_game(text)
    else:
        text = open(args[0], encoding='utf-8', errors='replace').read() if args else run_gradle()
        rep = report_build(text)
    open(OUT, 'w', encoding='utf-8').write(rep + '\n')
    print(rep)
    print('\n(saved to %s: paste that file into the chat)' % OUT)

if __name__ == '__main__':
    main()
