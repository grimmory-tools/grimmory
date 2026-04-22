<#ftl output_format="XML" encoding="UTF-8">
<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head>
    <title>Navigation</title>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <style>
        body { font-family: sans-serif; margin: 20px; }
        nav h1 { color: #333; }
        nav ol { list-style-type: decimal; }
        nav a { text-decoration: none; color: #0066cc; }
        nav a:hover { text-decoration: underline; }
    </style>
</head>
<body>
    <nav epub:type="toc" id="toc">
        <h1>Table of Contents</h1>
        <ol>
            <#if contentFileGroups?has_content>
                <#list contentFileGroups as file>
            <li>
                <a href="${file.htmlPath?replace('OEBPS/', '')}">Page ${file?counter}</a>
            </li>
                </#list>
            <#else>
            <li>
                <a href="Text/page-0001.xhtml">${title!'Unknown Comic'}</a>
            </li>
            </#if>
        </ol>
    </nav>
    
    <nav epub:type="page-list" id="page-list">
        <h1>List of Pages</h1>
        <ol>
            <#if contentFileGroups?has_content>
                <#list contentFileGroups as file>
            <li>
                <a href="${file.htmlPath?replace('OEBPS/', '')}" epub:type="pagebreak">${file?counter}</a>
            </li>
                </#list>
            </#if>
        </ol>
    </nav>
</body>
</html>