#!/usr/bin/env python3
"""
Generate transparent-background OctaPDF brand emblem & wordmark assets
from src/main/resources/static/octapdf-logo.png (1254x1254).

Follows docs/OctaPDF-Logo-Guidelines.pdf:
- Source lockup: 1254x1254 on #FDF8F3 cream field
- Octopus-only crop: 820x820 from X=209, Y=133
- Recolors only the #352F29 "Octa" wordmark letters to #F5EFE6 for dark-mode lockup
"""

from collections import deque
from pathlib import Path
import numpy as np
from PIL import Image, ImageFilter


REPO_ROOT = Path(__file__).resolve().parents[3]
SOURCE_PNG = REPO_ROOT / "src" / "main" / "resources" / "static" / "octapdf-logo.png"
OUT_DIR = Path(__file__).resolve().parent

# Brand colors from docs/OctaPDF-Logo-Guidelines.pdf & theme.dark.css
BG_CREAM = np.array([253.0, 248.0, 243.0], dtype=np.float32)  # #FDF8F3
DARK_OCTA_IVORY = np.array([245.0, 239.0, 230.0], dtype=np.float32)  # #F5EFE6

# Exact 820x820 crop box from OctaPDF-Logo-Guidelines.pdf (Page 3)
EMBLEM_CROP = (209, 133, 209 + 820, 133 + 820)

# Horizontal midpoint in the 20px gap (X=676..696) between "Octa" and "PDF"
OCTA_PDF_SPLIT_X = 686
WORDMARK_MIN_Y = 945


def border_flood_fill(mask_passable: np.ndarray) -> np.ndarray:
    """Return boolean mask of pixels reachable from the outer image border."""
    h, w = mask_passable.shape
    visited = np.zeros((h, w), dtype=bool)
    q = deque()

    for x in range(w):
        if mask_passable[0, x]:
            visited[0, x] = True
            q.append((0, x))
        if mask_passable[h - 1, x] and not visited[h - 1, x]:
            visited[h - 1, x] = True
            q.append((h - 1, x))
    for y in range(1, h - 1):
        if mask_passable[y, 0] and not visited[y, 0]:
            visited[y, 0] = True
            q.append((y, 0))
        if mask_passable[y, w - 1] and not visited[y, w - 1]:
            visited[y, w - 1] = True
            q.append((y, w - 1))

    while q:
        cy, cx = q.popleft()
        if cy > 0 and mask_passable[cy - 1, cx] and not visited[cy - 1, cx]:
            visited[cy - 1, cx] = True
            q.append((cy - 1, cx))
        if cy + 1 < h and mask_passable[cy + 1, cx] and not visited[cy + 1, cx]:
            visited[cy + 1, cx] = True
            q.append((cy + 1, cx))
        if cx > 0 and mask_passable[cy, cx - 1] and not visited[cy, cx - 1]:
            visited[cy, cx - 1] = True
            q.append((cy, cx - 1))
        if cx + 1 < w and mask_passable[cy, cx + 1] and not visited[cy, cx + 1]:
            visited[cy, cx + 1] = True
            q.append((cy, cx + 1))

    return visited


