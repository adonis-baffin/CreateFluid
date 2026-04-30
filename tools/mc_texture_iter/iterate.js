import { Jimp, rgbaToInt, intToRGBA } from "jimp";
import fs from "node:fs/promises";
import path from "node:path";

const OUTPUT_DIR = "D:\\Edge Downloads\\test";

const SAMPLES = [
  {
    id: "soul_bucket",
    input: "D:\\Edge Downloads\\ef25f79e-c32c-4b3a-a1a5-96243c0f63fb.png",
    type: "item",
    padding: 0.08,
    mask: "D:\\Geography\\Dev\\CreateFluid\\src\\main\\resources\\assets\\fluid\\textures\\item\\honey_bucket.png",
    maskMode: "overlay"
  },
  {
    id: "chocolate_bar",
    input: "D:\\Edge Downloads\\QQ20260428-225430.png",
    type: "item",
    padding: 0.08
  },
  {
    id: "ultimate_tomahawk",
    input: "D:\\Edge Downloads\\14cfc1b5-fd36-4017-894b-6dcc94f6045f.png",
    type: "weapon",
    padding: 0.12,
    mask: "D:\\Edge Downloads\\spinning_tomahawk.png"
  }
];

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value));
}

function colorDistance(a, b) {
  return Math.sqrt((a.r - b.r) ** 2 + (a.g - b.g) ** 2 + (a.b - b.b) ** 2);
}

function saturation(r, g, b) {
  return Math.max(r, g, b) - Math.min(r, g, b);
}

function getPixel(img, x, y) {
  return intToRGBA(img.getPixelColor(x, y));
}

function setPixel(img, x, y, rgba) {
  img.setPixelColor(rgbaToInt(rgba.r, rgba.g, rgba.b, rgba.a), x, y);
}

function estimateCornerBackground(img) {
  const radius = Math.max(3, Math.floor(Math.min(img.width, img.height) * 0.04));
  let sumR = 0;
  let sumG = 0;
  let sumB = 0;
  let count = 0;
  const corners = [
    [0, 0],
    [img.width - 1, 0],
    [0, img.height - 1],
    [img.width - 1, img.height - 1]
  ];

  for (const [cx, cy] of corners) {
    for (let dy = 0; dy < radius; dy++) {
      for (let dx = 0; dx < radius; dx++) {
        const x = cx === 0 ? dx : img.width - 1 - dx;
        const y = cy === 0 ? dy : img.height - 1 - dy;
        const { r, g, b, a } = getPixel(img, x, y);
        if (a <= 8) continue;
        sumR += r;
        sumG += g;
        sumB += b;
        count++;
      }
    }
  }

  return count
    ? { r: Math.round(sumR / count), g: Math.round(sumG / count), b: Math.round(sumB / count) }
    : { r: 0, g: 0, b: 0 };
}

function hasMeaningfulAlpha(img) {
  let transparent = 0;
  for (let y = 0; y < img.height; y++) {
    for (let x = 0; x < img.width; x++) {
      if (getPixel(img, x, y).a < 250) transparent++;
    }
  }
  return transparent / (img.width * img.height) > 0.005;
}

function buildForegroundMask(img, sample) {
  const alphaDriven = hasMeaningfulAlpha(img);
  const bg = estimateCornerBackground(img);
  const width = img.width;
  const height = img.height;
  const mask = new Uint8Array(width * height);

  const bgThresholdBase = sample.type === "weapon" ? 30 : 26;
  const edgeSoftThreshold = sample.type === "weapon" ? 18 : 14;

  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const idx = y * width + x;
      const p = getPixel(img, x, y);
      if (alphaDriven) {
        mask[idx] = p.a > 10 ? 1 : 0;
        continue;
      }

      const dist = colorDistance(p, bg);
      const sat = saturation(p.r, p.g, p.b);
      const centerBias = 1 - Math.hypot(x - width / 2, y - height / 2) / Math.hypot(width / 2, height / 2);
      const threshold = bgThresholdBase - centerBias * 6;

      if (dist >= threshold || sat >= 24) {
        mask[idx] = 1;
      } else if (dist >= edgeSoftThreshold && sat >= 10 && centerBias > 0.15) {
        mask[idx] = 1;
      }
    }
  }

  return { mask, alphaDriven, background: bg };
}

