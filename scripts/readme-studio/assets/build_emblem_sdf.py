#!/usr/bin/env python3
"""
Generate Signed Distance / Ribbon Flow / Relief Heightmap & Tangent-Space Normal Map
from scripts/readme-studio/assets/octapdf-emblem-alpha.png (820x820 RGBA).

Outputs:
- octapdf-emblem-sdf.png (820x820 RGB):
    R = Normalized signed distance field (0.5 at contour, >0.5 inside, <0.5 outside)
    G = Radial/geodesic ribbon flow field (0.0 at mantle hub -> 1.0 at tentacle tips)
    B = Smooth blurred relief heightmap for 3D embossed paper shading
- octapdf-emblem-normal.png (820x820 RGB):
    Tangent-space normal map derived from the smoothed relief heightmap
"""

from __future__ import annotations

import heapq
from pathlib import Path
import numpy as np
from PIL import Image, ImageFilter

ASSETS_DIR = Path(__file__).resolve().parent
INPUT_PNG = ASSETS_DIR / "octapdf-emblem-alpha.png"
OUT_SDF = ASSETS_DIR / "octapdf-emblem-sdf.png"
OUT_NORMAL = ASSETS_DIR / "octapdf-emblem-normal.png"

# Verified eye-bridge / mantle hub centroid inside 820x820 octapdf-emblem-alpha.png
MANTLE_CENTER_X = 438
MANTLE_CENTER_Y = 324
SDF_BAND_PX = 48.0


def edt_1d_parabolic(f: np.ndarray) -> np.ndarray:
  """Exact 1D squared Euclidean distance transform (Felzenszwalb & Huttenlocher) along axis 1."""
  n_rows, n_cols = f.shape
  out = np.empty_like(f, dtype=np.float32)
  inf = 1e12

  v = np.zeros(n_cols, dtype=np.int32)
  z = np.zeros(n_cols + 1, dtype=np.float32)

  for r in range(n_rows):
    row = f[r]
    k = 0
    v[0] = 0
    z[0] = -inf
    z[1] = inf
    for q in range(1, n_cols):
      fq = float(row[q])
      if fq >= 1e11:
        continue
      while True:
        vk = int(v[k])
        s = ((fq + q * q) - (float(row[vk]) + vk * vk)) / (2.0 * (q - vk))
        if s <= z[k]:
          k -= 1
          if k < 0:
            k = 0
            break
        else:
          break
      k += 1
      v[k] = q
      z[k] = s
      z[k + 1] = inf

    k = 0
    for q in range(n_cols):
      while z[k + 1] < q:
        k += 1
      vk = int(v[k])
      dx = q - vk
      out[r, q] = dx * dx + float(row[vk])
  return out


def edt_2d_with_nearest(binary_mask: np.ndarray) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
  """
  Compute exact 2D Euclidean distance to True pixels in binary_mask,
  plus nearest-foreground coordinates (ny, nx) via 2-pass squared EDT + propagation.
  """
  inf = 1e12
  f = np.where(binary_mask, 0.0, inf).astype(np.float32)
  d1 = edt_1d_parabolic(f)
  d2 = edt_1d_parabolic(d1.T).T
  dist = np.sqrt(np.maximum(d2, 0.0)).astype(np.float32)
  return dist, d2


def compute_geodesic_flow(inside: np.ndarray, cx: int, cy: int) -> np.ndarray:
  """
  Compute smooth ribbon-following distance from mantle hub (cx, cy) out to tentacle tips
  inside the foreground mask, then extrapolate smoothly outside via multi-pass blur.
  """
  h, w = inside.shape
  dist = np.full((h, w), np.inf, dtype=np.float32)

  # Seed small disc around mantle center that lies inside foreground
  pq: list[tuple[float, int, int]] = []
  for dy in range(-12, 13):
    for dx in range(-12, 13):
      y, x = cy + dy, cx + dx
      if 0 <= y < h and 0 <= x < w and inside[y, x]:
        d0 = float(np.hypot(dx, dy))
        if d0 < dist[y, x]:
          dist[y, x] = d0
          heapq.heappush(pq, (d0, y, x))

  neighbors = [
    (-1, 0, 1.0), (1, 0, 1.0), (0, -1, 1.0), (0, 1, 1.0),
    (-1, -1, 1.4142135), (-1, 1, 1.4142135), (1, -1, 1.4142135), (1, 1, 1.4142135),
  ]

  while pq:
    d, y, x = heapq.heappop(pq)
    if d > dist[y, x]:
      continue
    for dy, dx, step in neighbors:
      ny, nx = y + dy, x + dx
      if 0 <= ny < h and 0 <= nx < w and inside[ny, nx]:
        nd = d + step
        if nd < dist[ny, nx]:
          dist[ny, nx] = nd
          heapq.heappush(pq, (nd, ny, nx))

  # Fallback radial distance for any detached tiny region & exterior
  yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
  radial = np.hypot(xx - cx, yy - cy)
  max_geo = float(np.max(dist[np.isfinite(dist)])) if np.any(np.isfinite(dist)) else 1.0
  max_rad = float(np.max(radial))

  geo_norm = np.where(np.isfinite(dist), dist / max(1.0, max_geo), radial / max(1.0, max_rad))
  rad_norm = radial / max(1.0, max_rad)

  # Blend geodesic ribbon length (70%) + direct radial distance (30%) inside, radial outside
  flow = np.where(inside, 0.72 * geo_norm + 0.28 * rad_norm, rad_norm)
  flow_img = Image.fromarray(np.round(np.clip(flow, 0.0, 1.0) * 255.0).astype(np.uint8), mode="L")
  flow_smooth = np.array(flow_img.filter(ImageFilter.GaussianBlur(radius=4.5)), dtype=np.float32) / 255.0
  return np.clip(flow_smooth, 0.0, 1.0)


