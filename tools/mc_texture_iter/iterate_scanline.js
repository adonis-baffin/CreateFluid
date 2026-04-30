import { Jimp, rgbaToInt, intToRGBA } from "jimp";
import fs from "node:fs/promises";
import path from "node:path";

const OUTPUT_DIR = "D:\\Edge Downloads\\test\\scanline_proto";
const TARGET_SIZE = 16;

const SAMPLES = [
  {
    id: "soul_bucket",
    input: "D:\\Edge Downloads\\ef25f79e-c32c-4b3a-a1a5-96243c0f63fb.png",
    padding: 0.08,
    mask: "D:\\Geography\\Dev\\CreateFluid\\src\\main\\resources\\assets\\fluid\\textures\\item\\honey_bucket.png",
    maskMode: "overlay"
  },
  {
    id: "chocolate_bar",
    input: "D:\\Edge Downloads\\QQ20260428-225430.png",
    padding: 0.08
  },
  {
    id: "ultimate_tomahawk",
    input: "D:\\Edge Downloads\\14cfc1b5-fd36-4017-894b-6dcc94f6045f.png",
    padding: 0.12,
    mask: "D:\\Edge Downloads\\spinning_tomahawk.png",
    maskMode: "fit"
  },
  {
    id: "long_item_a",
    input: "D:\\Edge Downloads\\QQ20260429-034647.png",
    padding: 0.12
  },
  {
    id: "long_item_b",
    input: "D:\\Edge Downloads\\QQ20260428-223655.png",
    padding: 0.12
  },
  {
    id: "long_item_c",
    input: "D:\\Edge Downloads\\QQ20260428-223641.png",
    padding: 0.12
  }
];

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value));
}

function getPixel(img, x, y) {
  return intToRGBA(img.getPixelColor(x, y));
}

function setPixel(img, x, y, rgba) {
  img.setPixelColor(rgbaToInt(rgba.r, rgba.g, rgba.b, rgba.a), x, y);
}

function colorDistance(a, b) {
  return Math.sqrt((a.r - b.r) ** 2 + (a.g - b.g) ** 2 + (a.b - b.b) ** 2);
}

