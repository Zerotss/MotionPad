import { PairPage } from "./pages/pairPage/PairPage";
import { ConsolePage } from "./pages/consolePage/ConsolePage";
import React, { useCallback, useEffect, useRef, useState } from "react";
import {
  Alert,
  BackHandler,
  Image,
  Pressable,
  ScrollView,
  Text,
  View,
} from "react-native";
import { colors as c } from "./data/theme";
import { pad } from "./stores/session";
import type { Mode, Session } from "./types";
import { s } from "./pages/components/Controls";
import { LandingPage } from "./pages/landingPage/LandingPage";
import { MainPage } from "./pages/mainPage/MainPage";
export default function App() {
  const [st, set] = useState<Session | null>(null);
  const dialog = useRef(false);
  useEffect(() => {
    let alive = true;
    const poll = () =>
      pad.snapshot().then((x) => {
        if (alive) set(x);
      });
    poll();
    const timer = setInterval(poll, 650);
    return () => {
      alive = false;
      clearInterval(timer);
    };
  }, []);
  const go = useCallback((m: Mode) => {
    pad
      .navigate(m)
      .then(set)
      .catch((e) => Alert.alert("Connection", e.message));
  }, []);
  const exit = useCallback(() => {
    if (dialog.current) return;
    dialog.current = true;
    Alert.alert(
      "Leave controller mode?",
      "Your phone will stop sending motion data.",
      [
        {
          text: "Keep playing",
          onPress: () => {
            dialog.current = false;
          },
        },
        {
          text: "Leave",
          onPress: () => {
            dialog.current = false;
            pad.leave();
          },
        },
      ],
      { cancelable: false }
    );
  }, []);
  useEffect(() => {
    const sub = BackHandler.addEventListener("hardwareBackPress", () => {
      if (!st || st.mode === "home") return false;
      if (st.mode === "play" && st.frozen) return true;
      if (st.mode === "play" && st.pinned) exit();
      else pad.leave();
      return true;
    });
    return () => sub.remove();
  }, [st, exit]);
  return (
    <View style={{ backgroundColor: c.bg, flex: 1 }}>
      <ScrollView
        scrollEnabled={!(st?.mode === "play" && st.frozen)}
        keyboardShouldPersistTaps="handled"
        contentContainerStyle={{
          paddingHorizontal: 18,
          paddingVertical: 14,
          flexGrow: 1,
          alignSelf: "center",
          width: "100%",
          maxWidth: 740,
        }}
      >
        <View style={{ flexDirection: "row", alignItems: "center", gap: 10 }}>
          <Image
            source={require("./assets/logo.png")}
            style={{ width: 38, height: 38, borderRadius: 12 }}
          />
          <Text style={[s.title, { fontSize: 23, letterSpacing: -0.7 }]}>
            MotionPad
          </Text>
          {st && st.mode !== "home" && !(st.mode === "play" && st.frozen) && (
            <Pressable accessibilityRole="button" onPress={() => go("home")}
              style={{ marginLeft: "auto", paddingVertical: 12, paddingHorizontal: 6 }}>
              <Text style={{ color: c.accent, fontWeight: "600" }}>Change device role</Text>
            </Pressable>
          )}
        </View>
        {st?.mode === "home" && <LandingPage go={go} />}
        {st?.mode === "console" && <ConsolePage st={st} />}
        {st?.mode === "pair" && <PairPage go={go} />}
        {st?.mode === "play" && (
          <MainPage state={st} update={set} />
        )}
      </ScrollView>
    </View>
  );
}
