package com.jumastappworks.mapstead.data.reports

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PropertyReportPdfGenerator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val pageWidth = 595 // A4 width in points
    private val pageHeight = 842 // A4 height
    private val margin = 40f
    private val contentWidth = pageWidth - (margin * 2)

    private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
    private val dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.getDefault())

    suspend fun generate(
        document: PropertyReportDocument,
        outputFile: File
    ): PdfGenerationResult = withContext(Dispatchers.IO) {
        val pdfDocument = PdfDocument()
        val writer = PdfPageWriter(pdfDocument)
        
        try {
            val reportsDir = File(context.cacheDir, "reports")
            if (!reportsDir.exists()) reportsDir.mkdirs()
            if (!reportsDir.isDirectory || !isFileInsideDirectory(reportsDir, outputFile)) {
                return@withContext PdfGenerationResult.STORAGE_FAILURE
            }

            // Header
            writer.drawText(document.title, 18f, isBold = true)
            writer.drawSpace(4f)
            writer.drawWrappedText(document.propertyName, 14f, isBold = true, color = Color.DKGRAY)
            val generatedDateStr = dateTimeFormatter.format(document.generatedAt.atZone(ZoneId.systemDefault()))
            writer.drawText("Generated: $generatedDateStr", 9f, isItalic = true, color = Color.GRAY)
            writer.drawSpace(20f)

            document.sections.forEach { section ->
                renderSection(writer, section)
            }

            writer.finishCurrentPage()
            
            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }
            PdfGenerationResult.SUCCESS
        } catch (c: CancellationException) {
            if (outputFile.exists()) outputFile.delete()
            throw c
        } catch (e: Exception) {
            if (outputFile.exists()) outputFile.delete()
            PdfGenerationResult.ERROR
        } finally {
            pdfDocument.close()
        }
    }

    private fun renderSection(writer: PdfPageWriter, section: PropertyReportDocumentSection) {
        writer.ensureSpace(60f)
        writer.drawHorizontalRule()
        
        when (section) {
            is PropertyReportDocumentSection.PropertyProfile -> {
                writer.drawText("Property Profile", 12f, isBold = true)
                writer.drawField("Type", section.type)
                writer.drawField("Address", section.address)
                section.parcelNumber?.let { writer.drawField("Parcel #", it) }
                section.acreage?.let { writer.drawField("Acreage", it) }
                section.description?.let { writer.drawWrappedText("Description: $it", 10f) }
            }
            is PropertyReportDocumentSection.Infrastructure -> {
                writer.drawText("Systems & Equipment", 12f, isBold = true)
                if (section.items.isEmpty()) {
                    writer.drawText("No systems or equipment documented.", 10f, isItalic = true)
                } else {
                    section.items.forEach { item ->
                        writer.ensureSpace(30f)
                        val emergency = if (item.isEmergency) " [EMERGENCY]" else ""
                        writer.drawWrappedText("${item.name} (${item.category})$emergency", 10f, isBold = true)
                        writer.drawWrappedText("Status: ${item.status} | ${item.manufacturer ?: "Unknown"} ${item.model ?: ""}", 9f, color = Color.GRAY, indent = 10f)
                    }
                }
            }
            is PropertyReportDocumentSection.MaintenanceHistory -> {
                writer.drawText("Maintenance History", 12f, isBold = true)
                if (section.records.isEmpty()) {
                    writer.drawText("No maintenance history records found for the selected range.", 10f, isItalic = true)
                } else {
                    section.records.forEach { record ->
                        writer.ensureSpace(50f)
                        val costStr = formatCurrency(record.cost, record.currencyCode)
                        val dateStr = dateFormatter.format(record.date)
                        writer.drawWrappedText("$dateStr: ${record.title}$costStr", 10f, isBold = true)
                        writer.drawWrappedText("Item: ${record.infrastructureName ?: "General"}", 9f, color = Color.GRAY, indent = 10f)
                        if (record.category.isNotBlank()) {
                            writer.drawWrappedText("Category: ${record.category}", 9f, color = Color.GRAY, indent = 10f)
                        }
                        record.notes?.let { if (it.isNotBlank()) writer.drawWrappedText(it, 9f, isItalic = true, indent = 10f) }
                    }
                }
            }
            is PropertyReportDocumentSection.UpcomingMaintenance -> {
                writer.drawText("Upcoming Maintenance", 12f, isBold = true)
                if (section.tasks.isEmpty()) {
                    writer.drawText("No upcoming tasks found.", 10f, isItalic = true)
                } else {
                    section.tasks.forEach { task ->
                        writer.ensureSpace(30f)
                        val status = if (task.isEnabled) "Scheduled" else "Disabled"
                        val dateStr = dateFormatter.format(task.dueDate)
                        writer.drawWrappedText("$dateStr: ${task.taskTitle} ($status)", 10f, isBold = true)
                        writer.drawWrappedText("Item: ${task.itemName ?: "General"}", 9f, color = Color.GRAY, indent = 10f)
                    }
                }
            }
            is PropertyReportDocumentSection.MapSummary -> {
                writer.drawText("Map & GIS Summary", 12f, isBold = true)
                writer.drawText("Plans: ${section.planCount} | Layers: ${section.layerCount}", 10f)
                writer.drawText("Points: ${section.pointCount} | Lines: ${section.lineCount} | Areas: ${section.areaCount}", 10f)
            }
            is PropertyReportDocumentSection.AttachmentSummary -> {
                val s = section.summary
                writer.drawText("Attachment Summary", 12f, isBold = true)
                writer.drawText("Total Files: ${s.totalCount} (Photos: ${s.photoCount}, Documents: ${s.documentCount}, Others: ${s.otherCount})", 10f)
                writer.drawText("Property: ${s.propertyLevelCount} | Systems: ${s.infrastructureCount} | Records: ${s.maintenanceCount} | Map: ${s.mapFeatureCount}", 9f, color = Color.GRAY)
            }
        }
        writer.drawSpace(16f)
    }

    private fun formatCurrency(amount: Double?, code: String?): String {
        if (amount == null) return ""
        return try {
            val currency = Currency.getInstance(code ?: return " | Cost: ${String.format(Locale.getDefault(), "%.2f", amount)}")
            val formatter = NumberFormat.getCurrencyInstance(Locale.getDefault()).apply { this.currency = currency }
            " | Cost: ${formatter.format(amount)}"
        } catch (e: Exception) {
            " | Cost: ${String.format(Locale.getDefault(), "%.2f", amount)}${if (!code.isNullOrBlank()) " ($code)" else ""}"
        }
    }

    private inner class PdfPageWriter(private val document: PdfDocument) {
        private var _currentPage: PdfDocument.Page? = null
        var pageNumber = 0
        var currentY = margin

        val currentPage: PdfDocument.Page
            get() {
                if (_currentPage == null) startNewPage()
                return _currentPage!!
            }

        fun startNewPage() {
            finishCurrentPage()
            pageNumber++
            val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            _currentPage = document.startPage(info)
            currentY = margin
        }

        fun finishCurrentPage() {
            _currentPage?.let { 
                drawFooter(it.canvas, pageNumber)
                document.finishPage(it) 
                _currentPage = null
            }
        }

        fun ensureSpace(height: Float) {
            if (currentY + height > pageHeight - 60f) {
                startNewPage()
            }
        }

        fun drawText(text: String, size: Float, isBold: Boolean = false, isItalic: Boolean = false, color: Int = Color.BLACK, indent: Float = 0f): Float {
            val paint = createPaint(size, isBold, isItalic, color)
            ensureSpace(size + 2f)
            currentPage.canvas.drawText(text, margin + indent, currentY + size, paint)
            currentY += size + 4f
            return currentY
        }

        fun drawWrappedText(text: String, size: Float, isBold: Boolean = false, isItalic: Boolean = false, color: Int = Color.BLACK, indent: Float = 0f) {
            val paint = createPaint(size, isBold, isItalic, color)
            val maxWidth = contentWidth - indent
            
            // Handle newlines
            val paragraphs = text.split("\n")
            for (paragraph in paragraphs) {
                val words = paragraph.split(Regex("\\s+")).filter { it.isNotEmpty() }
                if (words.isEmpty()) {
                    drawSpace(size + 2f)
                    continue
                }

                var line = ""
                for (word in words) {
                    val testLine = if (line.isEmpty()) word else "$line $word"
                    if (paint.measureText(testLine) > maxWidth) {
                        if (line.isNotEmpty()) {
                            drawTextLine(line, paint, indent)
                        }
                        
                        // Handle very long words
                        if (paint.measureText(word) > maxWidth) {
                            var remainingWord = word
                            while (paint.measureText(remainingWord) > maxWidth) {
                                val splitIndex = findSplitIndex(remainingWord, paint, maxWidth)
                                drawTextLine(remainingWord.substring(0, splitIndex), paint, indent)
                                remainingWord = remainingWord.substring(splitIndex)
                            }
                            line = remainingWord
                        } else {
                            line = word
                        }
                    } else {
                        line = testLine
                    }
                }
                if (line.isNotEmpty()) drawTextLine(line, paint, indent)
            }
        }

        private fun drawTextLine(line: String, paint: Paint, indent: Float) {
            ensureSpace(paint.textSize + 2f)
            currentPage.canvas.drawText(line, margin + indent, currentY + paint.textSize, paint)
            currentY += paint.textSize + 4f
        }

        private fun findSplitIndex(word: String, paint: Paint, maxWidth: Float): Int {
            var low = 0
            var high = word.length
            var result = 1
            while (low <= high) {
                val mid = (low + high) / 2
                if (paint.measureText(word.substring(0, mid)) <= maxWidth) {
                    result = mid
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }
            return result
        }

        fun drawField(label: String, value: String) {
            ensureSpace(14f)
            val labelPaint = createPaint(10f, isBold = true)
            val labelWidth = labelPaint.measureText("$label: ")
            
            currentPage.canvas.drawText("$label: ", margin, currentY + 10f, labelPaint)
            
            // Draw value with potential wrapping if it's too long to fit on same line
            val remainingWidth = contentWidth - labelWidth
            val valuePaint = createPaint(10f)
            if (valuePaint.measureText(value) <= remainingWidth) {
                currentPage.canvas.drawText(value, margin + labelWidth, currentY + 10f, valuePaint)
                currentY += 14f
            } else {
                currentY += 12f // Move to next line for the wrapped value
                drawWrappedText(value, 10f, indent = 10f)
            }
        }

        fun drawHorizontalRule() {
            ensureSpace(10f)
            val paint = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }
            currentPage.canvas.drawLine(margin, currentY + 5f, pageWidth - margin, currentY + 5f, paint)
            currentY += 10f
        }

        fun drawSpace(h: Float) {
            currentY += h
        }

        private fun createPaint(size: Float, isBold: Boolean = false, isItalic: Boolean = false, color: Int = Color.BLACK): Paint {
            return Paint().apply {
                this.color = color
                this.textSize = size
                this.typeface = when {
                    isBold && isItalic -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
                    isBold -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isItalic -> Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                    else -> Typeface.DEFAULT
                }
                this.isAntiAlias = true
            }
        }

        private fun drawFooter(canvas: Canvas, pageNum: Int) {
            val paint = createPaint(8f, isItalic = true, color = Color.GRAY)
            val footerY = pageHeight - 20f
            canvas.drawText("Page $pageNum | Generated by Mapstead | Mapped positions are not survey-grade.", margin, footerY, paint)
        }
    }
}
