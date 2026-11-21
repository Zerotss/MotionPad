import React, { useState, useMemo, useEffect } from "react";
import { Text, View, useWindowDimensions } from "react-native";
import { colors as c } from "../../../data/theme";
import { PadTouches } from "../../../stores/PadTouches";
import { pad } from "../../../stores/session";
const buttons = [
  ["UP", "▲", 2, 0, 1, 0],
  ["LEFT", "◀", 1, 1, 0, 1],
  ["RIGHT", "▶", 3, 1, 2, 1],
  ["DOWN", "▼", 2, 2, 1, 2],
  ["A", "A", 2, 3.85, 7, 1],
  ["B", "B", 4, 3.85, 8, 1],
  ["MINUS", "−", 0.85, 5.3, 3, 0],
  ["HOME", "HOME", 2, 5.3, 4, 0],
  ["PLUS", "+", 3.15, 5.3, 5, 0],
  ["ONE", "1", 2, 6.65, 4, 2],
  ["TWO", "2", 2, 8, 5, 2],
] as const;
export function Gamepad({
  frozen,
  area,
}: {
  frozen: boolean;
  area: { width: number; height: number };
}) {
  const { width, height } = useWindowDimensions(),
    wide = width > height;
  const w = Math.min(area.width || width - 36, wide ? 740 : 490);
  const h = area.height || (wide ? 154 : 385);
  const rows = wide ? 3 : 9,
    cols = wide ? 9 : 5;
  const unit = Math.min((w - 16) / cols, (h - 16) / rows);
  const gap = (h - 16 - rows * unit) / (wide ? 2 : 5);
  const [held, setHeld] = useState<Record<string, boolean>>({});
  const pos = (col: number, row: number) => ({
    left: (w - (wide ? 9 : 5) * unit) / 2 + col * unit,
    top: 8 + row * unit + (wide ? row : (Math.max(0, row - 2) * 5) / 6) * gap,
  });
  const touches = useMemo(
    () =>
      new PadTouches((key, down) => {
        pad.key(key, down);
        setHeld((old) => ({ ...old, [key]: down }));
      }),
    []
  );
  useEffect(() => () => touches.clear(), [touches]);
  const boxes = buttons.map(([key, , x, y, lx, ly], i) => {
    const at = pos(wide ? lx : x, wide ? ly : y);
    const size = unit * (key === "A" ? 1.23 : i > 5 ? 0.87 : 1);
    return {
      key,
      size,
      left: at.left + (unit - size) / 2,
      top: at.top + (unit - size) / 2,
    };
  });
  // Match the rendered arrow centres even when the landscape layout spaces out rows.
  const center = pos(wide ? 1 : 2, 1);
  const cx = center.left + unit / 2;
  const cy = center.top + unit / 2;
  const rowStep = wide ? unit + gap : unit;
  function directionAt(x: number, y: number) {
    const dx = (x - cx) / unit;
    const dy = (y - cy) / rowStep;
    if (Math.abs(x - cx) > 1.5 * unit || Math.abs(y - cy) > rowStep + unit / 2)
      return undefined;
    if (Math.max(Math.abs(dx), Math.abs(dy)) < 0.22) return undefined;
    if (Math.abs(dx) > Math.abs(dy)) return dx < 0 ? "LEFT" : "RIGHT";
    return dy < 0 ? "UP" : "DOWN";
  }
  return (
    <View
      accessible
      accessibilityLabel="Wii Remote"
      collapsable={false}
      onStartShouldSetResponder={() => !frozen}
      onResponderGrant={() => true}
      onResponderTerminationRequest={() => false}
      onResponderTerminate={() => touches.clear()}
      onResponderStart={(e) => {
        if (!frozen)
          for (const t of e.nativeEvent.changedTouches) {
            const hit = boxes.find(
              (b) =>
                t.locationX >= b.left &&
                t.locationX < b.left + b.size &&
                t.locationY >= b.top &&
                t.locationY < b.top + b.size
            );
            const { locationX: x, locationY: y } = t;
            const onArm = hit && ["UP", "LEFT", "RIGHT", "DOWN"].includes(hit.key);
            const onCenter = Math.abs(x - cx) <= unit / 2 && Math.abs(y - cy) <= unit / 2;
            const steer = Boolean(onArm || onCenter);
            touches.down(String(t.identifier), steer ? directionAt(x, y) : hit?.key, steer);
          }
      }}
      onResponderMove={(e) => {
        if (frozen) return;
        for (const t of e.nativeEvent.changedTouches)
          touches.move(String(t.identifier), directionAt(t.locationX, t.locationY));
      }}
      onResponderEnd={(e) => {
        for (const t of e.nativeEvent.changedTouches)
          touches.up(String(t.identifier));
      }}
      style={{
        alignSelf: "center",
        width: w,
        height: h,
        backgroundColor: c.case,
        borderRadius: 40,
        borderBottomWidth: 5,
        borderColor: c.shadow,
        opacity: frozen ? 0.62 : 1,
      }}
    >
      <View
        pointerEvents="none"
        style={{ position: "absolute", top: 0, bottom: 0, left: 0, right: 0 }}
      >
        <View
          style={{
            position: "absolute",
            ...pos(wide ? 1 : 2, 1),
            width: unit + 1,
            height: unit + 1,
            backgroundColor: c.cross,
          }}
        />
        {buttons.map(([key, label, x, y, lx, ly], i) => {
          const cross = i < 4,
            small = i > 5,
            size = unit * (key === "A" ? 1.23 : small ? 0.87 : 1);
          const at = pos(wide ? lx : x, wide ? ly : y);
          const rounded = cross
            ? {
                borderTopLeftRadius: ["UP", "LEFT"].includes(key) ? 11 : 0,
                borderTopRightRadius: ["UP", "RIGHT"].includes(key) ? 11 : 0,
                borderBottomLeftRadius: ["DOWN", "LEFT"].includes(key) ? 11 : 0,
                borderBottomRightRadius: ["DOWN", "RIGHT"].includes(key)
                  ? 11
                  : 0,
              }
            : { borderRadius: key === "B" ? 18 : size / 2 };
          return (
            <View
              key={key}
              accessible
              accessibilityRole="button"
              accessibilityLabel={key}
              style={{
                position: "absolute",
                left: at.left + (unit - size) / 2,
                top: at.top + (unit - size) / 2,
                width: size,
                height: size,
                alignItems: "center",
                justifyContent: "center",
                ...rounded,
                backgroundColor: held[key]
                  ? c.pressed
                  : cross
                  ? c.cross
                  : key === "A"
                  ? c.accent
                  : key === "B"
                  ? "#36564c"
                  : c.key,
                borderBottomWidth: cross ? 0 : 3,
                borderColor: c.shadow,
              }}
            >
              <View pointerEvents="none">
                <Text
                  style={{
                    color: cross || key === "B" ? c.text : c.ink,
                    fontWeight: "800",
                    fontSize: key === "HOME" ? 11 : key === "A" ? 30 : 21,
                  }}
                >
                  {label}
                </Text>
              </View>
            </View>
          );
        })}
      </View>
    </View>
  );
}
