package com.networktoolbox.feature.webdiagnostics.export

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.ByteArrayOutputStream

/** Native, off-Main renderer of the same redacted document; no original result is accepted. */
object WebExportPdfRenderer {
    fun render(snapshot:WebExportDocument):ByteArray {
        val paint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color=Color.BLACK;textSize=11f;typeface=Typeface.create("sans-serif",Typeface.NORMAL)
        }
        val rows=snapshot.lines.flatMap {row->
            val layout=StaticLayout.Builder.obtain(row.text,0,row.text.length,paint,515)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build()
            (0 until layout.lineCount).map {i->
                WebExportLine(row.text.substring(layout.getLineStart(i),layout.getLineEnd(i)).trimEnd(),row.heading)
            }
        }.chunked(44).ifEmpty {listOf(emptyList())}
        val pdf=PdfDocument()
        try {
            rows.forEachIndexed {index,lines->
                val page=pdf.startPage(PdfDocument.PageInfo.Builder(595,842,index+1).create())
                page.canvas.drawColor(Color.WHITE)
                val title=Paint(paint).apply {textSize=14f;isFakeBoldText=true}
                page.canvas.drawText(snapshot.title,40f,44f,title)
                lines.forEachIndexed {i,line->
                    paint.isFakeBoldText=line.heading
                    page.canvas.drawText(line.text,40f,74f+i*16f,paint)
                }
                paint.isFakeBoldText=false
                page.canvas.drawText(if(snapshot.chinese) "第 ${index+1} / ${rows.size} 页" else "Page ${index+1} / ${rows.size}",40f,816f,paint)
                pdf.finishPage(page)
            }
            return ByteArrayOutputStream().use {pdf.writeTo(it);it.toByteArray()}
        } finally {pdf.close()}
    }
}
