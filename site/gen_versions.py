#!/usr/bin/env python3
"""Builds src/versions.json for the site: every released version of Monolith with its date,
notes (from the in-app Journal), build number, APK size and download link.
data/builds.json says which build of the CI is the official one of each version."""
import json, re, subprocess, urllib.request, os
HERE = os.path.dirname(os.path.abspath(__file__))
REPO = "Xoushal-from-Aprilia/Monolith"
SETTINGS = os.path.join(HERE, "..", "app/src/main/java/com/monolith/app/ui/screens/Settings.kt")

builds = json.load(open(os.path.join(HERE, "data/builds.json")))
rel = []
for page in range(1, 5):
    out = subprocess.run(["curl", "-s", f"https://api.github.com/repos/{REPO}/releases?per_page=100&page={page}"], capture_output=True, text=True).stdout
    chunk = json.loads(out)
    if not chunk: break
    rel += chunk
assets = {}
for r in rel:
    if r["tag_name"].startswith("build-") and r["assets"]:
        a = r["assets"][0]
        assets[int(r["tag_name"][6:])] = {"url": a["browser_download_url"], "size": a["size"], "at": r["created_at"]}

s = open(SETTINGS, encoding="utf-8").read()
body = s[s.index("private val JOURNAL = listOf("):]
journal = {}
for m in re.finditer(r'Release\(\s*"([^"]+)",\s*"([^"]+)",\s*listOf\((.*?)\),\s*\)', body, re.S):
    lines = [x.encode().decode("unicode_escape").encode("latin-1").decode("utf-8") for x in re.findall(r'"((?:[^"\\]|\\.)*)"', m.group(3))]
    journal[m.group(1)] = {"date": m.group(2), "notes": lines}

versions = []
for v, j in journal.items():
    if v in ("0.1.0", "0.1.1"):
        continue  # folded into 0.1.2 below
    notes = list(j["notes"])
    if v == "0.1.2":
        notes = journal.get("0.1.0", {}).get("notes", []) + journal.get("0.1.1", {}).get("notes", []) + notes
    b = builds.get(v)
    a = assets.get(b) if b else None
    versions.append({
        "version": v, "date": j["date"], "notes": notes, "build": b,
        "url": a["url"] if a else None, "size": a["size"] if a else None,
    })
versions.sort(key=lambda x: [int(p) for p in x["version"].split(".")], reverse=True)
json.dump(versions, open(os.path.join(HERE, "src/versions.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(len(versions), "versions; latest", versions[0]["version"], versions[0]["build"], versions[0]["size"])
missing = [x["version"] for x in versions if not x["url"]]
print("without apk:", missing)
