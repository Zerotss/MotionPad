import React from "react";
import { expect, jest, test } from "@jest/globals";
import TestRenderer, { act } from "react-test-renderer";
import { Pressable, Text } from "react-native";
import App from "../src/App";
import { pad } from "../src/stores/session";
import type { Mode, Session } from "../src/types";

jest.mock("../src/stores/session", () => ({ pad: { snapshot: jest.fn(), navigate: jest.fn() } }));
const session: Session = { mode: "home", paired: false, code: "123456", slot: 0,
  pinned: false, frozen: false, status: "", accel: false, gyro: false };

test.each<Mode>(["pair", "play", "console"])("can change role from %s", async mode => {
  jest.mocked(pad.snapshot).mockResolvedValue({ ...session, mode });
  jest.mocked(pad.navigate).mockResolvedValue(session);
  let view!: TestRenderer.ReactTestRenderer;
  await act(async () => { view = TestRenderer.create(<App />); });
  try {
    const button = view.root.findAllByType(Pressable).find(node =>
      node.findAllByType(Text).some(label => label.props.children === "Change device role"));
    expect(button).toBeDefined();
    await act(async () => { button!.props.onPress(); });
    expect(pad.navigate).toHaveBeenCalledWith("home");
    expect(view.root.findByProps({ label: "Console · Connect to Dolphin" })).toBeDefined();
  } finally {
    act(() => view.unmount());
  }
});
