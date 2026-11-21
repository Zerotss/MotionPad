// Android sends all fingers to the same responder. Only fingers that start on
// the D-pad can steer; action buttons stay held until their own finger lifts.
export class PadTouches {
  private fingers = new Map<string, { key?: string; steer: boolean }>();
  constructor(private edge: (key: string, down: boolean) => void) {}

  private held(key: string) {
    for (const finger of this.fingers.values()) if (finger.key === key) return true;
    return false;
  }

  down(id: string, key: string | undefined, steer = false) {
    if (this.fingers.has(id) || (!key && !steer)) return;
    if (key && !this.held(key)) this.edge(key, true);
    this.fingers.set(id, { key, steer });
  }

  move(id: string, direction: string | undefined) {
    const finger = this.fingers.get(id);
    if (!finger?.steer || finger.key === direction) return;
    const previous = finger.key;
    finger.key = undefined;
    if (previous && !this.held(previous)) this.edge(previous, false);
    if (direction && !this.held(direction)) this.edge(direction, true);
    finger.key = direction;
  }

  up(id: string) {
    const key = this.fingers.get(id)?.key;
    this.fingers.delete(id);
    if (key && !this.held(key)) this.edge(key, false);
  }

  clear() {
    for (const id of this.fingers.keys()) this.up(id);
  }
}