function saturation(r, g, b) {
  return Math.max(r, g, b) - Math.min(r, g, b);
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

function mergeColorCluster(clusters, pixel, count = 1, threshold = 18) {
  let best = null;
  let bestDist = Infinity;
  for (const cluster of clusters) {
    const dist = colorDistance(pixel, cluster.color);
    if (dist < bestDist) {
      bestDist = dist;
      best = cluster;
    }
  }

  if (!best || bestDist > threshold) {
    clusters.push({
      color: { r: pixel.r, g: pixel.g, b: pixel.b },
      count,
      spread: 0
    });
    return;
  }

  const total = best.count + count;
  best.color = {
    r: Math.round((best.color.r * best.count + pixel.r * count) / total),
    g: Math.round((best.color.g * best.count + pixel.g * count) / total),
    b: Math.round((best.color.b * best.count + pixel.b * count) / total)
  };
  best.spread = Math.max(best.spread, bestDist);
  best.count = total;
}

function sampleEdgeBackground(img, rowSamples = 20, colSamples = 20, linePoints = 50) {
  const clusters = [];
  const rowStep = (img.height - 1) / Math.max(1, rowSamples - 1);
  const colStep = (img.width - 1) / Math.max(1, colSamples - 1);
  const topLimit = Math.max(1, Math.floor(img.height * 0.25));
  const bottomLimit = img.height - 1 - topLimit;
  const leftLimit = Math.max(1, Math.floor(img.width * 0.2));
  const rightLimit = img.width - 1 - leftLimit;

  const sampleRow = (y) => {
    const points = [];
    for (let i = 0; i < linePoints; i++) {
      const x = Math.round((i * (img.width - 1)) / Math.max(1, linePoints - 1));
      const p = getPixel(img, x, y);
      if (p.a <= 10) continue;
      points.push({ x, ...p });
    }
    return points;
  };

  const sampleCol = (x) => {
    const points = [];
    for (let i = 0; i < linePoints; i++) {
      const y = Math.round((i * (img.height - 1)) / Math.max(1, linePoints - 1));
      const p = getPixel(img, x, y);
      if (p.a <= 10) continue;
      points.push({ y, ...p });
    }
    return points;
  };

  const collectStableRun = (points, axisKey, fromStart) => {
    if (!points.length) return [];
    const ordered = fromStart ? points : [...points].reverse();
    const seed = ordered[0];
    const run = [seed];
    for (let i = 1; i < ordered.length; i++) {
      const point = ordered[i];
      const closeToSeed = colorDistance(point, seed) <= 18;
      const closeToPrev = colorDistance(point, ordered[i - 1]) <= 14;
      const neutralish = saturation(point.r, point.g, point.b) <= 28;
      if ((closeToSeed && closeToPrev) || (neutralish && closeToSeed)) {
        run.push(point);
        continue;
      }
      break;
    }
    return run;
  };

  for (let i = 0; i < rowSamples; i++) {
    const y = Math.round(i * rowStep);
    const row = sampleRow(y);
    if (!row.length) continue;
    const left = row[0];
    const right = row[row.length - 1];
    const edgeClose = colorDistance(left, right) <= 20;
    const edgeBand = y <= topLimit || y >= bottomLimit;
    if (edgeClose || edgeBand) {
      const leftRun = collectStableRun(row, "x", true);
      const rightRun = collectStableRun(row, "x", false);
      for (const point of leftRun) mergeColorCluster(clusters, point, edgeBand ? 2 : 1);
      for (const point of rightRun) mergeColorCluster(clusters, point, edgeBand ? 2 : 1);
      const rowUniform = row.every((point) => colorDistance(point, left) <= 18 || saturation(point.r, point.g, point.b) <= 14);
      if (edgeClose && rowUniform) {
        for (const point of row) mergeColorCluster(clusters, point, 2);
      }
    }
  }

  for (let i = 0; i < colSamples; i++) {
    const x = Math.round(i * colStep);
    const col = sampleCol(x);
    if (!col.length) continue;
    const top = col[0];
    const bottom = col[col.length - 1];
    const edgeClose = colorDistance(top, bottom) <= 20;
    const edgeBand = x <= leftLimit || x >= rightLimit;
    if (edgeClose || edgeBand) {
      const topRun = collectStableRun(col, "y", true);
      const bottomRun = collectStableRun(col, "y", false);
      for (const point of topRun) mergeColorCluster(clusters, point, edgeBand ? 2 : 1);
      for (const point of bottomRun) mergeColorCluster(clusters, point, edgeBand ? 2 : 1);
    }
  }

  return clusters
    .filter((cluster) => cluster.count >= 4)
    .sort((a, b) => b.count - a.count)
    .slice(0, 8);
}

function isBackgroundPixel(pixel, models, alphaDriven) {
  if (pixel.a <= 10) return true;
  if (alphaDriven) return pixel.a <= 10;

  let nearest = Infinity;
  let spread = 0;
  for (const model of models) {
    const dist = colorDistance(pixel, model.color);
    if (dist < nearest) {
      nearest = dist;
      spread = model.spread || 0;
    }
  }

  const sat = saturation(pixel.r, pixel.g, pixel.b);
  const threshold = sat <= 18 ? 26 + spread * 0.35 : 18 + spread * 0.25;
  return nearest <= threshold;
}

function buildScanBounds(img, models, alphaDriven) {
  const rowBounds = Array(img.height).fill(null);
  const colBounds = Array(img.width).fill(null);

  for (let y = 0; y < img.height; y++) {
    let left = -1;
    let right = -1;
    for (let x = 0; x < img.width; x++) {
      const p = getPixel(img, x, y);
      if (!isBackgroundPixel(p, models, alphaDriven)) {
        left = x;
        break;
      }
    }
    for (let x = img.width - 1; x >= 0; x--) {
      const p = getPixel(img, x, y);
      if (!isBackgroundPixel(p, models, alphaDriven)) {
        right = x;
        break;
      }
    }
    if (left >= 0 && right >= left) rowBounds[y] = { left, right };
  }

  for (let x = 0; x < img.width; x++) {
    let top = -1;
    let bottom = -1;
    for (let y = 0; y < img.height; y++) {
      const p = getPixel(img, x, y);
      if (!isBackgroundPixel(p, models, alphaDriven)) {
        top = y;
        break;
      }
    }
    for (let y = img.height - 1; y >= 0; y--) {
      const p = getPixel(img, x, y);
      if (!isBackgroundPixel(p, models, alphaDriven)) {
        bottom = y;
        break;
      }
    }
    if (top >= 0 && bottom >= top) colBounds[x] = { top, bottom };
  }

  return { rowBounds, colBounds };
}

function buildForegroundMaskFromScans(img, models, alphaDriven) {
  const { rowBounds, colBounds } = buildScanBounds(img, models, alphaDriven);
  const mask = new Uint8Array(img.width * img.height);
  const centerX = (img.width - 1) / 2;
  const centerY = (img.height - 1) / 2;
  const radialMax = Math.hypot(centerX, centerY) || 1;

  for (let y = 0; y < img.height; y++) {
    const row = rowBounds[y];
    if (!row) continue;
    for (let x = row.left; x <= row.right; x++) {
      const col = colBounds[x];
      if (!col || y < col.top || y > col.bottom) continue;
      const p = getPixel(img, x, y);
      if (p.a <= 10) continue;
      const bgLike = isBackgroundPixel(p, models, false);
      const centerBias = 1 - Math.hypot(x - centerX, y - centerY) / radialMax;
      if (alphaDriven || !bgLike || centerBias > 0.32 || saturation(p.r, p.g, p.b) > 22) {
        mask[y * img.width + x] = 1;
      }
    }
  }

  return { mask, rowBounds, colBounds };
}

function buildEdgeConnectedBackgroundMask(img, models, alphaDriven) {
  const width = img.width;
  const height = img.height;
  const visited = new Uint8Array(width * height);
  const queue = [];
  const push = (x, y) => {
    const idx = y * width + x;
    if (visited[idx]) return;
    const p = getPixel(img, x, y);
    if (!isBackgroundPixel(p, models, alphaDriven)) return;
    visited[idx] = 1;
    queue.push(idx);
  };

  for (let x = 0; x < width; x++) {
    push(x, 0);
    push(x, height - 1);
  }
  for (let y = 1; y < height - 1; y++) {
    push(0, y);
    push(width - 1, y);
  }

  while (queue.length) {
    const idx = queue.pop();
    const x = idx % width;
    const y = Math.floor(idx / width);
    if (x > 0) push(x - 1, y);
    if (x < width - 1) push(x + 1, y);
    if (y > 0) push(x, y - 1);
    if (y < height - 1) push(x, y + 1);
  }

  return visited;
}

function combineForegroundMasks(scanMask, bgMask, img) {
  const out = new Uint8Array(scanMask.length);
  for (let y = 0; y < img.height; y++) {
    for (let x = 0; x < img.width; x++) {
      const idx = y * img.width + x;
      const p = getPixel(img, x, y);
      if (p.a <= 10) continue;
      if (scanMask[idx] || !bgMask[idx]) out[idx] = 1;
    }
  }
  return out;
}

function protectSubjectEdges(mask, img, models, alphaDriven) {
  const width = img.width;
  const height = img.height;
  const out = new Uint8Array(mask);

  for (let y = 1; y < height - 1; y++) {
    for (let x = 1; x < width - 1; x++) {
      const idx = y * width + x;
      if (out[idx]) continue;
      const p = getPixel(img, x, y);
      if (p.a <= 10) continue;

      let solidNeighbors = 0;
      let vividNeighbors = 0;
      let strongContrast = 0;
      for (const [dx, dy] of [[1,0],[-1,0],[0,1],[0,-1],[1,1],[-1,1],[1,-1],[-1,-1]]) {
        const nIdx = (y + dy) * width + (x + dx);
        if (!mask[nIdx]) continue;
        solidNeighbors++;
        const np = getPixel(img, x + dx, y + dy);
        if (saturation(np.r, np.g, np.b) > 22) vividNeighbors++;
        if (colorDistance(p, np) > 18) strongContrast++;
      }

      const nearBackground = isBackgroundPixel(p, models, alphaDriven);
      const sat = saturation(p.r, p.g, p.b);
      const keepAsEdge =
        solidNeighbors >= 3 &&
        (
          vividNeighbors >= 2 ||
          strongContrast >= 2 ||
          sat >= 18 ||
          !nearBackground
        );

      if (keepAsEdge) out[idx] = 1;
    }
  }

  return out;
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
    const score = pixels.length * (0.85 + Math.max(0, centerScore)) * (0.8 + fillScore * 0.5);

    if (!best || score > best.score) {
      best = { pixels, minX, minY, maxX, maxY, score };
    }
  }

  return best;
}

