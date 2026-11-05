import json

with open("diagnose_2528.json", "r", encoding="utf-8") as f:
    d = json.load(f)

for k, v in d.items():
    if "EWBSettings" in k or "UI" in k or "Panel" in k:
        print(f"=== {k} ===")
        print(v[:500] if len(v) > 500 else v)