function largestCentralComponent(mask, width, height) {
  const visited = new Uint8Array(width * height);
  const centerX = (width - 1) / 2;
  const centerY = (height - 1) / 2;
  const maxDist = Math.hypot(centerX, centerY) || 1;
  let best = null;

  for (let start = 0; start < mask.length; start++) {
    if (!mask[start] || visited[start]) continue;
    const queue = [start];
    visited[start] = 1;
    const pixels = [];
    let minX = width;
    let minY = height;
    let maxX = -1;
    let maxY = -1;
    let sumX = 0;
    let sumY = 0;

    while (queue.length) {
      const idx = queue.pop();
      pixels.push(idx);
      const x = idx % width;
      const y = Math.floor(idx / width);
      sumX += x;
      sumY += y;
      if (x < minX) minX = x;
      if (y < minY) minY = y;
      if (x > maxX) maxX = x;
      if (y > maxY) maxY = y;

      if (x > 0) {
        const n = idx - 1;
        if (mask[n] && !visited[n]) {
          visited[n] = 1;
          queue.push(n);
        }
      }
      if (x < width - 1) {
        const n = idx + 1;
        if (mask[n] && !visited[n]) {
          visited[n] = 1;
          queue.push(n);
        }
      }
      if (y > 0) {
        const n = idx - width;
        if (mask[n] && !visited[n]) {
          visited[n] = 1;
          queue.push(n);
        }
      }
      if (y < height - 1) {
        const n = idx + width;
        if (mask[n] && !visited[n]) {
          visited[n] = 1;
          queue.push(n);
        }
      }
    }

    const cx = sumX / pixels.length;
    const cy = sumY / pixels.length;
    const centerScore = 1 - Math.hypot(cx - centerX, cy - centerY) / maxDist;
    const bboxArea = (maxX - minX + 1) * (maxY - minY + 1);
    const fillScore = pixels.length / Math.max(1, bboxArea);
    const score = pixels.length * (0.7 + Math.max(0, centerScore)) * (0.85 + fillScore * 0.4);

    if (!best || score > best.score) {
      best = { pixels, minX, minY, maxX, maxY, score };
    }
  }

  return best;
}

function dilateToKeepEdges(componentMask, img, background, iterations = 1) {
  const width = img.width;
  const height = img.height;
  let current = componentMask;

  for (let step = 0; step < iterations; step++) {
    const next = new Uint8Array(current);
    for (let y = 1; y < height - 1; y++) {
      for (let x = 1; x < width - 1; x++) {
        const idx = y * width + x;
        if (current[idx]) continue;
        let touches = 0;
        for (const [dx, dy] of [[1,0],[-1,0],[0,1],[0,-1]]) {
          if (current[(y + dy) * width + (x + dx)]) touches++;
        }
        if (!touches) continue;
        const p = getPixel(img, x, y);
        const dist = colorDistance(p, background);
        if (dist >= 10 || saturation(p.r, p.g, p.b) >= 14) {
          next[idx] = 1;
        }
      }
    }
    current = next;
  }

  return current;
}

function applyMaskToImage(img, mask) {
  const out = img.clone();
  for (let y = 0; y < img.height; y++) {
    for (let x = 0; x < img.width; x++) {
      if (!mask[y * img.width + x]) {
        setPixel(out, x, y, { r: 0, g: 0, b: 0, a: 0 });
      }
    }
  }
  return out;
}

