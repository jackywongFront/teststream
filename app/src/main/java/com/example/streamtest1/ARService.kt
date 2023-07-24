package com.example.streamtest1

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.Display
import android.view.OrientationEventListener
import android.view.WindowManager
import androidx.core.util.Pair
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ARService : Service() {
    private var mMediaProjection: MediaProjection? = null
    private var mStoreDir: String? = null
    private var mImageReader: ImageReader? = null
    private var mHandler: Handler? = null
    private var mDisplay: Display? = null
    private var mVirtualDisplay: VirtualDisplay? = null
    private var mDensity = 0
    private var mWidth = 0
    private var mHeight = 0
    private var mRotation = 0
    private var mOrientationChangeCallback: OrientationChangeCallback? = null

//    private inner class ImageAvailableListener : ImageReader.OnImageAvailableListener {
//        override fun onImageAvailable(reader: ImageReader) {
//            var fos: FileOutputStream? = null
//            var bitmap: Bitmap? = null
//            try {
//                mImageReader!!.acquireLatestImage().use { image ->
//                    if (image != null) {
//                        val planes = image.planes
//                        val buffer = planes[0].buffer
//                        val pixelStride = planes[0].pixelStride
//                        val rowStride = planes[0].rowStride
//                        val rowPadding = rowStride - pixelStride * mWidth
//
//                        // create bitmap
//                        bitmap = Bitmap.createBitmap(
//                            mWidth + rowPadding / pixelStride,
//                            mHeight,
//                            Bitmap.Config.ARGB_8888
//                        )
//                        bitmap?.copyPixelsFromBuffer(buffer)
//
//                        // write bitmap to a file
//                        fos =
//                            FileOutputStream(mStoreDir + "/myscreen_" + IMAGES_PRODUCED + ".png")
//                        bitmap?.compress(Bitmap.CompressFormat.JPEG, 100, fos)
//                        IMAGES_PRODUCED++
//                        Log.e(
//                            TAG,
//                            "captured image: " + IMAGES_PRODUCED
//                        )
//                    }
//                }
//            } catch (e: Exception) {
//                e.printStackTrace()
//            } finally {
//                if (fos != null) {
//                    try {
//                        fos!!.close()
//                    } catch (ioe: IOException) {
//                        ioe.printStackTrace()
//                    }
//                }
//                if (bitmap != null) {
//                    bitmap!!.recycle()
//                }
//            }
//        }
//    }

    private inner class OrientationChangeCallback internal constructor(context: Context?) :
        OrientationEventListener(context) {
        override fun onOrientationChanged(orientation: Int) {
            val rotation = mDisplay!!.rotation
            if (rotation != mRotation) {
                mRotation = rotation
                try {
                    // clean up
                    if (mVirtualDisplay != null) mVirtualDisplay!!.release()
//                    if (mImageReader != null) mImageReader!!.setOnImageAvailableListener(null, null)

                    // re-create virtual display depending on device width / height
                    createVirtualDisplay()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private inner class MediaProjectionStopCallback : MediaProjection.Callback() {
        override fun onStop() {
            Log.e(TAG, "stopping projection.")
            mHandler!!.post {
                if (mVirtualDisplay != null) mVirtualDisplay!!.release()
//                if (mImageReader != null) mImageReader!!.setOnImageAvailableListener(null, null)
                if (mOrientationChangeCallback != null) mOrientationChangeCallback!!.disable()
                mMediaProjection!!.unregisterCallback(this@MediaProjectionStopCallback)
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        // create store dir
//        val dir = Environment
//            .getExternalStoragePublicDirectory(
//                Environment
//                .DIRECTORY_DOWNLOADS)
//        val externalFilesDir = getExternalFilesDir(null)
//        if (externalFilesDir != null) {
//            mStoreDir = externalFilesDir.absolutePath + "/screenshots/"
//            val storeDirectory = File(mStoreDir)
//            if (!storeDirectory.exists()) {
//                val success = storeDirectory.mkdirs()
//                if (!success) {
//                    Log.e(TAG, "failed to create file storage directory.")
//                    stopSelf()
//                }
//            }
//        } else {
//            Log.e(TAG, "failed to create file storage directory, getExternalFilesDir is null.")
//            stopSelf()
//        }
        Log.d("a","create")

        // start capture handling thread
        object : Thread() {
            override fun run() {
                Looper.prepare()
                mHandler = Handler()
                Looper.loop()
            }
        }.start()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.d("a","startCommand")
        if (isStartCommand(intent)) {
            // create notification
//            val notification: Pair<Int, Notification> = NotificationUtils.getNotification(this)
//            startForeground(notification.first, notification.second)
            // start projection
            val resultCode = intent.getIntExtra(RESULT_CODE, Activity.RESULT_CANCELED)
            val data = intent.getParcelableExtra<Intent>(DATA)
            startProjection(resultCode, data)
        } else if (isStopCommand(intent)) {
            stopProjection()
            stopSelf()
        } else {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startProjection(resultCode: Int, data: Intent?) {
        val mpManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        Log.d("a","start Projection")
        if (mMediaProjection == null) {
            mMediaProjection = mpManager.getMediaProjection(resultCode, data!!)
            if (mMediaProjection != null) {
                // display metrics
                mDensity = Resources.getSystem().displayMetrics.densityDpi
                val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
                mDisplay = windowManager.defaultDisplay

                // create virtual display depending on device width / height
//                createVirtualDisplay()
                startStreaming()
                // register orientation change callback
                mOrientationChangeCallback = OrientationChangeCallback(this)
                if (mOrientationChangeCallback!!.canDetectOrientation()) {
                    mOrientationChangeCallback!!.enable()
                }

                // register media projection stop callback
                mMediaProjection!!.registerCallback(MediaProjectionStopCallback(), mHandler)
            }
        }
    }
    lateinit var mediaRecorder : MediaRecorder

    private fun startStreaming(){
        mediaRecorder = MediaRecorder()
        val displayMetrics = resources.displayMetrics
        val frameWidth = displayMetrics.widthPixels
        val frameHeight = 2722
        Log.d("stream",frameHeight.toString())
        var fdPair : Array<ParcelFileDescriptor> = try {
            ParcelFileDescriptor.createPipe()
        } catch (e: IOException) {
            e.printStackTrace()
            return Service.START_NOT_STICKY as Unit
        }
        Log.d("stream","fd");
        //get a handle to your read and write fd objects.
//        readFD = fdPair[0]!!
//        writeFD = fdPair[1]!!
        //do ffmpeg
//        val command = "-f ${readFD.fileDescriptor} -pixel_format yuv420p -video_size ${frameWidth}x${frameHeight} -i - -c:v libx264 -preset ultrafast -tune zerolatency -f flv rtmp://192.168.0.119/live/livestream" // Replace with your RTMP server URL and stream key
//
//        FFmpegKit.execute(command)
        Log.w("stream","mr");

        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);

        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT)
        mediaRecorder.setVideoEncodingBitRate(44100)
        mediaRecorder.setVideoSize(frameWidth,frameHeight)
        val filepath : String = Environment
            .getExternalStoragePublicDirectory(Environment
                .DIRECTORY_DOWNLOADS).absolutePath
        val filemp : String = "/videotest.mp4"
//        mediaRecorder.setOutputFile(writeFD.fileDescriptor)
        mediaRecorder.setOutputFile(filepath.plus(filemp));
//        mediaRecorder.setOutputFile(testvid);


        try{
            mediaRecorder.prepare()
            mVirtualDisplay = mMediaProjection?.createVirtualDisplay("vd",frameWidth,frameHeight,displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,mediaRecorder.surface,null,null)
        }

        catch(e : IOException){
            Log.d("e", "IOException preparing MediaRecorder: " + e.message);

            e.printStackTrace()
            return
        }
        mediaRecorder.start();
    }


    private fun stopProjection() {
        if (mHandler != null) {
            mHandler!!.post {
                if (mMediaProjection != null) {
                    mMediaProjection!!.stop()
                }
                if(mediaRecorder !=null){
                    mediaRecorder.stop()
                }
            }
        }
    }

    @SuppressLint("WrongConstant")
    private fun createVirtualDisplay() {
        // get width and height
        mWidth = Resources.getSystem().displayMetrics.widthPixels
        mHeight = Resources.getSystem().displayMetrics.heightPixels

        // start capture reader
        mImageReader = ImageReader.newInstance(mWidth, mHeight, PixelFormat.RGBA_8888, 2)
        mVirtualDisplay = mMediaProjection!!.createVirtualDisplay(
            SCREENCAP_NAME, mWidth, mHeight,
            mDensity,
            virtualDisplayFlags, mImageReader!!.surface, null, mHandler
        )
//        mImageReader!!.setOnImageAvailableListener(ImageAvailableListener(), mHandler)
    }

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val RESULT_CODE = "RESULT_CODE"
        private const val DATA = "DATA"
        private const val ACTION = "ACTION"
        private const val START = "START"
        private const val STOP = "STOP"
        private const val SCREENCAP_NAME = "screencap"
        private var IMAGES_PRODUCED = 0
        fun getStartIntent(context: Context?, resultCode: Int, data: Intent?): Intent {
            val intent = Intent(context, this::class.java)
            intent.putExtra(ACTION, START)
            intent.putExtra(RESULT_CODE, resultCode)
            intent.putExtra(DATA, data)
            return intent
        }

        fun getStopIntent(context: Context?): Intent {
            val intent = Intent(context, this::class.java)
            intent.putExtra(ACTION, STOP)
            return intent
        }

        private fun isStartCommand(intent: Intent): Boolean {
            return (intent.hasExtra(RESULT_CODE) && intent.hasExtra(DATA)
                    && intent.hasExtra(ACTION)) && intent.getStringExtra(ACTION) == START
        }

        private fun isStopCommand(intent: Intent): Boolean {
            return (intent.hasExtra(ACTION) && intent.getStringExtra(ACTION) == STOP)
        }

        private val virtualDisplayFlags: Int
            private get() = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
    }
}