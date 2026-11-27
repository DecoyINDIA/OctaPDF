import * as THREE from '/scripts/readme-studio/vendor/three.module.js';

// ============================================================================
// Deterministic PRNG & Mathematical Easings
// ============================================================================

export function mulberry32(seed) {
  let a = seed >>> 0;
  return function () {
    a |= 0;
    a = (a + 0x6D2B79F5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

export const clamp01 = (v) => Math.max(0, Math.min(1, v));
export const lerp = (a, b, t) => a + (b - a) * t;

export function smoothstep(t) {
  const x = clamp01(t);
  return x * x * (3 - 2 * x);
}

export function smootherstep(t) {
  const x = clamp01(t);
  return x * x * x * (x * (x * 6 - 15) + 10);
}

export function band(t, a, b) {
  return smoothstep((t - a) / (b - a));
}

export function easeOutCubic(t) {
  return 1 - Math.pow(1 - clamp01(t), 3);
}

export function easeOutExpo(t) {
  const x = clamp01(t);
  return x === 1 ? 1 : 1 - Math.pow(2, -10 * x);
}

export function easeInOutQuint(t) {
  const x = clamp01(t);
  return x < 0.5 ? 16 * x * x * x * x * x : 1 - Math.pow(-2 * x + 2, 5) / 2;
}

export function springDamped(t, damping = 6.2, freq = 13.5) {
  const x = clamp01(t);
  if (x === 0) return 0;
  if (x === 1) return 1;
  const raw = 1 - Math.exp(-damping * x) * Math.cos(freq * x);
  const endVal = 1 - Math.exp(-damping) * Math.cos(freq);
  return raw / endVal;
}

// ============================================================================
// Procedural Studio Softbox PMREM Environment Map
// ============================================================================

export function createStudioEnvMap(renderer, mode = 'light') {
  const isDark = mode === 'dark';
  const envScene = new THREE.Scene();

  // Inverted studio dome
  const domeGeo = new THREE.SphereGeometry(18, 32, 24);
  const domeMat = new THREE.MeshBasicMaterial({
    color: isDark ? '#1A1714' : '#EFE9DF',
    side: THREE.BackSide
  });
  envScene.add(new THREE.Mesh(domeGeo, domeMat));

  const addPanel = (color, intensity, pos, scale, rot) => {
    const col = new THREE.Color(color).multiplyScalar(intensity);
    const panel = new THREE.Mesh(
      new THREE.PlaneGeometry(scale[0], scale[1]),
      new THREE.MeshBasicMaterial({ color: col, side: THREE.DoubleSide })
    );
    panel.position.set(pos[0], pos[1], pos[2]);
    if (rot) panel.rotation.set(rot[0], rot[1], rot[2]);
    else panel.lookAt(0, 0, 0);
    envScene.add(panel);
  };

  if (isDark) {
    // Overhead soft warm gallery light
    addPanel('#FFF2E2', 2.4, [0.8, 9.0, 2.5], [10, 8], [Math.PI / 2, 0, 0]);
    // Terracotta architectural kick card
    addPanel('#FF6B35', 1.6, [-7.5, 2.8, -5.0], [6, 5]);
    // Cool graphite fill bounce
    addPanel('#8E8277', 0.85, [-7.0, 2.5, 5.5], [8, 6]);
    // Warm amber side bounce
    addPanel('#D9B99B', 0.9, [8.0, 2.0, 4.0], [7, 5]);
    // Subtle matte plinth bounce
    addPanel('#26211D', 0.7, [0, -7.0, 0], [16, 16], [-Math.PI / 2, 0, 0]);
  } else {
    // Overhead softbox
    addPanel('#FFFDF9', 2.8, [0.5, 9.5, 2.2], [12, 10], [Math.PI / 2, 0, 0]);
    // Left cool-paper bounce card
    addPanel('#F4F1EC', 1.45, [-8.5, 3.2, 4.2], [9, 7]);
    // Right warm cream bounce card
    addPanel('#FFF3E3', 1.75, [8.5, 3.6, 3.5], [9, 7]);
    // Subtle back rim softbox
    addPanel('#FFE6CC', 1.25, [-2.5, 4.5, -8.5], [10, 5]);
    // Floor bounce
    addPanel('#EAE1D5', 1.15, [0, -7.5, 0], [18, 18], [-Math.PI / 2, 0, 0]);
  }

  const pmrem = new THREE.PMREMGenerator(renderer);
  pmrem.compileEquirectangularShader();
  const rt = pmrem.fromScene(envScene, 0.045);
  pmrem.dispose();
  domeGeo.dispose();
  domeMat.dispose();
  return rt.texture;
}

// ============================================================================
// Seamless Studio Cyclorama & Initialization
// ============================================================================

export function initStudio(canvasOrSelector = null, opts = {}) {
  const mode = opts.mode === 'dark' ? 'dark' : 'light';
  const isDark = mode === 'dark';

  const designW = opts.width ?? 1200;
  const designH = opts.height ?? 600;
  const actualW = window.innerWidth || designW;
  const actualH = window.innerHeight || designH;

  let canvas = null;
  if (typeof canvasOrSelector === 'string') {
    canvas = document.querySelector(canvasOrSelector);
  } else if (canvasOrSelector && canvasOrSelector.tagName === 'CANVAS') {
    canvas = canvasOrSelector;
  }

  const rendererOpts = {
    antialias: true,
    powerPreference: 'high-performance',
    alpha: Boolean(opts.alpha)
  };
  if (canvas) rendererOpts.canvas = canvas;

  const renderer = new THREE.WebGLRenderer(rendererOpts);
  if (!canvas) {
    canvas = renderer.domElement;
    const mount = (canvasOrSelector && canvasOrSelector.appendChild) ? canvasOrSelector : document.body;
    mount.appendChild(canvas);
  }
  canvas.style.display = 'block';
  canvas.style.width = '100%';
  canvas.style.height = '100%';

  renderer.setPixelRatio(window.devicePixelRatio || 1);
  renderer.setSize(actualW, actualH, false);
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = opts.exposure ?? 1.08;
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFSoftShadowMap;

  const bgHex = opts.background ?? (isDark ? '#141210' : '#FAF7F2');
  const scene = new THREE.Scene();
  scene.background = new THREE.Color(bgHex);
  scene.fog = new THREE.FogExp2(bgHex, opts.fogDensity ?? (isDark ? 0.042 : 0.028));

  const envMap = createStudioEnvMap(renderer, mode);
  scene.environment = envMap;

  const camera = new THREE.PerspectiveCamera(opts.fov ?? 32, actualW / actualH, 0.1, 60);
  const camPos = opts.camPos ?? [0, 2.1, 6.0];
  const lookAt = opts.lookAt ?? [0, 0.3, 0];
  camera.position.set(camPos[0], camPos[1], camPos[2]);
  camera.lookAt(lookAt[0], lookAt[1], lookAt[2]);

  // Studio Lighting Rig
  const hemi = new THREE.HemisphereLight(
    isDark ? '#4F453C' : '#FFFDF9',
    isDark ? '#141210' : '#DED3C6',
    isDark ? 0.72 : 0.88
  );
  scene.add(hemi);

  const key = new THREE.DirectionalLight(
    isDark ? '#FFEEDB' : '#FFF8EE',
    isDark ? 2.35 : 1.95
  );
  key.position.set(3.8, 6.8, 4.8);
  key.castShadow = true;
  key.shadow.mapSize.set(2048, 2048);
  key.shadow.radius = 4;
  key.shadow.camera.near = 1.0;
  key.shadow.camera.far = 22.0;
  const f = opts.shadowFrustum ?? 4.5;
  key.shadow.camera.left = -f;
  key.shadow.camera.right = f;
  key.shadow.camera.top = f;
  key.shadow.camera.bottom = -f;
  key.shadow.bias = -0.00035;
  key.shadow.normalBias = 0.015;
  scene.add(key);

  const fill = new THREE.DirectionalLight(
    isDark ? '#786D63' : '#F4EFE8',
    isDark ? 0.55 : 0.62
  );
  fill.position.set(-4.5, 2.8, 3.6);
  scene.add(fill);

  const rim = new THREE.DirectionalLight(
    isDark ? '#FF6B35' : '#FE9114',
    isDark ? 1.15 : 0.45
  );
  rim.position.set(-3.4, 3.4, -4.4);
  scene.add(rim);

  // Seamless Curved Studio Cyclorama + Shadow Catcher (zero horizon line)
  const floorY = opts.floorY ?? -1.05;
  const cyclorama = new THREE.Group();
  cyclorama.position.y = floorY;

  if (opts.cyclorama !== false) {
    // Smooth curve from flat stage floor up into seamless backdrop sweep
    const sweepPts = [];
    const flatDepth = 6.5;
    const curveRadius = 5.5;
    const wallHeight = 7.5;
    sweepPts.push(new THREE.Vector2(flatDepth, 0));
    sweepPts.push(new THREE.Vector2(-1.5, 0));
    const arcSteps = 20;
    for (let i = 1; i <= arcSteps; i++) {
      const a = (i / arcSteps) * (Math.PI / 2);
      sweepPts.push(new THREE.Vector2(
        -1.5 - Math.sin(a) * curveRadius,
        curveRadius - Math.cos(a) * curveRadius
      ));
    }
    sweepPts.push(new THREE.Vector2(-1.5 - curveRadius, curveRadius + wallHeight));

    // Build extruded wide cyclorama sheet along X
    const widthX = 36;
    const segsZ = sweepPts.length - 1;
    const cycGeo = new THREE.BufferGeometry();
    const verts = [];
    const uvs = [];
    const indices = [];
    for (let j = 0; j <= segsZ; j++) {
      const pt = sweepPts[j];
      const v = j / segsZ;
      verts.push(-widthX / 2, pt.y, pt.x);
      verts.push(widthX / 2, pt.y, pt.x);
      uvs.push(0, v, 1, v);
      if (j < segsZ) {
        const a = j * 2;
        indices.push(a, a + 2, a + 1, a + 1, a + 2, a + 3);
      }
    }
    cycGeo.setAttribute('position', new THREE.Float32BufferAttribute(verts, 3));
    cycGeo.setAttribute('uv', new THREE.Float32BufferAttribute(uvs, 2));
    cycGeo.setIndex(indices);
    cycGeo.computeVertexNormals();

    // ShadowMaterial catches soft PCFSoftShadowMap shadows directly over bgHex -> zero horizon seam
    const shadowCatcherMat = new THREE.ShadowMaterial({
      color: isDark ? '#050403' : '#594A3D',
      opacity: opts.shadowOpacity ?? (isDark ? 0.52 : 0.17)
    });
    const shadowSweep = new THREE.Mesh(cycGeo, shadowCatcherMat);
    shadowSweep.receiveShadow = true;
    cyclorama.add(shadowSweep);
  }
  scene.add(cyclorama);

  return {
    renderer,
    scene,
    camera,
    envMap,
    cyclorama,
    lights: { hemi, key, fill, rim },
    designSize: { width: designW, height: designH },
    actualSize: { width: actualW, height: actualH },
    scaleFactor: actualW / designW
  };
}

// ============================================================================
// Procedural Cotton-Paper PBR Material
// ============================================================================

let _cachedCottonMaps = null;

export function getCottonPaperMaps() {
  if (_cachedCottonMaps) return _cachedCottonMaps;

  const size = 512;
  const bumpCanvas = document.createElement('canvas');
  bumpCanvas.width = size;
  bumpCanvas.height = size;
  const bctx = bumpCanvas.getContext('2d');

  const roughCanvas = document.createElement('canvas');
  roughCanvas.width = size;
  roughCanvas.height = size;
  const rctx = roughCanvas.getContext('2d');

  const rng = mulberry32(46209);
  bctx.fillStyle = '#808080';
  bctx.fillRect(0, 0, size, size);
  rctx.fillStyle = '#DCDCDC';
  rctx.fillRect(0, 0, size, size);

  // Soft cotton-rag mottling (wrapped edges for seamless tiling)
  for (let i = 0; i < 3600; i++) {
    const x = rng() * size;
    const y = rng() * size;
    const r = 0.8 + rng() * 2.2;
    const bVal = Math.floor(114 + rng() * 30);
    const rVal = Math.floor(200 + rng() * 44);
    bctx.fillStyle = `rgba(${bVal},${bVal},${bVal},0.18)`;
    rctx.fillStyle = `rgba(${rVal},${rVal},${rVal},0.16)`;
    for (const dx of [-size, 0, size]) {
      for (const dy of [-size, 0, size]) {
        if (x + dx > -4 && x + dx < size + 4 && y + dy > -4 && y + dy < size + 4) {
          bctx.beginPath(); bctx.arc(x + dx, y + dy, r, 0, Math.PI * 2); bctx.fill();
          rctx.beginPath(); rctx.arc(x + dx, y + dy, r * 1.1, 0, Math.PI * 2); rctx.fill();
        }
      }
    }
  }

  // Fine interlocking cellulose fibers
  for (let i = 0; i < 2400; i++) {
    const x = rng() * size;
    const y = rng() * size;
    const len = 5 + rng() * 14;
    const ang = rng() * Math.PI * 2;
    const shade = rng() > 0.5 ? 162 : 98;
    bctx.strokeStyle = `rgba(${shade},${shade},${shade},0.22)`;
    bctx.lineWidth = 0.6;
    bctx.beginPath();
    bctx.moveTo(x, y);
    bctx.quadraticCurveTo(
      x + Math.cos(ang + 0.3) * len * 0.5,
      y + Math.sin(ang + 0.3) * len * 0.5,
      x + Math.cos(ang) * len,
      y + Math.sin(ang) * len
    );
    bctx.stroke();
  }

  const bumpMap = new THREE.CanvasTexture(bumpCanvas);
  bumpMap.wrapS = THREE.RepeatWrapping;
  bumpMap.wrapT = THREE.RepeatWrapping;
  bumpMap.repeat.set(2, 2);

  const roughnessMap = new THREE.CanvasTexture(roughCanvas);
  roughnessMap.wrapS = THREE.RepeatWrapping;
  roughnessMap.wrapT = THREE.RepeatWrapping;
  roughnessMap.repeat.set(2, 2);

  _cachedCottonMaps = { bumpMap, roughnessMap };
  return _cachedCottonMaps;
}

export function createCottonPaperMaterial(opts = {}) {
  const { bumpMap, roughnessMap } = getCottonPaperMaps();
  const matOpts = {
    color: opts.color ?? '#FFFDF9',
    roughness: opts.roughness ?? 0.88,
    metalness: opts.metalness ?? 0.0,
    sheen: opts.sheen ?? 0.4,
    sheenRoughness: opts.sheenRoughness ?? 0.7,
    sheenColor: new THREE.Color(opts.sheenColor ?? '#FFF8EE'),
    bumpMap,
    bumpScale: opts.bumpScale ?? 0.008,
    roughnessMap,
    envMapIntensity: opts.envMapIntensity ?? 0.65,
    side: opts.side ?? THREE.DoubleSide
  };

  if (opts.map) matOpts.map = opts.map;
  if (opts.normalMap) matOpts.normalMap = opts.normalMap;
  if (opts.normalScale) matOpts.normalScale = opts.normalScale;
  if (opts.vertexColors !== undefined) matOpts.vertexColors = opts.vertexColors;
  if (opts.transparent !== undefined) matOpts.transparent = opts.transparent;
  if (opts.opacity !== undefined) matOpts.opacity = opts.opacity;
  if (opts.clearcoat !== undefined) matOpts.clearcoat = opts.clearcoat;
  if (opts.clearcoatRoughness !== undefined) matOpts.clearcoatRoughness = opts.clearcoatRoughness;
  if (opts.emissive) matOpts.emissive = new THREE.Color(opts.emissive);
  if (opts.emissiveIntensity !== undefined) matOpts.emissiveIntensity = opts.emissiveIntensity;
  if (opts.alphaTest !== undefined) matOpts.alphaTest = opts.alphaTest;

  return new THREE.MeshPhysicalMaterial(matOpts);
}

// ============================================================================
// Authentic Editorial PDF Page Textures (1024 x 1320)
// ============================================================================

export function createPdfPageTexture(variant = 'brief', dark = false) {
  const W = 1024;
  const H = 1320;
  const c = document.createElement('canvas');
  c.width = W;
  c.height = H;
  const ctx = c.getContext('2d');

  const bg = dark ? '#1E1A17' : '#FFFDF9';
  const subtle = dark ? '#29231F' : '#F5EFE6';
  const ink = dark ? '#F5EFE6' : '#241E1B';
  const muted = dark ? '#A89E94' : '#6E645D';
  const rule = dark ? 'rgba(245, 239, 230, 0.14)' : 'rgba(36, 30, 27, 0.14)';
  const accent = dark ? '#FF6B35' : '#E8501C';

  ctx.fillStyle = bg;
  ctx.fillRect(0, 0, W, H);

  // Subtle paper vignette
  const vig = ctx.createRadialGradient(W * 0.5, H * 0.45, 160, W * 0.5, H * 0.5, 820);
  vig.addColorStop(0, 'rgba(0,0,0,0)');
  vig.addColorStop(1, dark ? 'rgba(0,0,0,0.22)' : 'rgba(218, 206, 190, 0.22)');
  ctx.fillStyle = vig;
  ctx.fillRect(0, 0, W, H);

  // Corner registration crop marks
  ctx.strokeStyle = rule;
  ctx.lineWidth = 1.5;
  const m = 54;
  const tk = 20;
  [[m, m, 1, 1], [W - m, m, -1, 1], [m, H - m, 1, -1], [W - m, H - m, -1, -1]].forEach(([x, y, dx, dy]) => {
    ctx.beginPath();
    ctx.moveTo(x, y + dy * tk);
    ctx.lineTo(x, y);
    ctx.lineTo(x + dx * tk, y);
    ctx.stroke();
  });

  // Running header & footer folio
  ctx.fillStyle = muted;
  ctx.font = '600 16px "Segoe UI", "Liberation Sans", sans-serif';
  ctx.fillText('OCTAPDF  /  PAPER ARCHITECTURE', 92, 92);
  ctx.textAlign = 'right';
  const folioMap = { brief: '01 / 04', architecture: '02 / 04', contract: '03 / 04', ledger: '04 / 04' };
  ctx.fillText(folioMap[variant] || '01 / 04', W - 92, 92);
  ctx.textAlign = 'left';

  ctx.strokeStyle = rule;
  ctx.lineWidth = 1;
  ctx.beginPath();
  ctx.moveTo(92, 112);
  ctx.lineTo(W - 92, 112);
  ctx.stroke();

  const drawTextParagraph = (lines, x, y, lineH = 29) => {
    ctx.fillStyle = ink;
    ctx.font = '400 19px "Georgia", "Times New Roman", serif';
    lines.forEach((ln, idx) => ctx.fillText(ln, x, y + idx * lineH));
    return y + lines.length * lineH;
  };

  if (variant === 'brief') {
    ctx.fillStyle = accent;
    ctx.fillRect(92, 154, 52, 4);
    ctx.fillStyle = muted;
    ctx.font = '700 15px "Segoe UI", sans-serif';
    ctx.fillText('EXECUTIVE BRIEF  ·  LOCAL RUNTIME', 92, 190);

    ctx.fillStyle = ink;
    ctx.font = '700 44px "Segoe UI", "Liberation Sans", sans-serif';
    ctx.fillText('Precision Document Craft', 92, 246);
    ctx.fillText('Without Cloud Exposure.', 92, 298);

    let y = drawTextParagraph([
      'Every merge, split, digital signature, and OCR pass executes strictly',
      'within local memory and ephemeral sandboxed storage. No external',
      'telemetry leaves the workstation.'
    ], 92, 360);

    // Subtle 3-cell KPI table
    y += 38;
    ctx.fillStyle = subtle;
    ctx.fillRect(92, y, W - 184, 134);
    ctx.strokeStyle = rule;
    ctx.strokeRect(92, y, W - 184, 134);
    const cols = [
      ['ENGINE', 'PDFBox 3.0.5', 'Pure JVM Core'],
      ['SECURITY', 'CSP Strict', 'Zero Telemetry'],
      ['OCR LANGS', '13 Packs', 'Tesseract Local']
    ];
    cols.forEach(([k, v, s], i) => {
      const cx = 124 + i * 280;
      if (i > 0) {
        ctx.beginPath();
        ctx.moveTo(cx - 24, y + 18);
        ctx.lineTo(cx - 24, y + 116);
        ctx.stroke();
      }
      ctx.fillStyle = accent;
      ctx.font = '700 13px "Segoe UI", sans-serif';
      ctx.fillText(k, cx, y + 38);
      ctx.fillStyle = ink;
      ctx.font = '700 28px "Segoe UI", sans-serif';
      ctx.fillText(v, cx, y + 78);
      ctx.fillStyle = muted;
      ctx.font = '400 15px "Segoe UI", sans-serif';
      ctx.fillText(s, cx, y + 108);
    });

    y += 188;
    ctx.fillStyle = ink;
    ctx.font = '700 22px "Segoe UI", sans-serif';
    ctx.fillText('1. Structural Integrity', 92, y);
    y = drawTextParagraph([
      'Origami-inspired page composition keeps vector glyphs, embedded ICC',
      'profiles, and XMP metadata intact across multi-stage transformations.',
      'Temporary scratch files are swept automatically by TempFileJanitorService.'
    ], 92, y + 36);

    y += 42;
    ctx.fillStyle = ink;
    ctx.font = '700 22px "Segoe UI", sans-serif';
    ctx.fillText('2. Verification Summary', 92, y);
    drawTextParagraph([
      'All endpoints enforce local-only Content-Security-Policy headers and',
      'strict MIME validation prior to native qpdf or LibreOffice invocation.'
    ], 92, y + 36);
  } else if (variant === 'architecture') {
    ctx.fillStyle = accent;
    ctx.fillRect(92, 154, 52, 4);
    ctx.fillStyle = muted;
    ctx.font = '700 15px "Segoe UI", sans-serif';
    ctx.fillText('SYSTEM SPECIFICATION  ·  STACK TOPOLOGY', 92, 190);

    ctx.fillStyle = ink;
    ctx.font = '700 42px "Segoe UI", sans-serif';
    ctx.fillText('Four-Tier Local Pipeline', 92, 246);

    const tiers = [
      ['01', 'Paper Desk Client', 'Bootstrap 5 · PDF.js · pdf-lib · signature_pad'],
      ['02', 'Spring Boot 3.4.5 + Jetty', 'SecurityHeadersFilter · IPRateLimitingFilter'],
      ['03', 'JVM Document Core', 'Apache PDFBox 3.0.5 · BouncyCastle 1.80 · Tabula'],
      ['04', 'Local Native Engines', 'qpdf · Tesseract OCR · LibreOffice · Poppler']
    ];
    let ty = 310;
    tiers.forEach(([num, title, sub]) => {
      ctx.strokeStyle = rule;
      ctx.strokeRect(92, ty, W - 184, 118);
      ctx.fillStyle = accent;
      ctx.font = '700 20px "Consolas", monospace';
      ctx.fillText(num, 122, ty + 48);
      ctx.fillStyle = ink;
      ctx.font = '700 24px "Segoe UI", sans-serif';
      ctx.fillText(title, 182, ty + 48);
      ctx.fillStyle = muted;
      ctx.font = '400 18px "Segoe UI", sans-serif';
      ctx.fillText(sub, 182, ty + 84);
      ty += 142;
    });

    // Blueprint mini schematic
    ctx.strokeStyle = accent;
    ctx.lineWidth = 1.5;
    ctx.strokeRect(92, ty + 20, W - 184, 210);
    ctx.strokeStyle = rule;
    for (let gx = 152; gx < W - 100; gx += 72) {
      ctx.beginPath(); ctx.moveTo(gx, ty + 20); ctx.lineTo(gx, ty + 230); ctx.stroke();
    }
    ctx.fillStyle = muted;
    ctx.font = '600 15px "Consolas", monospace';
    ctx.fillText('NETWORK: internal=true  |  ROOTFS: read_only  |  CONNECT-SRC: self', 120, ty + 130);
  } else if (variant === 'contract') {
    ctx.fillStyle = accent;
    ctx.fillRect(92, 154, 52, 4);
    ctx.fillStyle = muted;
    ctx.font = '700 15px "Segoe UI", sans-serif';
    ctx.fillText('EXECUTION & INTEGRITY ATTESTATION', 92, 190);

    ctx.fillStyle = ink;
    ctx.font = '700 40px "Georgia", serif';
    ctx.fillText('Certificate of Local Processing', 92, 248);

    let y = drawTextParagraph([
      'ARTICLE I — CUSTODY OF MATERIAL',
      'All source manuscripts, exhibits, and signature vectors remain exclusively',
      'under host filesystem custody throughout assembly and compression.',
      '',
      'ARTICLE II — CRYPTOGRAPHIC SEALING',
      'Digital signatures apply PKCS#7 / BouncyCastle 1.80 verification chains',
      'directly within local JVM execution without third-party key escrow.'
    ], 92, 318, 32);

    y += 96;
    ctx.fillStyle = subtle;
    ctx.fillRect(92, y, W - 184, 230);
    ctx.strokeStyle = rule;
    ctx.strokeRect(92, y, W - 184, 230);

    ctx.fillStyle = muted;
    ctx.font = '700 14px "Segoe UI", sans-serif';
    ctx.fillText('AUTHORIZED SIGNATURE', 130, y + 48);
    ctx.strokeStyle = accent;
    ctx.lineWidth = 3.2;
    ctx.beginPath();
    ctx.moveTo(136, y + 135);
    ctx.bezierCurveTo(180, y + 82, 224, y + 162, 286, y + 112);
    ctx.bezierCurveTo(335, y + 72, 378, y + 148, 456, y + 116);
    ctx.stroke();

    ctx.strokeStyle = rule;
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    ctx.moveTo(130, y + 162);
    ctx.lineTo(520, y + 162);
    ctx.stroke();

    ctx.fillStyle = ink;
    ctx.font = '600 16px "Segoe UI", sans-serif';
    ctx.fillText('Verified Local Seal · v0.46.2', 130, y + 194);
  } else {
    // 'ledger'
    ctx.fillStyle = accent;
    ctx.fillRect(92, 154, 52, 4);
    ctx.fillStyle = muted;
    ctx.font = '700 15px "Segoe UI", sans-serif';
    ctx.fillText('STRUCTURED DATA  ·  TABULA EXTRACTION', 92, 190);

    ctx.fillStyle = ink;
    ctx.font = '700 42px "Segoe UI", sans-serif';
    ctx.fillText('Repository Language Ledger', 92, 246);

    const rows = [
      ['Java', '1,680,452 B', '59.45%'],
      ['HTML', '492,618 B', '17.43%'],
      ['JavaScript', '408,096 B', '14.44%'],
      ['CSS', '104,548 B', '3.70%'],
      ['RTF', '41,852 B', '1.48%'],
      ['Python', '37,797 B', '1.34%'],
      ['Shell', '37,399 B', '1.32%'],
      ['Gherkin', '19,215 B', '0.68%'],
      ['Dockerfile', '4,585 B', '0.16%']
    ];
    let ry = 310;
    rows.forEach(([lang, bytes, pct], idx) => {
      if (idx % 2 === 0) {
        ctx.fillStyle = subtle;
        ctx.fillRect(92, ry - 30, W - 184, 56);
      }
      ctx.fillStyle = idx === 0 ? accent : ink;
      ctx.font = '700 20px "Segoe UI", sans-serif';
      ctx.fillText(lang, 118, ry + 6);

      ctx.fillStyle = muted;
      ctx.font = '400 19px "Consolas", monospace';
      ctx.textAlign = 'right';
      ctx.fillText(bytes, W - 260, ry + 6);
      ctx.fillStyle = ink;
      ctx.font = '700 19px "Consolas", monospace';
      ctx.fillText(pct, W - 118, ry + 6);
      ctx.textAlign = 'left';

      ry += 62;
    });
  }

  // Footer hairline
  ctx.strokeStyle = rule;
  ctx.lineWidth = 1;
  ctx.beginPath();
  ctx.moveTo(92, H - 102);
  ctx.lineTo(W - 92, H - 102);
  ctx.stroke();

  ctx.fillStyle = muted;
  ctx.font = '400 14px "Consolas", monospace';
  ctx.fillText('LOCAL CUSTODY  ·  MIT OPEN SOURCE', 92, H - 68);

  const tex = new THREE.CanvasTexture(c);
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.anisotropy = 8;
  return tex;
}

// ============================================================================
// Swiss Editorial Typography Overlay (Zero Rounded HTML Pill Badges)
// ============================================================================

export function createEditorialCaptionLayer(container = document.body, opts = {}) {
  const mode = opts.mode === 'dark' ? 'dark' : 'light';
  const designW = opts.designWidth ?? 1200;
  const designH = opts.designHeight ?? 600;
  const scale = (window.innerWidth || designW) / designW;

  const styleId = 'octa-studio-editorial-style';
  if (!document.getElementById(styleId)) {
    const st = document.createElement('style');
    st.id = styleId;
    st.textContent = `
      @font-face {
        font-family: 'OctaEditorialSans';
        src: url('/src/main/resources/static/pdfjs-legacy/standard_fonts/LiberationSans-Regular.ttf') format('truetype');
        font-weight: 400; font-style: normal; font-display: block;
      }
      @font-face {
        font-family: 'OctaEditorialSans';
        src: url('/src/main/resources/static/pdfjs-legacy/standard_fonts/LiberationSans-Bold.ttf') format('truetype');
        font-weight: 700; font-style: normal; font-display: block;
      }
      @font-face {
        font-family: 'OctaEditorialSerif';
        src: url('/src/main/resources/static/fonts/Tinos-Regular.woff2') format('woff2');
        font-weight: 400; font-style: normal; font-display: block;
      }
      .octa-hud-root {
        position: absolute;
        top: 0;
        left: 0;
        width: ${designW}px;
        height: ${designH}px;
        transform-origin: top left;
        pointer-events: none;
        font-family: 'OctaEditorialSans', 'Segoe UI', system-ui, sans-serif;
        -webkit-font-smoothing: antialiased;
      }
      .octa-hud-corner {
        position: absolute;
        width: 14px;
        height: 14px;
        border-style: solid;
        border-width: 0;
      }
      .octa-hud-kicker {
        position: absolute;
        top: 30px;
        left: 42px;
        font-size: 10.5px;
        font-weight: 700;
        letter-spacing: 0.24em;
        text-transform: uppercase;
        display: flex;
        align-items: center;
        gap: 10px;
      }
      .octa-hud-kicker-bar {
        width: 18px;
        height: 1.5px;
        display: inline-block;
      }
      .octa-hud-caption {
        position: absolute;
        left: 42px;
        bottom: 30px;
        font-size: 11px;
        font-weight: 700;
        letter-spacing: 0.18em;
        text-transform: uppercase;
      }
      .octa-hud-caption .idx {
        margin-right: 10px;
      }
    `;
    document.head.appendChild(st);
  }

  const root = document.createElement('div');
  root.className = 'octa-hud-root';
  root.style.transform = `scale(${scale})`;

  const ink = mode === 'dark' ? '#F5EFE6' : '#241E1B';
  const muted = mode === 'dark' ? '#A89E94' : '#6E645D';
  const accent = mode === 'dark' ? '#FF6B35' : '#E8501C';
  const hairline = mode === 'dark' ? 'rgba(245, 239, 230, 0.16)' : 'rgba(36, 30, 27, 0.14)';

  if (opts.corners !== false) {
    const pos = [
      ['top:22px;left:24px;border-top-width:1px;border-left-width:1px;'],
      ['top:22px;right:24px;border-top-width:1px;border-right-width:1px;'],
      ['bottom:22px;left:24px;border-bottom-width:1px;border-left-width:1px;'],
      ['bottom:22px;right:24px;border-bottom-width:1px;border-right-width:1px;']
    ];
    pos.forEach(([css]) => {
      const c = document.createElement('div');
      c.className = 'octa-hud-corner';
      c.style.cssText = `${css}border-color:${hairline};`;
      root.appendChild(c);
    });
  }

  const kickerEl = document.createElement('div');
  kickerEl.className = 'octa-hud-kicker';
  kickerEl.style.color = muted;
  kickerEl.innerHTML = `<span class="octa-hud-kicker-bar" style="background:${accent};"></span><span class="kicker-text"></span>`;
  if (!opts.kicker) kickerEl.style.opacity = '0';
  else kickerEl.querySelector('.kicker-text').textContent = opts.kicker;
  root.appendChild(kickerEl);

  const captionEl = document.createElement('div');
  captionEl.className = 'octa-hud-caption';
  captionEl.style.color = muted;
  captionEl.innerHTML = `<span class="idx" style="color:${accent};"></span><span class="lbl"></span>`;
  captionEl.style.opacity = '0';
  root.appendChild(captionEl);

  container.appendChild(root);

  return {
    root,
    kickerEl,
    captionEl,
    colors: { ink, muted, accent, hairline },
    setKicker(text, opacity = 1) {
      kickerEl.querySelector('.kicker-text').textContent = text;
      kickerEl.style.opacity = String(opacity);
    },
    setCaption(idx, label, opacity = 0.85) {
      captionEl.querySelector('.idx').textContent = idx;
      captionEl.querySelector('.lbl').textContent = label;
      captionEl.style.opacity = String(opacity);
    }
  };
}

// ============================================================================
// Folded Sheet & Tapered Paper Ribbon Geometry Builders
// ============================================================================

export function foldPlaneGeometry(w = 1.9, h = 2.5, segsX = 32, segsY = 44) {
  const geo = new THREE.PlaneGeometry(w, h, segsX, segsY);
  const basePos = Float32Array.from(geo.attributes.position.array);
  const count = geo.attributes.position.count;
  const colors = new Float32Array(count * 3);
  geo.setAttribute('color', new THREE.BufferAttribute(colors, 3));

  function applyFold({
    fold = 0,
    curl = 0,
    diagFold = 0,
    wavePhase = 0,
    waveAmp = 0,
    aoStrength = 0.24
  } = {}) {
    const pos = geo.attributes.position;
    const col = geo.attributes.color;
    for (let i = 0; i < count; i++) {
      const bx = basePos[i * 3];
      const by = basePos[i * 3 + 1];
      const nxNorm = bx / (w * 0.5);
      const nyNorm = by / (h * 0.5);

      // Center origami spine valley + diagonal facets
      const absX = Math.abs(nxNorm);
      const spineZ = absX * 0.68 * fold;
      const diagA = Math.abs(nxNorm * 0.78 - nyNorm * 0.62);
      const diagB = Math.abs(nxNorm * 0.78 + nyNorm * 0.62);
      const diagZ = (Math.max(0, 0.48 - diagA) * 0.42 + Math.max(0, 0.48 - diagB) * 0.32) * diagFold;

      // Smooth architectural page curl from upper-right corner
      const cornerDist = clamp01((nxNorm + nyNorm + 0.6) * 0.52);
      const curlZ = Math.pow(cornerDist, 2.2) * 0.65 * curl;
      const curlXShift = -Math.pow(cornerDist, 2.0) * 0.22 * curl;

      const waveZ = Math.sin(nxNorm * 2.4 + wavePhase) * 0.035 * waveAmp +
                    Math.cos(nyNorm * 1.9 - wavePhase) * 0.028 * waveAmp;

      const x = bx * (1 - 0.16 * fold) + curlXShift;
      const y = by * (1 - 0.05 * fold);
      const z = spineZ - diagZ + curlZ + waveZ;
      pos.setXYZ(i, x, y, z);

      // Soft vertex crease shading
      const creaseMask = Math.exp(-Math.pow(absX * 3.5, 2)) * 0.65 +
                         Math.exp(-Math.pow(Math.min(diagA, diagB) * 4.0, 2)) * 0.45 * diagFold;
      const shade = 1.0 - clamp01(creaseMask) * fold * aoStrength;
      col.setXYZ(i, shade, shade * 0.985, shade * 0.965);
    }
    pos.needsUpdate = true;
    col.needsUpdate = true;
    geo.computeVertexNormals();
  }

  applyFold();
  return { geometry: geo, applyFold };
}

export function createTaperedRibbonGeometry(segments = 48) {
  const geo = new THREE.BufferGeometry();
  const vertCount = (segments + 1) * 3;
  const positions = new Float32Array(vertCount * 3);
  const colors = new Float32Array(vertCount * 3);
  const uvs = new Float32Array(vertCount * 2);
  const indices = [];

  for (let s = 0; s < segments; s++) {
    const a = s * 3;
    const b = (s + 1) * 3;
    indices.push(a + 0, a + 1, b + 0, a + 1, b + 1, b + 0);
    indices.push(a + 1, a + 2, b + 1, a + 2, b + 2, b + 1);
  }
  for (let s = 0; s <= segments; s++) {
    const u = s / segments;
    const base = s * 2 * 3;
    uvs[base + 0] = u; uvs[base + 1] = 0;
    uvs[base + 2] = u; uvs[base + 3] = 0.5;
    uvs[base + 4] = u; uvs[base + 5] = 1;
  }

  geo.setAttribute('position', new THREE.BufferAttribute(positions, 3));
  geo.setAttribute('color', new THREE.BufferAttribute(colors, 3));
  geo.setAttribute('uv', new THREE.BufferAttribute(uvs, 2));
  geo.setIndex(indices);
  geo.userData.segments = segments;
  return geo;
}

const _rbP = new THREE.Vector3();
const _rbT = new THREE.Vector3();
const _rbS = new THREE.Vector3();
const _rbN = new THREE.Vector3();
const _rbC = new THREE.Color();

export function updateTaperedRibbon(geo, curve, {
  baseWidth = 0.14,
  creaseDepth = 0.25,
  twist = 0.0,
  colorLight = '#FE9114',
  colorMid = '#F54702',
  colorShadow = '#D53902',
  ivoryEdge = false,
  upAxis = [0, 0, 1]
} = {}) {
  const segs = geo.userData.segments || 48;
  const pos = geo.attributes.position;
  const col = geo.attributes.color;
  const up = new THREE.Vector3(upAxis[0], upAxis[1], upAxis[2]).normalize();
  const cL = new THREE.Color(colorLight);
  const cM = new THREE.Color(colorMid);
  const cS = new THREE.Color(colorShadow);
  const cIvory = new THREE.Color('#FFFDF9');

  for (let s = 0; s <= segs; s++) {
    const u = s / segs;
    curve.getPoint(u, _rbP);
    curve.getTangent(u, _rbT).normalize();

    _rbS.crossVectors(_rbT, up);
    if (_rbS.lengthSq() < 1e-4) _rbS.set(1, 0, 0);
    _rbS.normalize();
    _rbN.crossVectors(_rbS, _rbT).normalize();

    if (twist !== 0) {
      const a = u * twist;
      const cosA = Math.cos(a);
      const sinA = Math.sin(a);
      const sx = _rbS.x * cosA + _rbN.x * sinA;
      const sy = _rbS.y * cosA + _rbN.y * sinA;
      const sz = _rbS.z * cosA + _rbN.z * sinA;
      _rbS.set(sx, sy, sz).normalize();
      _rbN.crossVectors(_rbS, _rbT).normalize();
    }

    const taper = Math.sin(Math.pow(u, 0.72) * Math.PI * 0.92 + 0.14);
    const halfW = baseWidth * (0.35 + 0.75 * taper);
    const ridge = halfW * creaseDepth;

    const b = s * 3;
    pos.setXYZ(b + 0, _rbP.x - _rbS.x * halfW, _rbP.y - _rbS.y * halfW, _rbP.z - _rbS.z * halfW);
    pos.setXYZ(b + 1, _rbP.x + _rbN.x * ridge, _rbP.y + _rbN.y * ridge, _rbP.z + _rbN.z * ridge);
    pos.setXYZ(b + 2, _rbP.x + _rbS.x * halfW, _rbP.y + _rbS.y * halfW, _rbP.z + _rbS.z * halfW);

    if (ivoryEdge) {
      _rbC.copy(cIvory).lerp(cL, u * 0.55);
    } else {
      _rbC.copy(cS).lerp(cM, 0.35 + u * 0.45);
    }
    col.setXYZ(b + 0, _rbC.r, _rbC.g, _rbC.b);

    _rbC.copy(cM).lerp(cL, 0.55 - u * 0.2);
    col.setXYZ(b + 1, _rbC.r, _rbC.g, _rbC.b);

    _rbC.copy(cM).lerp(cS, 0.25 + u * 0.55);
    col.setXYZ(b + 2, _rbC.r, _rbC.g, _rbC.b);
  }

  pos.needsUpdate = true;
  col.needsUpdate = true;
  geo.computeVertexNormals();
}