function cleanupResidualBackground(img, background, strength = 16) {
  const out = img.clone();
  const width = out.width;
  const height = out.height;
  const keep = new Uint8Array(width * height);

  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const idx = y * width + x;
      const p = getPixel(out, x, y);
      if (p.a <= 10) continue;
      const dist = colorDistance(p, background);
      const sat = saturation(p.r, p.g, p.b);
      if (dist > strength || sat > 18) {
        keep[idx] = 1;
      }
    }
  }

  for (let y = 1; y < height - 1; y++) {
    for (let x = 1; x < width - 1; x++) {
      const idx = y * width + x;
      if (!keep[idx]) continue;
      let neighbors = 0;
      for (const [dx, dy] of [[1,0],[-1,0],[0,1],[0,-1]]) {
        if (keep[(y + dy) * width + (x + dx)]) neighbors++;
      }
      if (neighbors === 0) keep[idx] = 0;
    }
  }

  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      if (!keep[y * width + x]) {
        setPixel(out, x, y, { r: 0, g: 0, b: 0, a: 0 });
      }
    }
  }
  return out;
}

function boundsFromMask(mask, width, height) {
  let minX = width;
  let minY = height;
  let maxX = -1;
  let maxY = -1;
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      if (!mask[y * width + x]) continue;
      if (x < minX) minX = x;
      if (y < minY) minY = y;
      if (x > maxX) maxX = x;
      if (y > maxY) maxY = y;
    }
  }
  if (maxX < minX || maxY < minY) throw new Error("no foreground");
  return { minX, minY, maxX, maxY };
}

function cropSquare(img, bounds, paddingRatio) {
  const contentWidth = bounds.maxX - bounds.minX + 1;
  const contentHeight = bounds.maxY - bounds.minY + 1;
  const contentSide = Math.max(contentWidth, contentHeight);
  const padding = Math.round(contentSide * paddingRatio);
  const side = contentSide + padding * 2;
  const cx = (bounds.minX + bounds.maxX) / 2;
  const cy = (bounds.minY + bounds.maxY) / 2;
  let left = Math.round(cx - side / 2);
  let top = Math.round(cy - side / 2);
  left = clamp(left, 0, Math.max(0, img.width - side));
  top = clamp(top, 0, Math.max(0, img.height - side));
  return img.clone().crop({ x: left, y: top, w: Math.min(side, img.width - left), h: Math.min(side, img.height - top) });
}

function dominantBlockResize(img, targetSize) {
  const out = new Jimp({ width: targetSize, height: targetSize, color: 0x00000000 });
  for (let ty = 0; ty < targetSize; ty++) {
    const y0 = Math.floor((ty * img.height) / targetSize);
    const y1 = Math.max(y0 + 1, Math.ceil(((ty + 1) * img.height) / targetSize));
    for (let tx = 0; tx < targetSize; tx++) {
      const x0 = Math.floor((tx * img.width) / targetSize);
      const x1 = Math.max(x0 + 1, Math.ceil(((tx + 1) * img.width) / targetSize));
      const buckets = new Map();
      let solid = 0;
      for (let y = y0; y < y1; y++) {
        for (let x = x0; x < x1; x++) {
          const p = getPixel(img, x, y);
          if (p.a <= 10) continue;
          const key = `${Math.round(p.r / 24)},${Math.round(p.g / 24)},${Math.round(p.b / 24)}`;
          const weight = Math.max(0.35, (p.a / 255) ** 1.35);
          const bucket = buckets.get(key) || { w: 0, a: 0, r: 0, g: 0, b: 0 };
          bucket.w += weight;
          bucket.a += p.a * weight;
          bucket.r += p.r * weight;
          bucket.g += p.g * weight;
          bucket.b += p.b * weight;
          buckets.set(key, bucket);
          solid++;
        }
      }
      if (!solid) continue;
      let best = null;
      for (const bucket of buckets.values()) {
        if (!best || bucket.w > best.w) best = bucket;
      }
      let edgeBoost = null;
      if (solid > 0) {
        for (let y = y0; y < y1; y++) {
          for (let x = x0; x < x1; x++) {
            const p = getPixel(img, x, y);
            if (p.a <= 180) continue;
            if (!edgeBoost || p.a > edgeBoost.a) edgeBoost = p;
          }
        }
      }
      const chosen = edgeBoost && solid <= 3 ? edgeBoost : {
        r: Math.round(best.r / best.w),
        g: Math.round(best.g / best.w),
        b: Math.round(best.b / best.w),
        a: Math.round(best.a / best.w)
      };
      setPixel(out, tx, ty, {
        r: chosen.r,
        g: chosen.g,
        b: chosen.b,
        a: chosen.a
      });
    }
  }
  return out;
}

