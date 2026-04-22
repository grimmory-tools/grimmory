<#ftl output_format="XML" encoding="UTF-8">
<?xml version="1.0" encoding="UTF-8"?>
<ncx version="2005-1" xml:lang="${language!'en'}" xmlns="http://www.daisy.org/z3986/2005/ncx/">
    <head>
        <meta name="dtb:uid" content="${identifier}" />
        <meta name="dtb:depth" content="1" />
        <meta name="dtb:totalPageCount" content="${contentFileGroups?size}" />
        <meta name="dtb:maxPageNumber" content="${contentFileGroups?size}" />
        <meta name="generated" content="true" />
    </head>
    <docTitle>
        <text>${title!'Unknown Comic'}</text>
    </docTitle>
    <navMap>
        <navPoint id="cover" playOrder="1">
            <navLabel>
                <text>${title!'Unknown Comic'}</text>
            </navLabel>
            <#if contentFileGroups?has_content>
                <content src="${contentFileGroups[0].htmlPath?replace('OEBPS/', '')}" />
            </#if>
        </navPoint>
    </navMap>
</ncx>