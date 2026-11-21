import {expect,jest,test} from '@jest/globals';
import {PadTouches} from '../src/stores/PadTouches';
test('simultaneous buttons and two fingers on one button',()=>{
  const edge=jest.fn(),touches=new PadTouches(edge);
  touches.down('1','A');touches.down('2','B');touches.down('3','A');
  touches.up('1');touches.up('2');touches.clear();
  expect(edge.mock.calls).toEqual([['A',true],['B',true],['B',false],['A',false]]);
});
