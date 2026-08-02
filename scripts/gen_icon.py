"""Gera o icone do app (adaptativo + fallback legado) a partir de formas
geometricas simples via Pillow -- sem depender do Android Studio Image
Asset nem de um arquivo de design externo (Task #38).

Paleta: mesmas cores de app/src/main/java/com/bragro/mobile/ui/theme/Theme.kt
(BrGreen/BrYellow, bandeira do Brasil). O glifo (folha/broto) e uma vesica
piscis (intersecao de dois circulos), um recurso classico de desenho
geometrico pra conseguir uma forma de folha sem precisar de um editor
vetorial.

Uso: rode "python3 scripts/gen_icon.py" da raiz de native-app/ (precisa de
`pip install pillow`) -- regrava os PNGs em app/src/main/res/mipmap-*dpi/.
Rode de novo sempre que quiser ajustar o desenho do icone.
"""
from PIL import Image, ImageDraw, ImageChops
import os

GREEN = (47, 111, 79, 255)    # #2F6F4F (BrGreen)
YELLOW = (242, 192, 55, 255)  # #F2C037 (BrYellow)


def leaf_alpha(size: int) -> Image.Image:
    """Vesica piscis (folha com pontas em cima/embaixo), antialiased, como
    mascara de alpha "L" de size x size."""
    ss = 4  # supersampling pra antialiasing suave nas bordas do circulo
    big = size * ss
    r = int(big * 0.42)
    d = int(r * 0.62)  # deslocamento horizontal entre os centros -> lente com pontas verticais
    cx, cy = big // 2, big // 2

    m1 = Image.new("L", (big, big), 0)
    ImageDraw.Draw(m1).ellipse([cx - d - r, cy - r, cx - d + r, cy + r], fill=255)
    m2 = Image.new("L", (big, big), 0)
    ImageDraw.Draw(m2).ellipse([cx + d - r, cy - r, cx + d + r, cy + r], fill=255)
    lens = ImageChops.darker(m1, m2)  # AND "suave" (preserva antialiasing, ao contrario de modo "1")
    return lens.resize((size, size), Image.LANCZOS)


def make_foreground(size: int) -> Image.Image:
    """Camada de primeiro plano do icone adaptativo: fundo transparente,
    broto (caule verde + folha amarela) centralizado dentro da zona segura
    (~66/108 do canvas adaptativo)."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))

    draw = ImageDraw.Draw(img)
    cx = size // 2
    stem_w = max(3, round(size * 0.045))
    stem_top = int(size * 0.50)
    stem_bottom = int(size * 0.70)
    draw.rounded_rectangle(
        [cx - stem_w // 2, stem_top, cx + stem_w // 2, stem_bottom],
        radius=stem_w // 2, fill=GREEN,
    )

    leaf_size = int(size * 0.46)
    alpha = leaf_alpha(leaf_size)
    yellow_layer = Image.new("RGBA", (leaf_size, leaf_size), YELLOW)
    yellow_layer.putalpha(alpha)
    lx = (size - leaf_size) // 2
    ly = int(size * 0.24)
    img.alpha_composite(yellow_layer, (lx, ly))
    return img


def make_legacy_icon(size: int, round_mask: bool = False) -> Image.Image:
    """Icone legado (API<26, minSdk=24): fundo verde + broto compostos num
    unico bitmap, ja que icone adaptativo (2 camadas) so existe a partir do
    Android 8."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    bg = Image.new("RGBA", (size, size), GREEN)
    mask = Image.new("L", (size, size), 0)
    if round_mask:
        ImageDraw.Draw(mask).ellipse([0, 0, size, size], fill=255)
    else:
        radius = size // 6
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=255)
    img.paste(bg, (0, 0), mask)

    fg = make_foreground(size)
    scale = 1.15  # sem safe-zone do sistema aqui, entao o glifo pode ocupar um pouco mais de espaco
    fg_scaled = fg.resize((round(size * scale), round(size * scale)), Image.LANCZOS)
    offset = (-(fg_scaled.width - size) // 2, -(fg_scaled.height - size) // 2)
    img.alpha_composite(fg_scaled, offset)
    return img


def main() -> None:
    out = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")
    densities_fg = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
    densities_legacy = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}

    for dpi, size in densities_fg.items():
        d = os.path.join(out, f"mipmap-{dpi}")
        os.makedirs(d, exist_ok=True)
        make_foreground(size).save(os.path.join(d, "ic_launcher_foreground.png"))

    for dpi, size in densities_legacy.items():
        d = os.path.join(out, f"mipmap-{dpi}")
        os.makedirs(d, exist_ok=True)
        make_legacy_icon(size, round_mask=False).save(os.path.join(d, "ic_launcher.png"))
        make_legacy_icon(size, round_mask=True).save(os.path.join(d, "ic_launcher_round.png"))

    print("Icones gerados em", out)


if __name__ == "__main__":
    main()
