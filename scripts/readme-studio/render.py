#!/usr/bin/env python3
"""Deterministic headless Three.js / HTML5-to-animated-WebP renderer for OctaPDF README assets."""

from __future__ import annotations

import argparse
import functools
import http.server
import json
import shutil
import socketserver
import subprocess
import sys
import tempfile
import threading
from pathlib import Path
from typing import Any

from PIL import Image, ImageDraw
from playwright.sync_api import sync_playwright

REPO_ROOT = Path(__file__).resolve().parents[2]
COORDINATOR_V2_SHEETS = Path(
  r"C:\Users\subha\.gemini\antigravity\brain\207841c8-cc1b-4783-8f11-bc5d7b2c4f20\scratch\sheets\v2"
)
MAX_RECOMMENDED_BYTES = 3_500_000


class StudioHTTPHandler(http.server.SimpleHTTPRequestHandler):
  """Static handler with strict MIME types for ES modules on Windows."""

  extensions_map = {
    **http.server.SimpleHTTPRequestHandler.extensions_map,
    ".js": "application/javascript",
    ".mjs": "application/javascript",
    ".css": "text/css",
    ".html": "text/html; charset=utf-8",
    ".svg": "image/svg+xml",
    ".png": "image/png",
    ".jpg": "image/jpeg",
    ".jpeg": "image/jpeg",
    ".webp": "image/webp",
    ".woff2": "font/woff2",
    ".json": "application/json",
  }

  def __init__(self, *args: Any, external_html: Path | None = None, verbose: bool = False, **kwargs: Any) -> None:
    self._external_html = external_html
    self._verbose = verbose
    super().__init__(*args, **kwargs)

  def do_GET(self) -> None:
    if self._external_html is not None and self.path.split("?")[0] == "/__studio_scene__.html":
      data = self._external_html.read_bytes()
      self.send_response(200)
      self.send_header("Content-Type", "text/html; charset=utf-8")
      self.send_header("Content-Length", str(len(data)))
      self.send_header("Cache-Control", "no-store")
      self.end_headers()
      self.wfile.write(data)
      return
    super().do_GET()

  def log_message(self, format: str, *args: Any) -> None:
    if self._verbose:
      super().log_message(format, *args)


class ThreadedTCPServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
  daemon_threads = True
  allow_reuse_address = True


def resolve_tool(name: str) -> str:
  exe = shutil.which(name)
  if not exe:
    raise FileNotFoundError(f"Required executable '{name}' not found on PATH.")
  return exe


def start_server(
  root: Path, external_html: Path | None, verbose: bool
) -> tuple[ThreadedTCPServer, int, threading.Thread]:
  handler = functools.partial(
    StudioHTTPHandler,
    directory=str(root),
    external_html=external_html,
    verbose=verbose,
  )
  server = ThreadedTCPServer(("127.0.0.1", 0), handler)
  port = int(server.server_address[1])
  thread = threading.Thread(target=server.serve_forever, daemon=True)
  thread.start()
  return server, port, thread


def build_gl_args(gl_mode: str) -> list[str]:
  base = ["--enable-webgl", "--ignore-gpu-blocklist"]
  if gl_mode == "swiftshader":
    return ["--use-gl=angle", "--use-angle=swiftshader", *base]
  if gl_mode == "d3d11":
    return ["--use-gl=angle", "--use-angle=d3d11", *base]
  if sys.platform == "win32":
    return ["--use-gl=angle", "--use-angle=d3d11", *base]
  return ["--use-gl=angle", "--use-angle=swiftshader", *base]


