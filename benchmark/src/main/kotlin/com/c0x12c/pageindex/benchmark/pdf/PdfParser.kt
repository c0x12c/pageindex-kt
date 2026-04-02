package com.c0x12c.pageindex.benchmark.pdf

import com.c0x12c.pageindex.api.model.ParsedPage
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File

object PdfParser {

    fun parse(file: File): List<ParsedPage> {
        require(file.exists()) { "PDF not found: ${file.absolutePath}" }

        Loader.loadPDF(file).use { doc ->
            val stripper = PDFTextStripper()
            return (1..doc.numberOfPages).map { pageNum ->
                stripper.startPage = pageNum
                stripper.endPage = pageNum
                ParsedPage(pageNum, stripper.getText(doc))
            }
        }
    }
}
