package org.booklore.service.kobo;

import org.booklore.model.dto.settings.KoboSettings;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.service.appsettings.AppSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KoboCompatibilityService {

    private final AppSettingService appSettingService;

    public boolean isBookSupportedForKobo(BookEntity book) {
        if (book == null) {
            throw new IllegalArgumentException("Book cannot be null");
        }

        var primaryFile = book.getPrimaryBookFile();
        if (primaryFile == null) {
            return false;
        }
        BookFileType bookType = primaryFile.getBookType();
        if (bookType == null) {
            return false;
        }
        
        if (bookType == BookFileType.EPUB) {
            return true;
        }
        
        if (bookType == BookFileType.CBX) {
            return isCbxConversionEnabled() && meetsCbxConversionSizeLimit(book);
        }
        
        return false;
    }

    public boolean isCbxConversionEnabled() {
        try {
            KoboSettings koboSettings = appSettingService.getAppSettings().getKoboSettings();
            return koboSettings != null && koboSettings.isConvertCbxToEpub();
        } catch (Exception e) {
            return false;
        }
    }

    public boolean meetsCbxConversionSizeLimit(BookEntity book) {
        if (book == null || book.getPrimaryBookFile() == null || book.getPrimaryBookFile().getBookType() != BookFileType.CBX) {
            return false;
        }
        
        try {
            KoboSettings koboSettings = appSettingService.getAppSettings().getKoboSettings();
            if (koboSettings == null) {
                return false;
            }
            
            var pf = book.getPrimaryBookFile();
            long fileSizeKb = pf.getFileSizeKb() != null ? pf.getFileSizeKb() : 0;
            long limitKb = (long) koboSettings.getConversionLimitInMbForCbx() * 1024;
            
            return fileSizeKb <= limitKb;
        } catch (Exception e) {
            return false;
        }
    }
}