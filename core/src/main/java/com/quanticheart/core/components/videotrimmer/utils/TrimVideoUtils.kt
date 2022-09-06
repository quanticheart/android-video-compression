package com.quanticheart.core.components.videotrimmer.utils

import android.net.Uri
import android.util.Log
import com.googlecode.mp4parser.authoring.Track
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator
import com.googlecode.mp4parser.authoring.tracks.AppendTrack
import com.googlecode.mp4parser.authoring.tracks.CroppedTrack
import com.quanticheart.core.components.videotrimmer.interfaces.OnTrimVideoListener
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

object TrimVideoUtils {
    private val TAG = TrimVideoUtils::class.java.simpleName

    @JvmStatic
    @Throws(IOException::class)
    fun startTrim(
        src: File,
        dst: String,
        startMs: Long,
        endMs: Long,
        callback: OnTrimVideoListener
    ) {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "video_$timeStamp.mp4"
        val filePath = dst + fileName
        val file = File(filePath)
        file.parentFile?.mkdirs()
        Log.d(TAG, "Generated file path $filePath")
        genVideoUsingMp4Parser(src, file, startMs, endMs, callback)
    }

    @Throws(IOException::class)
    private fun genVideoUsingMp4Parser(
        src: File,
        dst: File,
        startMs: Long,
        endMs: Long,
        callback: OnTrimVideoListener
    ) {
        val movie = MovieCreator.build(src.absolutePath)
        val tracks = movie.tracks
        movie.tracks = LinkedList()
        var startTime1 = (startMs / 1000).toDouble()
        var endTime1 = (endMs / 1000).toDouble()
        var timeCorrected = false
        for (track in tracks) {
            if (track.syncSamples != null && track.syncSamples.isNotEmpty()) {
                if (timeCorrected) {
                    /*  This exception here could be a false positive in case we have multiple tracks
                     with sync samples at exactly the same positions. E.g. a single movie containing
                     multiple qualities of the same video (Microsoft Smooth Streaming file)*/
                    throw RuntimeException("The startTime has already been corrected by another track with SyncSample. Not Supported.")
                }
                startTime1 = correctTimeToSyncSample(track, startTime1, false)
                endTime1 = correctTimeToSyncSample(track, endTime1, true)
                timeCorrected = true
            }
        }
        for (track in tracks) {
            var currentTime = 0.0
            var lastTime = -1.0
            var startSample1: Long = -1
            var endSample1: Long = -1
            for ((currentSample, i) in track.sampleDurations.indices.withIndex()) {
                val delta = track.sampleDurations[i]
                if (currentTime > lastTime && currentTime <= startTime1) {
                    /*current sample is still before the new starttime*/
                    startSample1 = currentSample.toLong()
                }
                if (currentTime > lastTime && currentTime <= endTime1) {
                    /* current sample is after the new start time and still before the new endtime*/
                    endSample1 = currentSample.toLong()
                }
                lastTime = currentTime
                currentTime += delta.toDouble() / track.trackMetaData.timescale.toDouble()
            }
            movie.addTrack(AppendTrack(CroppedTrack(track, startSample1, endSample1)))
        }
        dst.parentFile?.mkdirs()
        if (!dst.exists()) {
            dst.createNewFile()
        }
        val out = DefaultMp4Builder().build(movie)
        val fos = FileOutputStream(dst)
        val fc = fos.channel
        out.writeContainer(fc)
        fc.close()
        fos.close()
        callback.getResult(Uri.parse(dst.toString()))
    }

    private fun correctTimeToSyncSample(track: Track, cutHere: Double, next: Boolean): Double {
        val timeOfSyncSamples = DoubleArray(track.syncSamples.size)
        var currentTime = 0.0
        for ((currentSample, i) in track.sampleDurations.indices.withIndex()) {
            val delta = track.sampleDurations[i]
            if (Arrays.binarySearch(track.syncSamples, (currentSample + 1).toLong()) >= 0) {
                timeOfSyncSamples[Arrays.binarySearch(
                    track.syncSamples,
                    (currentSample + 1).toLong()
                )] =
                    currentTime
            }
            currentTime += delta.toDouble() / track.trackMetaData.timescale.toDouble()
        }
        var previous = 0.0
        for (timeOfSyncSample in timeOfSyncSamples) {
            if (timeOfSyncSample > cutHere) {
                return if (next) {
                    timeOfSyncSample
                } else {
                    previous
                }
            }
            previous = timeOfSyncSample
        }
        return timeOfSyncSamples[timeOfSyncSamples.size - 1]
    }
}