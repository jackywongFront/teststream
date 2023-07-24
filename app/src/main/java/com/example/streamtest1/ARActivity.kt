package com.example.streamtest1

//import com.github.hiteshsondhi88.libffmpeg.ExecuteCallback
//import com.github.hiteshsondhi88.libffmpeg.FFmpeg


import ai.deepar.ar.ARErrorType
import ai.deepar.ar.AREventListener
import ai.deepar.ar.CameraResolutionPreset
import ai.deepar.ar.DeepAR
import ai.deepar.ar.DeepARImageFormat
import android.Manifest
import android.app.Activity
import android.app.Service
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.StrictMode
import android.text.format.DateFormat
import android.util.DisplayMetrics
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.FFmpegSessionCompleteCallback
import com.arthenica.ffmpegkit.LogCallback
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.SessionState
import com.arthenica.ffmpegkit.Statistics
import com.arthenica.ffmpegkit.StatisticsCallback
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.ExecutionException


class ARActivity : AppCompatActivity(), SurfaceHolder.Callback ,AREventListener {
    private var defaultLensFacing = CameraSelector.LENS_FACING_FRONT
    private var surfaceProvider: ARSurfaceProvider? = null
    private var lensFacing = defaultLensFacing
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var buffers: Array<ByteBuffer?>;
    private var currentBuffer = 0
    private var buffersInitialized = false
    val NUMBER_OF_BUFFERS: Int = 2
    val useExternalCameraTexture = true

    private var deepAR: DeepAR? = null

    private var currentMask = 0
    private var currentEffect = 0
    private var currentFilter = 0

    private var screenOrientation = 0

    lateinit var effects: ArrayList<String>

    private var recording = false
    private var currentSwitchRecording = false

    private var width = 0
    private var height = 0

    private var videoFile: File? = null

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var mediaProjection: MediaProjection
    private lateinit var mediaCodec: MediaCodec

    private lateinit var writeFD: ParcelFileDescriptor;
    private lateinit var readFD: ParcelFileDescriptor;
    private lateinit var fdPair: Array<ParcelFileDescriptor>;

    val startMediaProjection = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            mediaProjection = mediaProjectionManager
                .getMediaProjection(result.resultCode, result.data!!)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun executeMediaProjection() {
        mediaProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_CODE);
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d("a", "in")
        if (requestCode == REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            Log.d("a", "OK")
            startService(ARService.getStartIntent(this, resultCode, data))
        } else {
            Log.d("a", "no")

        }
//        }
//        if(this::mediaProjection.isInitialized)
//            startStreaming2()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ar)
        if (android.os.Build.VERSION.SDK_INT > 9) run {
            val policy: StrictMode.ThreadPolicy =
                StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy)
        }
        // Initialize camera view
