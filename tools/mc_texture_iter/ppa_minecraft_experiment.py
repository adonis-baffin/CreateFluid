from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from PIL import Image

from proper_pixel_art.pixelate import pixelate

OUTPUT_DIR = Path(r"D:\Edge Downloads\test\ppa_mc_experiment")
TARGET_SIZE = 16


@dataclass
class Sample:
    id: str
    input_path: Path
    num_colors: int | None = 16
    transparent_background: bool = True
    initial_upscale: int = 2
    pixel_width: int | None = None
    mask_path: Path | None = None
    mask_mode: str | None = None
    major_pixels: int = 14


SAMPLES = [
    Sample(
        id="soul_bucket",
        input_path=Path(r"D:\Edge Downloads\ef25f79e-c32c-4b3a-a1a5-96243c0f63fb.png"),
        mask_path=Path(r"D:\Geography\Dev\CreateFluid\src\main\resources\assets\fluid\textures\item\honey_bucket.png"),
        mask_mode="overlay",
        major_pixels=14,
    ),
    Sample(
        id="chocolate_bar",
        input_path=Path(r"D:\Edge Downloads\QQ20260428-225430.png"),
        major_pixels=14,
    ),
    Sample(
        id="ultimate_tomahawk",
        input_path=Path(r"D:\Edge Downloads\14cfc1b5-fd36-4017-894b-6dcc94f6045f.png"),
        mask_path=Path(r"D:\Edge Downloads\spinning_tomahawk.png"),
        mask_mode="fit",
        major_pixels=15,
    ),
    Sample(
        id="long_item_a",
        input_path=Path(r"D:\Edge Downloads\QQ20260429-034647.png"),
        mask_path=Path(r"D:\Geography\Dev\CreateFluid\src\main\resources\assets\fluid\textures\item\honey_bucket.png"),
        mask_mode="overlay",
        major_pixels=14,
    ),
    Sample(
        id="long_item_b",
        input_path=Path(r"D:\Edge Downloads\QQ20260428-223655.png"),
        mask_path=Path(r"D:\Geography\Dev\CreateFluid\src\main\resources\assets\fluid\textures\item\honey_bucket.png"),
        mask_mode="overlay",
        major_pixels=14,
    ),
    Sample(
        id="long_item_c",
        input_path=Path(r"D:\Edge Downloads\QQ20260428-223641.png"),
        mask_path=Path(r"D:\Geography\Dev\CreateFluid\src\main\resources\assets\fluid\textures\item\honey_bucket.png"),
        mask_mode="overlay",
        major_pixels=14,
    ),
]


def alpha_bbox(image: Image.Image) -> tuple[int, int, int, int] | None:
    alpha = image.getchannel("A")
    bbox = alpha.getbbox()
    if bbox is None:
        return None
    return bbox


def trim_to_alpha(image: Image.Image) -> Image.Image:
    bbox = alpha_bbox(image)
    if bbox is None:
        return image.copy()
    return image.crop(bbox)


def resize_nearest(image: Image.Image, size: tuple[int, int]) -> Image.Image:
    return image.resize(size, resample=Image.Resampling.NEAREST)


def fit_to_square(image: Image.Image, target_size: int, major_pixels: int) -> Image.Image:
    content = trim_to_alpha(image)
    width, height = content.size
    if width <= 0 or height <= 0:
        return Image.new("RGBA", (target_size, target_size), (0, 0, 0, 0))

    major_side = max(width, height)
    scale = major_pixels / max(1, major_side)
    out_w = max(1, min(target_size, round(width * scale)))
    out_h = max(1, min(target_size, round(height * scale)))
    resized = resize_nearest(content, (out_w, out_h))
    canvas = Image.new("RGBA", (target_size, target_size), (0, 0, 0, 0))
    offset_x = (target_size - out_w) // 2
    offset_y = (target_size - out_h) // 2
    canvas.alpha_composite(resized, (offset_x, offset_y))
    return canvas


def mask_bbox(mask: Image.Image) -> tuple[int, int, int, int]:
    bbox = mask.getchannel("A").getbbox()
    if bbox is None:
        raise ValueError("Mask has no alpha content")
    return bbox


def apply_mask_alpha(image: Image.Image, mask: Image.Image) -> Image.Image:
    out = image.copy().convert("RGBA")
    img_px = out.load()
    mask_px = mask.convert("RGBA").load()
    for y in range(out.height):
      for x in range(out.width):
        if mask_px[x, y][3] <= 10:
          img_px[x, y] = (0, 0, 0, 0)
    return out


def nearest_fill_outline(image: Image.Image, mask: Image.Image) -> Image.Image:
    out = image.copy().convert("RGBA")
    mask_rgba = mask.convert("RGBA")
    out_px = out.load()
    mask_px = mask_rgba.load()

    for y in range(out.height):
        for x in range(out.width):
            if mask_px[x, y][3] <= 10 or out_px[x, y][3] > 10:
                continue
            for radius in (1, 2, 3):
                found = None
                for dy in range(-radius, radius + 1):
                    for dx in range(-radius, radius + 1):
                        nx = x + dx
                        ny = y + dy
                        if nx < 0 or ny < 0 or nx >= out.width or ny >= out.height:
                            continue
                        if out_px[nx, ny][3] > 10:
                            found = out_px[nx, ny]
                            break
                    if found:
                        break
                if found:
                    out_px[x, y] = found
                    break
    return out


