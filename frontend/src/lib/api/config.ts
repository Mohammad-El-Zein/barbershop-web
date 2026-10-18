/**
 * Where the backend lives and which shop (tenant) this site belongs to.
 * NEXT_PUBLIC_* values are inlined at build time (see .env.example).
 */
export const API_URL = (process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080").replace(/\/$/, "");

/** The tenant slug of this website (the demo shop by default). */
export const TENANT = process.env.NEXT_PUBLIC_TENANT ?? "the-barber-news";
