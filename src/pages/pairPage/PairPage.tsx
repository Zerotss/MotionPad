import React, { useState } from "react";
import { Text, View, TextInput, Alert, Keyboard } from "react-native";
import { colors as c } from "../../data/theme";
import { pad } from "../../stores/session";
import type { Mode } from "../../types";
import { Action, s } from "../components/Controls";
export function PairPage({ go }: { go: (mode: Mode) => void }) {
  const [code, input] = useState(""),
    [slot, select] = useState(0);
  return (
    <View style={{ marginTop: 24, gap: 10 }}>
      <Text style={s.title}>Pair controller</Text>
      <Text style={s.body}>Enter the six-digit code shown on your Thor.</Text>
      <TextInput
        accessibilityLabel="Console pairing code"
        placeholder="000000"
        placeholderTextColor={c.muted}
        keyboardType="number-pad"
        value={code}
        maxLength={6}
        onChangeText={(v) => input(v.replace(/\D/g, ""))}
        style={{
          color: c.text,
          fontSize: 26,
          letterSpacing: 5,
          padding: 12,
          backgroundColor: c.panel,
          borderRadius: 12,
        }}
      />
      {[0, 1].map((i) => (
        <Action
          key={i}
          accent={slot === i}
          label={"Player " + (i + 1)}
          onPress={() => select(i)}
        />
      ))}
      <Action
        label="Pair"
        onPress={() => {
          if (code.length !== 6) {
            Alert.alert("Enter all six digits");
            return;
          }
          Keyboard.dismiss();
          pad.pair(code, slot).then(() => go("play"));
        }}
      />
    </View>
  );
}
