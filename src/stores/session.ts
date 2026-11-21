import { NativeModules } from "react-native";
import type { Session, Mode } from "../types";
export const pad = NativeModules.MotionPad as {
  snapshot(): Promise<Session>;
  navigate(mode: Mode): Promise<Session>;
  pair(code: string, slot: number): Promise<boolean>;
  switchPlayer(): Promise<Session>;
  forget(): Promise<Session>;
  key(key: string, down: boolean): void;
  freeze(enabled: boolean): Promise<Session>;
  leave(): void;
  linkDolphin(changeFolder: boolean, connectedMask: number): Promise<boolean>;
  openDolphin(): Promise<boolean>;
  export(slot: number): void;
};