//        cameraView = findViewById(R.id.cameraView)
//        cameraView.holder.addCallback(object : SurfaceHolder.Callback {
//            override fun surfaceCreated(holder: SurfaceHolder) {
//                // Start DeepAR rendering
//                deepAR.setRenderSurface(holder.surface, 400, 600)
//                deepAR.startCapture()
//            }
//
//            override fun surfaceChanged(
//                holder: SurfaceHolder,
//                format: Int,
//                width: Int,
//                height: Int
//            ) {
//            }
//
//            override fun surfaceDestroyed(holder: SurfaceHolder) {}
//        })
        if (checkSelfPermission(Manifest.permission.CAMERA) !== PackageManager.PERMISSION_GRANTED || checkSelfPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) !== PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.RECORD_AUDIO) !== PackageManager.PERMISSION_GRANTED || checkSelfPermission(
                Manifest.permission.FOREGROUND_SERVICE
            ) !== PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this@ARActivity,
                arrayOf(
                    Manifest.permission.FOREGROUND_SERVICE,
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ),
                1
            );
        } else {
            initialize();
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun initializeDeepAR() {
        deepAR = DeepAR(this)
        deepAR?.setLicenseKey("22a50143ce49ce1ed8c8c6851060b8f8eb84a2ffea5d3bad0b6cdef277a2cd63ba0914cffa32e20e")
        deepAR?.initialize(this, this)

        setupCamera()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun initialize() {
        initializeDeepAR()
        initializeFilters()
        initializeViews()
//        executeMediaProjection()
    }

    private fun initializeFilters() {
        effects = java.util.ArrayList()
        effects!!.add("none")
        effects!!.add("viking_helmet.deepar")
        effects!!.add("MakeupLook.deepar")
        effects!!.add("Split_View_Look.deepar")
        effects!!.add("Emotions_Exaggerator.deepar")
        effects!!.add("Emotion_Meter.deepar")
        effects!!.add("Stallone.deepar")
        effects!!.add("flower_face.deepar")
        effects!!.add("galaxy_background.deepar")
        effects!!.add("Humanoid.deepar")
        effects!!.add("Neon_Devil_Horns.deepar")
        effects!!.add("Ping_Pong.deepar")
        effects!!.add("Pixel_Hearts.deepar")
        effects!!.add("Snail.deepar")
        effects!!.add("Hope.deepar")
        effects!!.add("Vendetta_Mask.deepar")
        effects!!.add("Fire_Effect.deepar")
        effects!!.add("burning_effect.deepar")
        effects!!.add("Elephant_Trunk.deepar")
    }

    private fun bindImageAnalysis(cameraProvider: ProcessCameraProvider) {
        val cameraResolutionPreset = CameraResolutionPreset.P1920x1080
        val width: Int
        val height: Int
        val orientation = getScreenOrientation()
        if (orientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE || orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            width = cameraResolutionPreset.width
            height = cameraResolutionPreset.height
        } else {
            width = cameraResolutionPreset.height
            height = cameraResolutionPreset.width
        }
        val cameraResolution = Size(width, height)
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        if (this@ARActivity.useExternalCameraTexture) {
            val preview = Preview.Builder()
                .setTargetResolution(cameraResolution)
                .build()
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle((this as LifecycleOwner), cameraSelector, preview)
            if (surfaceProvider == null) {
                surfaceProvider = deepAR?.let { ARSurfaceProvider(this@ARActivity, it) }
            }
            preview.setSurfaceProvider(surfaceProvider)
            surfaceProvider?.setMirror(lensFacing == CameraSelector.LENS_FACING_FRONT)
        } else {
            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(cameraResolution)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), imageAnalyzer)
            buffersInitialized = false
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle((this as LifecycleOwner), cameraSelector, imageAnalysis)
        }
    }

    private fun initializeBuffers(size: Int) {
        buffers = arrayOfNulls<ByteBuffer>(NUMBER_OF_BUFFERS)
        for (i in 0 until NUMBER_OF_BUFFERS) {
            buffers[i] = ByteBuffer.allocateDirect(size)
            buffers[i]?.order(ByteOrder.nativeOrder())
            buffers[i]?.position(0)
        }
    }

    private val imageAnalyzer =
        ImageAnalysis.Analyzer { image ->
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer
            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()
            if (!buffersInitialized) {
                buffersInitialized = true
                initializeBuffers(ySize + uSize + vSize)
            }
            val byteData = ByteArray(ySize + uSize + vSize)
            val width = image.width
            val yStride = image.planes[0].rowStride
            val uStride = image.planes[1].rowStride
            val vStride = image.planes[2].rowStride
            var outputOffset = 0
            if (width == yStride) {
                yBuffer[byteData, outputOffset, ySize]
                outputOffset += ySize
            } else {
                var inputOffset = 0
                while (inputOffset < ySize) {
                    yBuffer.position(inputOffset)
                    yBuffer[byteData, outputOffset, Math.min(yBuffer.remaining(), width)]
                    outputOffset += width
                    inputOffset += yStride
                }
            }
            //U and V are swapped
            if (width == vStride) {
                vBuffer[byteData, outputOffset, vSize]
                outputOffset += vSize
            } else {
                var inputOffset = 0
                while (inputOffset < vSize) {
                    vBuffer.position(inputOffset)
                    vBuffer[byteData, outputOffset, Math.min(vBuffer.remaining(), width)]
                    outputOffset += width
                    inputOffset += vStride
                }
            }
            if (width == uStride) {
                uBuffer[byteData, outputOffset, uSize]
                outputOffset += uSize
            } else {
                var inputOffset = 0
                while (inputOffset < uSize) {
                    uBuffer.position(inputOffset)
                    uBuffer[byteData, outputOffset, Math.min(uBuffer.remaining(), width)]
                    outputOffset += width
                    inputOffset += uStride
                }
            }
            buffers[currentBuffer]?.put(byteData)
            buffers[currentBuffer]?.position(0)
            if (deepAR != null) {
                deepAR?.receiveFrame(
                    buffers[currentBuffer],
                    image.width, image.height,
                    image.imageInfo.rotationDegrees,
                    lensFacing == CameraSelector.LENS_FACING_FRONT,
                    DeepARImageFormat.YUV_420_888,
                    image.planes[1].pixelStride
                )
            }
            currentBuffer =
                (currentBuffer + 1) % NUMBER_OF_BUFFERS
            image.close()
        }


    private fun getFilterPath(filterName: String): String? {
        return if (filterName == "none") {
            null
        } else "file:///android_asset/$filterName"
    }

    private fun gotoNext() {
        currentEffect = (currentEffect + 1) % effects.size
        deepAR?.switchEffect("effect", getFilterPath(effects[currentEffect]))
    }

    private fun gotoPrevious() {
        currentEffect = (currentEffect - 1 + effects.size) % effects.size
        deepAR?.switchEffect("effect", getFilterPath(effects[currentEffect]))
    }

    override fun onStop() {
        recording = false
        currentSwitchRecording = false
        var cameraProvider: ProcessCameraProvider? = null
        try {
            cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()
        } catch (e: ExecutionException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        if (surfaceProvider != null) {
            surfaceProvider?.stop()
            surfaceProvider = null
        }
        deepAR?.release()
        deepAR = null
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (surfaceProvider != null) {
            surfaceProvider?.stop()
        }
        if (deepAR == null) {
            return
        }
        deepAR?.setAREventListener(null)
        deepAR?.release()
        deepAR = null
    }

    override fun surfaceCreated(p0: SurfaceHolder) {

    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // If we are using on screen rendering we have to set surface view where DeepAR will render
        deepAR?.setRenderSurface(holder.surface, width, height)
    }

    override fun surfaceDestroyed(p0: SurfaceHolder) {
        if (deepAR != null) {
            deepAR?.setRenderSurface(surfaceProvider?.getSurface(), 0, 0)
        }
    }

    fun pressStartRecording() {

    }

    override fun screenshotTaken(bitmap: Bitmap) {
//        val now = DateFormat.format("yyyy_MM_dd_hh_mm_ss", Date())
//        try {
//            val imageFile = File(
//                getExternalFilesDir(Environment.DIRECTORY_PICTURES),
//                "image_$now.jpg"
//            )
//            val outputStream = FileOutputStream(imageFile)
//            val quality = 100
//            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
//            outputStream.flush()
//            outputStream.close()
//            MediaScannerConnection.scanFile(
//                this@ARActivity,
//                arrayOf<String>(imageFile.toString()),
//                null,
//                null
//            )
//            Toast.makeText(
//                this@ARActivity,
//                "Screenshot " + imageFile.name + " saved.",
//                Toast.LENGTH_SHORT
//            ).show()
//        } catch (e: Throwable) {
//            e.printStackTrace()
//        }
    }

    override fun videoRecordingStarted() {}

    override fun videoRecordingFinished() {}

    override fun videoRecordingFailed() {}

    override fun videoRecordingPrepared() {}

    override fun shutdownFinished() {}

    override fun initialized() {
        // Restore effect state after deepar release
        deepAR?.switchEffect("effect", getFilterPath(effects[currentEffect]))
    }

    override fun faceVisibilityChanged(b: Boolean) {}

    override fun imageVisibilityChanged(s: String?, b: Boolean) {}

    override fun frameAvailable(image: Image?) {}

    override fun error(arErrorType: ARErrorType?, s: String?) {}


    override fun effectSwitched(s: String?) {}

    @RequiresApi(Build.VERSION_CODES.S)
    private fun setupCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                bindImageAnalysis(cameraProvider)
            } catch (e: ExecutionException) {
                e.printStackTrace()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
//        startStreaming2()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun initializeViews() {
        Log.w("initializeViews", "Start")

        val previousMask = findViewById<ImageButton>(R.id.previousMask)
        val nextMask = findViewById<ImageButton>(R.id.nextMask)
        val arView = findViewById<SurfaceView>(R.id.surface)
        arView.holder.addCallback(this)

        // Surface might already be initialized, so we force the call to onSurfaceChanged
        arView.visibility = View.GONE
        arView.visibility = View.VISIBLE
        val screenshotBtn = findViewById<ImageButton>(R.id.recordButton)
//        screenshotBtn.setOnClickListener { deepAR?.takeScreenshot() }
//        screenshotBtn.setOnClickListener { Toast.makeText(this@ARActivity, "You clicked me.", Toast.LENGTH_SHORT).show() }
        val switchCamera = findViewById<ImageButton>(R.id.switchCamera)

        switchCamera.setOnClickListener {
            lensFacing =
                if (lensFacing == CameraSelector.LENS_FACING_FRONT) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
            //unbind immediately to avoid mirrored frame.
            var cameraProvider: ProcessCameraProvider? = null
            try {
                cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()
            } catch (e: ExecutionException) {
                e.printStackTrace()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
            setupCamera()
        }
        val openActivity = findViewById<ImageButton>(R.id.openActivity)
        openActivity.setOnClickListener {
            val myIntent = Intent(this@ARActivity, BasicActivity::class.java)
//            val myIntent = Intent(this@ARActivity, typeOf(Activity));
            this@ARActivity.startActivity(myIntent)
        }
        val screenShotModeButton = findViewById<TextView>(R.id.screenshotModeButton)
        val recordModeBtn = findViewById<TextView>(R.id.recordModeButton)
        recordModeBtn.background.alpha = 0x00
        screenShotModeButton.background.alpha = 0x00
//        recordModeBtn.setOnClickListener(View.OnClickListener { this@ARActivity.startStreaming2() })
        recordModeBtn.setOnClickListener(View.OnClickListener { this@ARActivity.startStreamingPipe() })
//        screenShotModeButton.setOnClickListener(View.OnClickListener { this@ARActivity.startFF() })
//        screenShotModeButton.setOnClickListener(View.OnClickListener {
//            if (currentSwitchRecording) {
//                if (recording) {
//                    Toast.makeText(
//                        applicationContext,
//                        "Cannot switch to screenshots while recording!",
//                        Toast.LENGTH_SHORT
//                    ).show()
//                    return@OnClickListener
//                }
//                recordModeBtn.background.alpha = 0x00
//                screenShotModeButton.background.alpha = 0xA0
//                screenshotBtn.setOnClickListener { deepAR?.takeScreenshot() }
//                currentSwitchRecording = !currentSwitchRecording
//            }
//        })
//        recordModeBtn.setOnClickListener {
//            if (!currentSwitchRecording) {
//                recordModeBtn.background.alpha = 0xA0
//                screenShotModeButton.background.alpha = 0x00
//                screenshotBtn.setOnClickListener {
//                    if (recording) {
//                        deepAR?.stopVideoRecording()
//                        Toast.makeText(
//                            applicationContext,
//                            "Recording " + videoFileName?.getName() + " saved.",
//                            Toast.LENGTH_LONG
//                        ).show()
//                    } else {
//                        videoFileName = File(
//                            getExternalFilesDir(Environment.DIRECTORY_MOVIES),
//                            "video_" + SimpleDateFormat("yyyy_MM_dd_HH_mm_ss")
//                                .format(Date()) + ".mp4"
//                        )
//                        deepAR?.startVideoRecording(videoFileName.toString(), width / 2, height / 2)
//                        Toast.makeText(applicationContext, "Recording started.", Toast.LENGTH_SHORT)
//                            .show()
//                    }
//                    recording = !recording
//                }
//                currentSwitchRecording = !currentSwitchRecording
//            }
//        }
        previousMask.setOnClickListener { gotoPrevious() }
        nextMask.setOnClickListener { gotoNext() }
    }

    private fun getScreenOrientation(): Int {
        val rotation = windowManager.defaultDisplay.rotation
        val dm = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(dm)
        val width = dm.widthPixels
        val height = dm.heightPixels

        // if the device's natural orientation is portrait:
        val orientation: Int = if ((rotation == Surface.ROTATION_0
                    || rotation == Surface.ROTATION_180) && height > width ||
            (rotation == Surface.ROTATION_90
                    || rotation == Surface.ROTATION_270) && width > height
        ) {
            when (rotation) {
                Surface.ROTATION_0 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                Surface.ROTATION_90 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                Surface.ROTATION_180 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                Surface.ROTATION_270 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        } else {
            when (rotation) {
                Surface.ROTATION_0 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                Surface.ROTATION_90 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                Surface.ROTATION_180 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                Surface.ROTATION_270 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                else -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        }
        return orientation
    }

    override fun onResume() {
        super.onResume()
        deepAR?.resumeVideoRecording()
        if (ffmpegsessionid > -1) {
            FFmpegKit.cancel(ffmpegsessionid);
        }
    }

    override fun onPause() {
        super.onPause()
        deepAR?.pauseVideoRecording()
        if (ffmpegsessionid > -1) {
            FFmpegKit.cancel(ffmpegsessionid);
            ffmpegsessionid = -1;
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty()) {
            for (grantResult in grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    return  // no permission
                }
            }
            initialize();
        }
    }

    private val REQUEST_CODE = 100
    private fun startProjection() {
        val mProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mProjectionManager.createScreenCaptureIntent(), REQUEST_CODE)
    }

    private fun startStreaming() {
        // Create MediaFormat for the encoder
//        val displayMetrics = resources.displayMetrics
//        val frameWidth = displayMetrics.widthPixels
//        val frameHeight = 2722
//        val mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, frameWidth, frameHeight)
//        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
//        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 6000000)
//        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
//        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
//
//        // Create the MediaCodec encoder
//        mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
//        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = MediaCodec.createPersistentInputSurface()

//        deepAR!!.setRenderSurface(inputSurface,frameWidth,frameHeight)
        // Start the MediaCodec encoder
//        mediaCodec.start()
    }

    private lateinit var mediaRecorder: MediaRecorder
    lateinit var inputSurface: Surface
    lateinit var testvid: File
    lateinit var testoutput: File
    lateinit var virtualDisplay: VirtualDisplay

    @RequiresApi(Build.VERSION_CODES.S)

    private fun startStreaming2() {
        Log.d("stream", "Start")
//        if(this::mediaProjection.isInitialized){
//            mediaProjection.stop()
//        }
//        executeMediaProjection()
        inputSurface = MediaCodec.createPersistentInputSurface()
        //make a pipe containing a read and a write parcelfd
        if (this::mediaRecorder.isInitialized) {
            mediaRecorder.stop();
            mediaRecorder.reset();   // You can reuse the object by going back to setAudioSource() step
            mediaRecorder.release(); // Now the object cannot be reused
        }

        val filepath: String = Environment
            .getExternalStoragePublicDirectory(
                Environment
                    .DIRECTORY_DOWNLOADS
            ).absolutePath
        val filemp: String = "/videotest.mp4"
//        val dir = Environment.getExternalStorageDirectory()
//        val dir = this@ARActivity.cacheDir
        val dir = Environment
            .getExternalStoragePublicDirectory(
                Environment
                    .DIRECTORY_DOWNLOADS
            )
        try {
            testvid = File.createTempFile("videotest", ".h264", dir)
        } catch (e: IOException) {
            Log.d("aa", "external storage access error")
            e.printStackTrace()
            return
        }
//        try {
//            val sc : Socket = Socket("192.168.1.2",1935)
//            val pfd : ParcelFileDescriptor = ParcelFileDescriptor.fromSocket(sc)
//        } catch (e: Exception) {
//            Log.d("aa", "aaa")
//            e.printStackTrace()
//            return
//        }


//        val sc : Socket = Socket("192.168.1.20",1935)
//        val pfd : ParcelFileDescriptor = ParcelFileDescriptor.fromSocket(sc)

        mediaRecorder = MediaRecorder()
        val displayMetrics = resources.displayMetrics
        val frameWidth = displayMetrics.widthPixels
        val frameHeight = 2722
        Log.d("stream", frameHeight.toString())
        var fdPair: Array<ParcelFileDescriptor> = try {
            ParcelFileDescriptor.createPipe()
        } catch (e: IOException) {
            e.printStackTrace()
            return Service.START_NOT_STICKY as Unit
        }
        Log.d("stream", "fd");
        //get a handle to your read and write fd objects.
        readFD = fdPair[0]
        writeFD = fdPair[1]
        //do ffmpeg
//        val command = "-f ${readFD.fileDescriptor} -pixel_format yuv420p -video_size ${frameWidth}x${frameHeight} -i - -c:v libx264 -preset ultrafast -tune zerolatency -f flv rtmp://192.168.0.119/live/livestream" // Replace with your RTMP server URL and stream key
//
//        FFmpegKit.execute(command)
        Log.w("stream", "mr");

        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setInputSurface(inputSurface)
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        mediaRecorder.setVideoEncodingBitRate(44100)
        mediaRecorder.setVideoSize(frameWidth, frameHeight)

//        mediaRecorder.setOutputFile(writeFD.fileDescriptor)
//        mediaRecorder.setOutputFile(filepath.plus(filemp));
        mediaRecorder.setOutputFile(testvid);
//        mediaRecorder.setOutputFile(pfd.fileDescriptor);

        deepAR!!.setRenderSurface(inputSurface, frameWidth, frameHeight)

        try {
            mediaRecorder.prepare()
//            virtualDisplay = mediaProjection.createVirtualDisplay("vd",frameWidth,frameHeight,displayMetrics.densityDpi,
//                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,mediaRecorder.surface,null,null)
        } catch (e: Exception) {
            Log.d("e", "IOException preparing MediaRecorder: " + e.message);

            e.printStackTrace()
            return
        }
        mediaRecorder.start();
//         var recordView : VideoView = findViewById<VideoView>(R.id.recordSurface)
//        if(testvid.exists()){
//
////            recordView.setVideoPath(testvid.absolutePath)
////            recordView.visibility = View.VISIBLE
////            recordView.stopPlayback()
////            recordView.start()
//        }else{
//            Log.d("aa","no video file")
//        }
        // Recording is now started

//        mediaRecorder.stop();
//        mediaRecorder.reset();   // You can reuse the object by going back to setAudioSource() step
//        mediaRecorder.release(); // Now the object cannot be reused
        // Create MediaFormat for the encoder
        // Execute FFmpeg command to stream the encoded frames to the RTMP server
//        val command = "-f rawvideo -pixel_format yuv420p -video_size ${frameWidth}x${frameHeight} -i - -c:v libx264 -preset ultrafast -tune zerolatency -f flv rtmp://your-rtmp-server-url/live/stream-key" // Replace with your RTMP server URL and stream key

        // Create a separate thread for encoding and streaming
//        val encodingThread = Thread {
//            val bufferInfo = MediaCodec.BufferInfo()
//            while (true) {
//                val outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 1000)
//                if (outputBufferIndex >= 0) {
//                    val outputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex)
//                    // Read the encoded data from the output buffer
//
//                    // Stream the data to the RTMP server using FFmpeg
//
//                    mediaCodec.releaseOutputBuffer(outputBufferIndex, false)
//                }
//            }
//        }
//        encodingThread.start()
    }

    lateinit var ffmpegProcess: Process
    fun isProcessRunning(process: Process): Boolean {
        try {
            process.exitValue()
        } catch (e: IllegalThreadStateException) {
            return true
        }
        return false
    }

    private fun startStreaming4() {
        Log.d("stream", "Start")
//        if(this::mediaProjection.isInitialized){
//            mediaProjection.stop()
//        }
//        executeMediaProjection()
        inputSurface = MediaCodec.createPersistentInputSurface()
        //make a pipe containing a read and a write parcelfd
        if (this::mediaRecorder.isInitialized) {
            mediaRecorder.stop();
            mediaRecorder.reset();   // You can reuse the object by going back to setAudioSource() step
            mediaRecorder.release(); // Now the object cannot be reused
        }

//        val sc : Socket = Socket("192.168.1.2",1935)
//        val pfd : ParcelFileDescriptor = ParcelFileDescriptor.fromSocket(sc)

//        var fdPair : Array<ParcelFileDescriptor> = try {
//            ParcelFileDescriptor.createPipe()
//        } catch (e: IOException) {
//            e.printStackTrace()
//            return
//        }
//        //get a handle to your read and write fd objects.
//        readFD = fdPair[0]
//        writeFD = fdPair[1]

        mediaRecorder = MediaRecorder()
        val displayMetrics = resources.displayMetrics
        val frameWidth = displayMetrics.widthPixels
        val frameHeight = 2722

        Log.d("stream", frameHeight.toString())

        //do ffmpeg
//        val command = "-f ${readFD.fileDescriptor} -pixel_format yuv420p -video_size ${frameWidth}x${frameHeight} -i - -c:v libx264 -preset ultrafast -tune zerolatency -f flv rtmp://192.168.0.119/live/livestream" // Replace with your RTMP server URL and stream key
//
//        FFmpegKit.execute(command)
        Log.w("stream", "mr");

//        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
//        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
//        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
//        mediaRecorder.setInputSurface(inputSurface)
//        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
//        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
//        mediaRecorder.setVideoEncodingBitRate(44100)
//        mediaRecorder.setVideoSize(frameWidth,frameHeight)

//        mediaRecorder.setOutputFile(writeFD.fileDescriptor)
//        mediaRecorder.setOutputFile(filepath.plus(filemp));
//        mediaRecorder.setOutputFile(testvid);
//        mediaRecorder.setOutputFile(pfd.fileDescriptor);

//        deepAR!!.setRenderSurface(inputSurface, frameWidth, frameHeight)
//        deepAR!!.startVideoRecording(writeFD.fileDescriptor)

        val mediaFormat =
            MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, frameWidth, frameHeight)
        mediaFormat.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        )
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 6000000)
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

        // Create the MediaCodec encoder
        mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

        var surface = mediaCodec.createInputSurface()
        // Start the MediaCodec encoder
        mediaCodec.start()

        // Set DeepAR output surface as the encoder's input surface
