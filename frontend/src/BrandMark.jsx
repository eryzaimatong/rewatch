import markUrl from "./assets/brand/rewatch-mark.svg";

// Intrinsic size of the brand file (185.629 × 166.541). Both dimensions are
// always set together from this ratio so the mark can never be stretched.
const RATIO = 185.629 / 166.541;

/**
 * The Re:Watch mark — a swirl that reads as a play button and ends in Ree's
 * spiral tail. `size` is the HEIGHT in px; width follows the brand ratio.
 */
export default function BrandMark({ size = 28, className = "", ...rest }) {
  return (
    <img
      src={markUrl}
      alt=""
      aria-hidden="true"
      width={Math.round(size * RATIO)}
      height={size}
      className={`brand-mark-img ${className}`}
      draggable={false}
      {...rest}
    />
  );
}

/** Mark + "Re:Watch" wordmark (Inter 700, as on the board's logo lockup). */
export function Logo({ size = 28, className = "" }) {
  return (
    <span className={`brand-logo ${className}`} style={{ "--logo-size": `${size}px` }}>
      <BrandMark size={size} />
      <span className="brand-logo-word">Re:Watch</span>
    </span>
  );
}
