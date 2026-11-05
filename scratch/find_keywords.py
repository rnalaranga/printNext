import json

with open("diagnose_2528.json", "r", encoding="utf-8") as f:
    d = json.load(f)

for k, v in d.items():
    if "KeyMap" in v or "EnergySaver" in v or "sleephook" in v or "AutoClear" in v:
        print(f"=== Found in {k} ===")
        lines = v.splitlines()
        for line in lines:
            if any(x in line for x in ["KeyMap", "EnergySaver", "sleephook", "AutoClear", "DefaultScreen", "AutoScreenChange"]):
                print(line[:120])
