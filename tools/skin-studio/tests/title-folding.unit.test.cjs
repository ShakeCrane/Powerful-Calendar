"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const test = require("node:test");

const { fold } = require("../default-clear/source/web/title-folding.js");

function candidate(stableId, top, bottom, options = {}) {
  return {
    stableId,
    eventSeq: options.eventSeq ?? 1,
    regionId: options.regionId ?? "right-lane-0",
    rect: {
      left: options.left ?? 20,
      top,
      right: options.right ?? 120,
      bottom
    }
  };
}

function trace(result) {
  return result.decisions.map(({ candidate: item, visible, comparedTo }) =>
    `${item.stableId}:${visible ? "show" : "hide"}:${comparedTo ?? "-"}`);
}

test("A/B/C/D compares each candidate only with the current visible lead", () => {
  const result = fold([
    candidate("A", 0, 10),
    candidate("B", 8, 18),
    candidate("C", 11, 21),
    candidate("D", 19, 29)
  ]);

  assert.deepEqual(result.visibleIds, ["A", "C"]);
  assert.deepEqual(result.hiddenIds, ["B", "D"]);
  assert.deepEqual(trace(result), ["A:show:-", "B:hide:A", "C:show:A", "D:hide:C"]);
});

test("successive conflict runs keep one lead per run", () => {
  const result = fold([
    candidate("A", 0, 12), candidate("B", 4, 16),
    candidate("C", 14, 26), candidate("D", 18, 30),
    candidate("E", 29, 41), candidate("F", 33, 45),
    candidate("G", 50, 62), candidate("H", 54, 66)
  ]);

  assert.deepEqual(result.visibleIds, ["A", "C", "E", "G"]);
  assert.deepEqual(result.hiddenIds, ["B", "D", "F", "H"]);
  assert.deepEqual(result.decisions.filter(item => !item.visible).map(item => item.comparedTo),
    ["A", "C", "E", "G"]);
});

test("rectangles that only touch at vertical or horizontal edges do not collide", () => {
  const result = fold([
    candidate("A", 0, 10, { left: 0, right: 10 }),
    candidate("B", 10, 20, { left: 0, right: 10 }),
    candidate("C", 20, 30, { left: 0, right: 10 }),
    candidate("D", 0, 10, { left: 10, right: 20 })
  ]);

  assert.deepEqual(result.visibleIds, ["A", "D", "B", "C"]);
  assert.deepEqual(result.hiddenIds, []);
});

test("each region or lane has an independent lead", () => {
  const result = fold([
    candidate("right", 0, 20, { regionId: "right-lane-0" }),
    candidate("left", 0, 20, { regionId: "left-lane-0" }),
    candidate("right-next-lane", 1, 21, { regionId: "right-lane-1" })
  ]);

  assert.deepEqual(result.visibleIds, ["left", "right", "right-next-lane"]);
  assert.deepEqual(result.hiddenIds, []);
});

test("screen rectangles alone decide collisions; candidates have no event times", () => {
  const earlier = candidate("earlier", 100, 116);
  const later = candidate("later", 108, 124);

  assert.equal("startMillis" in earlier, false);
  assert.equal("endMillis" in earlier, false);
  assert.equal("startMillis" in later, false);
  assert.equal("endMillis" in later, false);
  const result = fold([earlier, later]);

  assert.deepEqual(result.visibleIds, ["earlier"]);
  assert.deepEqual(result.hiddenIds, ["later"]);
  assert.equal(result.decisions[1].comparedTo, "earlier");
});

test("sort order is region, screen top, event sequence, then stable ID", () => {
  const values = [
    candidate("z-same-seq", 0, 8, { eventSeq: 4, regionId: "b" }),
    candidate("later-top", 5, 9, { eventSeq: 0, regionId: "a" }),
    candidate("same-seq-b", 0, 8, { eventSeq: 2, regionId: "a" }),
    candidate("same-seq-a", 0, 8, { eventSeq: 2, regionId: "a" }),
    candidate("earlier-top", -1, 1, { eventSeq: 99, regionId: "a" })
  ];
  const expectedOrder = ["earlier-top", "same-seq-a", "same-seq-b", "later-top", "z-same-seq"];

  const forward = fold(values);
  const reversed = fold(values.slice().reverse());
  assert.deepEqual(forward.decisions.map(item => item.candidate.stableId), expectedOrder);
  assert.deepEqual(reversed, forward);
});

test("translation, positive zoom scaling, and reset geometry recompute stably", () => {
  const base = [
    candidate("A", 10, 22), candidate("B", 18, 30), candidate("C", 25, 37),
    candidate("D", 40, 52)
  ];
  const baseline = fold(base);
  const signature = result => trace(result);
  const transform = (dx, dy, scale) => base.map(item => ({
    ...item,
    rect: {
      left: item.rect.left * scale + dx,
      top: item.rect.top * scale + dy,
      right: item.rect.right * scale + dx,
      bottom: item.rect.bottom * scale + dy
    }
  }));

  assert.deepEqual(signature(fold(transform(-240, 35, 1))), signature(baseline));
  assert.deepEqual(signature(fold(transform(0, 0, 1.5))), signature(baseline));
  assert.deepEqual(signature(fold(transform(0, 0, 1))), signature(baseline));
});

test("fold does not mutate candidates or expose mutable rectangle aliases", () => {
  const input = [candidate("A", 0, 10)];
  const original = structuredClone(input);
  const result = fold(input);

  result.decisions[0].candidate.rect.top = 99;
  assert.deepEqual(input, original);
  assert.equal(input[0].rect.top, 0);
});

test("CommonJS and browser global expose the same fold API", () => {
  assert.equal(typeof fold, "function");

  const sourcePath = path.join(__dirname, "../default-clear/source/web/title-folding.js");
  const source = fs.readFileSync(sourcePath, "utf8");
  const browserWindow = {};
  vm.runInNewContext(source, { window: browserWindow, Number, Set, Array, Object, TypeError, Error, RangeError });
  assert.equal(typeof browserWindow.PowerCalTitleFolding.fold, "function");
  assert.deepEqual(
    Array.from(browserWindow.PowerCalTitleFolding.fold([candidate("browser", 0, 10)]).visibleIds),
    ["browser"]
  );
});

test("invalid candidate arrays, duplicate IDs, malformed fields, and invalid rectangles throw", () => {
  assert.throws(() => fold(null), /must be an array/);
  assert.throws(() => fold([candidate("duplicate", 0, 10), candidate("duplicate", 20, 30)]),
    /IDs must be unique/);
  assert.throws(() => fold([candidate("", 0, 10)]), /stableId/);
  assert.throws(() => fold([candidate("bad-region", 0, 10, { regionId: "" })]), /regionId/);
  assert.throws(() => fold([{ ...candidate("bad-seq", 0, 10), eventSeq: NaN }]), /finite eventSeq/);
  assert.throws(() => fold([{ ...candidate("nan", 0, 10), rect: { left: 0, top: NaN, right: 1, bottom: 2 } }]),
    /rect\.top must be finite/);
  assert.throws(() => fold([{ ...candidate("infinite", 0, 10), rect: { left: 0, top: 0, right: Infinity, bottom: 2 } }]),
    /rect\.right must be finite/);
  assert.throws(() => fold([{ ...candidate("zero-width", 0, 10), rect: { left: 2, top: 0, right: 2, bottom: 10 } }]),
    /positive area/);
  assert.throws(() => fold([{ ...candidate("negative-height", 10, 0) }]), /positive area/);
});
