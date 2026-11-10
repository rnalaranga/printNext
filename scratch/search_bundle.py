import re

with open('scratch/topaccess_bundle.js', 'r', encoding='utf-8', errors='ignore') as f:
    text = f.read()

keywords = ['ClearLog', 'DeleteLog', 'JobLog', 'JobHistory', 'ClearJob', 'JobStatus', 'LogClear', 'PrintLog', 'purge', 'purgeJob']
for kw in keywords:
    matches = [m.start() for m in re.finditer(re.escape(kw), text, re.IGNORECASE)]
    print(f'{kw}: {len(matches)} occurrences')
    for pos in matches[:3]:
        snippet = text[max(0, pos-80):min(len(text), pos+120)]
        print('  ...', snippet.replace('\n', ' ').strip(), '...')