def build_alpha_lockup(rgb: np.ndarray) -> np.ndarray:
    """
    Create RGBA lockup with smooth anti-aliased transparency on the outer cream
    background and outer soft shadow haze, while keeping enclosed eyes and
    enclosed inner ribbon highlights 100% opaque.
    """
    h, w, _ = rgb.shape
    dist = np.linalg.norm(rgb - BG_CREAM, axis=2)

    # Flood-fill ONLY from outer image border:
    # - Octopus region: clear cream field + outer low-chroma drop shadows (dist <= 96)
    #   without entering enclosed almond eyes or enclosed upper-right fold.
    # - Wordmark region: tight cream threshold (dist <= 12) for razor-sharp letters.
    passable = dist <= 96.0
    passable[WORDMARK_MIN_Y:, :] = dist[WORDMARK_MIN_Y:, :] <= 12.0
    bg_mask = border_flood_fill(passable)

    alpha = np.where(bg_mask, 0.0, 1.0).astype(np.float32)

    # Anti-aliased 3px boundary feathering around octopus silhouette
    bg_u8 = Image.fromarray((bg_mask * 255).astype(np.uint8))
    dilated = np.array(bg_u8.filter(ImageFilter.MaxFilter(7)), dtype=bool)
    rim = dilated & (~bg_mask)
    rim[WORDMARK_MIN_Y:, :] = False
    t = np.clip((dist[rim] - 68.0) / (118.0 - 68.0), 0.0, 1.0)
    alpha[rim] = t * t * (3.0 - 2.0 * t)

    # Wordmark AA ramp: exact linear coverage against BG_CREAM
    for x0, x1, dmax in [(0, OCTA_PDF_SPLIT_X, 240.0), (OCTA_PDF_SPLIT_X, w, 215.0)]:
        sub_bg = bg_mask[WORDMARK_MIN_Y:, x0:x1]
        sub_d = dist[WORDMARK_MIN_Y:, x0:x1]
        aa = (~sub_bg) & (sub_d < dmax)
        a_sub = alpha[WORDMARK_MIN_Y:, x0:x1]
        a_sub[aa] = np.clip((sub_d[aa] - 8.0) / (dmax - 8.0), 0.0, 1.0)
        alpha[WORDMARK_MIN_Y:, x0:x1] = a_sub

    # Color decontamination on semi-transparent boundary pixels (eliminates cream halo on dark BG)
    fg = rgb.copy()
    aa_mask = (alpha > 0.01) & (alpha < 0.995)
    a_safe = np.maximum(alpha[aa_mask], 0.10)[:, None]
    fg[aa_mask] = np.clip(
        (rgb[aa_mask] - (1.0 - alpha[aa_mask])[:, None] * BG_CREAM) / a_safe, 0.0, 255.0
    )

    fg[alpha <= 0.005] = 0.0
    alpha[alpha <= 0.005] = 0.0

    # Remove tiny isolated foreground speckles (< 32 px) in the octopus region
    octo_fg = alpha[:WORDMARK_MIN_Y, :] > 0.05
    vis = np.zeros_like(octo_fg)
    oh, ow = octo_fg.shape
    for sy in range(oh):
        for sx in range(ow):
            if octo_fg[sy, sx] and not vis[sy, sx]:
                comp = []
                q = deque([(sy, sx)])
                vis[sy, sx] = True
                while q:
                    cy, cx = q.popleft()
                    comp.append((cy, cx))
                    for ny, nx in ((cy - 1, cx), (cy + 1, cx), (cy, cx - 1), (cy, cx + 1)):
                        if 0 <= ny < oh and 0 <= nx < ow and octo_fg[ny, nx] and not vis[ny, nx]:
                            vis[ny, nx] = True
                            q.append((ny, nx))
                if len(comp) < 32:
                    for cy, cx in comp:
                        alpha[cy, cx] = 0.0
                        fg[cy, cx] = 0.0

    return np.dstack([np.round(fg), np.round(alpha * 255.0)]).astype(np.uint8)


def recolor_octa_for_dark(rgba: np.ndarray) -> np.ndarray:
    """Recolor ONLY the #352F29 'Octa' letters to #F5EFE6 warm ivory, preserving alpha & PDF/octopus."""
    out = rgba.copy()
    region = out[WORDMARK_MIN_Y:, :OCTA_PDF_SPLIT_X, :]
    mask = region[:, :, 3] > 0
    region[mask, 0] = int(DARK_OCTA_IVORY[0])
    region[mask, 1] = int(DARK_OCTA_IVORY[1])
    region[mask, 2] = int(DARK_OCTA_IVORY[2])
    out[WORDMARK_MIN_Y:, :OCTA_PDF_SPLIT_X, :] = region
    return out


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    src = Image.open(SOURCE_PNG).convert("RGB")
    rgb = np.array(src, dtype=np.float32)

    lockup_rgba = build_alpha_lockup(rgb)
    lockup_img = Image.fromarray(lockup_rgba, mode="RGBA")
    lockup_path = OUT_DIR / "octapdf-lockup-alpha.png"
    lockup_img.save(lockup_path, optimize=True)

    emblem_img = lockup_img.crop(EMBLEM_CROP)
    emblem_path = OUT_DIR / "octapdf-emblem-alpha.png"
    emblem_img.save(emblem_path, optimize=True)

    emblem_dark_path = OUT_DIR / "octapdf-emblem-dark-alpha.png"
    emblem_img.save(emblem_dark_path, optimize=True)

    lockup_dark_rgba = recolor_octa_for_dark(lockup_rgba)
    lockup_dark_img = Image.fromarray(lockup_dark_rgba, mode="RGBA")
    lockup_dark_path = OUT_DIR / "octapdf-lockup-dark-alpha.png"
    lockup_dark_img.save(lockup_dark_path, optimize=True)

    for p in (emblem_path, lockup_path, emblem_dark_path, lockup_dark_path):
        im = Image.open(p)
        print(f"Saved {p.relative_to(REPO_ROOT)}: {im.size} {im.mode} ({p.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
