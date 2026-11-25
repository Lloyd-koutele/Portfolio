/**
 * Native listener that keeps a pointer press on a link from reaching the flipbook, so clicking a link or a button
 * opens it instead of turning the page. React's synthetic stopPropagation runs too late for native listeners.
 */
export function stopFlipPropagation(node: HTMLElement | null): void {
  if (!node || node.dataset.stopFlip === 'true') return;
  node.dataset.stopFlip = 'true';
  const stop = (event: Event) => event.stopPropagation();
  node.addEventListener('mousedown', stop);
  node.addEventListener('touchstart', stop, { passive: true });
  node.addEventListener('pointerdown', stop);
}
