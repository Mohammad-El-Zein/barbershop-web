/**
 * Shared by the client consent store (lib/consent.ts) and the server layout.
 *
 * CONSENT_BOOT_SCRIPT runs in <head> before the first paint: without a valid stored
 * decision it marks <html data-consent="ask">, and CSS shows the (server-rendered,
 * hidden) banner right away — no wait for JavaScript, no layout jump, and the
 * banner doesn't turn into a late "largest contentful paint". It only reads
 * localStorage; nothing is loaded or stored.
 */
export const CONSENT_VERSION = 1;
export const CONSENT_KEY = "consent";
export const CONSENT_MAX_AGE_MS = 365 * 24 * 60 * 60 * 1000;

export const CONSENT_BOOT_SCRIPT = `(function(){try{var c=JSON.parse(localStorage.getItem(${JSON.stringify(CONSENT_KEY)})||"null");if(c&&c.v===${CONSENT_VERSION}&&Date.now()-new Date(c.at).getTime()<${CONSENT_MAX_AGE_MS})return}catch(e){}document.documentElement.setAttribute("data-consent","ask")})()`;
