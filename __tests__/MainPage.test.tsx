import React from 'react';
import { Dimensions } from "react-native";
import {test, expect, jest} from '@jest/globals';
import { Gamepad } from "../src/pages/mainPage/sections/Gamepad";
import TestRenderer, {act} from 'react-test-renderer';
import {MainPage} from '../src/pages/mainPage/MainPage';
import type {Session} from '../src/types';
import {pad} from '../src/stores/session';

jest.mock('../src/stores/session',()=>({pad:{key:jest.fn(),freeze:jest.fn(),switchPlayer:jest.fn()}}));
const state:Session={mode:'play',slot:0,code:'123456',paired:true,frozen:false,pinned:false,status:'Connected',accel:true,gyro:true};
test('the handset exposes the v14 controls and retains a one-second Freeze hold',()=>{
 jest.spyOn(require('react-native').Vibration,'vibrate').mockImplementation(()=>{});
 const view=TestRenderer.create(<MainPage state={state} update={()=>{}}/>);
 const freeze=view.root.findAllByProps({accessibilityLabel:'Freeze · hold for one second'})[0];
 expect(freeze.props.delayLongPress).toBe(1000);
 const center=view.root.findAllByProps({accessibilityLabel:'Center pointer'})[0];
 act(()=>{center.props.onPressIn();center.props.onPressOut()});
 expect(require("react-native").Vibration.vibrate).toHaveBeenCalledWith(35);
 expect((pad.key as jest.Mock).mock.calls).toEqual([['CENTER',true],['CENTER',false]]);
 for(const name of ['UP','DOWN','LEFT','RIGHT','A','B','HOME','ONE','TWO','MINUS','PLUS'])
   expect(view.root.findAllByProps({accessibilityLabel:name}).length).toBeGreaterThan(0);
 act(()=>view.update(<MainPage state={{...state,frozen:true}} update={()=>{}}/>));
 const leave=view.root.findAllByProps({accessibilityLabel:'Exit Freeze · hold for one second'})[0];
 expect(leave.props.delayLongPress).toBe(1000);

 act(()=>{leave.props.onPressIn({nativeEvent:{timestamp:10}});leave.props.onPressOut();leave.props.onPress({nativeEvent:{timestamp:120}})});
 expect(pad.freeze).not.toHaveBeenCalled();
 (pad.freeze as jest.Mock<()=>Promise<Session>>).mockResolvedValue(state);
 act(()=>leave.props.onPress({nativeEvent:{timestamp:1030}}));
 expect(pad.freeze).toHaveBeenCalledTimes(1);
 act(()=>view.unmount());
});

test("Freeze can be activated and released twice", async () => {
  const change = jest.mocked(pad.freeze);
  change.mockReset();
  change.mockImplementation(async enabled => ({ ...state, frozen: enabled }));
  const update = (next: Session) => view.update(<MainPage state={next} update={update} />);
  const view = TestRenderer.create(<MainPage state={state} update={update} />);
  for (const enable of [true, false, true, false]) {
    const label = enable ? "Freeze" : "Exit Freeze";
    const button = view.root.findAllByProps({ accessibilityLabel: label + " · hold for one second" })[0];
    await act(async () => { button.props.onLongPress(); });
    expect(view.root.findAllByProps({ accessibilityLabel: "Center pointer" })[0].props.disabled).toBe(enable);
  }
  expect(change.mock.calls).toEqual([[true], [false], [true], [false]]);
  act(() => view.unmount());
});

test.each([[360, 640], [720, 360]])("slides between rendered arrows with A held at %i × %i", (width, height) => {
  const original = { window: Dimensions.get("window"), screen: Dimensions.get("screen") };
  const window = { ...original.window, width, height };
  Dimensions.set({ window, screen: window });
  const view = TestRenderer.create(<Gamepad frozen={false} area={{ width, height }} />);
  try {
    const remote = view.root.findAllByProps({ accessibilityLabel: "Wii Remote" })[0];
    const touch = (id: number, label: string) => {
      const box = view.root.findAllByProps({ accessibilityLabel: label })[0].props.style;
      return { identifier: id, locationX: box.left + box.width / 2, locationY: box.top + box.height / 2 };
    };
    const event = (id: number, label: string) => ({ nativeEvent: { changedTouches: [touch(id, label)] } });
    jest.mocked(pad.key).mockClear();
    act(() => {
      remote.props.onResponderStart(event(1, "A"));
      remote.props.onResponderStart(event(2, "UP"));
      for (const key of ["RIGHT", "RIGHT", "DOWN", "LEFT"]) remote.props.onResponderMove(event(2, key));
      remote.props.onResponderMove(event(2, "B"));
      remote.props.onResponderMove(event(2, "UP"));
      remote.props.onResponderEnd(event(2, "UP"));
      remote.props.onResponderEnd(event(1, "A"));
      remote.props.onResponderStart(event(2, "LEFT"));
      remote.props.onResponderEnd(event(2, "LEFT"));
    });
    expect(jest.mocked(pad.key).mock.calls).toEqual([
      ["A", true], ["UP", true], ["UP", false], ["RIGHT", true], ["RIGHT", false],
      ["DOWN", true], ["DOWN", false], ["LEFT", true], ["LEFT", false], ["UP", true],
      ["UP", false], ["A", false], ["LEFT", true], ["LEFT", false],
    ]);
  } finally {
    act(() => view.unmount());
    Dimensions.set(original);
  }
});
