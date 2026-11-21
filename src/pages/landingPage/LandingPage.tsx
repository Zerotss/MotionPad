import React from "react";
import { View, Text } from "react-native";
import { Action, s } from "../components/Controls";
import type { Mode } from "../../types";
export function LandingPage({ go }: { go: (m: Mode) => void }) {
  return (
    <View style={{ gap: 8, marginTop: 30 }}>
      <Text style={s.title}>Which device are you using?</Text>
      <Text style={s.muted}>MotionPad will open in this role on this device. You can change it later.</Text>
      <Action
        accent
        label="Phone · Use as a controller"
        onPress={() => go("play")}
      />
      <Action
        label="Console · Connect to Dolphin"
        onPress={() => go("console")}
      />
    </View>
  );
}
