# -*- coding: utf-8 -*-
"""
从 kurisu_greetings.txt 生成 app/src/main/res/values/strings.xml。

用法：
    python tools/sync_strings.py            # 生成
    python tools/sync_strings.py --check    # 只校验两边是否一致，不写文件

为什么要有这个脚本：句子有两处副本（草稿 txt 和 strings.xml），
手改两遍一定会漂移。这份脚本让 txt 成为唯一源头。
"""
import re
import sys
import html
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DRAFT = ROOT / "kurisu_greetings.txt"
XML = ROOT / "app" / "src" / "main" / "res" / "values" / "strings.xml"

# 草稿里的分类顺序就是 XML 里的顺序
KEY_ORDER = [
    "greetings_anytime",
    "greetings_lab",
    "greetings_morning",
    "greetings_afternoon",
    "greetings_evening",
    "greetings_night",
    "greetings_spring",
    "greetings_summer",
    "greetings_autumn",
    "greetings_winter",
    "greetings_sunny",
    "greetings_cloudy",
    "greetings_foggy",
    "greetings_rainy",
    "greetings_snowy",
    "greetings_stormy",
    "greetings_hot",
    "greetings_cold",
]

TITLES = {
    "greetings_anytime": "通用：任何时候都能用，兜底",
    "greetings_lab": "同一个实验室的日常：自言自语 + 对同事说的话",
    "greetings_morning": "05:00 - 10:59",
    "greetings_afternoon": "11:00 - 17:59",
    "greetings_evening": "18:00 - 22:59",
    "greetings_night": "23:00 - 04:59",
    "greetings_spring": "季节 · 春（3 - 5 月）",
    "greetings_summer": "季节 · 夏（6 - 8 月）",
    "greetings_autumn": "季节 · 秋（9 - 11 月）",
    "greetings_winter": "季节 · 冬（12 - 2 月）",
    "greetings_sunny": "天气 · 晴",
    "greetings_cloudy": "天气 · 阴 / 多云",
    "greetings_foggy": "天气 · 雾",
    "greetings_rainy": "天气 · 雨",
    "greetings_snowy": "天气 · 雪",
    "greetings_stormy": "天气 · 雷暴",
    "greetings_hot": "天气 · 高温",
    "greetings_cold": "天气 · 低温",
}

HEADER = """<resources>
    <string name="app_name">问候</string>
    <string name="widget_description">随机显示一句问候，点一下换一句</string>
    <string name="widget_placeholder">你好呀</string>
    <string name="main_hint">长按桌面空白处 →「小组件」→ 找到「问候」→ 拖到桌面。\\n\\n点桌面上的小组件可以立刻换一句。</string>
    <string name="main_shuffle">立刻换一句（测试用）</string>
    <string name="main_request_location">授权定位（用于天气句子）</string>
    <string name="toast_shuffled">已让桌面上的小组件换一句</string>
    <string name="toast_weather_fetching">已开始获取天气，稍等片刻</string>

    <string name="main_status_loading">正在读取状态…</string>
    <string name="main_status_location_ok">定位：已授权</string>
    <string name="main_status_location_no">定位：未授权 —— 天气句子不会出现</string>
    <string name="main_status_weather">天气：%1$s，%2$.1f℃，%3$s</string>
    <string name="main_status_weather_stale">天气：%1$s，%2$.1f℃（数据偏旧，等下次刷新）</string>
    <string name="main_status_weather_none">天气：还没有数据（检查网络和定位授权）</string>
    <string name="main_attribution">天气数据来自 Open-Meteo.com</string>
    <string name="main_day">白天</string>
    <string name="main_night">夜间</string>

    <!--
      ============================================================
      问候语池（牧濑红莉栖口吻）。本文件由 tools/sync_strings.py
      从仓库根目录的 kurisu_greetings.txt 生成 —— 不要手改这里，
      改那边然后重跑脚本，否则两份副本会漂移。

      分组：
        时段：morning / afternoon / evening / night
        季节：spring / summer / autumn / winter
        天气：sunny / cloudy / foggy / rainy / snowy / stormy / hot / cold
        通用：anytime（兜底，永远都能用）
        日常：lab（同一个实验室的日常对话 / 自言自语）

      抽取权重在 GreetingRepository.kt：
        时段 25% / 日常 25% / 天气 20% / 通用 20% / 季节 10%
      哪一组空了，或者天气拿不到，就自动降级到别的组。

      注意：这里的中文里 % 要写成 %%、& 要写成 &amp;，
      脚本已经自动处理。
      ============================================================
    -->
"""


def parse_draft(text):
    groups = {}
    current = None
    in_notes = False
    for raw in text.splitlines():
        line = raw.strip()
        if "备注" in line:
            in_notes = True
            continue
        if "greetings_" in line and "|" in line:
            current = line.split("|")[0].strip()
            groups[current] = []
            continue
        if not line or re.fullmatch(r"=+", line):
            continue
        if line.startswith("【") or line.startswith("·"):
            continue
        if in_notes or current is None:
            continue
        groups[current].append(line)
    return groups


def esc(s):
    """Android 字符串资源转义：先做 XML 实体，再处理 %。"""
    out = html.escape(s, quote=False)
    out = out.replace("%", "%%")
    return out


def build_xml(groups):
    parts = [HEADER]
    for key in KEY_ORDER:
        items = groups.get(key, [])
        title = TITLES.get(key, "")
        parts.append(f"    <!-- {title} -->\n")
        parts.append(f'    <string-array name="{key}">\n')
        for it in items:
            parts.append(f"        <item>{esc(it)}</item>\n")
        parts.append("    </string-array>\n\n")
    # 去掉最后一个多余空行，闭合
    parts.append("</resources>\n")
    return "".join(parts)


def parse_xml_items(text):
    """从现有 XML 里抠出每个数组的 item，用于 --check。"""
    groups = {}
    for m in re.finditer(
        r'<string-array name="(greetings_[a-z]+)">(.*?)</string-array>',
        text,
        re.S,
    ):
        key = m.group(1)
        body = m.group(2)
        items = [html.unescape(i) for i in re.findall(r"<item>(.*?)</item>", body, re.S)]
        groups[key] = items
    return groups


def main():
    check_only = "--check" in sys.argv
    groups = parse_draft(DRAFT.read_text(encoding="utf-8"))
    total = sum(len(v) for v in groups.values())

    unknown = [k for k in groups if k not in KEY_ORDER]
    if unknown:
        print(f"草稿里有未登记的分类：{unknown}")
        return 2

    if check_only:
        if not XML.exists():
            print("strings.xml 不存在")
            return 2
        on_disk = parse_xml_items(XML.read_text(encoding="utf-8"))
        ok = True
        for key in KEY_ORDER:
            a, b = groups.get(key, []), on_disk.get(key, [])
            if a != b:
                ok = False
                print(f"不一致：{key}  txt={len(a)} xml={len(b)}")
                for x in a:
                    if x not in b:
                        print(f"    只在 txt: {x}")
                for x in b:
                    if x not in a:
                        print(f"    只在 xml: {x}")
        print(f"txt={total} 条，检查结果：{'一致' if ok else '不一致'}")
        return 0 if ok else 1

    XML.write_text(build_xml(groups), encoding="utf-8")
    print(f"已写入 {XML}")
    for key in KEY_ORDER:
        print(f"  {key:<22} {len(groups.get(key, []))}")
    print(f"  合计 {total} 条")
    return 0


if __name__ == "__main__":
    sys.exit(main())