def gaussian_blur_float(arr: np.ndarray, radius: float) -> np.ndarray:
  """Apply smooth separable float32 Gaussian blur in pure NumPy (zero 8-bit quantization)."""
  if radius <= 0.0:
    return arr.astype(np.float32)
  sigma = max(0.25, float(radius))
  half = int(np.ceil(3.0 * sigma))
  x = np.arange(-half, half + 1, dtype=np.float32)
  kernel = np.exp(-0.5 * (x / sigma) ** 2)
  kernel /= np.sum(kernel)

  pad_x = np.pad(arr.astype(np.float32), ((0, 0), (half, half)), mode="edge")
  horiz = np.apply_along_axis(lambda row: np.convolve(row, kernel, mode="valid"), 1, pad_x)
  pad_y = np.pad(horiz, ((half, half), (0, 0)), mode="edge")
  vert = np.apply_along_axis(lambda col: np.convolve(col, kernel, mode="valid"), 0, pad_y)
  return vert.astype(np.float32)


def main() -> None:
  src = Image.open(INPUT_PNG).convert("RGBA")
  arr = np.array(src, dtype=np.float32) / 255.0
  rgb = arr[:, :, :3]
  alpha = arr[:, :, 3]

  inside = alpha >= 0.5
  dist_out, _ = edt_2d_with_nearest(inside)
  dist_in, _ = edt_2d_with_nearest(~inside)
  signed_dist = dist_in - dist_out

  # R channel: Normalized SDF (0.5 at edge, smooth linear band +-48px)
  sdf_norm = np.clip(0.5 + signed_dist / (2.0 * SDF_BAND_PX), 0.0, 1.0)
  sdf_norm = gaussian_blur_float(sdf_norm, radius=0.8)

  # G channel: Radial / geodesic ribbon flow from mantle hub out to tentacle tips
  flow_norm = compute_geodesic_flow(inside, MANTLE_CENTER_X, MANTLE_CENTER_Y)

  # B channel: Blurred 3D relief heightmap for embossed paper & tangent-space normal map
  # Rounded pillow profile from interior SDF + subtle luminance fold sculpting
  edge_ramp = np.clip(signed_dist / 22.0, 0.0, 1.0)
  pillow = np.sqrt(np.maximum(0.0, 1.0 - (1.0 - edge_ramp) ** 2))
  lum = 0.299 * rgb[:, :, 0] + 0.587 * rgb[:, :, 1] + 0.114 * rgb[:, :, 2]
  raw_relief = alpha * (0.78 * pillow + 0.22 * lum)
  relief = gaussian_blur_float(raw_relief, radius=3.5)
  relief_max = float(np.max(relief))
  if relief_max > 1e-6:
    relief = np.clip(relief / relief_max, 0.0, 1.0)

  sdf_rgb = np.dstack([
    np.round(np.clip(sdf_norm, 0.0, 1.0) * 255.0),
    np.round(np.clip(flow_norm, 0.0, 1.0) * 255.0),
    np.round(np.clip(relief, 0.0, 1.0) * 255.0),
  ]).astype(np.uint8)
  Image.fromarray(sdf_rgb, mode="RGB").save(OUT_SDF, optimize=True)

  # Tangent-space normal map from smoothed relief heightmap
  # Use sobel-like central differences with smooth radius=4.0 pre-filter for clean highlights
  h_smooth = gaussian_blur_float(raw_relief / max(1e-6, relief_max), radius=4.5)
  gy, gx = np.gradient(h_smooth)
  strength = 18.0
  nx = -gx * strength
  # Image Y grows downward while OpenGL/Three.js UV +Y points upward -> +gy tilts normal UP
  ny = gy * strength
  nz = np.ones_like(nx)
  norm_len = np.sqrt(nx * nx + ny * ny + nz * nz)
  nx /= norm_len
  ny /= norm_len
  nz /= norm_len

  normal_rgb = np.dstack([
    np.round((nx * 0.5 + 0.5) * 255.0),
    np.round((ny * 0.5 + 0.5) * 255.0),
    np.round((nz * 0.5 + 0.5) * 255.0),
  ]).astype(np.uint8)
  Image.fromarray(normal_rgb, mode="RGB").save(OUT_NORMAL, optimize=True)

  print(f"Saved {OUT_SDF.name}: {OUT_SDF.stat().st_size} bytes (min/max SDF={sdf_norm.min():.3f}/{sdf_norm.max():.3f})")
  print(f"Saved {OUT_NORMAL.name}: {OUT_NORMAL.stat().st_size} bytes (center normal={normal_rgb[MANTLE_CENTER_Y, MANTLE_CENTER_X].tolist()})")


if __name__ == "__main__":
  main()
