import React from "react";
import { Text, View, Pressable, Alert } from "react-native";
import { colors as c } from "../../data/theme";
import { pad } from "../../stores/session";
import type { Session } from "../../types";
import { Action, s } from "../components/Controls";
export function ConsolePage({ st }: { st: Session }) {
  const [busy, setBusy] = React.useState(false);
  const [manual, setManual] = React.useState(false);
  const mask = st.connectedMask ?? 0;
  const enabled = mask === 3 ? "Wii Remotes 1 and 2" : mask === 2 ? "Wii Remote 2" : "Wii Remote 1";
  const open = () => pad.openDolphin().catch(e => Alert.alert("Dolphin", e.message));
  const link = async (changeFolder = false) => {
    if (busy) return;
    setBusy(true);
    try {
      if (await pad.linkDolphin(changeFolder, mask)) {
        if (changeFolder) {
          Alert.alert("Config folder saved", "Tap Link with Dolphin to apply the settings to this folder.");
          return;
        }
        Alert.alert("Dolphin is configured", enabled + " enabled. All other Wii Remotes are set to None.", [
          { text: "Later" }, { text: "Open Dolphin", onPress: open },
        ]);
      }
    } catch (e) {
      Alert.alert("Dolphin setup", e instanceof Error ? e.message : "Could not configure Dolphin.");
    } finally {
      setBusy(false);
    }
  };
  const confirmLink = () => {
    if (busy) return;
    if (!mask) {
      Alert.alert("No controllers connected", "Open controller mode on a phone and wait for it to show as connected.");
      return;
    }
    Alert.alert("Link controllers with Dolphin?",
      (st.dolphinFolderSaved ? "MotionPad will use your saved Config folder. " : "Open Dolphin Emulator → Config, then tap Use this folder. ") +
      `Connect every phone you want to use and check that its player shows as connected here. Player 1 always uses Wii Remote 1 and Player 2 always uses Wii Remote 2. ${enabled} will be enabled. All other Wii Remotes will be set to None. Close Dolphin first. This replaces DSUClient.ini with the Thor server and saves a backup.`,
      [{ text: "Cancel", style: "cancel" }, { text: "Continue", onPress: () => link() }],
      { cancelable: true });
  };
  return (
    <View style={{ gap: 9, marginTop: 20 }}>
      <View style={{ flexDirection: "row", alignItems: "center", justifyContent: "space-between" }}>
        <Text style={s.title}>Thor receiver</Text>
        {st.dolphinFolderSaved && <Pressable accessibilityRole="button" accessibilityLabel="Change Dolphin Config folder"
          disabled={busy} onPress={() => link(true)}
          style={{ minWidth: 48, minHeight: 48, alignItems: "center", justifyContent: "center" }}>
          <Text style={{ fontSize: 22 }}>🐬</Text>
        </Pressable>}
      </View>
      <Text style={s.muted}>{st.status}</Text>
      <Text style={s.body}>Pairing code for both phones</Text>
      <Text
        selectable
        style={{
          color: c.accent,
          fontSize: 40,
          letterSpacing: 5,
          fontWeight: "700",
        }}
      >
        {st.code}
      </Text>
      {st.dolphinConfigured && <Action accent label="Open Dolphin" onPress={open} />}
      <Text style={s.body}>Connect the phones you want to use. Choose a different player on each. Close Dolphin before linking.</Text>
      <Text style={s.muted}>
        {st.dolphinFolderSaved ? "Using your saved Dolphin Config folder. Tap 🐬 to change it." :
          "In the Android folder picker, open Dolphin Emulator → Config and tap Use this folder. MotionPad will remember it."}
      </Text>
      <Action accent={!st.dolphinConfigured}
        label={busy ? "Setting up…" : st.dolphinConfigured ? "Set up controllers again" : "Link with Dolphin"}
        onPress={confirmLink} />
      <Action label={manual ? "Hide manual setup" : "Manual setup"} onPress={() => setManual(!manual)} />
      {manual && <>
        <Text style={s.body}>1. Connect the Thor and each phone to the same Wi-Fi. Open Console on the Thor. On each phone, choose Phone and enter the console code the first time. Use a different player on each phone and check the connection status above.</Text>
        <Text style={s.body}>2. Close Dolphin. Save the profiles below in Dolphin Emulator → Config → Profiles → Wiimote. Create the Profiles and Wiimote folders if needed.</Text>
        {[0, 1].map(i => <Action key={i} label={"Save ThorCentro-J" + (i + 1) + ".ini"} onPress={() => pad.export(i)} />)}
        <Text style={s.body}>3. In Dolphin’s DSU settings, enable the client and add a server named Thor at 127.0.0.1, port 26760. If your version has no DSU menu, close Dolphin and edit Config/DSUClient.ini. Under [Server], set Enabled = True and add Thor:127.0.0.1:26760; to Entries. Keep any other entries.</Text>
        <Text selectable style={s.muted}>{"For a new DSUClient.ini:\n[Server]\nEnabled = True\nEntries = Thor:127.0.0.1:26760;"}</Text>
        <Text style={s.body}>4. Open Dolphin’s Wii controller settings. For each connected player, set the matching Wii Remote to Emulated Wii Remote and load ThorCentro-J1 or ThorCentro-J2. Set unused Wii Remotes to None. Check that their devices are DSUClient/0/Thor and DSUClient/1/Thor respectively.</Text>
        <Text style={s.body}>5. Leave the Thor receiver running and start a Wii game. For later sessions, start the receiver and open controller mode on each phone. Your pairing and Dolphin settings are saved.</Text>
      </>}
    </View>
  );
}
