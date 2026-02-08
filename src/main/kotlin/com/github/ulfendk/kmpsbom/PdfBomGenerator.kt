package com.github.ulfendk.kmpsbom

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import java.io.File
import java.io.FileOutputStream

/**
 * Generates a PDF representation of a CycloneDX BOM from markdown content
 */
object PdfBomGenerator {
    
    /**
     * Generates a PDF file from markdown content
     * 
     * @param markdownContent The markdown content to convert
     * @param outputFile The output PDF file
     */
    fun generateFromMarkdown(markdownContent: String, outputFile: File) {
        // Convert markdown to HTML
        val html = convertMarkdownToHtml(markdownContent)
        
        // Convert HTML to PDF
        convertHtmlToPdf(html, outputFile)
    }
    
    private fun convertMarkdownToHtml(markdown: String): String {
        val parser = Parser.builder().build()
        val document = parser.parse(markdown)
        val renderer = HtmlRenderer.builder().build()
        val htmlBody = renderer.render(document)
        
        // Wrap in a complete XHTML document with styling for better PDF output
        // Note: Using XHTML (self-closing tags) for compatibility with openhtmltopdf
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8" />
                <style>
                    body {
                        font-family: 'Arial', 'Helvetica', sans-serif;
                        font-size: 10pt;
                        line-height: 1.5;
                        margin: 40px;
                        color: #333;
                    }
                    h1 {
                        color: #2c3e50;
                        border-bottom: 3px solid #3498db;
                        padding-bottom: 10px;
                        font-size: 24pt;
                    }
                    h2 {
                        color: #34495e;
                        border-bottom: 2px solid #95a5a6;
                        padding-bottom: 5px;
                        margin-top: 20px;
                        font-size: 18pt;
                    }
                    h3 {
                        color: #7f8c8d;
                        font-size: 14pt;
                        margin-top: 15px;
                    }
                    h4 {
                        color: #2c3e50;
                        font-size: 12pt;
                        margin-top: 10px;
                        margin-bottom: 5px;
                    }
                    ul {
                        margin: 10px 0;
                        padding-left: 20px;
                    }
                    li {
                        margin: 5px 0;
                    }
                    code {
                        background-color: #f4f4f4;
                        padding: 2px 4px;
                        border-radius: 3px;
                        font-family: 'Courier New', monospace;
                        font-size: 9pt;
                    }
                    strong {
                        color: #2c3e50;
                    }
                    @page {
                        size: A4;
                        margin: 20mm;
                    }
                </style>
            </head>
            <body>
                $htmlBody
            </body>
            </html>
        """.trimIndent()
    }
    
    private fun convertHtmlToPdf(html: String, outputFile: File) {
        FileOutputStream(outputFile).use { os ->
            val builder = PdfRendererBuilder()
            builder.withHtmlContent(html, null)
            builder.toStream(os)
            builder.run()
        }
    }
}
