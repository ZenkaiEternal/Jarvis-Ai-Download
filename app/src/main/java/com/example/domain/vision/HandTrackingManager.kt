package com.example.domain.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class SquareRoiTransform(
    val squareSize: Int,
    val frameWidth: Int,
    val frameHeight: Int,
    val padX: Float,
    val padY: Float
) {
    fun unmapX(squareNormX: Float): Float {
        if (squareSize <= 0 || frameWidth <= 0) return squareNormX.coerceIn(0f, 1f)
        return ((squareNormX * squareSize - padX) / frameWidth).coerceIn(0f, 1f)
    }

    fun unmapY(squareNormY: Float): Float {
        if (squareSize <= 0 || frameHeight <= 0) return squareNormY.coerceIn(0f, 1f)
        return ((squareNormY * squareSize - padY) / frameHeight).coerceIn(0f, 1f)
    }

    companion object {
        fun fromDimensions(width: Int, height: Int): SquareRoiTransform {
            val w = width.coerceAtLeast(1)
            val h = height.coerceAtLeast(1)
            val s = maxOf(w, h)
            return SquareRoiTransform(
                squareSize = s,
                frameWidth = w,
                frameHeight = h,
                padX = (s - w) * 0.5f,
                padY = (s - h) * 0.5f
            )
        }
    }
}

/**
 * Local-only front-camera hand tracking + facial recognition controller backed by CameraX,
 * MediaPipe HandLandmarker (square-ROI padded), and ML Kit FaceDetection + FaceSignatureMatcher.
 * Stops all camera capture and frame processing immediately when disabled or destroyed.
 */
