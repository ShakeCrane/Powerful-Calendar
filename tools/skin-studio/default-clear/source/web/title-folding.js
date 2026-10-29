(function exposeTitleFolding(root, factory) {
  const api = factory();

  if (typeof module === "object" && module !== null && module.exports) {
    module.exports = api;
  }
  if (root) {
    root.PowerCalTitleFolding = api;
  }
})(typeof window !== "undefined" ? window : globalThis, function createTitleFolding() {
  "use strict";

  function compareText(first, second) {
    if (first < second) return -1;
    if (first > second) return 1;
    return 0;
  }

  function validateCandidates(candidates) {
    if (!Array.isArray(candidates)) {
      throw new TypeError("Title folding candidates must be an array");
    }

    const seenIds = new Set();
    candidates.forEach((candidate, index) => {
      if (!candidate || typeof candidate !== "object" || Array.isArray(candidate)) {
        throw new TypeError(`Title candidate at index ${index} must be an object`);
      }
      if (typeof candidate.stableId !== "string" || candidate.stableId.length === 0) {
        throw new TypeError(`Title candidate at index ${index} must have a non-empty stableId`);
      }
      if (seenIds.has(candidate.stableId)) {
        throw new Error(`Title candidate IDs must be unique: ${candidate.stableId}`);
      }
      seenIds.add(candidate.stableId);

      if (typeof candidate.regionId !== "string" || candidate.regionId.length === 0) {
        throw new TypeError(`Title candidate ${candidate.stableId} must have a non-empty regionId`);
      }
      if (typeof candidate.eventSeq !== "number" || !Number.isFinite(candidate.eventSeq)) {
        throw new TypeError(`Title candidate ${candidate.stableId} must have a finite eventSeq`);
      }

      const rect = candidate.rect;
      if (!rect || typeof rect !== "object" || Array.isArray(rect)) {
        throw new TypeError(`Title candidate ${candidate.stableId} must have a rectangle`);
      }
      for (const edge of ["left", "top", "right", "bottom"]) {
        if (typeof rect[edge] !== "number" || !Number.isFinite(rect[edge])) {
          throw new TypeError(`Title candidate ${candidate.stableId} rect.${edge} must be finite`);
        }
      }
      if (!(rect.right > rect.left) || !(rect.bottom > rect.top)) {
        throw new RangeError(`Title candidate ${candidate.stableId} rectangle must have positive area`);
      }
    });
  }

  function compareCandidates(first, second) {
    return compareText(first.regionId, second.regionId) ||
      first.rect.top - second.rect.top ||
      first.eventSeq - second.eventSeq ||
      compareText(first.stableId, second.stableId);
  }

  function overlaps(first, second) {
    return first.left < second.right && second.left < first.right &&
      first.top < second.bottom && second.top < first.bottom;
  }

  /**
   * Folds only titles whose measured screen rectangles collide within the same region.
   * Hidden candidates never replace the current visible lead.
   *
   * @param {Array<{stableId: string, eventSeq: number, regionId: string,
   *   rect: {left: number, top: number, right: number, bottom: number}}>} candidates
   * @returns {{decisions: Array<{candidate: object, visible: boolean, comparedTo: string|null}>,
   *   visibleIds: string[], hiddenIds: string[]}}
   */
  function fold(candidates) {
    validateCandidates(candidates);

    const ordered = candidates.slice().sort(compareCandidates);
    const decisions = [];
    let activeRegion = null;
    let lead = null;

    for (const candidate of ordered) {
      if (candidate.regionId !== activeRegion) {
        activeRegion = candidate.regionId;
        lead = null;
      }

      const visible = lead === null || !overlaps(lead.rect, candidate.rect);
      decisions.push({
        candidate: {
          stableId: candidate.stableId,
          eventSeq: candidate.eventSeq,
          regionId: candidate.regionId,
          rect: {
            left: candidate.rect.left,
            top: candidate.rect.top,
            right: candidate.rect.right,
            bottom: candidate.rect.bottom
          }
        },
        visible,
        comparedTo: lead === null ? null : lead.stableId
      });

      if (visible) lead = candidate;
    }

    return {
      decisions,
      visibleIds: decisions.filter(decision => decision.visible)
        .map(decision => decision.candidate.stableId),
      hiddenIds: decisions.filter(decision => !decision.visible)
        .map(decision => decision.candidate.stableId)
    };
  }

  return Object.freeze({ fold });
});
