/** DOM nodes the editor needs outside React's tree (coordinate conversion, thumbnail capture). */
export const editorRefs: { frame: HTMLDivElement | null; thumbnail: HTMLDivElement | null } = {
  frame: null,
  thumbnail: null,
};

export function frameRect(): DOMRect | null {
  return editorRefs.frame?.getBoundingClientRect() ?? null;
}
