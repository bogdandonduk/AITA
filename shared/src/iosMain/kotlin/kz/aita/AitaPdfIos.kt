@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package kz.aita

import kotlinx.cinterop.*
import platform.CoreGraphics.*
import platform.Foundation.*
import platform.UIKit.*
import platform.posix.memcpy

actual fun renderAitaPdfDocument(document: AitaPdfDocument): ByteArray {
    val fonts = mutableMapOf<AitaPdfStyle, UIFont>()
    fun font(style: AitaPdfStyle): UIFont = fonts.getOrPut(style) {
        if (style.bold) UIFont.boldSystemFontOfSize(style.size.toDouble()) else UIFont.systemFontOfSize(style.size.toDouble())
    }
    fun text(value: String, style: AitaPdfStyle): NSAttributedString = NSAttributedString.create(
        string = value,
        attributes = mapOf(NSFontAttributeName to font(style), NSForegroundColorAttributeName to UIColor.blackColor)
    )
    val pages = layoutAitaPdfDocument(document, { value, style -> text(value, style).size().useContents { width.toFloat() } }) { style ->
        font(style).let { -it.ascender.toFloat() to -it.descender.toFloat() }
    }
    val data = NSMutableData()
    UIGraphicsBeginPDFContextToData(data, CGRectMake(0.0, 0.0, document.width.toDouble(), document.maxHeight.toDouble()), null)
    try {
        pages.forEach { page ->
            UIGraphicsBeginPDFPageWithInfo(CGRectMake(0.0, 0.0, page.width.toDouble(), page.height.toDouble()), null)
            val context = UIGraphicsGetCurrentContext() ?: error("PDF context is unavailable")
            CGContextSetRGBFillColor(context, 1.0, 1.0, 1.0, 1.0)
            CGContextFillRect(context, CGRectMake(0.0, 0.0, page.width.toDouble(), page.height.toDouble()))
            page.lines.forEach { line ->
                val barcode = line.barcode
                if (barcode != null) {
                    CGContextSetRGBFillColor(context, 0.0, 0.0, 0.0, 1.0)
                    barcode.bars.forEach { bar ->
                        CGContextFillRect(context, CGRectMake((line.x + bar.x).toDouble(),
                            (line.baseline + bar.y).toDouble(), bar.width.toDouble(), bar.height.toDouble()))
                    }
                } else if (line.divider) {
                    CGContextSetRGBStrokeColor(context, 0.0, 0.0, 0.0, 1.0)
                    CGContextSetLineWidth(context, 0.6)
                    CGContextMoveToPoint(context, document.margin.toDouble(), line.baseline.toDouble())
                    CGContextAddLineToPoint(context, (page.width - document.margin).toDouble(), line.baseline.toDouble())
                    CGContextStrokePath(context)
                } else {
                    text(line.text, line.style).drawAtPoint(CGPointMake(line.x.toDouble(), line.baseline - font(line.style).ascender))
                }
            }
        }
    } finally { UIGraphicsEndPDFContext() }
    require(data.length <= Int.MAX_VALUE.toULong())
    return ByteArray(data.length.toInt()).also { result ->
        if (result.isNotEmpty()) result.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
    }
}
