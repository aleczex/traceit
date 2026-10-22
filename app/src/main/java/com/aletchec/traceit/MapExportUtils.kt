package com.aletchec.traceit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.pdf.PdfDocument
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.io.FileOutputStream

object MapExportUtils {

    private fun createCircleMarker(
        context: Context,
        mapView: MapView,
        position: GeoPoint,
        fillColor: Int,
        strokeColor: Int,
        title: String
    ): Marker {
        val size = 28
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val fillPaint = Paint().apply {
            isAntiAlias = true
            color = fillColor
            style = Paint.Style.FILL
        }
        val strokePaint = Paint().apply {
            isAntiAlias = true
            color = strokeColor
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        val radius = (size / 2f) - 2f
        val cx = size / 2f
        val cy = size / 2f

        canvas.drawCircle(cx, cy, radius, fillPaint)
        canvas.drawCircle(cx, cy, radius, strokePaint)

        val drawable = BitmapDrawable(context.resources, bitmap)

        return Marker(mapView).apply {
            this.position = position
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            this.title = title
            icon = drawable
        }
    }

    fun prepareMapForExport(
        context: Context,
        mapView: MapView,
        points: List<GpxManager.TrackPoint>
    ): Runnable {
        val previousOverlays = ArrayList(mapView.overlays)
        val previousCenter = mapView.mapCenter
        val previousZoom = mapView.zoomLevelDouble

        val validPoints = points.filter { !it.isIgnored }
        mapView.overlays.clear()

        if (validPoints.isNotEmpty()) {
            val geoPoints = validPoints.map { GeoPoint(it.latitude, it.longitude) }

            val polyline = Polyline().apply {
                setPoints(geoPoints)
                outlinePaint.color = Color.BLUE
                outlinePaint.strokeWidth = 5f
            }
            mapView.overlays.add(polyline)

            val startMarker = createCircleMarker(
                context,
                mapView,
                geoPoints.first(),
                fillColor = Color.GREEN,
                strokeColor = Color.WHITE,
                title = context.getString(R.string.start_marker_title)
            )
            mapView.overlays.add(startMarker)

            val endMarker = createCircleMarker(
                context,
                mapView,
                geoPoints.last(),
                fillColor = Color.RED,
                strokeColor = Color.WHITE,
                title = context.getString(R.string.latest_position_marker_title)
            )
            mapView.overlays.add(endMarker)

            if (geoPoints.size == 1) {
                mapView.controller.setCenter(geoPoints.first())
                mapView.controller.setZoom(16.0)
            } else {
                val boundingBox = BoundingBox.fromGeoPoints(geoPoints)
                mapView.zoomToBoundingBox(boundingBox, false, 80)
                if (mapView.zoomLevelDouble > 16.5) {
                    mapView.controller.setZoom(16.5)
                    mapView.controller.setCenter(boundingBox.centerWithDateLine)
                }
            }
        }

        mapView.invalidate()

        return Runnable {
            mapView.overlays.clear()
            mapView.overlays.addAll(previousOverlays)
            mapView.controller.setCenter(previousCenter)
            mapView.controller.setZoom(previousZoom)
            mapView.invalidate()
        }
    }

    fun captureMapView(mapView: MapView): Bitmap? {
        return try {
            val width = mapView.width
            val height = mapView.height
            if (width <= 0 || height <= 0) return null
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            mapView.draw(canvas)
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    fun generateAnnotatedMapBitmap(
        context: Context,
        mapView: MapView,
        fileName: String,
        distanceStr: String,
        durationStr: String,
        startTimeStr: String,
        endTimeStr: String
    ): Bitmap? {
        val mapBitmap = captureMapView(mapView) ?: return null
        return try {
            val headerHeight = 120
            val width = mapBitmap.width
            val height = mapBitmap.height + headerHeight

            val annotatedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(annotatedBitmap)
            canvas.drawColor(Color.WHITE)

            val paint = Paint().apply {
                isAntiAlias = true
                color = Color.BLACK
            }

            // Draw File Name Header
            paint.textSize = 20f
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            canvas.drawText(fileName, 24f, 35f, paint)

            // Draw Stats line 1: Distance & Duration
            paint.textSize = 14f
            paint.typeface = android.graphics.Typeface.DEFAULT
            val distLabel = context.getString(R.string.distance_label, distanceStr)
            val timeLabel = context.getString(R.string.total_time_label, durationStr)
            canvas.drawText("$distLabel  |  $timeLabel", 24f, 65f, paint)

            // Draw Stats line 2: Start & End Time
            val startLabel = context.getString(R.string.start_time_label, startTimeStr)
            val endLabel = context.getString(R.string.end_time_label, endTimeStr)
            canvas.drawText("$startLabel  |  $endLabel", 24f, 92f, paint)

            // Draw Map Bitmap below header
            canvas.drawBitmap(mapBitmap, 0f, headerHeight.toFloat(), null)

            annotatedBitmap
        } catch (e: Exception) {
            mapBitmap
        }
    }

    fun generatePdf(
        context: Context,
        mapView: MapView,
        fileName: String,
        distanceStr: String,
        durationStr: String,
        startTimeStr: String,
        endTimeStr: String
    ): File? {
        val mapBitmap = captureMapView(mapView) ?: return null
        return try {
            val pdfDocument = PdfDocument()
            // A4 dimensions in points: 595 x 842
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            val paint = Paint().apply {
                isAntiAlias = true
                color = Color.BLACK
            }

            // Draw File Name Header
            paint.textSize = 18f
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            canvas.drawText(fileName, 40f, 45f, paint)

            // Draw Stats Line 1
            paint.textSize = 12f
            paint.typeface = android.graphics.Typeface.DEFAULT
            val distLabel = context.getString(R.string.distance_label, distanceStr)
            val timeLabel = context.getString(R.string.total_time_label, durationStr)
            canvas.drawText("$distLabel  |  $timeLabel", 40f, 70f, paint)

            // Draw Stats Line 2
            val startLabel = context.getString(R.string.start_time_label, startTimeStr)
            val endLabel = context.getString(R.string.end_time_label, endTimeStr)
            canvas.drawText("$startLabel  |  $endLabel", 40f, 90f, paint)

            // Draw Map Bitmap scaled nicely
            val availableWidth = 515f // 595 - 80 margin
            val availableHeight = 680f // 842 - 140 top/bottom margin
            val mapAspect = mapBitmap.height.toFloat() / mapBitmap.width.toFloat()

            var drawWidth = availableWidth
            var drawHeight = drawWidth * mapAspect
            if (drawHeight > availableHeight) {
                drawHeight = availableHeight
                drawWidth = drawHeight / mapAspect
            }

            val left = 40f + (availableWidth - drawWidth) / 2f
            val top = 110f

            val srcRect = android.graphics.Rect(0, 0, mapBitmap.width, mapBitmap.height)
            val destRect = android.graphics.RectF(left, top, left + drawWidth, top + drawHeight)
            canvas.drawBitmap(mapBitmap, srcRect, destRect, null)

            pdfDocument.finishPage(page)

            val file = File(context.cacheDir, "track_export_${System.currentTimeMillis()}.pdf")
            FileOutputStream(file).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()
            file
        } catch (e: Exception) {
            null
        }
    }

    fun printDocument(context: Context, pdfFile: File, jobName: String) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: android.os.CancellationSignal?,
                callback: PrintDocumentAdapter.LayoutResultCallback?,
                extras: android.os.Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val pInfo = PrintDocumentInfo.Builder(jobName)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(1)
                    .build()
                callback?.onLayoutFinished(pInfo, true)
            }

            override fun onWrite(
                pages: Array<out android.print.PageRange>?,
                destination: android.os.ParcelFileDescriptor?,
                cancellationSignal: android.os.CancellationSignal?,
                callback: PrintDocumentAdapter.WriteResultCallback?
            ) {
                try {
                    pdfFile.inputStream().use { input ->
                        FileOutputStream(destination?.fileDescriptor).use { output ->
                            input.copyTo(output)
                        }
                    }
                    callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.localizedMessage)
                }
            }
        }
        printManager.print(jobName, adapter, PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
    }
}
