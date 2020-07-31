package com.appexecutors.picker

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.view.HapticFeedbackConstants.LONG_PRESS
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.View.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.webkit.MimeTypeMap
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.net.toFile
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup
import com.appexecutors.picker.databinding.ActivityPickerBinding
import com.appexecutors.picker.gallery.BottomSheetMediaRecyclerAdapter
import com.appexecutors.picker.gallery.BottomSheetMediaRecyclerAdapter.Companion.HEADER
import com.appexecutors.picker.gallery.BottomSheetMediaRecyclerAdapter.Companion.SPAN_COUNT
import com.appexecutors.picker.gallery.InstantMediaRecyclerAdapter
import com.appexecutors.picker.gallery.MediaModel
import com.appexecutors.picker.interfaces.MediaClickInterface
import com.appexecutors.picker.interfaces.PermissionCallback
import com.appexecutors.picker.utils.GeneralUtils.getStringDate
import com.appexecutors.picker.utils.GeneralUtils.hideStatusBar
import com.appexecutors.picker.utils.GeneralUtils.manipulateBottomSheetVisibility
import com.appexecutors.picker.utils.GeneralUtils.showStatusBar
import com.appexecutors.picker.utils.HeaderItemDecoration
import com.appexecutors.picker.utils.LuminosityAnalyzer
import com.appexecutors.picker.utils.MediaConstants.IMAGE_VIDEO_URI
import com.appexecutors.picker.utils.MediaConstants.getImageVideoCursor
import com.appexecutors.picker.utils.PermissionUtils
import com.appexecutors.picker.utils.PickerOptions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.android.synthetic.main.activity_picker.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.collections.ArrayList
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

@Suppress("DEPRECATION")
class Picker : AppCompatActivity() {

