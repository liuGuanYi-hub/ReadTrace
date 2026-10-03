#!/usr/bin/env python3
"""从 `adb shell dumpsys activity top` 的输出中求解控件的屏幕绝对坐标。

## 为什么需要这个脚本

自动化验证 App 时经常遇到两个障碍：

1. **非 exported 的 Activity 无法 `am start`**（抛 SecurityException），只能靠 UI 点击进入；
2. **`uiautomator dump` 对带常驻自绘动画的页面会失败**（`could not get idle state`），
   此时唯一可用的视图信息来源是 `dumpsys activity top` 的 View Hierarchy。

但 dumpsys 给出的 `l,t-r,b` 是**相对直接父容器的布局坐标**（且**不含 ScrollView 的 scrollY**），
不能直接拿去 `input tap`。必须按缩进重建 view 树、累加整条祖先链的偏移，才能得到屏幕坐标。

## 用法

    adb shell dumpsys activity top > top.txt
    python dumpsys_coords.py top.txt backupButton

## 注意

- dumpsys 可能同时输出多个 Activity 的 View Hierarchy（如 Launcher + 前台 App），
  脚本自动取**最后一段**（通常即前台 Activity）。
- 坐标**不含滚动偏移**。目标控件若在滚动区，先用 `input swipe` 让它进入视口，
  再用 `uiautomator dump` 的 `bounds` 交叉校验（uiautomator 给的是屏幕绝对坐标，
  对静态页面通常可用）——两条路径互相印证，比单独依赖任一条更可靠。
- 匹配基于**整行文本**，可用控件 id（如 `backupButton`）或类名片段。
"""
import re
import sys

PATTERN = re.compile(
    r'^(\s*)([\w.$]+)\{([0-9a-f]+)\s+(.*?)\s(-?\d+),(-?\d+)-(-?\d+),(-?\d+)(\s+#\S+)?'
)


def main() -> int:
    if len(sys.argv) < 3:
        print(__doc__)
        return 2

    path, target = sys.argv[1], sys.argv[2]

    with open(path, encoding='utf-8', errors='ignore') as f:
        lines = f.read().splitlines()

    starts = [i for i, line in enumerate(lines) if 'View Hierarchy' in line]
    start = starts[-1] if starts else 0

    stack = []          # (indent, left, top) —— 祖先链
    parsed = 0
    hits = 0

    for line in lines[start:]:
        m = PATTERN.match(line)
        if not m:
            continue
        parsed += 1

        indent = len(m.group(1))
        cls = m.group(2)
        flags = m.group(4)
        l0, t0, r0, b0 = (int(v) for v in m.groups()[4:8])

        # 弹出不属于当前层级的祖先
        while stack and stack[-1][0] >= indent:
            stack.pop()

        abs_left = l0 + sum(s[1] for s in stack)
        abs_top = t0 + sum(s[2] for s in stack)

        if target.lower() in line.lower():
            hits += 1
            abs_right = abs_left + (r0 - l0)
            abs_bottom = abs_top + (b0 - t0)
            print(
                f'{cls.split(".")[-1]}  '
                f'visible={flags.startswith("V")}  '
                f'screen=({abs_left},{abs_top})-({abs_right},{abs_bottom})  '
                f'center=({(abs_left + abs_right) // 2},{(abs_top + abs_bottom) // 2})'
            )

        stack.append((indent, l0, t0))

    print(f'[info] 解析 {parsed} 个控件，命中 {hits} 个')
    if hits == 0:
        print(f'未找到 {target} —— 匹配基于整行文本，可换用更短的子串或控件 id')
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