//        deepAR!!.setRenderSurface(surface, frameWidth, frameHeight)

//        val command = "-f rawvideo -pixel_format yuv420p -video_size ${frameWidth}x${frameHeight} -i - -c:v libx264 -preset ultrafast -tune zerolatency -f flv rtmp://192.168.1.2/live/livestream"
      val command = "-y -f android_camera -camera_index 1 -i 0:0 -r 30 -pixel_format bgr0 -video_size ${frameWidth}x${frameHeight}  -c:v libx264 -preset ultrafast -tune zerolatency -f flv rtmp://192.168.1.2/live/livestream"
        val session : FFmpegSession = FFmpegKit.execute(command)
        ffmpegsessionid = session.sessionId
        if (ReturnCode.isSuccess(session.returnCode)) {

            // SUCCESS

        } else if (ReturnCode.isCancel(session.returnCode)) {

            // CANCEL

        } else {

            // FAILURE
            Log.d("aaaa", String.format("Command failed with state %s and rc %s.%s", session.state, session.returnCode, session.failStackTrace));
//            testvid.delete()
//            testoutput.delete()
        }

        val encodingThread = Thread {
            val bufferInfo = MediaCodec.BufferInfo()
            while (true) {
                val outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000)
                if (outputBufferIndex >= 0) {
                    val outputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex)
                    // Read the encoded data from the output buffer

                    // Stream the data to the RTMP server using FFmpeg

                    mediaCodec.releaseOutputBuffer(outputBufferIndex, false)
                }
            }
        }
        encodingThread.start()
    }