function dilateMask(mask, img, models, iterations = 1) {
  const width = img.width;
  const height = img.height;
  let current = new Uint8Array(mask);

  for (let step = 0; step < iterations; step++) {
    const next = new Uint8Array(current);
    for (let y = 1; y < height - 1; y++) {
      for (let x = 1; x < width - 1; x++) {
        const idx = y * width + x;
        if (current[idx]) continue;
        let touches = 0;
        for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
          if (current[(y + dy) * width + (x + dx)]) touches++;
        }
        if (!touches) continue;
        const p = getPixel(img, x, y);
        if (p.a > 12 && (!isBackgroundPixel(p, models, false) || saturation(p.r, p.g, p.b) > 18)) {
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

function cropRect(img, bounds, paddingRatio = 0.08) {
  const w = bounds.maxX - bounds.minX + 1;
  const h = bounds.maxY - bounds.minY + 1;
  const padX = Math.round(w * paddingRatio);
  const padY = Math.round(h * paddingRatio);
  const x = clamp(bounds.minX - padX, 0, img.width - 1);
  const y = clamp(bounds.minY - padY, 0, img.height - 1);
  const right = clamp(bounds.maxX + padX, 0, img.width - 1);
  const bottom = clamp(bounds.maxY + padY, 0, img.height - 1);
  return img.clone().crop({ x, y, w: right - x + 1, h: bottom - y + 1 });
}

function classifyShape(mask, width, height, bounds) {
  const w = bounds.maxX - bounds.minX + 1;
  const h = bounds.maxY - bounds.minY + 1;
  const ratio = Math.max(w, h) / Math.max(1, Math.min(w, h));
  let activeRows = 0;
  let activeCols = 0;

  for (let y = bounds.minY; y <= bounds.maxY; y++) {
    let rowCount = 0;
    for (let x = bounds.minX; x <= bounds.maxX; x++) {
      if (mask[y * width + x]) rowCount++;
    }
    if (rowCount > Math.max(1, Math.round(w * 0.12))) activeRows++;
  }

  for (let x = bounds.minX; x <= bounds.maxX; x++) {
    let colCount = 0;
    for (let y = bounds.minY; y <= bounds.maxY; y++) {
      if (mask[y * width + x]) colCount++;
    }
    if (colCount > Math.max(1, Math.round(h * 0.12))) activeCols++;
  }

  const effectiveRatio = Math.max(activeCols, activeRows) / Math.max(1, Math.min(activeCols, activeRows));
  const finalRatio = Math.max(ratio, effectiveRatio);
  if (finalRatio >= 2.15) return { kind: "slender", majorPixels: 15 };
  if (finalRatio >= 1.45) return { kind: "flat", majorPixels: 14 };
  return { kind: "balanced", majorPixels: 13 };
}

function dominantResizeToBox(img, outW, outH) {
  const out = new Jimp({ width: outW, height: outH, color: 0x00000000 });
  for (let ty = 0; ty < outH; ty++) {
    const y0 = Math.floor((ty * img.height) / outH);
    const y1 = Math.max(y0 + 1, Math.ceil(((ty + 1) * img.height) / outH));
    for (let tx = 0; tx < outW; tx++) {
      const x0 = Math.floor((tx * img.width) / outW);
      const x1 = Math.max(x0 + 1, Math.ceil(((tx + 1) * img.width) / outW));
      const buckets = new Map();
      let foreground = 0;
      for (let y = y0; y < y1; y++) {
        for (let x = x0; x < x1; x++) {
          const p = getPixel(img, x, y);
          if (p.a <= 10) continue;
          foreground++;
          const key = `${Math.round(p.r / 24)},${Math.round(p.g / 24)},${Math.round(p.b / 24)}`;
          const bucket = buckets.get(key) || { w: 0, a: 0, r: 0, g: 0, b: 0, strong: 0 };
          const weight = Math.max(0.25, p.a / 255);
          bucket.w += weight;
          bucket.a += p.a * weight;
          bucket.r += p.r * weight;
          bucket.g += p.g * weight;
          bucket.b += p.b * weight;
          if (p.a >= 220) bucket.strong++;
          buckets.set(key, bucket);
        }
      }
      if (!foreground || !buckets.size) continue;
      let best = null;
      for (const bucket of buckets.values()) {
        if (!best || bucket.strong > best.strong || (bucket.strong === best.strong && bucket.w > best.w)) best = bucket;
      }
      setPixel(out, tx, ty, {
        r: Math.round(best.r / best.w),
        g: Math.round(best.g / best.w),
        b: Math.round(best.b / best.w),
        a: Math.round(best.a / best.w)
      });
    }
  }
  return out;
}

function dominantFitResize(img, targetSize, majorPixels) {
  const out = new Jimp({ width: targetSize, height: targetSize, color: 0x00000000 });
  const maxSide = Math.max(img.width, img.height);
  const scale = majorPixels / Math.max(1, maxSide);
  const outW = clamp(Math.round(img.width * scale), 1, targetSize);
  const outH = clamp(Math.round(img.height * scale), 1, targetSize);
  const resized = dominantResizeToBox(img, outW, outH);
  const offsetX = Math.floor((targetSize - outW) / 2);
  const offsetY = Math.floor((targetSize - outH) / 2);
  out.composite(resized, offsetX, offsetY);
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

async function applyShapeMask(img, maskPath) {
  const mask = await Jimp.read(maskPath);
  mask.resize({ w: img.width, h: img.height, mode: "nearestNeighbor" });
  const out = img.clone();
  for (let y = 0; y < img.height; y++) {
    for (let x = 0; x < img.width; x++) {
      const m = getPixel(mask, x, y);
      if (m.a <= 10) setPixel(out, x, y, { r: 0, g: 0, b: 0, a: 0 });
    }
  }
  return out;
}

async function fitToMask(crop, maskPath, targetSize = 16) {
  const maskImg = await Jimp.read(maskPath);
  maskImg.resize({ w: targetSize, h: targetSize, mode: "nearestNeighbor" });
  const maskBits = getAlphaMask(maskImg);
  const maskBounds = boundsFromMask(maskBits, targetSize, targetSize);
  const maskWidth = maskBounds.maxX - maskBounds.minX + 1;
  const maskHeight = maskBounds.maxY - maskBounds.minY + 1;

  let best = null;
  for (const widthScale of [0.8, 0.9, 1.0, 1.1, 1.2]) {
    for (const heightScale of [0.8, 0.9, 1.0, 1.1, 1.2]) {
      const baseW = Math.max(1, Math.round(maskWidth * widthScale));
      const baseH = Math.max(1, Math.round(maskHeight * heightScale));
      const resized = dominantResizeToBox(crop, baseW, baseH);
      const placed = new Jimp({ width: targetSize, height: targetSize, color: 0x00000000 });
      const px = Math.round((targetSize - resized.width) / 2);
      const py = Math.round((targetSize - resized.height) / 2);
      placed.composite(resized, px, py);
      const masked = await applyShapeMask(placed, maskPath);
      const alphaBits = getAlphaMask(masked);

      let overlap = 0;
      let outside = 0;
      let insideMiss = 0;
      for (let i = 0; i < alphaBits.length; i++) {
        const a = alphaBits[i];
        const m = maskBits[i];
        if (a && m) overlap++;
        else if (a && !m) outside++;
        else if (!a && m) insideMiss++;
      }

      const distortionPenalty = Math.abs(widthScale - 1) + Math.abs(heightScale - 1);
      const score = overlap * 5 - outside * 6 - insideMiss * 2 - distortionPenalty * 2;
      if (!best || score > best.score) best = { img: masked, score };
    }
  }
  return best ? best.img : await applyShapeMask(dominantFitResize(crop, targetSize, 14), maskPath);
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
      if (p.a <= 10) setPixel(out, x, y, m);
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
      if (p.a <= 10 || saturation(p.r, p.g, p.b) > 18) continue;
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
      if (opaqueNeighbors <= 3 && vividNeighbors <= 2) toClear.push([x, y]);
    }
  }
  for (const [x, y] of toClear) setPixel(out, x, y, { r: 0, g: 0, b: 0, a: 0 });
  return out;
}

function upscaleNearest(img, factor) {
  return img.clone().resize({ w: img.width * factor, h: img.height * factor, mode: "nearestNeighbor" });
}

async function processSample(sample) {
  const img = await Jimp.read(sample.input);
  const alphaDriven = hasMeaningfulAlpha(img);
  const bgModels = alphaDriven ? [] : sampleEdgeBackground(img);
  const { mask: scanMask } = buildForegroundMaskFromScans(img, bgModels, alphaDriven);
  const bgMask = buildEdgeConnectedBackgroundMask(img, bgModels, alphaDriven);
  const mergedMask = combineForegroundMasks(scanMask, bgMask, img);
  const component = largestCentralComponent(mergedMask, img.width, img.height);
  if (!component) throw new Error(`no component for ${sample.id}`);

  const componentMask = new Uint8Array(img.width * img.height);
  for (const idx of component.pixels) componentMask[idx] = 1;
  const edgeProtectedMask = alphaDriven ? componentMask : protectSubjectEdges(componentMask, img, bgModels, alphaDriven);
  const expandedMask = alphaDriven ? edgeProtectedMask : dilateMask(edgeProtectedMask, img, bgModels, 1);
  const masked = applyMaskToImage(img, expandedMask);
  const bounds = boundsFromMask(expandedMask, img.width, img.height);
  const crop = cropRect(masked, bounds, sample.padding);
  const shape = classifyShape(expandedMask, img.width, img.height, bounds);
  let out16 = dominantFitResize(crop, TARGET_SIZE, shape.majorPixels);

  if (sample.mask && sample.maskMode === "overlay") {
    out16 = await applyShapeMask(out16, sample.mask);
  } else if (sample.mask && sample.maskMode === "fit") {
    out16 = await fitToMask(crop, sample.mask, TARGET_SIZE);
  }

  if (sample.id === "soul_bucket" && sample.mask) {
    out16 = await repairBucketOutline(out16, sample.mask);
  }
  if (sample.id === "chocolate_bar") {
    out16 = trimResidualGrayEdgePixels(out16);
  }

  const sampleDir = path.join(OUTPUT_DIR, sample.id);
  await fs.mkdir(sampleDir, { recursive: true });
  await masked.write(path.join(sampleDir, "masked.png"));
  await crop.write(path.join(sampleDir, "crop.png"));
  await out16.write(path.join(sampleDir, "out16.png"));
  await upscaleNearest(out16, 16).write(path.join(sampleDir, "out16_preview.png"));

  return {
    id: sample.id,
    alphaDriven,
    bgModels: bgModels.map((model) => ({ color: model.color, count: model.count, spread: model.spread })),
    bounds,
    shape
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

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
