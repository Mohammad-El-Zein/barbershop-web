import { notFound } from "next/navigation";

/** Any unknown path below /de or /en → the localized 404 page (app/[locale]/not-found.tsx). */
export default function CatchAll() {
  notFound();
}