class HandTrackingManager(
    private val context: Context,
    private val onPinchAction: () -> Unit = {},
    private val onFaceRecognized: (FaceRecognitionOutput) -> Unit = {}
) {
    companion object {
        private const val TAG = "HandTrackingManager"
        private const val MODEL_ASSET_PATH = "hand_landmarker.task"
        private const val FACE_ANALYSIS_INTERVAL_MS = 340L
        private const val MAX_CAMERA_RECOVERY_ATTEMPTS = 2
        private const val CAMERA_RECOVERY_DELAY_MS = 750L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val detectorLock = Any()
    private val gestureProcessor = HandGestureProcessor()

    private val _trackingState = MutableStateFlow(HandTrackingOutput())
    val trackingState: StateFlow<HandTrackingOutput> = _trackingState.asStateFlow()

    private val _faceState = MutableStateFlow(FaceRecognitionOutput())
    val faceState: StateFlow<FaceRecognitionOutput> = _faceState.asStateFlow()

    private val _errorState = MutableStateFlow<String?>(null)
    val errorState: StateFlow<String?> = _errorState.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var cameraProvider: ProcessCameraProvider? = null
    private var handLandmarker: HandLandmarker? = null
    private var faceDetector: FaceDetector? = null
    private var analysisExecutor: ExecutorService? = null
    private var lifecycleOwnerRef: WeakReference<LifecycleOwner>? = null
    private var previewViewRef: WeakReference<PreviewView>? = null
    private var recoveryAttempts = 0
    private var recoveryRunnable: Runnable? = null

    private val isActiveFlag = AtomicBoolean(false)
    private val isFaceScanInFlight = AtomicBoolean(false)
    private val frameTransforms = ConcurrentHashMap<Long, SquareRoiTransform>()

    @Volatile
    private var latestTransform: SquareRoiTransform? = null

    @Volatile
    private var lastFaceScanMs = 0L

    @Volatile
    private var mirrorHorizontally = true

    @Volatile
    private var enrolledProfiles: List<FaceProfileSnapshot> = emptyList()

    fun updateEnrolledProfiles(profiles: List<FaceProfileSnapshot>) {
        enrolledProfiles = profiles
    }

    fun start(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ) {
        if (isActiveFlag.getAndSet(true)) return
        recoveryAttempts = 0
        lifecycleOwnerRef = WeakReference(lifecycleOwner)
        previewViewRef = WeakReference(previewView)
        _errorState.value = null
        gestureProcessor.reset()
        frameTransforms.clear()
        latestTransform = null

        val executor = Executors.newSingleThreadExecutor()
        analysisExecutor = executor

        initHandLandmarker()
        initFaceDetector()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            if (!isActiveFlag.get()) return@addListener
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider
                bindCameraUseCases(provider, lifecycleOwner, previewView, executor)
            } catch (e: Exception) {
                Log.e(TAG, "CameraX provider initialization failed", e)
                _errorState.value = e.localizedMessage ?: "Front camera unavailable"
                stop()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases(
        provider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        executor: ExecutorService
    ) {
        if (!isActiveFlag.get()) return
        try {
            val resolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(480, 360),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()

            val preview = Preview.Builder()
                .setResolutionSelector(resolutionSelector)
                .build()
                .also { it.surfaceProvider = previewView.surfaceProvider }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(executor) { imageProxy ->
                        processImageProxy(imageProxy)
                    }
                }

            provider.unbindAll()
            val hasFront = provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
            mirrorHorizontally = hasFront
            val selector = if (hasFront) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            val camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, imageAnalyzer)
            _isRunning.value = true

            camera.cameraInfo.cameraState.observe(lifecycleOwner) { camState ->
                if (!isActiveFlag.get()) return@observe
                if (camState.type == CameraState.Type.OPEN) {
                    recoveryAttempts = 0
                    if (_errorState.value == "Reconnecting camera...") {
                        _errorState.value = null
                    }
                }
                val err = camState.error
                if (err != null) {
                    Log.w(TAG, "CameraState error code=${err.code}, type=${err.type}")
                    if (recoveryAttempts < MAX_CAMERA_RECOVERY_ATTEMPTS) {
                        recoveryAttempts += 1
                        _errorState.value = "Reconnecting camera..."
                        scheduleCameraRecovery()
                    } else {
                        _errorState.value = "Camera interrupted (code ${err.code})"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "CameraX binding failed", e)
            _errorState.value = e.localizedMessage ?: "Front camera unavailable"
            stop()
        }
    }

    private fun scheduleCameraRecovery() {
        recoveryRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            if (!isActiveFlag.get()) return@Runnable
            val provider = cameraProvider ?: return@Runnable
            val owner = lifecycleOwnerRef?.get() ?: return@Runnable
            val preview = previewViewRef?.get() ?: return@Runnable
            val executor = analysisExecutor ?: return@Runnable
            if (executor.isShutdown) return@Runnable
            bindCameraUseCases(provider, owner, preview, executor)
        }
        recoveryRunnable = runnable
        mainHandler.postDelayed(runnable, CAMERA_RECOVERY_DELAY_MS)
    }

    private fun initHandLandmarker() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_ASSET_PATH)
                .build()

            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(2)
                .setMinHandDetectionConfidence(0.55f)
                .setMinHandPresenceConfidence(0.55f)
                .setMinTrackingConfidence(0.55f)
                .setResultListener { result, _ ->
                    handleLandmarkerResult(result)
                }
                .setErrorListener { error ->
                    Log.w(TAG, "MediaPipe HandLandmarker error: ${error.message}")
                    _errorState.value = error.message
                }
                .build()

            synchronized(detectorLock) {
                handLandmarker?.close()
                handLandmarker = HandLandmarker.createFromOptions(context, options)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaPipe HandLandmarker", e)
            _errorState.value = "Hand Landmarker model unavailable"
        }
    }

    private fun initFaceDetector() {
        try {
            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.18f)
                .build()
            synchronized(detectorLock) {
                faceDetector?.close()
                faceDetector = FaceDetection.getClient(options)
            }
        } catch (e: Exception) {
            Log.w(TAG, "ML Kit FaceDetector unavailable: ${e.message}")
            synchronized(detectorLock) {
                faceDetector = null
            }
        }
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        if (!isActiveFlag.get()) {
            imageProxy.close()
            return
        }

        val rotationDeg = imageProxy.imageInfo.rotationDegrees
        val rawBitmap: Bitmap? = try {
            imageProxy.toBitmap()
        } catch (e: Exception) {
            Log.w(TAG, "toBitmap failed: ${e.message}")
            null
        } finally {
            // Release CameraX ImageReader buffer immediately before downstream ML work
            imageProxy.close()
        }

        if (rawBitmap == null || !isActiveFlag.get()) {
            rawBitmap?.recycle()
            return
        }

        var rotated: Bitmap? = null
        try {
            rotated = rotateBitmapIfNeeded(rawBitmap, rotationDeg)
            if (rotated !== rawBitmap) {
                rawBitmap.recycle()
            }

            val nowMs = SystemClock.uptimeMillis()

            // Trigger throttled on-device facial recognition using an isolated bitmap copy
            val activeDetector = synchronized(detectorLock) { faceDetector }
            if (activeDetector != null &&
                nowMs - lastFaceScanMs >= FACE_ANALYSIS_INTERVAL_MS &&
                isFaceScanInFlight.compareAndSet(false, true)
            ) {
                lastFaceScanMs = nowMs
                val faceBitmap = rotated.copy(Bitmap.Config.ARGB_8888, false)
                if (faceBitmap != null) {
                    val frameW = faceBitmap.width.coerceAtLeast(1)
                    val frameH = faceBitmap.height.coerceAtLeast(1)
                    activeDetector.process(InputImage.fromBitmap(faceBitmap, 0))
                        .addOnSuccessListener { faces ->
                            if (isActiveFlag.get()) {
                                handleFaceResults(faces, frameW, frameH)
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.w(TAG, "Face scan error: ${e.message}")
                        }
                        .addOnCompleteListener {
                            faceBitmap.recycle()
                            isFaceScanInFlight.set(false)
                        }
                } else {
                    isFaceScanInFlight.set(false)
                }
            }

            // Pad frame to 1:1 square so MediaPipe's PalmDetection -> LandmarkProjection
            // always sees a square ROI and never emits NORM_RECT / IMAGE_DIMENSIONS warnings.
            val tf = SquareRoiTransform.fromDimensions(rotated.width, rotated.height)
            val squareBitmap: Bitmap = if (rotated.width == rotated.height) {
                rotated
            } else {
                Bitmap.createBitmap(tf.squareSize, tf.squareSize, Bitmap.Config.ARGB_8888).also { sq ->
                    Canvas(sq).drawBitmap(rotated, tf.padX, tf.padY, null)
                    rotated.recycle()
                }
            }

            if (frameTransforms.size > 16) {
                val cutoff = nowMs - 2500L
                frameTransforms.keys.filter { it < cutoff }.forEach { frameTransforms.remove(it) }
            }
            frameTransforms[nowMs] = tf
            latestTransform = tf

            synchronized(detectorLock) {
                val landmarker = handLandmarker
                if (landmarker != null && isActiveFlag.get()) {
                    val mpImage = BitmapImageBuilder(squareBitmap).build()
                    landmarker.detectAsync(mpImage, nowMs)
                } else {
                    squareBitmap.recycle()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Frame analysis skipped: ${e.message}")
            if (rotated != null && !rotated.isRecycled) {
                rotated.recycle()
            }
        }
    }

    private fun handleFaceResults(faces: List<Face>, frameWidth: Int, frameHeight: Int) {
        val primary = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
        if (primary == null) {
            val cleared = FaceRecognitionOutput(faceDetected = false, status = "STANDBY")
            _faceState.value = cleared
            return
        }

        val box = primary.boundingBox
        val rawLeft = (box.left.toFloat() / frameWidth).coerceIn(0f, 1f)
        val rawRight = (box.right.toFloat() / frameWidth).coerceIn(0f, 1f)
        val normBox = NormalizedFaceBox(
            left = if (mirrorHorizontally) (1f - rawRight).coerceIn(0f, 1f) else rawLeft,
            top = (box.top.toFloat() / frameHeight).coerceIn(0f, 1f),
            right = if (mirrorHorizontally) (1f - rawLeft).coerceIn(0f, 1f) else rawRight,
            bottom = (box.bottom.toFloat() / frameHeight).coerceIn(0f, 1f)
        )

        val descriptor = extractFaceDescriptor(primary)
        val profiles = enrolledProfiles
        val match = if (descriptor.isNotEmpty() && profiles.isNotEmpty()) {
            FaceSignatureMatcher.matchProfile(descriptor, profiles)
        } else {
            null
        }

        val (label, status, confidence) = when {
            match != null -> Triple(match.personName, "VERIFIED", match.confidence)
            profiles.isEmpty() -> Triple("FACE DETECTED", "DETECTION_ONLY", if (descriptor.isNotEmpty()) 0.85f else 0.55f)
            else -> Triple("UNRECOGNIZED", "UNRECOGNIZED", 0.35f)
        }

        val output = FaceRecognitionOutput(
            faceDetected = true,
            matchedProfileId = match?.profileId,
            recognizedLabel = label,
            confidence = confidence,
            smilingProbability = primary.smilingProbability?.takeIf { it >= 0f },
            leftEyeOpenProbability = primary.leftEyeOpenProbability?.takeIf { it >= 0f },
            rightEyeOpenProbability = primary.rightEyeOpenProbability?.takeIf { it >= 0f },
            headYawDeg = primary.headEulerAngleY,
            headPitchDeg = primary.headEulerAngleX,
            headRollDeg = primary.headEulerAngleZ,
            normalizedBox = normBox,
            descriptor = descriptor,
            status = status
        )
        _faceState.value = output
        onFaceRecognized(output)
    }

    private fun extractFaceDescriptor(face: Face): List<Float> {
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: return emptyList()
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return emptyList()
        val noseBase = face.getLandmark(FaceLandmark.NOSE_BASE)?.position ?: return emptyList()
        val mouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position ?: return emptyList()
        val mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position ?: return emptyList()
        val mouthBottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position
        val leftCheek = face.getLandmark(FaceLandmark.LEFT_CHEEK)?.position
        val rightCheek = face.getLandmark(FaceLandmark.RIGHT_CHEEK)?.position

        return FaceSignatureMatcher.buildDescriptor(
            leftEye = FacePoint2D(leftEye.x, leftEye.y),
            rightEye = FacePoint2D(rightEye.x, rightEye.y),
            noseBase = FacePoint2D(noseBase.x, noseBase.y),
            mouthLeft = FacePoint2D(mouthLeft.x, mouthLeft.y),
            mouthRight = FacePoint2D(mouthRight.x, mouthRight.y),
            mouthBottom = mouthBottom?.let { FacePoint2D(it.x, it.y) },
            leftCheek = leftCheek?.let { FacePoint2D(it.x, it.y) },
            rightCheek = rightCheek?.let { FacePoint2D(it.x, it.y) },
            boxWidth = face.boundingBox.width().toFloat(),
            boxHeight = face.boundingBox.height().toFloat()
        )
    }

    private fun handleLandmarkerResult(result: HandLandmarkerResult) {
        if (!isActiveFlag.get()) return
        val tf = frameTransforms.remove(result.timestampMs()) ?: latestTransform
        val hands = result.landmarks().map { hand ->
            hand.map { lm ->
                NormalizedLandmarkPoint(
                    x = tf?.unmapX(lm.x()) ?: lm.x(),
                    y = tf?.unmapY(lm.y()) ?: lm.y(),
                    z = lm.z()
                )
            }
        }

        val output = gestureProcessor.processMultiHandLandmarks(
            hands = hands,
            nowMs = System.currentTimeMillis(),
            mirrorX = mirrorHorizontally
        )
        _trackingState.value = output

        if (output.pinchTriggered) {
            mainHandler.post {
                if (isActiveFlag.get()) {
                    onPinchAction()
                }
            }
        }
    }

    private fun rotateBitmapIfNeeded(source: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return source
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    fun resetPose() {
        gestureProcessor.reset()
        _trackingState.value = HandTrackingOutput()
    }

    fun stop() {
        val wasActive = isActiveFlag.getAndSet(false)
        _isRunning.value = false

        recoveryRunnable?.let { mainHandler.removeCallbacks(it) }
        recoveryRunnable = null
        recoveryAttempts = 0

        if (!wasActive && cameraProvider == null && analysisExecutor == null) {
            return
        }

        val providerToUnbind = cameraProvider
        cameraProvider = null
        if (providerToUnbind != null) {
            val unbindRunnable = Runnable {
                try {
                    providerToUnbind.unbindAll()
                } catch (_: Exception) {
                }
            }
            if (Looper.myLooper() == Looper.getMainLooper()) {
                unbindRunnable.run()
            } else {
                mainHandler.post(unbindRunnable)
            }
        }

        synchronized(detectorLock) {
            try {
                handLandmarker?.close()
            } catch (_: Exception) {
            }
            handLandmarker = null
            try {
                faceDetector?.close()
            } catch (_: Exception) {
            }
            faceDetector = null
        }

        try {
            analysisExecutor?.shutdownNow()
        } catch (_: Exception) {
        }
        analysisExecutor = null
        frameTransforms.clear()
        latestTransform = null
        isFaceScanInFlight.set(false)
        gestureProcessor.reset()
        _trackingState.value = HandTrackingOutput()
        _faceState.value = FaceRecognitionOutput()
    }
}
