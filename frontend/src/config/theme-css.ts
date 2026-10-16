import { siteConfig, type Palette } from "./site.config";

/** CSS custom properties for one palette. */
function vars(p: Palette) {
  return [
    `--bg:${p.bg}`,
    `--surface:${p.surface}`,
    `--text:${p.text}`,
    `--muted:${p.muted}`,
    `--gold:${p.gold}`,
    `--gold-deep:${p.goldDeep}`,
    `--gold-text:${p.goldText}`,
    `--line:${p.line}`,
  ].join(";");
}

/**
 * Theme stylesheet generated from site.config.ts, injected in the root layout.
 * - :root = dark (also the no-JS default)
 * - html.light = light theme (class set by next-themes before first paint)
 * - .scene-dark = always dark, for the cinematic scene and anything over it
 *   (e.g. the nav while it floats above the scene), regardless of theme
 */
export function themeCss() {
  const { dark, light } = siteConfig.theme;
  return [
    `:root{${vars(dark)};color-scheme:dark}`,
    `html.light{${vars(light)};color-scheme:light}`,
    `.scene-dark{${vars(dark)};color-scheme:dark}`,
  ].join("\n");
}
