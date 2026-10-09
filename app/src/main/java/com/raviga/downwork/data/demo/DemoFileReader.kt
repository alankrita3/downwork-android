package com.raviga.downwork.data.demo

import com.raviga.downwork.data.api.FileTypes
import java.io.File
import java.util.zip.ZipFile

/**
 * Best-effort text extraction for the demo backend, so uploads can be shown
 * without the real API. Text, Markdown, RTF and Word files are read for real;
 * PDFs need the server, so the demo says so and hands back a sample.
 */
internal object DemoFileReader {

    data class Read(val text: String, val pageCount: Int?)

    fun read(file: File, contentType: String): Read = when (contentType) {
        FileTypes.TXT, FileTypes.MD -> Read(file.readText(), null)
        FileTypes.RTF -> Read(stripRtf(file.readText()), null)
        FileTypes.DOCX -> Read(readDocx(file), null)
        FileTypes.PDF -> Read(PDF_NOTE, pdfPages(file))
        else -> Read("", null)
    }

    private fun readDocx(file: File): String = runCatching {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("word/document.xml") ?: return@use ""
            val xml = zip.getInputStream(entry).bufferedReader().readText()
            xml.replace(Regex("</w:p>"), "\n")
                .replace(Regex("<w:tab/>"), " ")
                .replace(Regex("<[^>]+>"), "")
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&apos;", "'")
                .lines().joinToString("\n") { it.trim() }
                .replace(Regex("\n{3,}"), "\n\n")
                .trim()
        }
    }.getOrDefault("")

    private fun stripRtf(rtf: String): String = rtf
        .replace(Regex("\\{\\\\\\*[^{}]*\\}"), "")
        .replace(Regex("\\\\par[d]?\\b ?"), "\n")
        .replace(Regex("\\\\'[0-9a-fA-F]{2}"), "")
        .replace(Regex("\\\\[a-zA-Z]+-?\\d* ?"), "")
        .replace(Regex("[{}]"), "")
        .lines().joinToString("\n") { it.trim() }
        .trim()

    private fun pdfPages(file: File): Int? = runCatching {
        Regex("/Type\\s*/Page[^s]").findAll(file.readText(Charsets.ISO_8859_1)).count().takeIf { it > 0 }
    }.getOrNull()

    private const val PDF_NOTE =
        "Demo mode can't read the text inside a PDF; the real backend does. Here is a sample instead. " +
            "We run a chain of three bakeries in Pune and want an ordering app. Customers browse cakes and breads, " +
            "customise a cake message, pick a pickup slot at their nearest branch and pay with UPI or card. " +
            "Each branch needs a tablet view of today's orders. The owner wants a weekly sales report by branch."
}
