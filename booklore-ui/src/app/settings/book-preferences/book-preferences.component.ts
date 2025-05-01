import {Component, inject, OnInit} from '@angular/core';
import {Observable} from 'rxjs';
import {FormsModule} from '@angular/forms';
import {NgForOf} from '@angular/common';
import {Select} from 'primeng/select';
import {RadioButton} from 'primeng/radiobutton';
import {Divider} from 'primeng/divider';
import {Button} from 'primeng/button';
import {Tooltip} from 'primeng/tooltip';
import {User, UserBookPreferences, UserService} from '../user-management/user.service';

@Component({
  selector: 'app-book-preferences',
  templateUrl: './book-preferences.component.html',
  standalone: true,
  styleUrls: ['./book-preferences.component.scss'],
  imports: [Select, FormsModule, NgForOf, RadioButton, Divider, Button, Tooltip]
})
export class BookPreferences implements OnInit {
  readonly spreads = [
    { name: 'Even Spread', key: 'even' },
    { name: 'Odd Spread', key: 'odd' },
    { name: 'No Spread', key: 'off' }
  ];

  readonly zooms = [
    { name: 'Auto Zoom', key: 'auto' },
    { name: 'Page Fit', key: 'page-fit' },
    { name: 'Page Width', key: 'page-width' },
    { name: 'Actual Size', key: 'page-actual' }
  ];

  readonly themes = [
    { name: 'White', key: 'white' },
    { name: 'Black', key: 'black' },
    { name: 'Grey', key: 'grey' },
    { name: 'Sepia', key: 'sepia' }
  ];

  readonly fonts = [
    { name: 'Serif', key: 'serif' },
    { name: 'Sans Serif', key: 'sans-serif' },
    { name: 'Roboto', key: 'roboto' },
    { name: 'Cursive', key: 'cursive' },
    { name: 'Monospace', key: 'monospace' }
  ];

  readonly individualOrGlobal = ['Global', 'Individual'];

  selectedSpread!: 'off' | 'even' | 'odd';
  selectedZoom!: string;
  showSidebar = false;
  selectedTheme!: string;
  fontSize = 100;
  selectedFont!: string;
  selectedPdfScope!: string;
  selectedEpubScope!: string;

  private readonly userService = inject(UserService);
  userData$: Observable<User | null> = this.userService.userData$;

  ngOnInit(): void {
    this.userData$.subscribe(userData => {
      if (userData) this.populateSettings(userData.bookPreferences);
    });
  }

  private populateSettings(prefs: UserBookPreferences): void {
    this.selectedPdfScope = prefs.perBookSetting.pdf;
    this.selectedEpubScope = prefs.perBookSetting.epub;
    this.selectedSpread = prefs.pdfReaderSetting.pageSpread;
    this.selectedZoom = prefs.pdfReaderSetting.pageZoom;
    this.showSidebar = prefs.pdfReaderSetting.showSidebar;
    this.selectedTheme = prefs.epubReaderSetting.theme;
    this.fontSize = prefs.epubReaderSetting.fontSize;
    this.selectedFont = prefs.epubReaderSetting.font;
  }

  private updatePreference<T>(updateFn: (prefs: UserBookPreferences) => void): void {
    const user = this.userService.getLocalUser();
    if (user) {
      updateFn(user.bookPreferences);
      this.userService.updateBookPreferences(user.id, user.bookPreferences);
    }
  }

  onThemeChange(): void {
    this.updatePreference(prefs => prefs.epubReaderSetting.theme = this.selectedTheme);
  }

  onFontChange(): void {
    this.updatePreference(prefs => prefs.epubReaderSetting.font = this.selectedFont);
  }

  onSpreadChange(): void {
    this.updatePreference(prefs => prefs.pdfReaderSetting.pageSpread = this.selectedSpread);
  }

  onZoomChange(): void {
    this.updatePreference(prefs => prefs.pdfReaderSetting.pageZoom = this.selectedZoom);
  }

  onFontSizeChange(): void {
    this.updatePreference(prefs => prefs.epubReaderSetting.fontSize = this.fontSize);
  }

  onPdfScopeChange(): void {
    this.updatePreference(prefs => prefs.perBookSetting.pdf = this.selectedPdfScope);
  }

  onEpubScopeChange(): void {
    this.updatePreference(prefs => prefs.perBookSetting.epub = this.selectedEpubScope);
  }

  increaseFontSize(): void {
    if (this.fontSize < 250) {
      this.fontSize += 10;
      this.onFontSizeChange();
    }
  }

  decreaseFontSize(): void {
    if (this.fontSize > 50) {
      this.fontSize -= 10;
      this.onFontSizeChange();
    }
  }
}
