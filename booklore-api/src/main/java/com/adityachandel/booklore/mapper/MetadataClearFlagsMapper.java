package com.adityachandel.booklore.mapper;

import com.adityachandel.booklore.model.MetadataClearFlags;
import com.adityachandel.booklore.model.dto.request.BulkMetadataUpdateRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MetadataClearFlagsMapper {

    /*@Mapping(target = "title", source = "clearTitle")
    @Mapping(target = "subtitle", source = "clearSubtitle")
    @Mapping(target = "publisher", source = "clearPublisher")
    @Mapping(target = "publishedDate", source = "clearPublishedDate")
    @Mapping(target = "description", source = "clearDescription")
    @Mapping(target = "seriesName", source = "clearSeriesName")
    @Mapping(target = "seriesNumber", source = "clearSeriesNumber")
    @Mapping(target = "seriesTotal", source = "clearSeriesTotal")
    @Mapping(target = "isbn13", source = "clearIsbn13")
    @Mapping(target = "isbn10", source = "clearIsbn10")
    @Mapping(target = "asin", source = "clearAsin")
    @Mapping(target = "goodreadsId", source = "clearGoodreadsId")
    @Mapping(target = "hardcoverId", source = "clearHardcoverId")
    @Mapping(target = "googleId", source = "clearGoogleId")
    @Mapping(target = "pageCount", source = "clearPageCount")
    @Mapping(target = "language", source = "clearLanguage")
    @Mapping(target = "amazonRating", source = "clearAmazonRating")
    @Mapping(target = "amazonReviewCount", source = "clearAmazonReviewCount")
    @Mapping(target = "goodreadsRating", source = "clearGoodreadsRating")
    @Mapping(target = "goodreadsReviewCount", source = "clearGoodreadsReviewCount")
    @Mapping(target = "hardcoverRating", source = "clearHardcoverRating")
    @Mapping(target = "hardcoverReviewCount", source = "clearHardcoverReviewCount")
    @Mapping(target = "personalRating", source = "clearPersonalRating")
    @Mapping(target = "authors", source = "clearAuthors")
    @Mapping(target = "categories", source = "clearGenres")
    @Mapping(target = "cover", source = "clearCover")
    MetadataClearFlags toClearFlags(BulkMetadataUpdateRequest request);*/


    @Mapping(target = "publisher", source = "clearPublisher")
    @Mapping(target = "publishedDate", source = "clearPublishedDate")
    @Mapping(target = "seriesName", source = "clearSeriesName")
    @Mapping(target = "seriesTotal", source = "clearSeriesTotal")
    @Mapping(target = "language", source = "clearLanguage")
    @Mapping(target = "authors", source = "clearAuthors")
    @Mapping(target = "categories", source = "clearGenres")
    MetadataClearFlags toClearFlags(BulkMetadataUpdateRequest request);
}
