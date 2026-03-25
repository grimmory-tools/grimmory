import {inject, Injectable} from "@angular/core";
import {Title} from "@angular/platform-browser";
import {Book} from "../../features/book/model/book.model";

@Injectable({providedIn: 'root'})
export class PageTitleService {
    protected titleService = inject(Title)

    private appName = 'Grimmory'

    setBookPageTitle(book: Book) {
        const title = [
            `${book.libraryName}/${book.metadata?.title || book.fileName}`,
            book.metadata?.seriesName ? `(${book.metadata.seriesName} series)` : false,
            book.metadata?.authors?.length ? `- by ${new Intl.ListFormat('en').format(book.metadata?.authors)}` : false,
            `(${book.primaryFile?.bookType})`
        ].filter(part => part);

        this.setPageTitle(title.join(' '));
    }

    setPageTitle(pageTitle: string) {
        this.titleService.setTitle([pageTitle, this.appName].filter(part => part).join(' - '));
    }
}
