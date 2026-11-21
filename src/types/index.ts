export type Mode = "home" | "pair" | "play" | "console";
export interface Session {
  mode: Mode;
  code: string;
  paired: boolean;
  slot: number;
  frozen: boolean;
  pinned: boolean;
  dolphinFolderSaved?: boolean;
  connectedMask?: number;
  dolphinConfigured?: boolean;
  status: string;
  accel: boolean;
  gyro: boolean;
}
