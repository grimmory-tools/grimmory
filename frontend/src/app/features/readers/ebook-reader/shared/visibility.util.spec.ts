import {describe, expect, it, vi} from 'vitest';

import {ReaderHeaderFooterVisibilityManager} from './visibility.util';

describe('ReaderHeaderFooterVisibilityManager', () => {
  it('shows and hides the header and footer based on pointer movement', () => {
    const manager = new ReaderHeaderFooterVisibilityManager(1000);
    const callback = vi.fn();
    manager.onStateChange(callback);

    expect(manager.getVisibilityState()).toEqual({headerVisible: false, footerVisible: false});

    manager.handleMouseMove(5);
    expect(manager.getVisibilityState()).toEqual({headerVisible: true, footerVisible: false});

    manager.handleMouseMove(990);
    expect(manager.getVisibilityState()).toEqual({headerVisible: false, footerVisible: true});

    manager.handleMouseLeave();
    expect(manager.getVisibilityState()).toEqual({headerVisible: false, footerVisible: false});
    expect(callback).toHaveBeenCalled();
  });

  it('keeps both zones visible while pinned', () => {
    const manager = new ReaderHeaderFooterVisibilityManager(1000);

    manager.togglePinned();
    manager.handleMouseMove(500);

    expect(manager.getVisibilityState()).toEqual({headerVisible: true, footerVisible: true});

    manager.handleMouseLeave();

    expect(manager.getVisibilityState()).toEqual({headerVisible: true, footerVisible: true});
  });

  it('shows each zone independently when the explicit enter handlers run', () => {
    const manager = new ReaderHeaderFooterVisibilityManager(1000);

    manager.handleHeaderZoneEnter();
    expect(manager.getVisibilityState()).toEqual({headerVisible: true, footerVisible: false});

    manager.handleFooterZoneEnter();
    expect(manager.getVisibilityState()).toEqual({headerVisible: true, footerVisible: true});
  });

  it('uses the updated window height for the footer trigger zone', () => {
    const manager = new ReaderHeaderFooterVisibilityManager(1000);

    manager.updateWindowHeight(200);
    manager.handleMouseMove(175);

    expect(manager.getVisibilityState()).toEqual({headerVisible: false, footerVisible: true});
  });
});
