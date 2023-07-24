//package com.example.streamtest1
//
//
////import org.florescu.android.rangeseekbar.RangeSeekBar
//import android.content.Intent
//import android.net.Uri
//import android.os.Bundle
//import android.os.FileUtils
//import android.widget.MediaController
//import android.widget.Toast
//import android.widget.VideoView
//import androidx.appcompat.app.AppCompatActivity
//import java.io.File
//
//
//class MainActivity : AppCompatActivity() {
//
////    private lateinit var video_url: String
//    private lateinit var videoView: VideoView
//    private var r: Runnable? = null
//    var mediaControls: MediaController?=null;
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_main);
//        videoView = findViewById<VideoView>(R.id.layout_movie_wrapper);
//        if (mediaControls == null) {
//            // creating an object of media controller class
//            mediaControls = MediaController(this)
//
//            // set the anchor view for the video view
//            mediaControls!!.setAnchorView(videoView)
////        }
//        videoView.setVideoURI(Uri.parse("http://techslides.com/demos/sample-videos/small.mp4"));
//        videoView.requestFocus();
//        videoView.start();
//        videoView.setOnCompletionListener {
//            Toast.makeText(applicationContext, "Video completed",
//                Toast.LENGTH_LONG).show()
//            true
//        }
//        videoView.setOnErrorListener { mp, what, extra ->
//            Toast.makeText(applicationContext, "An Error Occurred " +
//                    "While Playing Video !!!", Toast.LENGTH_LONG).show()
//            false
//        }
//    }
//
////    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
////        super.onActivityResult(requestCode, resultCode, data)
////        if (resultCode == RESULT_OK) {
////            if (requestCode == 123) {
////                if (data != null) {
////                    // get the video Uri
////                    val uri = data.data
////                    try {
////                        // get the file from theUri using getFileFromUri() method present
////                        // in FileUtils.java
////                        val videoFile: File = FileUtils.getFileFromUri(this, uri)
////
////                        // now set the video uri in the VideoView
////                        videoView!!.setVideoURI(uri)
////
////                        // after successful retrieval of the video and properly
////                        // setting up the retried video uri in
////                        // VideoView, Start the VideoView to play that video
////                        videoView!!.start()
////
////                        // get the absolute path of the video file. We will require
////                        // this as an input argument in
////                        // the ffmpeg command.
////                        video_url = videoFile.absolutePath
////                    } catch (e: Exception) {
////                        Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show()
////                        e.printStackTrace()
////                    }
////                }
////            }
////        }
////    }
//}
//