    private lateinit var mBinding: ActivityPickerBinding

    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var flashMode: Int = ImageCapture.FLASH_MODE_OFF
    private var lastScaleFactor = 0f
    private var statusBarSize = 0

    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var mPickerOptions: PickerOptions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_picker)

        mPickerOptions = intent?.getSerializableExtra(PICKER_OPTIONS) as PickerOptions

        mBinding.viewFinder.post {
            if (allPermissionsGranted()) {
                startCamera()
            }
        }

        // Setup the listener for take photo button
        mBinding.imageViewClick.setOnClickListener { takePhoto() }

        outputDirectory = getOutputDirectory()

        cameraExecutor = Executors.newSingleThreadExecutor()

        window.decorView.setOnApplyWindowInsetsListener { _, insets ->
            statusBarSize = insets.systemWindowInsetTop
            insets
        }
        showStatusBar(this)
        getMedia()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            cameraProvider = cameraProviderFuture.get()

            // Select lensFacing depending on the available cameras
            lensFacing = when {
                hasBackCamera() -> CameraSelector.LENS_FACING_BACK
                hasFrontCamera() -> CameraSelector.LENS_FACING_FRONT
                else -> throw IllegalStateException("Back and front camera are unavailable")
            }

            bindCameraUseCases()

            setupFlash()

        }, ContextCompat.getMainExecutor(this))

        mBinding.imageViewChangeCamera.let {

            it.setOnClickListener {
                lensFacing = if (CameraSelector.LENS_FACING_FRONT == lensFacing) {
                    CameraSelector.LENS_FACING_BACK
                } else {
                    CameraSelector.LENS_FACING_FRONT
                }
                // Re-bind use cases to update selected camera
                bindCameraUseCases()

                setupFlash()
            }

        }

        val scaleGestureDetector = ScaleGestureDetector(this, zoomListener)

        viewFinder.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            return@setOnTouchListener true
        }

        if (!mPickerOptions.allowFrontCamera) mBinding.imageViewChangeCamera.visibility = GONE
    }

    private val zoomListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(scaleDetector: ScaleGestureDetector): Boolean {
            val zoomRatio: Float? = camera?.cameraInfo?.zoomState?.value?.zoomRatio
            val minZoomRatio: Float? = camera?.cameraInfo?.zoomState?.value?.minZoomRatio
            val maxZoomRatio: Float? = camera?.cameraInfo?.zoomState?.value?.maxZoomRatio
            val scaleFactor = scaleDetector.scaleFactor
            lastScaleFactor =
                if (lastScaleFactor == 0f || (sign(scaleFactor) == sign(lastScaleFactor))) {
                    camera?.cameraControl?.setZoomRatio(
                        (minZoomRatio!!).coerceAtLeast(
                            (zoomRatio!! * scaleFactor).coerceAtMost(maxZoomRatio!!)
                        )
                    )
                    scaleFactor
                } else {
                    0f
                }
            return true
        }
    }

    private fun setupFlash(){

        flashMode = ImageCapture.FLASH_MODE_OFF
        mBinding.imageViewFlash.setImageDrawable(resources.getDrawable(R.drawable.ic_baseline_flash_off_36, null))

        val hasFlash = camera?.cameraInfo?.hasFlashUnit()

        if (hasFlash!= null && hasFlash) mBinding.imageViewFlash.visibility = VISIBLE
        else mBinding.imageViewFlash.visibility = GONE

        mBinding.imageViewFlash.setOnClickListener {
            when (flashMode) {
                ImageCapture.FLASH_MODE_OFF -> {
                    mBinding.imageViewFlash.setImageDrawable(resources.getDrawable(R.drawable.ic_baseline_flash_on_36, null))
                    camera?.cameraControl?.enableTorch(true)
                    flashMode = ImageCapture.FLASH_MODE_ON
                }
                ImageCapture.FLASH_MODE_ON -> {
                    mBinding.imageViewFlash.setImageDrawable(resources.getDrawable(R.drawable.ic_baseline_flash_off_36, null))
                    camera?.cameraControl?.enableTorch(false)
                    flashMode = ImageCapture.FLASH_MODE_OFF
                }

            }
        }

    }

    /** Declare and bind preview, capture and analysis use cases */
    private fun bindCameraUseCases() {

        // Get screen metrics used to setup camera for full screen resolution
        val metrics = DisplayMetrics().also { viewFinder.display.getRealMetrics(it) }
        Log.d(TAG, "Screen metrics: ${metrics.widthPixels} x ${metrics.heightPixels}")

        val screenAspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)
        Log.d(TAG, "Preview aspect ratio: $screenAspectRatio")

        val rotation = viewFinder.display.rotation

        // CameraProvider
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")

        // CameraSelector
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        // Preview
        preview = Preview.Builder()
            // We request aspect ratio but no resolution
            .setTargetAspectRatio(screenAspectRatio)
            // Set initial target rotation
            .setTargetRotation(rotation)
            .build()

        // ImageCapture
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            // We request aspect ratio but no resolution to match preview config, but letting
            // CameraX optimize for whatever specific resolution best fits our use cases
            .setTargetAspectRatio(screenAspectRatio)
            // Set initial target rotation, we will have to call this again if rotation changes
            // during the lifecycle of this use case
            .setTargetRotation(rotation)
            .build()

        videoCapture = VideoCapture.Builder().build()

        // ImageAnalysis
        imageAnalyzer = ImageAnalysis.Builder()
            // We request aspect ratio but no resolution
            .setTargetAspectRatio(screenAspectRatio)
            // Set initial target rotation, we will have to call this again if rotation changes
            // during the lifecycle of this use case
            .setTargetRotation(rotation)
            .build()
            // The analyzer can then be assigned to the instance
            .also {
                it.setAnalyzer(cameraExecutor, LuminosityAnalyzer { _ ->
                    // Values returned from our analyzer are passed to the attached listener
                    // We log image analysis results here - you should do something useful
                    // instead!
                    // Log.d(TAG, "Average luminosity: $luma")
                })
            }

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageCapture, videoCapture)

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(viewFinder.createSurfaceProvider())
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }

        videoTouchListener()
    }

    private fun takePhoto() {

        if (isTakingVideo) return
        // Get a stable reference of the modifiable image capture use case
        imageCapture?.let { imageCapture ->

            // Create output file to hold the image
            val photoFile = File(
                outputDirectory,
                SimpleDateFormat(FILENAME_FORMAT, Locale.ENGLISH
                ).format(System.currentTimeMillis()) + PHOTO_EXTENSION)

            // Setup image capture metadata
            val metadata = ImageCapture.Metadata().apply {

                // Mirror image when using the front camera
                isReversedHorizontal = lensFacing == CameraSelector.LENS_FACING_FRONT
            }

            // Create output options object which contains file + metadata
            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile)
                .setMetadata(metadata)
                .build()

            // Setup image capture listener which is triggered after photo has been taken
            imageCapture.takePicture(
                outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
                    override fun onError(exc: ImageCaptureException) {
                        Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                    }

                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        val savedUri = output.savedUri ?: Uri.fromFile(photoFile)
                        Log.d(TAG, "Photo capture succeeded: $savedUri")

                        // If the folder selected is an external media directory, this is
                        // unnecessary but otherwise other apps will not be able to access our
                        // images unless we scan them using [MediaScannerConnection]
                        val mimeType = MimeTypeMap.getSingleton()
                            .getMimeTypeFromExtension(savedUri.toFile().extension)
                        MediaScannerConnection.scanFile(
                            this@Picker,
                            arrayOf(savedUri.toFile().absolutePath),
                            arrayOf(mimeType)
                        ) { _, uri ->
                            Log.d(TAG, "Image capture scanned into media store: $uri")
                        }

                        val mPathList = ArrayList<String>()
                        mPathList.add(savedUri.toString())

                        val intent = Intent()
                        intent.putExtra(PICKED_MEDIA_LIST, mPathList)
                        setResult(Activity.RESULT_OK, intent)
                        finish()
                    }
                })

            // We can only change the foreground Drawable using API level 23+ API
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

                // Display flash animation to indicate that photo was captured
                container.postDelayed({
                    container.foreground = ColorDrawable(Color.WHITE)
                    container.postDelayed(
                        { container.foreground = null }, ANIMATION_FAST_MILLIS)
                }, ANIMATION_SLOW_MILLIS)
            }
        }
    }

    private var isTakingVideo = false

    @SuppressLint("ClickableViewAccessibility", "RestrictedApi")
    private fun videoTouchListener(){

        mBinding.imageViewClick.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                mBinding.imageViewVideoRedBg.visibility = GONE
                mBinding.imageViewVideoRedBg.animate().scaleX(1f).scaleY(1f)
                    .setDuration(300).setInterpolator(AccelerateDecelerateInterpolator()).start()
                mBinding.imageViewClick.animate().scaleX(1f).scaleY(1f).setDuration(300)
                    .setInterpolator(AccelerateDecelerateInterpolator()).start()
            } else if (event.action == MotionEvent.ACTION_DOWN) {
                mBinding.imageViewVideoRedBg.visibility = VISIBLE
                mBinding.imageViewVideoRedBg.animate().scaleX(1.2f).scaleY(1.2f)
                    .setDuration(300).setInterpolator(AccelerateDecelerateInterpolator()).start()
                mBinding.imageViewClick.animate().scaleX(1.2f).scaleY(1.2f).setDuration(300)
                    .setInterpolator(AccelerateDecelerateInterpolator()).start()
            }
            if (event.action == MotionEvent.ACTION_UP && isTakingVideo) {
                //stop video
                videoCapture?.stopRecording()
            }

            false
        }

        mBinding.imageViewClick.setOnLongClickListener {
            if (!mPickerOptions.excludeVideos) takeVideo(it)
            false
        }
    }

    private var mVideoCounterProgress = 0

    private var mVideoCounterHandler: Handler? = Handler()

    private var mVideoCounterRunnable = Runnable{}

    @SuppressLint("RestrictedApi")
    private fun takeVideo(it: View){

        val videoFile = File(
            outputDirectory,
            SimpleDateFormat(FILENAME_FORMAT, Locale.ENGLISH
            ).format(System.currentTimeMillis()) + VIDEO_EXTENSION)

        isTakingVideo = true
        it.performHapticFeedback(LONG_PRESS)

        mBinding.constraintTimer.visibility = VISIBLE

        mBinding.progressbarVideoCounter.progress = 0

        mBinding.progressbarVideoCounter.max = mPickerOptions.maxVideoDuration
        mBinding.progressbarVideoCounter.invalidate()

        mVideoCounterRunnable = Runnable{
            ++mVideoCounterProgress
            mBinding.progressbarVideoCounter.progress = mVideoCounterProgress

            var min = 0

            var secondBuilder = "$mVideoCounterProgress"

            if (mVideoCounterProgress > 59){
                min = mVideoCounterProgress / 60
                secondBuilder = "${mVideoCounterProgress - (60 * min)}"
            }

            if (secondBuilder.length == 1) secondBuilder = "0$mVideoCounterProgress"

            val time = "0$min:$secondBuilder"

            mBinding.textViewVideoTimer.text = time

            if (mVideoCounterProgress == (mPickerOptions.maxVideoDuration)){
                mVideoCounterHandler?.removeCallbacks(mVideoCounterRunnable)
                videoCapture?.stopRecording()
                mVideoCounterHandler = null
            }

            mVideoCounterHandler?.postDelayed(mVideoCounterRunnable, 1000)
        }

        mVideoCounterHandler?.postDelayed(mVideoCounterRunnable, 1000)

        mBinding.imageViewClick.animate().scaleX(1.2f).scaleY(1.2f).setDuration(300)
            .setInterpolator(AccelerateDecelerateInterpolator()).start()

        mBinding.imageViewFlash.visibility = GONE
        mBinding.imageViewChangeCamera.visibility = GONE
        mBinding.textViewMessageBottom.visibility = GONE

        //start video
        videoCapture?.startRecording(videoFile, cameraExecutor, object : VideoCapture.OnVideoSavedCallback{
            override fun onVideoSaved(file: File) {

                if (mVideoCounterHandler != null) mVideoCounterHandler?.removeCallbacks(mVideoCounterRunnable)

                val savedUri = Uri.fromFile(file)
                Log.d(TAG, "Video capture succeeded: $savedUri")

                // If the folder selected is an external media directory, this is
                // unnecessary but otherwise other apps will not be able to access our
                // images unless we scan them using [MediaScannerConnection]
                val mimeType = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(savedUri.toFile().extension)
                MediaScannerConnection.scanFile(
                    this@Picker,
                    arrayOf(savedUri.toFile().absolutePath),
                    arrayOf(mimeType)
                ) { _, uri ->
                    Log.d(TAG, "Image capture scanned into media store: $uri")
                }

                val mPathList = ArrayList<String>()
                mPathList.add(savedUri.toString())

                val intent = Intent()
                intent.putExtra(PICKED_MEDIA_LIST, mPathList)
                setResult(Activity.RESULT_OK, intent)
                finish()

                isTakingVideo = false
            }

            override fun onError(
                videoCaptureError: Int,
                message: String,
                cause: Throwable?
            ) {
                //
            }

        })
    }

    private val galleryImageList = ArrayList<MediaModel>()
    private var mInstantMediaAdapter: InstantMediaRecyclerAdapter? = null
    private var mBottomMediaAdapter: BottomSheetMediaRecyclerAdapter? = null

    private fun getMedia() {

        CoroutineScope(Dispatchers.Main).launch{
            val cursor: Cursor? = withContext(Dispatchers.IO){
                getImageVideoCursor(this@Picker, mPickerOptions.excludeVideos)
            }

            if (cursor != null){

                Log.e(TAG, "getMedia: ${cursor.count}" )

                val index = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
                val dateIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_ADDED)
                val typeIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)

                var headerDate = ""

                while (cursor.moveToNext()){
                    val id = cursor.getLong(index)
                    val path = ContentUris.withAppendedId(IMAGE_VIDEO_URI, id)
                    val mediaType = cursor.getInt(typeIndex)
                    val longDate = cursor.getLong(dateIndex)
                    val mediaDate = getStringDate(this@Picker, longDate)

                    if (!headerDate.equals(mediaDate, true)) {
                        headerDate = mediaDate
                        galleryImageList.add(MediaModel(null, mediaType, headerDate))
                    }

                    galleryImageList.add(MediaModel(path, mediaType, ""))
                }

                mInstantMediaAdapter = InstantMediaRecyclerAdapter(galleryImageList, mMediaClickListener, this@Picker)
                mInstantMediaAdapter?.maxCount = mPickerOptions.maxCount
                mBinding.recyclerViewInstantMedia.adapter = mInstantMediaAdapter

                handleBottomSheet()
            }

        }
    }

    private val mMediaClickListener = object: MediaClickInterface{
        override fun onMediaClick(media: MediaModel) {
            pickImages()
        }

        override fun onMediaLongClick(media: MediaModel, intentFrom: String) {

            if (intentFrom == InstantMediaRecyclerAdapter::class.java.simpleName){
                if (mInstantMediaAdapter?.imageCount!! > 0){
                    mBinding.textViewImageCount.text = mInstantMediaAdapter?.imageCount?.toString()
                    mBinding.textViewTopSelect.text = String.format(getString(R.string.images_selected), mInstantMediaAdapter?.imageCount?.toString())
                    showTopViews()
                }else hideTopViews()
            }


            if (intentFrom == BottomSheetMediaRecyclerAdapter::class.java.simpleName){
                if (mBottomMediaAdapter?.imageCount!! > 0){
                    mBinding.textViewImageCount.text = mBottomMediaAdapter?.imageCount?.toString()
                    mBinding.textViewTopSelect.text = String.format(getString(R.string.images_selected), mBottomMediaAdapter?.imageCount?.toString())
                    showTopViews()
                }else hideTopViews()

            }

        }

    }

    private fun showTopViews(){
        mBinding.constraintCheck.visibility = VISIBLE
        mBinding.textViewOk.visibility = VISIBLE

        mBinding.imageViewCheck.visibility = GONE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mBinding.constraintBottomSheetTop.setBackgroundColor(resources.getColor(R.color.colorPrimary, null))
            DrawableCompat.setTint(mBinding.imageViewBack.drawable, resources.getColor(R.color.colorWhite, null))
        }else{
            mBinding.constraintBottomSheetTop.setBackgroundColor(resources.getColor(R.color.colorPrimary))
            DrawableCompat.setTint(mBinding.imageViewBack.drawable, resources.getColor(R.color.colorWhite))
        }
    }

    private fun hideTopViews(){
        mBinding.constraintCheck.visibility = GONE
        mBinding.textViewOk.visibility = GONE
        mBinding.imageViewCheck.visibility = VISIBLE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mBinding.constraintBottomSheetTop.setBackgroundColor(resources.getColor(R.color.colorWhite, null))
            DrawableCompat.setTint(mBinding.imageViewBack.drawable, resources.getColor(R.color.colorBlack, null))
        }else{
            mBinding.constraintBottomSheetTop.setBackgroundColor(resources.getColor(R.color.colorWhite))
            DrawableCompat.setTint(mBinding.imageViewBack.drawable, resources.getColor(R.color.colorBlack))
        }
    }

    private var bottomSheetBehavior: BottomSheetBehavior<ConstraintLayout>? = null

    private fun handleBottomSheet(){

        mBottomMediaAdapter = BottomSheetMediaRecyclerAdapter(galleryImageList, mMediaClickListener, this@Picker)
        mBottomMediaAdapter?.maxCount = mPickerOptions.maxCount

        val layoutManager = GridLayoutManager(this, SPAN_COUNT)
        mBinding.recyclerViewBottomSheetMedia.layoutManager = layoutManager

        layoutManager.spanSizeLookup = object : SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (mBottomMediaAdapter?.getItemViewType(position) == HEADER) {
                    SPAN_COUNT
                } else 1
            }
        }

        mBinding.recyclerViewBottomSheetMedia.adapter = mBottomMediaAdapter
        mBinding.recyclerViewBottomSheetMedia.addItemDecoration(HeaderItemDecoration(mBottomMediaAdapter!!, this))

        bottomSheetBehavior = BottomSheetBehavior.from(mBinding.bottomSheet)

        var notifiedUp = false
        var notifiedDown = false

        bottomSheetBehavior?.addBottomSheetCallback(object: BottomSheetBehavior.BottomSheetCallback(){
            var oldOffSet = 0f
            override fun onSlide(bottomSheet: View, slideOffset: Float) {

                val inRangeExpanding = oldOffSet < slideOffset
                val inRangeCollapsing = oldOffSet > slideOffset
                oldOffSet = slideOffset

                if (slideOffset == 1f){
                    notifiedUp = false
                    notifiedDown = false
                    mBinding.imageViewArrowUp.visibility = INVISIBLE
                }else if (slideOffset == 0f){
                    notifiedUp = false
                    notifiedDown = false
                    mBinding.imageViewArrowUp.visibility = VISIBLE
                }

                if (slideOffset > 0.6f && slideOffset < 0.8f){
                    if (!notifiedUp && inRangeExpanding) {
                        Log.e(TAG, "onSlide 1: $slideOffset")
                        mBottomMediaAdapter?.notifyDataSetChanged()
                        notifiedUp = true

                        var count = 0
                        galleryImageList.map { mediaModel ->
                            if (mediaModel.isSelected) count++
                        }
                        mBottomMediaAdapter?.imageCount = count
                    }



                }else if (slideOffset > 0.1f && slideOffset < 0.3f){
                    if (!notifiedDown && inRangeCollapsing) {
                        Log.e(TAG, "onSlide 2: $slideOffset")
                        mInstantMediaAdapter?.notifyDataSetChanged()
                        notifiedDown = true

                        var count = 0
                        galleryImageList.map { mediaModel ->
                            if (mediaModel.isSelected) count++
                        }
                        mInstantMediaAdapter?.imageCount = count
                        mBinding.textViewImageCount.text = count.toString()
                    }

                }

                val imageCount = if (mInstantMediaAdapter?.imageCount!! > 0) mInstantMediaAdapter?.imageCount!! else mBottomMediaAdapter?.imageCount!!

                manipulateBottomSheetVisibility(this@Picker, slideOffset, mBinding, imageCount)

            }

            override fun onStateChanged(bottomSheet: View, newState: Int) {/*Not Required*/}

        })

        mBinding.imageViewBack.setOnClickListener {
            bottomSheetBehavior?.setState(BottomSheetBehavior.STATE_COLLAPSED)
        }

        mBinding.constraintCheck.setOnClickListener { pickImages() }
        mBinding.textViewOk.setOnClickListener { pickImages() }
        mBinding.imageViewCheck.setOnClickListener {
            mBinding.constraintCheck.visibility = VISIBLE
            mBinding.textViewOk.visibility = VISIBLE

            mBinding.textViewTopSelect.text = resources.getString(R.string.tap_to_select)

            mBottomMediaAdapter?.mTapToSelect = true

            mBinding.imageViewCheck.visibility = GONE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mBinding.constraintBottomSheetTop.setBackgroundColor(resources.getColor(R.color.colorPrimary, null))
                DrawableCompat.setTint(mBinding.imageViewBack.drawable, resources.getColor(R.color.colorWhite, null))
            }else{
                mBinding.constraintBottomSheetTop.setBackgroundColor(resources.getColor(R.color.colorPrimary))
                DrawableCompat.setTint(mBinding.imageViewBack.drawable, resources.getColor(R.color.colorWhite))
            }
        }

        if (statusBarSize > 0) {
            val params = mBinding.constraintBottomSheetTop.layoutParams as ConstraintLayout.LayoutParams
            params.setMargins(0, statusBarSize, 0, 0)
        }
        hideStatusBar(this)
    }

    private fun pickImages(){
        val mPathList = ArrayList<String>()

        galleryImageList.map { mediaModel ->
            if (mediaModel.isSelected) {
                mPathList.add(mediaModel.mMediaUri.toString())
            }
        }

        val intent = Intent()
        intent.putExtra(PICKED_MEDIA_LIST, mPathList)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() } }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    /**
     *  [androidx.camera.core.AspectRatio]. Currently it has values of 4:3 & 16:9.
     *
     *  Detecting the most suitable ratio for dimensions provided in @params by counting absolute
     *  of preview ratio to one of the provided values.
     *
     *  @param width - preview width
     *  @param height - preview height
     *  @return suitable aspect ratio
     */
    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    /** Returns true if the device has an available back camera. False otherwise */
    private fun hasBackCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false
    }

    /** Returns true if the device has an available front camera. False otherwise */
    private fun hasFrontCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false
    }

    companion object {
        private const val TAG = "Picker"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        const val REQUEST_CODE_PICKER = 10
        const val PICKER_OPTIONS = "PICKER_OPTIONS"
        const val PICKED_MEDIA_LIST = "PICKED_MEDIA_LIST"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val PHOTO_EXTENSION = ".jpg"
        private const val VIDEO_EXTENSION = ".mp4"
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0

        const val ANIMATION_FAST_MILLIS = 50L
        const val ANIMATION_SLOW_MILLIS = 100L

        @JvmStatic
        fun startPicker(fragment: Fragment, mPickerOptions: PickerOptions){
            PermissionUtils.checkForCameraWritePermissions(fragment, object : PermissionCallback {
                override fun onPermission(approved: Boolean) {
                    val mPickerIntent = Intent(fragment.activity, Picker::class.java)
                    mPickerIntent.putExtra(PICKER_OPTIONS, mPickerOptions)
                    fragment.startActivityForResult(mPickerIntent, REQUEST_CODE_PICKER)
                }
            })
        }

        @JvmStatic
        fun startPicker(activity: FragmentActivity, mPickerOptions: PickerOptions){
            PermissionUtils.checkForCameraWritePermissions(activity, object : PermissionCallback{
                override fun onPermission(approved: Boolean) {
                    val mPicEditorIntent = Intent(activity, Picker::class.java)
                    mPicEditorIntent.putExtra(PICKER_OPTIONS, mPickerOptions)
                    activity.startActivityForResult(mPicEditorIntent, REQUEST_CODE_PICKER)
                }
            })
        }
    }
}