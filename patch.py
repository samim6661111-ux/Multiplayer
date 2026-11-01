import re, sys
path = sys.argv[1]
s = open(path, encoding='utf-8').read()
hook = "    invoke-static {p0}, Lcom/gtavsource/android/MpMenu;->show(Landroid/app/Activity;)V\n"
if "MpMenu;->show" in s:
    print("already patched"); sys.exit(0)
m = re.search(r"\.method [^\n]*onResume\(\)V.*?\.end method", s, re.S)
if not m: sys.exit("onResume not found")
body = m.group(0)
sup = "invoke-super {p0}, Landroid/app/Activity;->onResume()V\n"
i = body.find(sup)
if i < 0: sys.exit("super call not found")
j = i + len(sup)
new = body[:j] + hook + body[j:]
s = s[:m.start()] + new + s[m.end():]
open(path, 'w', encoding='utf-8').write(s)
print("patched onResume")
