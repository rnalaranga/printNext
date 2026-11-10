import urllib.request
import re

url = "http://192.168.1.11/topaccess.e49ee3eefb40b1c28871.bundle.js"
print("Downloading JS...")
req = urllib.request.urlopen(url)
content = req.read().decode('utf-8', errors='ignore')
print(f"Length: {len(content)}")

# Look for login endpoints
matches = re.findall(r'["\'](/[^"\']*login[^"\']*)["\']', content, re.IGNORECASE)
print("Login paths:", set(matches))

matches_cws = re.findall(r'["\'](/contentwebserver[^"\']*)["\']', content, re.IGNORECASE)
print("CWS paths:", set(matches_cws))

matches_auth = re.findall(r'["\'](/api[^"\']*)["\']', content, re.IGNORECASE)
print("API paths:", set(matches_auth)[:10])

# Search for csrf or password
matches_kw = re.findall(r'([a-zA-Z0-9_]*password[a-zA-Z0-9_]*)', content, re.IGNORECASE)
print("Password keys:", set(matches_kw)[:15])
