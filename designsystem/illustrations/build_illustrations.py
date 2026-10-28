#!/usr/bin/env python3
"""
Builds the illustrations of the design system from scenes/*.svg into
src/main/res/drawable/illustration_<scene>.xml (VectorDrawable).

A scene is an SVG that puts together pieces of Humaaans (humaaans/, CC0) and own props:

    <use href="humaaans/standing-3.svg" top="primary" bottom="tertiary" transform="..."/>
    <path d="..." fill="slot:paper"/>

Colors that follow the theme are written as placeholders (see SLOTS), which Illustration.kt
replaces by colors of the ColorScheme. In a Humaaans piece the clothes of the groups Body/* and
Bottom/* take the slot named by top= and bottom=: the lightest color the base, the others its
shade. Seats (Objects/*) take seat= or neutral. Skin, hair and shoes keep their colors.

    python3 build_illustrations.py              # writes the drawables
    python3 build_illustrations.py --preview D  # also renders D/<scene>-light.png and -dark.png
"""
import argparse
import colorsys
import copy
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = HERE.parent / "src/main/res/drawable"
SVG_NS = "http://www.w3.org/2000/svg"
XLINK = "{http://www.w3.org/1999/xlink}href"

# Order and placeholder colors must match IllustrationSlot in Illustration.kt
SLOTS = ["primary", "primaryShade", "secondary", "secondaryShade", "tertiary", "tertiaryShade",
         "neutral", "neutralShade", "paper", "line", "ink"]


def placeholder(slot):
    return "#FFFE00%02X" % (SLOTS.index(slot) + 1)


# Baseline Material 3 colors, only for the preview; shades as in Illustration.kt
PREVIEW = {
    "light": dict(primary="#6750A4", secondary="#625B71", tertiary="#7D5260", neutral="#E7E0EC",
                  paper="#FFFFFF", line="#CAC4D0", ink="#49454F", background="#FEF7FF"),
    "dark": dict(primary="#D0BCFF", secondary="#CCC2DC", tertiary="#EFB8C8", neutral="#49454F",
                 paper="#36343B", line="#938F99", ink="#CAC4D0", background="#141218"),
}
SHADE = 0.22


def shade(hex_color):
    r, g, b = (int(hex_color[i:i + 2], 16) for i in (1, 3, 5))
    return "#%02X%02X%02X" % tuple(round(c * (1 - SHADE)) for c in (r, g, b))


def preview_color(slot, mode):
    colors = PREVIEW[mode]
    return shade(colors[slot[:-5]]) if slot.endswith("Shade") else colors[slot]


# Humaaans colors that stay as they are inside clothes: skin, white, greys of shirts and shoes
FIXED = {"#B28B67", "#997659", "#915B3C", "#D4A181", "#57331F", "#784931", "#BA8B72", "#3D2216",
         "#FFFFFF", "#E4E4E4", "#F2F2F2", "#DDE3E9", "#C5CFD6", "#AFB9C5", "#2C2C2C", "#323337",
         "#000000", "#CBCBCB"}


def tag(el):
    return el.tag.split("}")[-1]


def luminance(hex_color):
    r, g, b = (int(hex_color[i:i + 2], 16) / 255 for i in (1, 3, 5))
    return colorsys.rgb_to_hls(r, g, b)[1]


class Converter:
    def __init__(self, mode):
        # mode None writes placeholders, "light"/"dark" writes preview colors
        self.mode = mode

    def color(self, value, slot_map):
        if value is None or value == "none":
            return None
        if value.startswith("slot:"):
            slot = value[5:]
            return placeholder(slot) if self.mode is None else preview_color(slot, self.mode)
        value = value.upper()
        if value in slot_map:
            return self.color("slot:" + slot_map[value], {})
        return value


def parse_transform(value):
    """[(kind, args)] in the order written, i.e. the last one applies first"""
    result = []
    for kind, args in re.findall(r"(\w+)\(([^)]*)\)", value or ""):
        result.append((kind, [float(a) for a in re.split(r"[\s,]+", args.strip()) if a]))
    return result


def shape_path(el):
    t = tag(el)
    f = lambda name, default=0.0: float(el.get(name, default))
    if t == "path":
        return el.get("d")
    if t == "polygon":
        points = re.split(r"[\s,]+", el.get("points").strip())
        pairs = [f"{points[i]},{points[i + 1]}" for i in range(0, len(points), 2)]
        return "M" + " L".join(pairs) + " Z"
    if t == "rect":
        x, y, w, h = f("x"), f("y"), f("width"), f("height")
        rx = min(f("rx", el.get("ry", 0)), w / 2)
        ry = min(f("ry", el.get("rx", 0)), h / 2)
        if rx == 0:
            return f"M{x},{y} h{w} v{h} h{-w} Z"
        return (f"M{x + rx},{y} h{w - 2 * rx} a{rx},{ry} 0 0 1 {rx},{ry} v{h - 2 * ry} "
                f"a{rx},{ry} 0 0 1 {-rx},{ry} h{-(w - 2 * rx)} a{rx},{ry} 0 0 1 {-rx},{-ry} "
                f"v{-(h - 2 * ry)} a{rx},{ry} 0 0 1 {rx},{-ry} Z")
    if t in ("circle", "ellipse"):
        cx, cy = f("cx"), f("cy")
        rx = f("r") if t == "circle" else f("rx")
        ry = f("r") if t == "circle" else f("ry")
        return (f"M{cx - rx},{cy} a{rx},{ry} 0 1 0 {2 * rx},0 "
                f"a{rx},{ry} 0 1 0 {-2 * rx},0 Z")
    return None


