<#ftl output_format="XML" encoding="UTF-8">
<?xml version="1.0" encoding="UTF-8"?>
<package version="3.0" unique-identifier="BookID" xmlns="http://www.idpf.org/2007/opf">
  <metadata xmlns:opf="http://www.idpf.org/2007/opf" xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:title id="title">${title!""}</dc:title>
    <#if subtitle?has_content>
    <meta property="title-type" refines="#title">main</meta>
    <meta property="display-seq" refines="#title">1</meta>
    <dc:title id="subtitle">${subtitle}</dc:title>
    <meta property="title-type" refines="#subtitle">subtitle</meta>
    <meta property="display-seq" refines="#subtitle">2</meta>
    </#if>
    
    <#if authors?has_content>
      <#list authors as author>
    <dc:creator id="creator${author?index}">${author}</dc:creator>
    <meta property="role" refines="#creator${author?index}" scheme="marc:relators">aut</meta>
      </#list>
    </#if>
    
    <dc:language>${language!""}</dc:language>
    
    <#if publisher?has_content>
    <dc:publisher>${publisher}</dc:publisher>
    </#if>
    
    <#if publishedDate?has_content>
    <dc:date>${publishedDate}</dc:date>
    </#if>
    
    <#if description?has_content>
    <dc:description>${description}</dc:description>
    </#if>
    
    <#-- Series metadata -->
    <#if seriesName?has_content>
    <meta property="belongs-to-collection" id="series">${seriesName}</meta>
    <meta property="collection-type" refines="#series">series</meta>
      <#if seriesNumber?has_content>
    <meta property="group-position" refines="#series">${seriesNumber}</meta>
      </#if>
    </#if>
    
    <#-- Identifiers -->
    <dc:identifier id="BookID">${identifier!""}</dc:identifier>
    <#if isbn13?has_content>
    <dc:identifier id="isbn13">${isbn13}</dc:identifier>
    <meta refines="#isbn13" property="identifier-type" scheme="onix:codelist5">15</meta>
    </#if>
    <#if isbn10?has_content>
    <dc:identifier id="isbn10">${isbn10}</dc:identifier>
    <meta refines="#isbn10" property="identifier-type" scheme="onix:codelist5">02</meta>
    </#if>
    <#if asin?has_content>
    <dc:identifier id="asin">${asin}</dc:identifier>
    <meta refines="#asin" property="identifier-type">ASIN</meta>
    </#if>
    <#if goodreadsId?has_content>
    <dc:identifier id="goodreads">${goodreadsId}</dc:identifier>
    <meta refines="#goodreads" property="identifier-type">GOODREADS</meta>
    </#if>
    
    <#-- Categories/Subjects -->
    <#if categories?has_content>
      <#list categories as category>
    <dc:subject>${category}</dc:subject>
      </#list>
    </#if>
    
    <#-- Tags as additional subjects -->
    <#if tags?has_content>
      <#list tags as tag>
    <dc:subject>${tag}</dc:subject>
      </#list>
    </#if>
    
    <#if pageCount?has_content>
    <meta property="schema:numberOfPages">${pageCount}</meta>
    </#if>
    
    <#-- EPUB 3.0 required metadata -->
    <meta property="dcterms:modified">${modified!""}</meta>
    <meta name="cover" content="cover" />
    
    <#-- Comic-specific metadata -->
    <meta property="rendition:layout">pre-paginated</meta>
    <meta property="rendition:orientation">auto</meta>
    <meta property="rendition:spread">landscape</meta>
  </metadata>

  <manifest>
    <item id="cover" href="${coverImagePath}" media-type="image/jpeg" properties="cover-image" />
    <item id="ncx" href="${tocNcxPath}" media-type="application/x-dtbncx+xml" />
    <item id="nav" href="${navXhtmlPath}" properties="nav" media-type="application/xhtml+xml" />

    <#-- Loop over the content file groups and emit the two items per entry -->
    <#list contentFileGroups as file>
      <item id="${'page_' + file.contentKey}" href="${file.htmlPath}" media-type="application/xhtml+xml" />
      <item id="${'img_' + file.contentKey}" href="${file.imagePath}" media-type="image/jpeg" />
    </#list>

    <item id="css" href="${stylesheetCssPath}" media-type="text/css" />
  </manifest>

  <spine page-progression-direction="ltr" toc="ncx">
    <#list contentFileGroups as file>
      <itemref idref="page_${file.contentKey}" properties="page-spread-center" />
    </#list>
  </spine>
</package>