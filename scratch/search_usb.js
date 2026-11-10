const fs = require('fs');
const content = fs.readFileSync('scratch/topaccess_bundle.js', 'utf8');

const regexes = [
    /PrintJobType/gi,
    /USBDirectPrint/gi,
    /MediaPrint/gi,
    /USB Print/gi,
    /PRINT_JOB_TYPE/gi
];

for (const r of regexes) {
    let match;
    let count = 0;
    while ((match = r.exec(content)) !== null && count < 5) {
        const start = Math.max(0, match.index - 100);
        const end = Math.min(content.length, match.index + 150);
        console.log(`=== MATCH ${r} ===`);
        console.log(content.substring(start, end).replace(/\n/g, ' '));
        count++;
    }
}