def clothes_map(group, slot, fixed=FIXED):
    """Lightest color of the clothes to the slot, the others to its shade"""
    colors = set()

    def collect(el):
        if (el.get("id") or "").startswith(("Accessories/Shoe", "Head")):
            return
        fill = (el.get("fill") or "").upper()
        if fill.startswith("#") and fill not in fixed:
            colors.add(fill)
        for child in el:
            collect(child)

    collect(group)
    ordered = sorted(colors, key=luminance, reverse=True)
    return {c: slot if i == 0 else slot + "Shade" for i, c in enumerate(ordered)}


class Writer:
    def __init__(self, converter):
        self.converter = converter
        self.lines = []
        self.defs = {}

    def emit(self, depth, text):
        self.lines.append("    " * depth + text)

    def node(self, el, depth, inherited, slot_map, roles):
        t = tag(el)
        if t in ("title", "desc", "defs", "mask"):
            return
        fill = el.get("fill", inherited.get("fill"))
        fill_rule = el.get("fill-rule", inherited.get("fill-rule"))
        alpha = inherited.get("alpha", 1.0) * float(el.get("opacity", 1)) * float(el.get("fill-opacity", 1))
        state = dict(fill=fill, alpha=alpha)
        state["fill-rule"] = fill_rule

        if t in ("g", "svg") and roles:
            gid = el.get("id") or ""
            for prefix, role in (("Body/", "top"), ("Bottom/", "bottom")):
                if gid.startswith(prefix) and roles.get(role):
                    slot_map = {**slot_map, **clothes_map(el, roles[role])}
            # seats are grey in all pieces, all of their colors follow the theme
            if gid.startswith("Objects/"):
                slot_map = {**slot_map, **clothes_map(el, roles.get("seat") or "neutral", fixed=set())}

        transforms = parse_transform(el.get("transform"))
        for kind, args in transforms:
            self.open_group(kind, args, depth)
            depth += 1

        mask = re.match(r"url\(#(.+)\)", el.get("mask") or "")
        if mask:
            self.emit(depth, "<group>")
            depth += 1
            self.emit(depth, f'<clip-path android:pathData="{self.mask_path(mask.group(1))}"/>')

        if t in ("g", "svg"):
            for child in el:
                self.node(child, depth, state, slot_map, roles)
        elif t == "use":
            href = el.get("href") or el.get(XLINK)
            if href.startswith("#"):
                target = self.defs[href[1:]]
                if el.get("fill"):
                    target = copy.copy(target)
                    target.set("fill", el.get("fill"))
                self.node(target, depth, state, slot_map, roles)
            else:
                self.piece(el, href, depth, state)
        else:
            color = self.converter.color(fill, slot_map)
            data = shape_path(el)
            if color and data:
                attrs = [f'android:pathData="{data}"', f'android:fillColor="{color}"']
                if alpha < 1:
                    attrs.append(f'android:fillAlpha="{alpha:g}"')
                if fill_rule == "evenodd":
                    attrs.append('android:fillType="evenOdd"')
                self.emit(depth, "<path " + "\n".join(
                    ["    " * depth + "    " + a if i else a for i, a in enumerate(attrs)]) + "/>")

        if mask:
            depth -= 1
            self.emit(depth, "</group>")
        for _ in transforms:
            depth -= 1
            self.emit(depth, "</group>")

    def open_group(self, kind, args, depth):
        if kind == "translate":
            attrs = f'android:translateX="{args[0]:g}" android:translateY="{args[1] if len(args) > 1 else 0:g}"'
        elif kind == "rotate":
            if len(args) == 3:
                attrs = f'android:rotation="{args[0]:g}" android:pivotX="{args[1]:g}" android:pivotY="{args[2]:g}"'
            else:
                attrs = f'android:rotation="{args[0]:g}"'
        elif kind == "scale":
            attrs = f'android:scaleX="{args[0]:g}" android:scaleY="{args[1] if len(args) > 1 else args[0]:g}"'
        else:
            sys.exit(f"transform {kind} not supported")
        self.emit(depth, f"<group {attrs}>")

    def mask_path(self, mask_id):
        mask = self.defs[mask_id]
        use = next(el for el in mask.iter() if tag(el) == "use")
        return shape_path(self.defs[(use.get("href") or use.get(XLINK))[1:]])

    def piece(self, use, href, depth, state):
        root = ET.parse(HERE / href).getroot()
        outer = self.defs
        # ids repeat between pieces
        self.defs = {el.get("id"): el for el in root.iter() if el.get("id")}
        roles = {role: use.get(role) for role in ("top", "bottom", "seat")}
        for child in root:
            self.node(child, depth, state, {}, roles)
        self.defs = outer


