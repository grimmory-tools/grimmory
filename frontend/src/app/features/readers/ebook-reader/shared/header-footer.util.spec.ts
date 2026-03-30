import {describe, expect, it} from 'vitest';

import {PageDecorator} from './header-footer.util';

describe('PageDecorator', () => {
  it('ignores missing renderers and missing footer page info', () => {
    expect(() => PageDecorator.updateHeadersAndFooters(null, 'Chapter 1')).not.toThrow();

    const head = document.createElement('div');
    const foot = document.createElement('div');

    PageDecorator.updateHeadersAndFooters({heads: [head], feet: [foot]}, 'Chapter 1');

    expect(head.textContent).toBe('Chapter 1');
    expect(foot.childElementCount).toBe(0);
  });

  it('renders single-column headers and footers with theme-aware styling', () => {
    const head = document.createElement('div');
    const foot = document.createElement('div');

    PageDecorator.updateHeadersAndFooters(
      {heads: [head], feet: [foot]},
      'Chapter 7',
      {percentCompleted: 42, sectionTimeText: '3m 10s'},
      {fg: '#111111', bg: '#ffffff'},
      '2m left'
    );

    const headerContent = head.firstElementChild as HTMLElement | null;
    const footerContent = foot.firstElementChild as HTMLElement | null;

    expect(head.style.visibility).toBe('visible');
    expect(headerContent?.textContent).toContain('Chapter 7');
    expect(headerContent?.style.color).toBe('rgb(17, 17, 17)');
    expect(headerContent?.children).toHaveLength(2);
    expect(footerContent?.children).toHaveLength(2);
    expect(footerContent?.textContent).toContain('2m left');
    expect(footerContent?.textContent).toContain('42%');
  });

  it('renders multi-column header and footer content only on the outer columns', () => {
    const leftHead = document.createElement('div');
    const rightHead = document.createElement('div');
    const leftFoot = document.createElement('div');
    const rightFoot = document.createElement('div');

    PageDecorator.updateHeadersAndFooters(
      {heads: [leftHead, rightHead], feet: [leftFoot, rightFoot]},
      'Chapter 12',
      {percentCompleted: 88, sectionTimeText: '45s'}
    );

    const leftHeaderContent = leftHead.firstElementChild as HTMLElement | null;
    const rightHeaderContent = rightHead.firstElementChild as HTMLElement | null;
    const leftFooterContent = leftFoot.firstElementChild as HTMLElement | null;
    const rightFooterContent = rightFoot.firstElementChild as HTMLElement | null;

    expect(leftHeaderContent?.textContent).toContain('Chapter 12');
    expect(rightHeaderContent?.textContent).toBe('');

    expect(leftFooterContent?.textContent).toContain('Time remaining in section: 45s');
    expect(leftFooterContent?.children).toHaveLength(2);
    expect(rightFooterContent?.textContent).toContain('88%');
    expect(rightFooterContent?.children).toHaveLength(2);
  });
});