async function applyShapeMask(img, maskPath) {
  const mask = await Jimp.read(maskPath);
  mask.resize({ w: img.width, h: img.height });
  const out = img.clone();
  for (let y = 0; y < img.height; y++) {
    for (let x = 0; x < img.width; x++) {
      const m = getPixel(mask, x, y);
      if (m.a <= 10) setPixel(out, x, y, { r: 0, g: 0, b: 0, a: 0 });
    }
  }
  return out;
}

function getAlphaMask(img) {
  const mask = new Uint8Array(img.width * img.height);
  for (let y = 0; y < img.height; y++) {
    for (let x = 0; x < img.width; x++) {
      mask[y * img.width + x] = getPixel(img, x, y).a > 10 ? 1 : 0;
    }
  }
  return mask;
}

async function fitToMask(crop, maskPath, targetSize = 16, options = {}) {
  const maskImg = await Jimp.read(maskPath);
  maskImg.resize({ w: targetSize, h: targetSize, mode: "nearestNeighbor" });
  const maskBits = getAlphaMask(maskImg);
  const maskBounds = boundsFromMask(maskBits, targetSize, targetSize);
  const maskWidth = maskBounds.maxX - maskBounds.minX + 1;
  const maskHeight = maskBounds.maxY - maskBounds.minY + 1;

  let best = null;
  const preserveAspect = !!options.preserveAspect;
  const uniformScales = preserveAspect ? [0.88, 0.94, 1.0, 1.06, 1.12] : null;
  const widthScales = preserveAspect ? uniformScales : [0.8, 0.9, 1.0, 1.1, 1.2];
  const heightScales = preserveAspect ? uniformScales : [0.8, 0.9, 1.0, 1.1, 1.2];
  const offsetRange = preserveAspect ? 1 : 2;

  for (const widthScale of widthScales) {
    for (const heightScale of heightScales) {
      if (preserveAspect && widthScale !== heightScale) continue;
      const baseW = Math.max(1, Math.round(maskWidth * widthScale));
      const baseH = Math.max(1, Math.round(maskHeight * heightScale));
      const resized = crop.clone().resize({ w: baseW, h: baseH, mode: "nearestNeighbor" });

      for (let oy = -offsetRange; oy <= offsetRange; oy++) {
        for (let ox = -offsetRange; ox <= offsetRange; ox++) {
          const canvas = new Jimp({ width: targetSize, height: targetSize, color: 0x00000000 });
          const x = Math.round((targetSize - baseW) / 2) + ox;
          const y = Math.round((targetSize - baseH) / 2) + oy;
          canvas.composite(resized, x, y);
          const masked = await applyShapeMask(canvas, maskPath);
          const alphaBits = getAlphaMask(masked);

          let overlap = 0;
          let outside = 0;
          let insideMiss = 0;
          let painted = 0;
          for (let i = 0; i < alphaBits.length; i++) {
            const a = alphaBits[i];
            const m = maskBits[i];
            if (a) painted++;
            if (a && m) overlap++;
            else if (a && !m) outside++;
            else if (!a && m) insideMiss++;
          }

          const distortionPenalty = Math.abs(widthScale - 1) + Math.abs(heightScale - 1);
          const score = overlap * 5 - outside * 6 - insideMiss * 2 - distortionPenalty * 3;
          if (!best || score > best.score) {
            best = { img: masked, score };
          }
        }
      }
    }
  }

  return best ? best.img : applyShapeMask(dominantBlockResize(crop, targetSize), maskPath);
}

async function repairBucketOutline(img, maskPath) {
  const mask = await Jimp.read(maskPath);
  mask.resize({ w: img.width, h: img.height, mode: "nearestNeighbor" });
  const out = img.clone();

  for (let y = 0; y < img.height; y++) {
    for (let x = 0; x < img.width; x++) {
      const m = getPixel(mask, x, y);
      if (m.a <= 10) continue;
      const p = getPixel(out, x, y);
      if (p.a > 10) continue;
      setPixel(out, x, y, m);
    }
  }

  return out;
}

