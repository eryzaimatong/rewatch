import { ImageResponse } from "@vercel/og";
export const config = { runtime: "edge" };
export default function handler() {
  return new ImageResponse({ type: "div", props: { children: "hello" } }, { width: 400, height: 200 });
}
