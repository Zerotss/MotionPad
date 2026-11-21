import React, { useState } from "react";
import {
  Animated,
  Alert,
  Vibration,
  useWindowDimensions,
  LayoutAnimation,
  Pressable,
  Text,
  View,
  UIManager,
} from "react-native";
import { Gamepad } from "./sections/Gamepad";
import { pad } from "../../stores/session";
import { Action, s } from "../components/Controls";
import { colors as c } from "../../data/theme";
import type { Session } from "../../types";
UIManager.setLayoutAnimationEnabledExperimental?.(true);
export function MainPage({
  state,
  update,
}: {
  state: Session;
  update: (s: Session) => void;
}) {
  const [open, setOpen] = useState(false);
  const pressStartedAt = React.useRef<number | null>(null);
  const changingFreeze = React.useRef(false);
  const progress = React.useRef(new Animated.Value(0)).current;
  const [area, setArea] = useState({ width: 0, height: 0 });
  const window = useWindowDimensions();
  const wide = window.width > window.height;
  const resetProgress = () => {
    progress.stopAnimation();
    progress.setValue(0);
  };
  React.useEffect(() => () => progress.stopAnimation(), [progress]);
  const changeFreeze = () => {
    if (changingFreeze.current) return;
    pressStartedAt.current = null;
    changingFreeze.current = true;
    Vibration.vibrate(55);
    setOpen(false);
    resetProgress();
    pad.freeze(!state.frozen).then(update)
      .catch(() => Alert.alert("Could not change Freeze", "Please hold the button again."))
      .finally(() => { changingFreeze.current = false; });
  };
  const tool = {
    width: 46,
    height: 46,
    borderRadius: 14,
    alignItems: "center" as const,
    justifyContent: "center" as const,
    backgroundColor: c.panel,
  };
  const freezeWidth = state.frozen ? 96 : 46;
  return (
    <View style={{ flexGrow: 1, gap: 7 }}>
      <Text style={s.muted}>{state.status}</Text>
      <View style={{ flexDirection: "row", alignItems: "center", gap: 8 }}>
        <Text style={[s.title, { flex: 1 }]}>Player {state.slot + 1}</Text>
        <Pressable
          accessibilityLabel="Center pointer"
          disabled={state.frozen}
          onPressIn={() => {
            Vibration.vibrate(35);
            pad.key("CENTER", true);
          }}
          onPressOut={() => pad.key("CENTER", false)}
          style={tool}
        >
          <Text style={{ color: c.text, fontSize: 27 }}>◎</Text>
        </Pressable>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={state.frozen ? "Exit Freeze · hold for one second" : "Freeze · hold for one second"}
          key={state.frozen ? "exit-freeze" : "enter-freeze"}
          delayLongPress={1000}
          unstable_pressDelay={0}
          onPressIn={event => {
            pressStartedAt.current = event.nativeEvent.timestamp;
            Vibration.vibrate(35);
            progress.setValue(0);
            Animated.timing(progress, {
              toValue: 1,
              duration: 1000,
              useNativeDriver: true,
            }).start();
          }}
          onPressOut={resetProgress}
          onLongPress={changeFreeze}
          onPress={event => {
            const start = pressStartedAt.current;
            if (start !== null && event.nativeEvent.timestamp - start >= 1000) changeFreeze();
          }}
          style={[
            tool,
            { overflow: "hidden", width: freezeWidth },
            state.frozen && { backgroundColor: c.accent },
          ]}
        >
          <Animated.View
            pointerEvents="none"
            style={{
              position: "absolute",
              left: 0,
              top: 0,
              bottom: 0,
              width: freezeWidth,
              backgroundColor: c.pressed,
              transform: [
                {
                  translateX: progress.interpolate({
                    inputRange: [0, 1],
                    outputRange: [-freezeWidth, 0],
                  }),
                },
              ],
            }}
          />
          <Text style={{ color: state.frozen ? c.ink : c.text, fontSize: state.frozen ? 17 : 25 }}>
            {state.frozen ? "Exit · 1 s" : "❄"}
          </Text>
        </Pressable>
      </View>
      {state.frozen && (
        <Text style={s.muted}>{state.pinned ? "Buttons locked · Motion active" : "Motion active · To block Home, enable app pinning in Android settings, then turn Freeze off and on again."}</Text>
      )}
      <View
        style={{ flexGrow: 1, minHeight: wide ? 154 : 385 }}
        onLayout={({ nativeEvent: { layout } }) =>
          setArea((old) =>
            old.width === layout.width && old.height === layout.height
              ? old
              : { width: layout.width, height: layout.height }
          )
        }
      >
        <View
          style={{ position: "absolute", top: 0, left: 0, right: 0, bottom: 0 }}
        >
          <Gamepad
            key={String(state.frozen)}
            frozen={state.frozen}
            area={area}
          />
        </View>
      </View>
      {!state.frozen && (
        <View
          style={{ marginTop: "auto", borderTopWidth: 1, borderColor: c.edge }}
        >
          <Pressable
            accessibilityRole="button"
            accessibilityState={{ expanded: open }}
            onPress={() => {
              LayoutAnimation.configureNext(
                LayoutAnimation.Presets.easeInEaseOut
              );
              setOpen(!open);
            }}
            style={{
              paddingVertical: 15,
              flexDirection: "row",
              justifyContent: "space-between",
            }}
          >
            <Text style={s.body}>Connection and controls</Text>
            <Text style={s.body}>{open ? "⌃" : "⌄"}</Text>
          </Pressable>
          {open && (
            <>
              <Text style={s.muted}>
                Accelerometer · {state.accel ? "active" : "no data"} Gyroscope
                · {state.gyro ? "active" : "no data"}
              </Text>

              <Text style={s.muted}>
                Aim at the center and tap the crosshair. To lock the buttons while
                keeping motion active, hold the snowflake for one second.
              </Text>
              <Action
                label="Switch player"
                onPress={() => pad.switchPlayer().then(update)}
              />
              <Action
                label="Forget pairing"
                onPress={() => pad.forget().then(update)}
              />
            </>
          )}
        </View>
      )}
    </View>
  );
}
