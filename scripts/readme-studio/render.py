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
COORDINATOR_SCRATCH = Path(
  r"C:\Users\subha\.gemini\antigravity\brain\207841c8-cc1b-47a5-82d0-f80cff627412\scratch"
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


def make_contact_sheet(frame_paths: list[Path], out_path: Path, frames: int) -> Path:
  pcts = [0, 25, 50, 75]
  indices = [min(frames - 1, int(round((p / 100.0) * frames))) if p > 0 else 0 for p in pcts]
  samples = [Image.open(frame_paths[idx]).convert("RGBA") for idx in indices]
  w, h = samples[0].size
  pad = 12
  bar = 28
  sheet_w = w * 2 + pad * 3
  sheet_h = (h + bar) * 2 + pad * 3
  sheet = Image.new("RGBA", (sheet_w, sheet_h), (22, 20, 18, 255))
  draw = ImageDraw.Draw(sheet)

  positions = [
    (pad, pad),
    (pad * 2 + w, pad),
    (pad, pad * 2 + h + bar),
    (pad * 2 + w, pad * 2 + h + bar),
  ]
  for (x, y), img, pct, idx in zip(positions, samples, pcts, indices):
    draw.rectangle([x, y, x + w, y + bar], fill=(38, 34, 30, 255))
    draw.text((x + 10, y + 7), f"{pct}%  |  frame {idx}/{frames - 1}  (t={idx / frames:.3f})", fill=(245, 239, 230, 255))
    sheet.paste(img, (x, y + bar))

  out_path.parent.mkdir(parents=True, exist_ok=True)
  sheet.convert("RGB").save(out_path, format="PNG", optimize=True)
  for im in samples:
    im.close()
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
  if COORDINATOR_SCRATCH.parent.exists():
    COORDINATOR_SCRATCH.mkdir(parents=True, exist_ok=True)
    return COORDINATOR_SCRATCH / f"{out_path.stem}-contact-sheet.png"
  return out_path.with_name(f"{out_path.stem}-contact-sheet.png")


def render_scene(args: argparse.Namespace) -> dict[str, Any]:
  html_path = Path(args.html).resolve()
  if not html_path.is_file():
    raise FileNotFoundError(f"Scene HTML not found: {html_path}")

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
        device_scale_factor=args.dpr,
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
      sheet_path = make_contact_sheet(frame_paths, target_sheet, args.frames)

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
      "probe": probe,
    }
  finally:
    server.shutdown()
    server.server_close()
    shutil.rmtree(temp_dir, ignore_errors=True)


def parse_args() -> argparse.Namespace:
  parser = argparse.ArgumentParser(
    description="Deterministic Three.js/WebGL2-to-animated-WebP renderer for OctaPDF README visuals."
  )
  parser.add_argument("--html", required=True, help="Path to scene HTML exposing window.renderFrame(i, totalFrames, t).")
  parser.add_argument("--out", required=True, help="Output animated WebP file path.")
  parser.add_argument("--width", type=int, default=960, help="Viewport/output width in pixels (default: 960).")
  parser.add_argument("--height", type=int, default=540, help="Viewport/output height in pixels (default: 540).")
  parser.add_argument("--fps", type=int, default=30, help="Output framerate (default: 30).")
  parser.add_argument("--frames", type=int, default=90, help="Total frames to render (default: 90).")
  parser.add_argument("--quality", type=int, default=82, help="libwebp_anim quality 0-100 (default: 82).")
  parser.add_argument("--dpr", type=float, default=1.0, help="Device scale factor (default: 1.0).")
  parser.add_argument(
    "--gl",
    choices=["auto", "d3d11", "swiftshader"],
    default="auto",
    help="WebGL ANGLE backend (default: auto).",
  )
  parser.add_argument("--contact-sheet", help="Optional output PNG path for 4-frame (0/25/50/75%%) contact sheet.")
  parser.add_argument("--no-contact-sheet", action="store_true", help="Skip generating the 4-frame contact sheet.")
  parser.add_argument("--verbose", action="store_true", help="Log HTTP requests.")
  return parser.parse_args()


def main() -> int:
  args = parse_args()
  result = render_scene(args)
  print(json.dumps(result, indent=2))
  if result["probe"]["nb_read_frames"] not in (None, args.frames):
    print(
      f"WARNING: ffprobe read {result['probe']['nb_read_frames']} frames, expected {args.frames}",
      file=sys.stderr,
    )
    return 2
  if not result["probe"]["under_3_5mb"]:
    print(
      f"WARNING: output exceeds 3.5 MB target ({result['probe']['size_mb']} MB)",
      file=sys.stderr,
    )
  return 0


if __name__ == "__main__":
  raise SystemExit(main())