def make_contact_sheet(
  frame_paths: list[Path], out_path: Path, frames: int, target_w: int, target_h: int
) -> Path:
  """Generate a crisp 6-frame (3x2) contact sheet at 0%, 16%, 33%, 50%, 66%, 83%."""
  pcts = [0, 16, 33, 50, 66, 83]
  indices = [min(frames - 1, int(round((p / 100.0) * frames))) if p > 0 else 0 for p in pcts]

  tile_w = min(640, max(480, target_w // 2))
  tile_h = max(1, int(round(tile_w * (target_h / max(1, target_w)))))

  samples: list[Image.Image] = []
  for idx in indices:
    with Image.open(frame_paths[idx]) as raw:
      samples.append(raw.convert("RGB").resize((tile_w, tile_h), Image.Resampling.LANCZOS))

  pad = 12
  bar = 28
  cols = 3
  rows = 2
  sheet_w = tile_w * cols + pad * (cols + 1)
  sheet_h = (tile_h + bar) * rows + pad * (rows + 1)
  sheet = Image.new("RGB", (sheet_w, sheet_h), (22, 20, 18))
  draw = ImageDraw.Draw(sheet)

  for slot, (img, pct, idx) in enumerate(zip(samples, pcts, indices)):
    r, c = divmod(slot, cols)
    x = pad + c * (tile_w + pad)
    y = pad + r * (tile_h + bar + pad)
    draw.rectangle([x, y, x + tile_w, y + bar], fill=(38, 34, 30))
    draw.text(
      (x + 10, y + 7),
      f"{pct}%  |  frame {idx}/{frames - 1}  (t={idx / frames:.3f})",
      fill=(245, 239, 230),
    )
    sheet.paste(img, (x, y + bar))
    img.close()

  out_path.parent.mkdir(parents=True, exist_ok=True)
  sheet.save(out_path, format="PNG", optimize=True)
  return out_path


def probe_webp(ffprobe_bin: str, out_path: Path) -> dict[str, Any]:
  cmd = [
    ffprobe_bin,
    "-v",
    "error",
    "-count_frames",
    "-select_streams",
    "v:0",
    "-show_entries",
    "stream=codec_name,width,height,nb_read_frames,r_frame_rate",
    "-show_entries",
    "format=size,duration",
    "-of",
    "json",
    str(out_path),
  ]
  proc = subprocess.run(cmd, capture_output=True, text=True, check=True)
  data = json.loads(proc.stdout)
  stream = (data.get("streams") or [{}])[0]
  fmt = data.get("format") or {}
  size_bytes = out_path.stat().st_size
  return {
    "codec": stream.get("codec_name"),
    "width": stream.get("width"),
    "height": stream.get("height"),
    "nb_read_frames": int(stream["nb_read_frames"]) if "nb_read_frames" in stream else None,
    "r_frame_rate": stream.get("r_frame_rate"),
    "duration": fmt.get("duration"),
    "size_bytes": size_bytes,
    "size_mb": round(size_bytes / (1024 * 1024), 4),
    "under_3_5mb": size_bytes < MAX_RECOMMENDED_BYTES,
  }


def default_contact_sheet_path(out_path: Path) -> Path:
  if COORDINATOR_V2_SHEETS.parents[1].exists():
    COORDINATOR_V2_SHEETS.mkdir(parents=True, exist_ok=True)
    return COORDINATOR_V2_SHEETS / f"{out_path.stem}.png"
  return out_path.with_name(f"{out_path.stem}-contact-sheet.png")


def render_scene(args: argparse.Namespace) -> dict[str, Any]:
  html_path = Path(args.html).resolve()
  if not html_path.is_file():
    raise FileNotFoundError(f"Scene HTML not found: {html_path}")

  if args.supersample < 1:
    raise ValueError("--supersample must be >= 1")

  out_path = Path(args.out).resolve()
  out_path.parent.mkdir(parents=True, exist_ok=True)

  ffmpeg_bin = resolve_tool("ffmpeg")
  ffprobe_bin = resolve_tool("ffprobe")

  try:
    rel_url = "/" + html_path.relative_to(REPO_ROOT).as_posix()
    external_html = None
  except ValueError:
    rel_url = "/__studio_scene__.html"
    external_html = html_path

  server, port, _ = start_server(REPO_ROOT, external_html, args.verbose)
  url = f"http://127.0.0.1:{port}{rel_url}"

  errors: list[str] = []
  frame_paths: list[Path] = []
  temp_dir = Path(tempfile.mkdtemp(prefix="octapdf-studio-"))
  effective_dpr = args.dpr * args.supersample

  try:
    with sync_playwright() as pw:
      gl_modes = [args.gl] if args.gl != "auto" else (["d3d11", "swiftshader"] if sys.platform == "win32" else ["swiftshader"])
      browser = None
      used_gl = None

      for mode in gl_modes:
        candidate = pw.chromium.launch(headless=True, args=build_gl_args(mode))
        probe_page = candidate.new_page()
        has_gl = probe_page.evaluate("() => Boolean(document.createElement('canvas').getContext('webgl2'))")
        probe_page.close()
        if has_gl:
          browser = candidate
          used_gl = mode
          break
        candidate.close()

      if browser is None:
        raise RuntimeError("Failed to initialize WebGL2 in headless Chromium.")

      context = browser.new_context(
        viewport={"width": args.width, "height": args.height},
        device_scale_factor=effective_dpr,
      )
      page = context.new_page()
      page.on("pageerror", lambda err: errors.append(f"pageerror: {err}"))
      page.on(
        "console",
        lambda msg: errors.append(f"console.{msg.type}: {msg.text}") if msg.type == "error" else None,
      )

      page.goto(url, wait_until="networkidle")
      page.wait_for_function("() => typeof window.renderFrame === 'function'", timeout=30_000)
      page.evaluate(
        """async () => {
          if (document.fonts && document.fonts.ready) await document.fonts.ready;
          if (window.sceneReady) await window.sceneReady;
        }"""
      )

      if errors:
        raise RuntimeError("Scene initialization errors:\n" + "\n".join(errors))

      for i in range(args.frames):
        t = i / args.frames
        page.evaluate(
          """async ([idx, total, timeNorm]) => {
            await window.renderFrame(idx, total, timeNorm);
            await new Promise((resolve) => requestAnimationFrame(() => resolve()));
          }""",
          [i, args.frames, t],
        )
        if errors:
          raise RuntimeError(f"Runtime errors on frame {i}:\n" + "\n".join(errors))
        frame_file = temp_dir / f"frame_{i:05d}.png"
        page.screenshot(path=str(frame_file), type="png")
        frame_paths.append(frame_file)

      context.close()
      browser.close()

    ffmpeg_cmd = [
      ffmpeg_bin,
      "-y",
      "-framerate",
      str(args.fps),
      "-i",
      str(temp_dir / "frame_%05d.png"),
      "-vf",
      f"scale={args.width}:{args.height}:flags=lanczos",
      "-vcodec",
      "libwebp_anim",
      "-lossless",
      "0",
      "-compression_level",
      "6",
      "-q:v",
      str(args.quality),
      "-loop",
      "0",
      "-an",
      str(out_path),
    ]
    subprocess.run(ffmpeg_cmd, capture_output=True, text=True, check=True)

    sheet_path: Path | None = None
    if not args.no_contact_sheet:
      target_sheet = Path(args.contact_sheet).resolve() if args.contact_sheet else default_contact_sheet_path(out_path)
      sheet_path = make_contact_sheet(frame_paths, target_sheet, args.frames, args.width, args.height)

    probe = probe_webp(ffprobe_bin, out_path)
    return {
      "status": "ok",
      "html": str(html_path),
      "out": str(out_path),
      "contact_sheet": str(sheet_path) if sheet_path else None,
      "gl_backend": used_gl,
      "requested_frames": args.frames,
      "fps": args.fps,
      "quality": args.quality,
      "supersample": args.supersample,
      "capture_px": [int(round(args.width * effective_dpr)), int(round(args.height * effective_dpr))],
      "probe": probe,
    }
  finally:
    server.shutdown()
    server.server_close()
    shutil.rmtree(temp_dir, ignore_errors=True)


def parse_args() -> argparse.Namespace:
  parser = argparse.ArgumentParser(
    description="Deterministic supersampled Three.js/WebGL2-to-animated-WebP renderer for OctaPDF README visuals."
  )
  parser.add_argument("--html", required=True, help="Path to scene HTML exposing window.renderFrame(i, totalFrames, t).")
  parser.add_argument("--out", required=True, help="Output animated WebP file path.")
  parser.add_argument("--width", type=int, default=960, help="Output width in pixels (default: 960).")
  parser.add_argument("--height", type=int, default=540, help="Output height in pixels (default: 540).")
  parser.add_argument("--fps", type=int, default=30, help="Output framerate (default: 30).")
  parser.add_argument("--frames", type=int, default=90, help="Total frames to render (default: 90).")
  parser.add_argument("--quality", type=int, default=84, help="libwebp_anim quality 0-100 (default: 84).")
  parser.add_argument("--supersample", type=int, default=2, help="Supersample multiplier before Lanczos downscale (default: 2).")
  parser.add_argument("--dpr", type=float, default=1.0, help="Base device scale factor multiplied by --supersample (default: 1.0).")
  parser.add_argument(
    "--gl",
    choices=["auto", "d3d11", "swiftshader"],
    default="auto",
    help="WebGL ANGLE backend (default: auto).",
  )
  parser.add_argument("--contact-sheet", help="Optional output PNG path for 6-frame (0/16/33/50/66/83%%) contact sheet.")
  parser.add_argument("--no-contact-sheet", action="store_true", help="Skip generating the contact sheet.")
  parser.add_argument("--allow-oversize", action="store_true", help="Warn instead of failing if WebP exceeds 3.5 MB.")
  parser.add_argument("--verbose", action="store_true", help="Log HTTP requests.")
  return parser.parse_args()


def main() -> int:
  args = parse_args()
  result = render_scene(args)
  print(json.dumps(result, indent=2))
  if result["probe"]["nb_read_frames"] not in (None, args.frames):
    print(
      f"ERROR: ffprobe read {result['probe']['nb_read_frames']} frames, expected {args.frames}",
      file=sys.stderr,
    )
    return 2
  if not result["probe"]["under_3_5mb"]:
    print(
      f"ERROR: output exceeds 3.5 MB limit ({result['probe']['size_mb']} MB / {result['probe']['size_bytes']} bytes)",
      file=sys.stderr,
    )
    if not args.allow_oversize:
      return 3
  return 0


if __name__ == "__main__":
  raise SystemExit(main())
