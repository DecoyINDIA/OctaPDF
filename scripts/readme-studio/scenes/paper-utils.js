import * as THREE from '/scripts/readme-studio/vendor/three.module.js';

export const PALETTE = {
  canvas: '#FAF7F2',
  paper: '#FFFFFF',
  ivory: '#FDF9F2',
  subtle: '#F5EFE6',
  border: '#E8E2D8',
  ink: '#241E1B',
  muted: '#6E645D',
  accent: '#E8501C',
  ribbonLight: '#FE9114',
  ribbonFace: '#F54702',
  ribbonShadow: '#D53902',
  darkCanvas: '#161412',
  darkSurface: '#1F1C19',
  darkAccent: '#FF6B35',
};

export function clamp01(x) {
  return Math.max(0, Math.min(1, x));
}

export function smoothstep(edge0, edge1, x) {
  const t = clamp01((x - edge0) / (edge1 - edge0));
  return t * t * (3 - 2 * t);
}

export function smootherstep(edge0, edge1, x) {
  const t = clamp01((x - edge0) / (edge1 - edge0));
  return t * t * t * (t * (t * 6 - 15) + 10);
}

export function easeInOutCubic(t) {
  const x = clamp01(t);
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function easeOutBack(t, overshoot = 1.35) {
  const x = clamp01(t);
  const c3 = overshoot + 1;
  return 1 + c3 * Math.pow(x - 1, 3) + overshoot * Math.pow(x - 1, 2);
}

export function cubicBezier3(p0, p1, p2, p3, t, target = new THREE.Vector3()) {
  const u = 1 - t;
  const tt = t * t;
  const uu = u * u;
  const uuu = uu * u;
  const ttt = tt * t;
  target.set(
    uuu * p0.x + 3 * uu * t * p1.x + 3 * u * tt * p2.x + ttt * p3.x,
    uuu * p0.y + 3 * uu * t * p1.y + 3 * u * tt * p2.y + ttt * p3.y,
    uuu * p0.z + 3 * uu * t * p1.z + 3 * u * tt * p2.z + ttt * p3.z
  );
  return target;
}

function mulberry32(seed) {
  let a = seed >>> 0;
  return function () {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/**
 * Generates deterministic procedural paper-fiber bump & roughness textures.
 */
export function createPaperFiberMaps(size = 512, seed = 42) {
  const bumpCanvas = document.createElement('canvas');
  bumpCanvas.width = size;
  bumpCanvas.height = size;
  const bctx = bumpCanvas.getContext('2d');
  const img = bctx.createImageData(size, size);
  const rand = mulberry32(seed);

  for (let i = 0; i < img.data.length; i += 4) {
    const v = 122 + Math.floor((rand() - 0.5) * 24);
    img.data[i] = v;
    img.data[i + 1] = v;
    img.data[i + 2] = v;
    img.data[i + 3] = 255;
  }
  bctx.putImageData(img, 0, 0);

  // Fine cellulose micro-fibers
  bctx.lineWidth = 0.8;
  for (let i = 0; i < 650; i++) {
    const x = rand() * size;
    const y = rand() * size;
    const len = 6 + rand() * 18;
    const ang = rand() * Math.PI * 2;
    const bright = rand() > 0.5 ? 'rgba(255,255,255,0.09)' : 'rgba(90,78,69,0.08)';
    bctx.strokeStyle = bright;
    bctx.beginPath();
    bctx.moveTo(x, y);
    bctx.lineTo(x + Math.cos(ang) * len, y + Math.sin(ang) * len);
    bctx.stroke();
  }

  const bumpMap = new THREE.CanvasTexture(bumpCanvas);
  bumpMap.wrapS = THREE.RepeatWrapping;
  bumpMap.wrapT = THREE.RepeatWrapping;
  bumpMap.needsUpdate = true;

  return { bumpMap };
}

/**
 * Generates a crisp architectural PDF sheet face texture with header, typography rules,
 * optional figure/chart block, signature field, and page index badge.
 */
export function createDocumentPageTexture(options = {}) {
  const {
    width = 512,
    height = 680,
    pageIndex = '01',
    title = 'ARCHITECTURAL SPECIFICATION',
    accentBand = true,
    hasSignatureGuide = false,
    hasStampTarget = false,
    variant = 'prose',
    bg = '#FFFFFF',
  } = options;

  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext('2d');

  // Ivory-white cardstock ground
  ctx.fillStyle = bg;
  ctx.fillRect(0, 0, width, height);

  // Subtle paper margin border
  ctx.strokeStyle = '#E8E2D8';
  ctx.lineWidth = 2;
  ctx.strokeRect(22, 22, width - 44, height - 44);

  // Top terracotta hairline bar
  if (accentBand) {
    ctx.fillStyle = '#E8501C';
    ctx.fillRect(42, 42, 48, 5);
  }

  // Page index top-right
  ctx.fillStyle = '#6E645D';
  ctx.font = '600 18px ui-monospace, SFMono-Regular, Consolas, monospace';
  ctx.textAlign = 'right';
  ctx.fillText(`PG ${pageIndex}`, width - 44, 52);

  // Document title
  ctx.textAlign = 'left';
  ctx.fillStyle = '#241E1B';
  ctx.font = '700 20px "Segoe UI", system-ui, -apple-system, sans-serif';
  ctx.fillText(title, 42, 86);

  ctx.fillStyle = '#6E645D';
  ctx.font = '500 12px ui-monospace, SFMono-Regular, Consolas, monospace';
  ctx.fillText('OCTAPDF  //  LOCAL PAPER DESK ENGINE', 42, 108);

  // Divider hairline
  ctx.fillStyle = '#E8E2D8';
  ctx.fillRect(42, 122, width - 84, 1.5);

  let y = 148;

  if (variant === 'diagram') {
    // Architectural wireframe schematic block
    ctx.fillStyle = '#F5EFE6';
    ctx.fillRect(42, y, width - 84, 150);
    ctx.strokeStyle = '#E8E2D8';
    ctx.lineWidth = 1.5;
    ctx.strokeRect(42, y, width - 84, 150);

    // Geometric wireframe preview inside
    ctx.strokeStyle = '#6E645D';
    ctx.beginPath();
    ctx.rect(72, y + 26, 130, 96);
    ctx.rect(232, y + 26, 190, 42);
    ctx.rect(232, y + 80, 120, 42);
    ctx.stroke();

    ctx.fillStyle = '#E8501C';
    ctx.fillRect(368, y + 80, 54, 42);
    y += 176;
  } else if (variant === 'columns') {
    const colW = (width - 104) / 2;
    for (let c = 0; c < 2; c++) {
      const cx = 42 + c * (colW + 20);
      ctx.fillStyle = c === 0 ? '#F5EFE6' : '#FAF7F2';
      ctx.fillRect(cx, y, colW, 132);
      ctx.fillStyle = '#241E1B';
      for (let r = 0; r < 5; r++) {
        const lw = colW - 28 - ((r * 23 + c * 17) % 45);
        ctx.fillRect(cx + 14, y + 20 + r * 21, lw, 6);
      }
    }
    y += 158;
  }

  // Crisp typographic lines
  const lineWidths = [0.92, 0.86, 0.95, 0.72, 0.90, 0.84, 0.64, 0.93, 0.78, 0.88, 0.56];
  for (let i = 0; i < lineWidths.length; i++) {
    if (y > height - 155) break;
    ctx.fillStyle = i % 4 === 0 ? '#241E1B' : '#8D8279';
    const h = i % 4 === 0 ? 7 : 5;
    ctx.fillRect(42, y, (width - 84) * lineWidths[i], h);
    y += i % 4 === 3 ? 24 : 16;
  }

  // Optional signature field at bottom
  if (hasSignatureGuide) {
    const sy = height - 116;
    ctx.fillStyle = '#FAF7F2';
    ctx.fillRect(42, sy - 24, 240, 74);
    ctx.strokeStyle = '#E8E2D8';
    ctx.strokeRect(42, sy - 24, 240, 74);

    ctx.strokeStyle = '#6E645D';
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    ctx.moveTo(58, sy + 28);
    ctx.lineTo(266, sy + 28);
    ctx.stroke();

    ctx.fillStyle = '#6E645D';
    ctx.font = '600 10px ui-monospace, SFMono-Regular, Consolas, monospace';
    ctx.fillText('AUTHORIZED SIGNATURE', 58, sy + 42);
  }

  if (hasStampTarget) {
    const sx = width - 102;
    const sy = height - 92;
    ctx.save();
    ctx.strokeStyle = 'rgba(232, 80, 28, 0.35)';
    ctx.setLineDash([4, 4]);
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    for (let k = 0; k < 8; k++) {
      const a = (k / 8) * Math.PI * 2 + Math.PI / 8;
      const px = sx + Math.cos(a) * 34;
      const py = sy + Math.sin(a) * 34;
      if (k === 0) ctx.moveTo(px, py);
      else ctx.lineTo(px, py);
    }
    ctx.closePath();
    ctx.stroke();
    ctx.restore();
  }

  // Footer rule
  ctx.fillStyle = '#E8E2D8';
  ctx.fillRect(42, height - 42, width - 84, 1);

  const tex = new THREE.CanvasTexture(canvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.anisotropy = 8;
  tex.needsUpdate = true;
  return tex;
}

/**
 * Builds a physical paper sheet mesh with distinct top face texture and warm cardstock edges.
 */
export function createPaperSheetMesh({
  width = 1.6,
  height = 2.12,
  thickness = 0.022,
  frontTexture = null,
  bumpMap = null,
  backColor = '#F5EFE6',
  edgeColor = '#E6DDD0',
  roughness = 0.68,
} = {}) {
  const geo = new THREE.BoxGeometry(width, height, thickness);
  const edgeMat = new THREE.MeshStandardMaterial({
    color: edgeColor,
    roughness: 0.82,
    metalness: 0.01,
  });
  const backMat = new THREE.MeshStandardMaterial({
    color: backColor,
    roughness: 0.78,
    metalness: 0.01,
    bumpMap,
    bumpScale: bumpMap ? 0.004 : 0,
  });
  const frontMat = new THREE.MeshStandardMaterial({
    color: '#FFFFFF',
    map: frontTexture,
    bumpMap,
    bumpScale: bumpMap ? 0.0045 : 0,
    roughness,
    metalness: 0.015,
  });

  // BoxGeometry face order: +x, -x, +y, -y, +z (front), -z (back)
  const mesh = new THREE.Mesh(geo, [edgeMat, edgeMat, edgeMat, edgeMat, frontMat, backMat]);
  mesh.castShadow = true;
  mesh.receiveShadow = true;
  return mesh;
}

/**
 * Creates a studio lighting rig with warm eggshell hemisphere + soft PCF key light + cool rim fill.
 */
export function setupStudioLighting(scene, {
  background = '#FAF7F2',
  shadowExtent = 5.5,
  keyPosition = [4.5, 7.2, 4.8],
  keyIntensity = 2.15,
} = {}) {
  scene.background = new THREE.Color(background);

  const hemi = new THREE.HemisphereLight('#FFFDF9', '#D6CEC3', 1.05);
  scene.add(hemi);

  const keyLight = new THREE.DirectionalLight('#FFF5E8', keyIntensity);
  keyLight.position.set(...keyPosition);
  keyLight.castShadow = true;
  keyLight.shadow.mapSize.set(2048, 2048);
  keyLight.shadow.camera.near = 0.5;
  keyLight.shadow.camera.far = 24;
  keyLight.shadow.camera.left = -shadowExtent;
  keyLight.shadow.camera.right = shadowExtent;
  keyLight.shadow.camera.top = shadowExtent;
  keyLight.shadow.camera.bottom = -shadowExtent;
  keyLight.shadow.bias = -0.00035;
  keyLight.shadow.normalBias = 0.012;
  scene.add(keyLight);

  const rimLight = new THREE.DirectionalLight('#F3ECE4', 0.55);
  rimLight.position.set(-4.5, 3.2, -3.5);
  scene.add(rimLight);

  return { hemi, keyLight, rimLight };
}

/**
 * Creates an embossed octagonal terracotta seal mesh (OctaPDF 8-sided mark).
 */
export function createOctagonalSeal(radius = 0.16, depth = 0.018) {
  const group = new THREE.Group();

  const outer = new THREE.Mesh(
    new THREE.CylinderGeometry(radius, radius * 1.04, depth, 8),
    new THREE.MeshStandardMaterial({
      color: PALETTE.accent,
      roughness: 0.34,
      metalness: 0.08,
    })
  );
  outer.rotation.x = Math.PI / 2;
  outer.castShadow = true;
  outer.receiveShadow = true;
  group.add(outer);

  const innerRing = new THREE.Mesh(
    new THREE.CylinderGeometry(radius * 0.72, radius * 0.72, depth * 1.15, 8),
    new THREE.MeshStandardMaterial({
      color: PALETTE.ribbonLight,
      roughness: 0.32,
      metalness: 0.12,
    })
  );
  innerRing.rotation.x = Math.PI / 2;
  group.add(innerRing);

  const core = new THREE.Mesh(
    new THREE.CylinderGeometry(radius * 0.44, radius * 0.44, depth * 1.28, 8),
    new THREE.MeshStandardMaterial({
      color: PALETTE.ribbonShadow,
      roughness: 0.38,
      metalness: 0.06,
    })
  );
  core.rotation.x = Math.PI / 2;
  group.add(core);

  return group;
}
