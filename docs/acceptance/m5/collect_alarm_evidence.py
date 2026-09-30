import io
import sys

# 从 dumpsys alarm 全量输出里筛出"本应用待触发"的闹钟块。
# 只认以 ` com.lnx.app}` 结尾的块 —— 历史记录(Reason=alarm_cancelled 等)不带这个结尾,
# 之前用 grep 'walarm:com.lnx.app' 数到 0 是假的:tag 实际是 *walarm*:...,少了那个星号。

OUT = sys.argv[1]
HEADER = sys.argv[2]

lines = io.open("_dump.txt", encoding="utf-8", errors="replace").read().splitlines()
now = io.open("_now.txt", encoding="utf-8").read().strip()

blocks = []
i = 0
while i < len(lines):
    line = lines[i]
    if "Alarm{" in line and line.rstrip().endswith(" com.lnx.app}"):
        block = [line]
        j = i + 1
        while j < len(lines) and not lines[j].strip().startswith(("ELAPSED", "RTC_", "ELAPSED_WAKEUP", "RTC_WAKEUP")):
            block.append(lines[j])
            j += 1
        blocks.append(block)
        i = j
    else:
        i += 1

fire = [b for b in blocks if any("REMINDER_FIRE" in x for x in b)]
cont = [b for b in blocks if any("REMINDER_CONTINUE" in x for x in b)]

out = [
    HEADER,
    "采集时间: " + now,
    "待触发闹钟总数(lnx): %d" % len(blocks),
    "  其中 提醒(REMINDER_FIRE): %d" % len(fire),
    "  其中 续排(REMINDER_CONTINUE): %d" % len(cont),
    "",
]
for b in blocks:
    out.extend(b)
    out.append("")

io.open(OUT, "w", encoding="utf-8").write("\n".join(out) + "\n")
print("pending=%d fire=%d continue=%d" % (len(blocks), len(fire), len(cont)))
