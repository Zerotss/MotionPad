import React from "react";
import { Text, Pressable, StyleSheet } from "react-native";
import { colors as c } from "../../data/theme";
export function Action({
  label,
  onPress,
  accent = false,
}: {
  label: string;
  onPress: () => void;
  accent?: boolean;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      style={({ pressed }) => [
        s.button,
        {
          backgroundColor: accent ? c.accent : c.panel,
          opacity: pressed ? 0.75 : 1,
        },
      ]}
    >
      <Text
        style={{
          fontSize: 16,
          fontWeight: "700",
          color: accent ? c.ink : c.text,
        }}
      >
        {label}
      </Text>
    </Pressable>
  );
}
export const s = StyleSheet.create({
  button: {
    padding: 14,
    borderRadius: 14,
    alignItems: "center",
    marginVertical: 4,
  },
  title: { color: c.text, fontWeight: "700", fontSize: 20 },
  body: { color: c.text, fontSize: 16, lineHeight: 23 },
  muted: { color: c.muted, fontSize: 13, marginVertical: 7 },
});
