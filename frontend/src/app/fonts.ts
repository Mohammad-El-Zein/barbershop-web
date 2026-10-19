import { Anton, Inter } from "next/font/google";

/**
 * The site's fonts, shared by both root layouts ([locale]/layout.tsx and global-not-found.tsx):
 * declared once, so both pages use the same CSS chunk (declared twice, the build split a
 * second render-blocking stylesheet off every page).
 * Condensed display face for headlines, Inter for body copy (latin covers ä ö ü ß).
 */
export const anton = Anton({ variable: "--font-anton", weight: "400", subsets: ["latin"], display: "swap" });
export const inter = Inter({ variable: "--font-inter", subsets: ["latin"], display: "swap" });