def convert(scene, mode=None):
    root = ET.parse(scene).getroot()
    _, _, width, height = (float(v) for v in root.get("viewBox").split())
    writer = Writer(Converter(mode))
    for el in root.iter():
        if el.get("id"):
            writer.defs[el.get("id")] = el
    for child in root:
        writer.node(child, 1, {}, {}, None)
    return width, height, writer.lines


def vector_drawable(scene):
    width, height, lines = convert(scene)
    scale = 200 / height
    # Shown at the size of the page, not as an icon, so the size limit of lint for icons does not apply
    header = (f'<?xml version="1.0" encoding="utf-8"?>\n'
              f"<!-- Generated by illustrations/build_illustrations.py from scenes/{scene.name}, "
              f"Humaaans by Pablo Stanley (CC0). Do not edit. -->\n"
              f'<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
              f'    xmlns:tools="http://schemas.android.com/tools"\n'
              f'    tools:ignore="VectorRaster"\n'
              f'    android:width="{width * scale:.0f}dp"\n    android:height="200dp"\n'
              f'    android:viewportWidth="{width:g}"\n    android:viewportHeight="{height:g}">\n')
    return header + "\n".join(lines) + "\n</vector>\n"


def preview_svg(scene, mode):
    """Same tree as the drawable, back as SVG, so that a preview shows what Android draws"""
    width, height, lines = convert(scene, mode)
    xml = "\n".join(lines)
    xml = re.sub(r"<clip-path android:pathData=\"([^\"]*)\"/>", lambda m: f'<clip d="{m.group(1)}"/>', xml)
    out = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width:g} {height:g}" '
           f'width="{width:g}" height="{height:g}">',
           f'<rect width="100%" height="100%" fill="{PREVIEW[mode]["background"]}"/>']
    clip_id = 0
    stack = []
    for line in xml.splitlines():
        line = line.strip()
        if line.startswith("<group"):
            attrs = dict(re.findall(r'android:(\w+)="([^"]*)"', line))
            if "translateX" in attrs:
                out.append(f'<g transform="translate({attrs["translateX"]},{attrs["translateY"]})">')
            elif "rotation" in attrs:
                out.append(f'<g transform="rotate({attrs["rotation"]},{attrs.get("pivotX", 0)},{attrs.get("pivotY", 0)})">')
            elif "scaleX" in attrs:
                out.append(f'<g transform="scale({attrs["scaleX"]},{attrs["scaleY"]})">')
            else:
                out.append("<g>")
            stack.append("</g>")
        elif line.startswith("<clip-path"):
            clip_id += 1
            d = re.search(r'pathData="([^"]*)"', line).group(1)
            out.append(f'<clipPath id="c{clip_id}"><path d="{d}"/></clipPath><g clip-path="url(#c{clip_id})">')
            stack[-1] = "</g></g>"
        elif line == "</group>":
            out.append(stack.pop())
        elif line.startswith("<path") or line.startswith("android:"):
            out.append(line)
    svg = "\n".join(out) + "\n</svg>\n"
    svg = re.sub(r"<path\s+android:pathData=\"([^\"]*)\"\s+android:fillColor=\"([^\"]*)\"((?:\s+android:\w+=\"[^\"]*\")*)/>",
                 _svg_path, svg)
    return svg


def _svg_path(match):
    d, color, rest = match.groups()
    attrs = dict(re.findall(r'android:(\w+)="([^"]*)"', rest))
    extra = ""
    if "fillAlpha" in attrs:
        extra += f' fill-opacity="{attrs["fillAlpha"]}"'
    if attrs.get("fillType") == "evenOdd":
        extra += ' fill-rule="evenodd"'
    if len(color) == 9:
        color = "#" + color[3:]
    return f'<path d="{d}" fill="{color}"{extra}/>'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--preview", type=Path)
    parser.add_argument("scenes", nargs="*")
    args = parser.parse_args()
    scenes = [Path(s) for s in args.scenes] or sorted((HERE / "scenes").glob("*.svg"))
    OUT.mkdir(exist_ok=True)
    for scene in scenes:
        name = "illustration_" + scene.stem.replace("-", "_")
        (OUT / f"{name}.xml").write_text(vector_drawable(scene))
        print("wrote", name)
        if args.preview:
            args.preview.mkdir(parents=True, exist_ok=True)
            for mode in PREVIEW:
                svg = args.preview / f"{scene.stem}-{mode}.svg"
                svg.write_text(preview_svg(scene, mode))
                subprocess.run(["inkscape", str(svg), "--export-type=png", "--export-height=400",
                                f"--export-filename={args.preview / f'{scene.stem}-{mode}.png'}"],
                               check=True, stderr=subprocess.DEVNULL)


if __name__ == "__main__":
    main()
