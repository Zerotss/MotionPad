import React from "react";
import { Alert } from "react-native";
import { expect, jest, test } from "@jest/globals";
import TestRenderer, { act } from "react-test-renderer";
import { ConsolePage } from "../src/pages/consolePage/ConsolePage";
import { pad } from "../src/stores/session";
import type { Session } from "../src/types";

jest.mock("../src/stores/session", () => ({ pad: { linkDolphin: jest.fn(), export: jest.fn() } }));
const state: Session = { mode: "console", code: "123456", slot: 0, paired: true, connectedMask: 1,
  frozen: false, pinned: false, status: "P1 connected · P2 waiting", accel: false, gyro: false };

test.each([false, true])("confirms slot assignments before setup (configured=%s)", configured => {
  const alert = jest.spyOn(Alert, "alert").mockImplementation(() => {});
  const view = TestRenderer.create(<ConsolePage st={{ ...state, dolphinConfigured: configured }} />);
  const label = configured ? "Set up controllers again" : "Link with Dolphin";
  act(() => view.root.findByProps({ label }).props.onPress());
  expect(alert.mock.calls[0][1]).toContain("Connect every phone");
  expect(alert.mock.calls[0][1]).toContain("Player 2 always uses Wii Remote 2");
  expect(pad.linkDolphin).not.toHaveBeenCalled();
  act(() => view.root.findByProps({ label: "Manual setup" }).props.onPress());
  expect(view.root.findByProps({ label: "Save ThorCentro-J2.ini" })).toBeDefined();
  act(() => view.unmount());
  alert.mockRestore();
});

test("only linked folders expose the picker icon; normal setup reuses the saved folder", async () => {
  const alert = jest.spyOn(Alert, "alert").mockImplementation(() => {});
  const link = jest.mocked(pad.linkDolphin).mockResolvedValue(false);
  link.mockClear();
  const view = TestRenderer.create(<ConsolePage st={state} />);
  const icon = { accessibilityLabel: "Change Dolphin Config folder" };
  try {
    expect(view.root.findAllByProps(icon)).toHaveLength(0);
    act(() => view.update(<ConsolePage st={{ ...state, dolphinConfigured: true, dolphinFolderSaved: true }} />));
    await act(async () => { await view.root.findAllByProps(icon)[0].props.onPress(); });
    expect(link).toHaveBeenLastCalledWith(true, 1);
    act(() => view.root.findByProps({ label: "Set up controllers again" }).props.onPress());
    const confirm = alert.mock.calls[0][2]?.find(button => button.text === "Continue");
    expect(confirm).toBeDefined();
    await act(async () => { await confirm?.onPress?.(); });
    expect(link).toHaveBeenLastCalledWith(false, 1);
  } finally {
    act(() => view.unmount());
    alert.mockRestore();
  }
});

test.each([0, 1, 2, 3])("previews connected mask %i and hides the notification permission action", mask => {
  const alert = jest.spyOn(Alert, "alert").mockImplementation(() => {});
  const view = TestRenderer.create(<ConsolePage st={{ ...state, connectedMask: mask }} />);
  try {
    expect(view.root.findAllByProps({ label: "Allow notifications" })).toHaveLength(0);
    act(() => view.root.findByProps({ label: "Link with Dolphin" }).props.onPress());
    const [title, message, actions] = alert.mock.calls[0];
    if (mask === 0) {
      expect(title).toBe("No controllers connected");
      expect(actions).toBeUndefined();
    } else {
      const enabled = ["", "Wii Remote 1", "Wii Remote 2", "Wii Remotes 1 and 2"][mask];
      expect(message).toContain(enabled + " will be enabled.");
      expect(message).toContain("All other Wii Remotes will be set to None.");
    }
  } finally {
    act(() => view.unmount());
    alert.mockRestore();
  }
});
