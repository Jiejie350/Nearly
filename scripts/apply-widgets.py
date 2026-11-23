#!/usr/bin/env python3
"""Copies the native Nearly widget kit into the generated Capacitor android project.
Run after `npx cap add android` and before `npx cap sync android`. Safe to run twice."""
import os, re, shutil, sys

PKG = "com.benjamin.nearly"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KIT = os.path.join(ROOT, "native-widget")
APP = os.path.join(ROOT, "android", "app", "src", "main")
if not os.path.isdir(APP):
    sys.exit("android/ not found - run `npx cap add android` first")

# 1) res/*  (merge)
shutil.copytree(os.path.join(KIT, "res"), os.path.join(APP, "res"), dirs_exist_ok=True)

# 2) java/*  (overwrites the generated MainActivity with ours)
jdir = os.path.join(APP, "java", *PKG.split("."))
os.makedirs(jdir, exist_ok=True)
for f in os.listdir(os.path.join(KIT, "java")):
    shutil.copy(os.path.join(KIT, "java", f), jdir)

# 3) manifest
mpath = os.path.join(APP, "AndroidManifest.xml")
m = open(mpath, encoding="utf-8").read()
if "NearlyWidgetProvider" in m:
    print("Manifest already patched")
else:
    snip = open(os.path.join(KIT, "AndroidManifest-snippet.xml"), encoding="utf-8").read()
    parts = re.split(r"<!-- =====\s*2\).*?===== -->", snip, flags=re.S)
    perms = re.sub(r"<!-- =====\s*1\).*?===== -->", "", parts[0], flags=re.S).strip()
    inner = parts[1].strip()
    have = lambda p: p in m
    perms = "\n".join(l for l in perms.splitlines() if "android:name=" not in l or not have(re.search(r'android:name="([^"]+)"', l).group(1)))
    m = m.replace("<application", perms + "\n\n    <application", 1)
    m = m.replace("</application>", inner + "\n    </application>", 1)
    open(mpath, "w", encoding="utf-8").write(m)
    print("Manifest patched")
print("Nearly widgets applied")
