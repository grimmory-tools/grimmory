import {Component, EventEmitter, inject, Output} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {DynamicDialogRef} from 'primeng/dynamicdialog';

@Component({
  selector: 'app-icon-picker-component',
  imports: [
    FormsModule
  ],
  templateUrl: './icon-picker-component.html',
  styleUrl: './icon-picker-component.scss'
})
export class IconPickerComponent {

  iconCategories: string[] = [
    "address-book", "align-center", "align-justify", "align-left", "align-right", "amazon", "android",
    "angle-double-down", "angle-double-left", "angle-double-right", "angle-double-up", "angle-down", "angle-left",
    "angle-right", "angle-up", "apple", "arrow-circle-down", "arrow-circle-left", "arrow-circle-right", "arrow-circle-up",
    "arrow-down", "arrow-down-left", "arrow-down-left-and-arrow-up-right-to-center", "arrow-down-right", "arrow-left",
    "arrow-right", "arrow-right-arrow-left", "arrow-up", "arrow-up-left", "arrow-up-right", "arrow-up-right-and-arrow-down-left-from-center",
    "arrows-alt", "arrows-h", "arrows-v", "asterisk", "at", "backward", "ban", "barcode", "bars", "bell", "bell-slash",
    "bitcoin", "bolt", "book", "bookmark", "bookmark-fill", "box", "briefcase", "building", "building-columns", "bullseye",
    "calculator", "calendar", "calendar-clock", "calendar-minus", "calendar-plus", "calendar-times", "camera", "car",
    "caret-down", "caret-left", "caret-right", "caret-up", "cart-arrow-down", "cart-minus", "cart-plus", "chart-bar",
    "chart-line", "chart-pie", "chart-scatter", "check", "check-circle", "check-square", "chevron-circle-down",
    "chevron-circle-left", "chevron-circle-right", "chevron-circle-up", "chevron-down", "chevron-left", "chevron-right",
    "chevron-up", "circle", "circle-fill", "circle-off", "circle-on", "clipboard", "clock", "clone", "cloud", "cloud-download",
    "cloud-upload", "code", "cog", "comment", "comments", "compass", "copy", "credit-card", "crown", "database", "delete-left",
    "desktop", "directions", "directions-alt", "discord", "dollar", "download", "eject", "ellipsis-h", "ellipsis-v",
    "envelope", "equals", "eraser", "ethereum", "euro", "exclamation-circle", "exclamation-triangle", "expand",
    "external-link", "eye", "eye-slash", "face-smile", "facebook", "fast-backward", "fast-forward", "file", "file-arrow-up",
    "file-check", "file-edit", "file-excel", "file-export", "file-import", "file-o", "file-pdf", "file-plus", "file-word",
    "filter", "filter-fill", "filter-slash", "flag", "flag-fill", "folder", "folder-open", "folder-plus", "forward", "gauge",
    "gift", "github", "globe", "google", "graduation-cap", "hammer", "hashtag", "headphones", "heart", "heart-fill", "history",
    "home", "hourglass", "id-card", "image", "images", "inbox", "indian-rupee", "info", "info-circle", "instagram", "key",
    "language", "lightbulb", "link", "linkedin", "list", "list-check", "lock", "lock-open", "map", "map-marker", "mars", "megaphone",
    "microchip", "microchip-ai", "microphone", "microsoft", "minus", "minus-circle", "mobile", "money-bill", "moon", "objects-column",
    "palette", "paperclip", "pause", "pause-circle", "paypal", "pen-to-square", "pencil", "percentage", "phone", "pinterest", "play",
    "play-circle", "plus", "plus-circle", "pound", "power-off", "prime", "print", "qrcode", "question", "question-circle", "receipt",
    "reddit", "refresh", "replay", "reply", "save", "search", "search-minus", "search-plus", "send", "server", "share-alt", "shield",
    "shop", "shopping-bag", "shopping-cart", "sign-in", "sign-out", "sitemap", "slack", "sliders-h", "sliders-v", "sort",
    "sort-alpha-down", "sort-alpha-down-alt", "sort-alpha-up", "sort-alpha-up-alt", "sort-alt", "sort-alt-slash",
    "sort-amount-down", "sort-amount-down-alt", "sort-amount-up", "sort-amount-up-alt", "sort-down", "sort-down-fill",
    "sort-numeric-down", "sort-numeric-down-alt", "sort-numeric-up", "sort-numeric-up-alt", "sort-up", "sort-up-fill", "sparkles",
    "spinner", "spinner-dotted", "star", "star-fill", "star-half", "star-half-fill", "step-backward", "step-backward-alt",
    "step-forward", "step-forward-alt", "stop", "stop-circle", "stopwatch", "sun", "sync", "table", "tablet", "tag", "tags",
    "telegram", "th-large", "thumbs-down", "thumbs-down-fill", "thumbs-up", "thumbs-up-fill", "thumbtack", "ticket", "tiktok",
    "times", "times-circle", "trash", "trophy", "truck", "turkish-lira", "twitch", "twitter", "undo", "unlock", "upload", "user",
    "user-edit", "user-minus", "user-plus", "users", "venus", "verified", "video", "vimeo", "volume-down", "volume-off",
    "volume-up", "wallet", "warehouse", "wave-pulse", "whatsapp", "wifi", "window-maximize", "window-minimize", "wrench",
    "youtube"
  ];

  searchText: string = '';
  selectedIcon: string | null = null;
  icons: string[] = this.createIconList(this.iconCategories);

  ref = inject(DynamicDialogRef);

  createIconList(categories: string[]): string[] {
    return categories.map(iconName => `pi pi-${iconName}`);
  }

  filteredIcons(): string[] {
    if (!this.searchText) return this.icons;
    return this.icons.filter(icon => icon.toLowerCase().includes(this.searchText.toLowerCase()));
  }

  selectIcon(icon: string) {
    this.selectedIcon = icon;
    this.ref.close(icon);
  }

  cancel() {
    this.ref.close();
  }
}