def fit_inside_mask(image: Image.Image, mask: Image.Image, preserve_aspect: bool = True) -> Image.Image:
    bbox = mask_bbox(mask)
    x0, y0, x1, y1 = bbox
    box_w = x1 - x0
    box_h = y1 - y0
    content = trim_to_alpha(image)
    width, height = content.size
    if width <= 0 or height <= 0:
        return Image.new("RGBA", mask.size, (0, 0, 0, 0))

    if preserve_aspect:
        scale = min(box_w / max(1, width), box_h / max(1, height))
        out_w = max(1, round(width * scale))
        out_h = max(1, round(height * scale))
    else:
        out_w = box_w
        out_h = box_h

    resized = resize_nearest(content, (out_w, out_h))
    canvas = Image.new("RGBA", mask.size, (0, 0, 0, 0))
    offset_x = x0 + (box_w - out_w) // 2
    offset_y = y0 + (box_h - out_h) // 2
    canvas.alpha_composite(resized, (offset_x, offset_y))
    return canvas


def fit_to_mask_search(image: Image.Image, mask: Image.Image) -> Image.Image:
    bbox = mask_bbox(mask)
    x0, y0, x1, y1 = bbox
    box_w = x1 - x0
    box_h = y1 - y0
    content = trim_to_alpha(image)
    mask_alpha = mask.getchannel("A")
    best_score = None
    best_image = None

    for width_scale in (0.8, 0.9, 1.0, 1.1, 1.2):
        for height_scale in (0.8, 0.9, 1.0, 1.1, 1.2):
            out_w = max(1, round(box_w * width_scale))
            out_h = max(1, round(box_h * height_scale))
            resized = resize_nearest(content, (out_w, out_h))
            for ox in (-1, 0, 1):
                for oy in (-1, 0, 1):
                    canvas = Image.new("RGBA", mask.size, (0, 0, 0, 0))
                    offset_x = x0 + (box_w - out_w) // 2 + ox
                    offset_y = y0 + (box_h - out_h) // 2 + oy
                    canvas.alpha_composite(resized, (offset_x, offset_y))
                    masked = apply_mask_alpha(canvas, mask)
                    score = score_mask_fit(masked, mask_alpha)
                    if best_score is None or score > best_score:
                        best_score = score
                        best_image = masked
    if best_image is None:
        return apply_mask_alpha(fit_inside_mask(image, mask), mask)
    return best_image


def score_mask_fit(image: Image.Image, mask_alpha: Image.Image) -> int:
    img_alpha = image.getchannel("A")
    img_px = img_alpha.load()
    mask_px = mask_alpha.load()
    overlap = outside = inside_miss = 0
    for y in range(image.height):
        for x in range(image.width):
            painted = img_px[x, y] > 10
            allowed = mask_px[x, y] > 10
            if painted and allowed:
                overlap += 1
            elif painted and not allowed:
                outside += 1
            elif allowed and not painted:
                inside_miss += 1
    return overlap * 5 - outside * 6 - inside_miss * 2


def save_preview(image: Image.Image, out_path: Path, factor: int = 16) -> None:
    preview = resize_nearest(image, (image.width * factor, image.height * factor))
    preview.save(out_path)


def process_sample(sample: Sample) -> None:
    out_dir = OUTPUT_DIR / sample.id
    out_dir.mkdir(parents=True, exist_ok=True)

    source = Image.open(sample.input_path).convert("RGBA")
    ppa = pixelate(
        source,
        num_colors=sample.num_colors,
        initial_upscale_factor=sample.initial_upscale,
        scale_result=1,
        transparent_background=sample.transparent_background,
        pixel_width=sample.pixel_width,
        intermediate_dir=None,
    )
    ppa = ppa.convert("RGBA")
    ppa.save(out_dir / "ppa_raw.png")
    save_preview(ppa, out_dir / "ppa_raw_preview.png")

    base16 = fit_to_square(ppa, TARGET_SIZE, sample.major_pixels)
    base16.save(out_dir / "base16.png")
    save_preview(base16, out_dir / "base16_preview.png")

    final = base16
    if sample.mask_path is not None:
        mask = Image.open(sample.mask_path).convert("RGBA")
        if mask.size != (TARGET_SIZE, TARGET_SIZE):
            mask = resize_nearest(mask, (TARGET_SIZE, TARGET_SIZE))

        if sample.mask_mode == "overlay":
            masked = apply_mask_alpha(fit_inside_mask(ppa, mask), mask)
            final = nearest_fill_outline(masked, mask)
        elif sample.mask_mode == "fit":
            final = fit_to_mask_search(ppa, mask)

        final.save(out_dir / "masked16.png")
        save_preview(final, out_dir / "masked16_preview.png")

    final.save(out_dir / "out16.png")
    save_preview(final, out_dir / "out16_preview.png")


def main() -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    for sample in SAMPLES:
        print(f"processing {sample.id}")
        process_sample(sample)


if __name__ == "__main__":
    main()