function trimResidualGrayEdgePixels(img) {
  const out = img.clone();
  const toClear = [];

  for (let y = 0; y < img.height; y++) {
    for (let x = 0; x < img.width; x++) {
      const p = getPixel(out, x, y);
      if (p.a <= 10) continue;
      if (saturation(p.r, p.g, p.b) > 18) continue;

      let opaqueNeighbors = 0;
      let vividNeighbors = 0;
      for (const [dx, dy] of [[1,0],[-1,0],[0,1],[0,-1],[1,1],[-1,1],[1,-1],[-1,-1]]) {
        const nx = x + dx;
        const ny = y + dy;
        if (nx < 0 || ny < 0 || nx >= img.width || ny >= img.height) continue;
        const np = getPixel(out, nx, ny);
        if (np.a > 10) {
          opaqueNeighbors++;
          if (saturation(np.r, np.g, np.b) > 18) vividNeighbors++;
        }
      }

      if (opaqueNeighbors <= 3 && vividNeighbors <= 2) {
        toClear.push([x, y]);
      }
    }
  }

  for (const [x, y] of toClear) {
    setPixel(out, x, y, { r: 0, g: 0, b: 0, a: 0 });
  }
  return out;
}

function upscaleNearest(img, factor) {
  return img.clone().resize({ w: img.width * factor, h: img.height * factor, mode: "nearestNeighbor" });
}

async function processSample(sample) {
  const img = await Jimp.read(sample.input);
  const { mask, alphaDriven, background } = buildForegroundMask(img, sample);
  const component = largestCentralComponent(mask, img.width, img.height);
  if (!component) throw new Error(`no component for ${sample.id}`);

  const componentMask = new Uint8Array(img.width * img.height);
  for (const idx of component.pixels) componentMask[idx] = 1;
  const expandedMask = alphaDriven ? componentMask : dilateToKeepEdges(componentMask, img, background, 2);
  const masked = applyMaskToImage(img, expandedMask);
  const bounds = boundsFromMask(expandedMask, img.width, img.height);
  const cleanedMasked = cleanupResidualBackground(masked, background, sample.type === "weapon" ? 18 : 15);
  const cleanBounds = boundsFromMask(getAlphaMask(cleanedMasked), img.width, img.height);
  const crop = cropSquare(cleanedMasked, cleanBounds, sample.padding);
  let out16;
  if (sample.mask && sample.maskMode === "overlay") {
    out16 = await applyShapeMask(dominantBlockResize(crop, 16), sample.mask);
  } else if (sample.mask) {
    out16 = await fitToMask(crop, sample.mask, 16, { preserveAspect: sample.preserveAspect });
  } else {
    out16 = dominantBlockResize(crop, 16);
  }

  if (sample.id === "soul_bucket" && sample.mask) {
    out16 = await repairBucketOutline(out16, sample.mask);
  }
  if (sample.id === "chocolate_bar") {
    out16 = trimResidualGrayEdgePixels(out16);
  }

  const sampleDir = path.join(OUTPUT_DIR, sample.id);
  await fs.mkdir(sampleDir, { recursive: true });
  await cleanedMasked.write(path.join(sampleDir, "masked.png"));
  await crop.write(path.join(sampleDir, "crop.png"));
  await out16.write(path.join(sampleDir, "out16.png"));
  await upscaleNearest(out16, 16).write(path.join(sampleDir, "out16_preview.png"));

  return {
    id: sample.id,
    alphaDriven,
    background,
    bounds,
    sampleDir
  };
}

async function main() {
  await fs.mkdir(OUTPUT_DIR, { recursive: true });
  const results = [];
  for (const sample of SAMPLES) {
    console.log(`processing ${sample.id}`);
    results.push(await processSample(sample));
  }
  console.log(JSON.stringify(results, null, 2));
}

main().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