//        try{
//            mediaRecorder.prepare()
//        }
//        catch(e : Exception){
//            Log.d("e", "IOException preparing MediaRecorder: " + e.message);
//
//            e.printStackTrace()
//            return
//        }
//        try{
//            mediaRecorder.start()
//        }
//        catch(e : Exception){
//            Log.d("e", "starting MediaRecorder: " + e.cause +" " +e.message);
//
//            e.printStackTrace()
//            return
//        }
//}
    lateinit var inpustSurface2 : Surface
    protected fun startStreamingPipe(){
        var pipe1 = FFmpegKitConfig.registerNewFFmpegPipe(this@ARActivity)
        val displayMetrics = resources.displayMetrics
        val frameWidth = displayMetrics.widthPixels
        val frameHeight = 2722
        var rtmp = "rtmp://192.168.1.2/live/livestream"
//        val mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, frameWidth, frameHeight)
//        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
//        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 6000000)
//        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
//        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
//
//        // Create the MediaCodec encoder
//        mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
//        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
//        inpustSurface2 = mediaCodec.createInputSurface()
//
//        // Start the MediaCodec encoder
//        mediaCodec.start()
//
//        // Set DeepAR output surface as the encoder's input surface
//        deepAR!!.setRenderSurface(inpustSurface2,frameWidth,frameHeight)

        Log.d("tst",String.format("${CameraSelector.LENS_FACING_BACK}" ))
        val command = "-y -f rawvideo -pixel_format yuv420p -video_size ${frameWidth}x${frameHeight} -i - -c:v libx264 -f flv rtmp://192.168.1.2/live/livestream"
        val command2 = "-y -f android_camera -camera_index 1 -i 0:0 -r 30 -pixel_format rgb24 -video_size ${frameWidth}x${frameHeight}  -c:v libx264 -preset ultrafast -tune zerolatency -f flv rtmp://192.168.1.2/live/livestream"

    val session : FFmpegSession = FFmpegKit.execute(command2)
        ffmpegsessionid = session.sessionId
        if (ReturnCode.isSuccess(session.returnCode)) {
            // SUCCESS
        } else if (ReturnCode.isCancel(session.returnCode)) {
            // CANCEL
        } else {
            // FAILURE
            Log.d("aaaa", String.format("Command failed with state %s and rc %s.%s", session.state, session.returnCode, session.failStackTrace));
        }

//        FFmpegKit.executeAsync(command, {
//
//            @Override
//             fun apply(session : FFmpegSession) {
//                val state : SessionState = session.state;
//                val returnCode : ReturnCode = session.returnCode;
//                // CALLED WHEN SESSION IS EXECUTED
//                Log.d("ffmpeg", String.format("FFmpeg process exited with state %s and rc %s.%s", state, returnCode, session.getFailStackTrace()));
//            }
//        }, {
//
//            @Override
//            fun apply(log : com.arthenica.ffmpegkit.Log ) {
//                // CALLED WHEN SESSION PRINTS LOGS
//            }
//        }, {
//
//            @Override
//            fun apply(statistics : Statistics) {
//                // CALLED WHEN SESSION GENERATES STATISTICS
//                Log.d("ffmpeg", String.format("stats %s", statistics));
//
//            }
//        });
//        Runtime.getRuntime().exec(arrayOf("sh", "-c", "cat <image path> > $pipe1"))
//        var img : ByteArray = ByteArray(640*480*3);   // dummy image
//        var out : FileOutputStream = FileOutputStream(pipe1);
//        try {
//            for (i in 0..99) { // write 100 empty frames
//                out.write(img);
//            }
//        } catch (e : Exception) {
//            e.printStackTrace();
//        } finally {
//            out.close();
//        }
    }

    protected fun startFF(){
//        mediaRecorder.stop();
//        mediaRecorder.reset();   // You can reuse the object by going back to setAudioSource() step
//        mediaRecorder.release();

        val cmd = "-re -i - -movflags isml+frag_keyframe -c:v copy -c:a copy -f flv -y rtmp://192.168.1.2/live/livestream"
//        val cmd = "-i - -c:v copy -c:a copy -f flv -y -movflags faststart ${testoutput.absolutePath} "
        val session : FFmpegSession = FFmpegKit.execute(cmd);
        ffmpegsessionid = session.sessionId
        if (ReturnCode.isSuccess(session.getReturnCode())) {

            // SUCCESS

        } else if (ReturnCode.isCancel(session.getReturnCode())) {

            // CANCEL

        } else {

            // FAILURE
            Log.d("aaaa", String.format("Command failed with state %s and rc %s.%s", session.getState(), session.getReturnCode(), session.getFailStackTrace()));
            testvid.delete()
            testoutput.delete()
        }
    }
    var ffmpegsessionid : Long = -1;
    protected fun startStreaming3() {
        if (!currentSwitchRecording) {
            if (recording) {
                deepAR?.stopVideoRecording()
                if(ffmpegsessionid > -1){
                    FFmpegKit.cancel(ffmpegsessionid)
                    ffmpegsessionid = -1;
                }
                Toast.makeText(
                    applicationContext,
                    "Recording " + videoFile?.getName() + " saved.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                videoFile = File(
                    getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                    "video_" + SimpleDateFormat("yyyy_MM_dd_HH_mm_ss")
                        .format(Date()) + ".mp4"
                )
                val videoFileName = videoFile?.absolutePath
                val testvideoFile = File(
                    getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                    "video_" + SimpleDateFormat("yyyy_MM_dd_HH_mm_ss")
                        .format(Date()) + ".mp4"
                )
                val testvfile = testvideoFile?.absolutePath
                deepAR?.startVideoRecording(videoFile?.name, width / 2, height / 2)
                Toast.makeText(applicationContext, "Recording started.", Toast.LENGTH_SHORT)
                    .show()

                val cmd = "-i $videoFileName -c:v libx264 -c:a copy -f flv rtmp://192.168.1.2:1935/live/livestream "
//                val cmd = "-i ${videoFile?.name}  -c:v libx264 -c:a copy -f flv ${testvideoFile?.name} "

                val session : FFmpegSession = FFmpegKit.execute(cmd);
                ffmpegsessionid = session.sessionId
            }
            recording = !recording
        }
        currentSwitchRecording = !currentSwitchRecording
    }
}

