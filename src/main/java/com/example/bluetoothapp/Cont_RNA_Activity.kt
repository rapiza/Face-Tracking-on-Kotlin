package com.example.bluetoothapp

import android.app.AlertDialog
import android.app.ProgressDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.DialogInterface
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.os.AsyncTask
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.*
import com.example.bluetoothapp.ml.MobileFaceNet
import org.opencv.android.*
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.CascadeClassifier
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.lang.reflect.Array
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.*
import kotlin.collections.ArrayList


class Cont_RNA_Activity : AppCompatActivity(),CameraBridgeViewBase.CvCameraViewListener2 {

    companion object {
        var m_myUUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        var m_bluetoothSocket: BluetoothSocket? = null
        lateinit var m_progress: ProgressDialog
        lateinit var m_bluetoothAdapter: BluetoothAdapter
        var m_isConnected: Boolean = false
        lateinit var m_address: String

        // Face model
        private const val FACE_DIR = "facelib"
        private const val FACE_MODEL = "haarcascade_frontalface_alt2.xml"
        private const val byteSize = 4096 // buffer size

        //FaceNet MObile
        private const val IMAGE_MEAN = 127.5f
        private const val IMAGE_STD = 127.5f
    }

    private val imCameraView  by lazy { findViewById<JavaCamera2View>(R.id.Cm2View) }
    lateinit var cvBaseLoaderCallback: BaseLoaderCallback
    private lateinit var switch: Switch
    private lateinit var back: Button
    private lateinit var fab: View
    lateinit  var frameGray: Mat
    lateinit var frame: Mat
    lateinit var FRAME: Mat
    var alto = 0
    var ancho = 0
    var xx = 0
    var yy = 0
    var flag = 0
    var flagtw = 1
    //Variables para leer el detector Face
    // face library
    var faceDetector: CascadeClassifier? = null
    lateinit var faceDir: File
    var imageRatio = 0.0 // Para escalar la imagen
    //var bboxes: MutableList<Double> = mutableListOf()
    var bboxes = arrayListOf<DoubleArray>()
    var rost = arrayListOf<Bitmap>()
    var refNombre = arrayListOf<String>()
    var embedding_ref = arrayListOf<FloatArray>()

    val interpreter by lazy {
        Interpreter(loadmodelFile())
    }
    // Our model expects a RGB image, hence the channel size is 3
    private val channelSize = 3

    // Width of the image that our model expects
    var inputImageWidth = 112

    // Height of the image that our model expects
    var inputImageHeight = 112

    // Size of the input buffer size (if your model expects a float input, multiply this with 4)
    private var modelInputSize = 4*inputImageWidth * inputImageHeight * channelSize


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_cont__r_n_a_)

        //println("verla identidad de estos arrya ${resultArray[0]} EEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE")
         instancia()
        val bundle = intent.extras
        val direcc = bundle?.get("Device_address")
        m_address = direcc.toString()

        ConnectToDevice(this).execute()
        //se desconecta la comunicacion y regresa a la pantalla principal
        back.setOnClickListener(){
            disconnect()
        }


        //para la camara permiso y activacion y tambien asegurarse de tener Opencv dentro del paquete
        imCameraView.setCameraPermissionGranted()//acordarse de esta linea
        imCameraView.visibility = SurfaceView.VISIBLE
        imCameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_BACK)
        imCameraView.setCvCameraViewListener(this)

        cvBaseLoaderCallback = object: BaseLoaderCallback(this){
            override fun onManagerConnected(status: Int) {
                when (status){
                    SUCCESS -> {
                        //Log.i(TAG, "OpenCV loaded successfully")
                        loadFaceLib()
                        if (faceDetector!!.empty()) {
                            faceDetector = null
                        } else {
                            faceDir.delete()
                        }
                        Toast.makeText(applicationContext, "Opencv Siiiii", Toast.LENGTH_SHORT).show()
                        imCameraView.enableView()
                    }
                    else ->{
                        super.onManagerConnected(status)
                    }
                }
            }
        }

        switch.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                // The switch is enabled/checked
                //Toast.makeText(this, "Estas Aqu'i se supone el analisis ", Toast.LENGTH_SHORT).show()
                flag = 1
            } else {
                // The switch is disabled
                flag = 0
                //Toast.makeText(this, "regresa a la pantalla principal", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun instancia(){
        switch = findViewById(R.id.Btn_switch)
        back = findViewById(R.id.Btn_bc)
        fab  = findViewById(R.id.Btn_Agr)
    }

    private fun sendComand(input: String){
        if(m_bluetoothSocket != null){
            try{
                //println("Envio de datos al dispositivo conectado")
                m_bluetoothSocket!!.outputStream.write(input.toByteArray())
            }catch (e: IOException){
                e.printStackTrace()
            }
        }
    }

    private fun disconnect(){
        if (m_bluetoothSocket != null) {
            try {
                m_bluetoothSocket!!.close()
                m_bluetoothSocket = null
                m_isConnected = false
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        finish()
    }

    private class ConnectToDevice(c: Context): AsyncTask<Void,Void,String>(){
        private var connectSuccess: Boolean = true
        private val context: Context
        init{
            this.context = c
        }

        override fun onPreExecute() {
            super.onPreExecute()
            m_progress = ProgressDialog.show(context, "Connecting...", "please wait")
        }

        override fun doInBackground(vararg params: Void?): String? {
            try {
                if (m_bluetoothSocket == null || !m_isConnected) {
                    m_bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                    val device: BluetoothDevice = m_bluetoothAdapter.getRemoteDevice(m_address)
                    m_bluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(m_myUUID)
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery()
                    m_bluetoothSocket!!.connect()
                }
            } catch (e: IOException) {
                connectSuccess = false
                e.printStackTrace()
            }
            return null
        }

        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)
            if (!connectSuccess) {
                Log.i("data", "couldn't connect")
            } else {
                m_isConnected = true
            }
            m_progress.dismiss()
        }
    }

    override fun onCameraViewStarted(width: Int, height: Int) {
        frame = Mat(width,height,CvType.CV_8UC4)
        frameGray = Mat(width,height,CvType.CV_8UC4)
        FRAME = Mat(width,height,CvType.CV_8UC4)
    }

    override fun onCameraViewStopped() {
        frame.release()
        frameGray.release()
        FRAME.release()
    }

    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?): Mat {
        frame = inputFrame!!.rgba()
        //Imgproc.cvtColor(frame,frameGray,Imgproc.COLOR_RGBA2GRAY)
        //println("mirar que es raro")
        //println(bboxes)
        centroImagen()
        //Imgproc.line(frame, Point(0.0,(alto/2).toDouble()), Point(ancho.toDouble(),(alto/2).toDouble()),Scalar(150.0,50.0,80.0),2)//linea horizontal
        //Imgproc.line(frame, Point((ancho/2).toDouble(),0.0), Point((ancho/2).toDouble(),alto.toDouble()),Scalar(150.0,30.0,80.0),2)//linea vertical
        bboxes.clear()
        if(flag == 1) {
            //frameGray = inputFrame.gray() //imagen en escala de grises
            //imageRatio = 1.0
            frameGray = getImage(inputFrame.gray())
            detectFaces(frameGray,1.3,5)
            rost = extract_face(frame,bboxes,112.0,112.0)
            if(rost.size==0){
                sendComand("K:-960:0:A")
            }
            for(face_des in 0..rost.size-1){
                draw_box(frame,bboxes, Scalar(25.0,2.0,255.0),3)
                if(fab.isPressed == false && flagtw == 1){

                    var reslDescEmbddg = Array(1){FloatArray(192)}
                    interpreter.run(convertBitmapToByteBuffer(rost[face_des]),reslDescEmbddg)
                     for(reff in 0..refNombre.size-1){
                        var valrDistancia = clcCoinc(reff,reslDescEmbddg[0])
                        if(valrDistancia <=1.0){
                            var xo = bboxes[face_des][0]
                            var yo = bboxes[face_des][1]
                            var xf = bboxes[face_des][2]
                            var yf = bboxes[face_des][3]
                            var ctrX = xo + ((xf-xo)/2)
                            var ctrY = yo + ((yf-yo)/2)
                            var area = ((xf-xo)*(yf-yo))
                            var error = ctrX-xx
                            var datos = "K:"+(error.toInt()).toString()+":"+((area*100/300000).toInt()).toString()+":A"
                            sendComand(datos)
                            Imgproc.rectangle(frame, Point(xo,yo), Point(xf,yf),Scalar(25.0,2.0,255.0),3)
                            Imgproc.putText(frame,refNombre[reff],Point(xo,(yo-15)), Imgproc.FONT_ITALIC, 1.5,Scalar(0.0,255.0,0.0),2)
                            Imgproc.putText(frame,"X: ${ctrX.toInt()} Y: ${ctrY.toInt()}", Point(ctrX,ctrY),Imgproc.FONT_HERSHEY_COMPLEX_SMALL,1.5,Scalar(255.0,0.0,0.0),3)
                            Imgproc.putText(frame,"Error: ${error.toInt()}", Point(ctrX,ctrY+100),Imgproc.FONT_HERSHEY_COMPLEX_SMALL,1.5,Scalar(255.0,0.0,0.0),3)
                            Imgproc.putText(frame,"Area: ${area.toInt()} Porcentaje: ${(area*100/300000).toInt()}", Point(ctrX,ctrY+50),Imgproc.FONT_HERSHEY_COMPLEX_SMALL,1.5,Scalar(255.0,0.0,0.0),3)
                        }else{
                            sendComand("K:-960:0:A")
                        }
                     }
                }else{
                    flagtw = 0
                    sendComand("K:-960:0:A")
                }
            }
        }else{
            sendComand("K:-960:0:A")
        }
        return frame
    }

    private fun clcCoinc(idx:Int, vctDsc:FloatArray):Float{
        var distancia:Float = 0.0f

        for(h in 0..vctDsc.size-1){
            var difff = vctDsc[h]-embedding_ref[idx][h]
            distancia += difff*difff
        }
        distancia = Math.sqrt(distancia.toDouble()).toFloat()
        return distancia
    }
    //Funciones implementadas para el proyecto
    private fun centroImagen(){
        ancho = frame?.width()!!
        alto = frame?.height()!!
        /*print("Ancho o número de columnas: ")
        println(ancho)
        print("Alto o número de filas: ")
        println(alto)*/
        //ancho es 1920 y alto es 864
        xx= ancho/2
        yy=alto/2
    }

    private fun detectFaces(image:Mat,fcScalar:Double,minNeig:Int){
        val face = MatOfRect()
        faceDetector!!.detectMultiScale(image,face,fcScalar,minNeig)
        for (rect in face.toArray()) {
            var x = 0.0
            var y = 0.0
            var w = 0.0
            var h = 0.0
            if (imageRatio.equals(1.0)) { //Bounding Box para la imagen no escalada
                x = rect.x.toDouble()
                y = rect.y.toDouble()
                w = x + rect.width
                h = y + rect.height
            } else {  //Bounding Box para la imagen escalada
                x = rect.x.toDouble() / imageRatio
                y = rect.y.toDouble() / imageRatio
                w = x + (rect.width / imageRatio)
                h = y + (rect.height / imageRatio)
            }
            bboxes.addAll(listOf(doubleArrayOf(x,y,w,h)))
        }
    }

    //FFunciones para escalar la imagen
    fun ratioTo(src: Size): Double {
        val w = src.width
        val h = src.height
        val heightMax = 200
        var ratio: Double = 0.0

        if (w > h) {
            if (w < heightMax) return 1.0
            ratio = heightMax / w
        } else {
            if (h < heightMax) return 1.0
            ratio = heightMax / h
        }

        return ratio
    }

    fun getImage(src: Mat): Mat {
        val imageSize = Size(src.width().toDouble(), src.height().toDouble())
        imageRatio = ratioTo(imageSize)

        if (imageRatio.equals(1.0)) return src

        val dstSize = Size(imageSize.width*imageRatio, imageSize.height*imageRatio)
        val dst = Mat()
        Imgproc.resize(src, dst, dstSize)
        return dst
    }

    private fun draw_box(image:Mat,listbox:ArrayList<DoubleArray>,color:Scalar,line_width:Int):Mat{
        if (listbox.isEmpty()){
            return image
        }else{
            for (item in listbox) {
                Imgproc.rectangle(image, Point(item[0],item[1]), Point(item[2],item[3]),color,line_width)
            }
        }
        return image
    }

    private fun extract_face(image:Mat,listBx:ArrayList<DoubleArray>,cl:Double,rw:Double):ArrayList<Bitmap>{
        var rostros_almc = arrayListOf<Bitmap>()
        lateinit var face:Mat
        lateinit var roi:Rect
        for (box in listBx){
            var x = box[0]
            var y = box[1]
            var xf = box[2]
            var yf = box[3]
            roi = Rect(x.toInt(),y.toInt(),(xf-x).toInt(),(yf-y).toInt())
            face = image.submat(roi)
            Imgproc.resize(face,face,Size(cl,rw))

            var mtrImg: Bitmap = Bitmap.createBitmap(face.cols(),face.rows(),Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(face,mtrImg)

            rostros_almc.addAll(listOf(mtrImg))
            fab.setOnClickListener(){
                mstrImagen(mtrImg)
            }
        }
        return rostros_almc
    }

    private fun mstrImagen(imgbit:Bitmap){
        var IMGCV:ByteBuffer
        val dialog = AlertDialog.Builder(this)
        val dialogView = layoutInflater.inflate(R.layout.custom_dialog,null)
        val etName = dialogView.findViewById<EditText>(R.id.text_ing)
        val showImg = dialogView.findViewById<ImageView>(R.id.imageView)
        dialog.setView(dialogView)
        dialog.setCancelable(false)
        dialog.setPositiveButton("Add",{ dialogInterface: DialogInterface, i: Int -> })

        //var mtrImg: Bitmap = Bitmap.createBitmap(imgRef.cols(),imgRef.rows(),Bitmap.Config.ARGB_8888)
        //Utils.matToBitmap(imgRef,mtrImg)
        showImg.setImageBitmap(imgbit)
        IMGCV = convertBitmapToByteBuffer(imgbit)
        var resultArray =  Array(1){ FloatArray(192)}
        interpreter.run(IMGCV,resultArray)
        val customDialog = dialog.create()
        customDialog.show()
        customDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(){
            if(etName.text.isNotEmpty()){
                var nomb: String = etName.text.toString()

                refNombre.addAll(listOf(nomb))
                embedding_ref.addAll(listOf(resultArray[0]))
                customDialog.dismiss()
                flagtw = 1
            }else{
                Toast.makeText(applicationContext, "Ingresa - Nombre ", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadmodelFile(): MappedByteBuffer {
        val fileDescriptor: AssetFileDescriptor = assets.openFd("mobile_face_net.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel: FileChannel = inputStream.channel
        val startOffset: Long = fileDescriptor.startOffset
        val declaredLength: Long = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun convertBitmapToByteBuffer(imgBitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(modelInputSize)
        byteBuffer.order(ByteOrder.nativeOrder())
        //calcula el numero de pixeles en la imagen
        val pixels = IntArray(inputImageWidth * inputImageHeight)
        imgBitmap.getPixels(pixels, 0, imgBitmap.width, 0, 0, imgBitmap.width, imgBitmap.height)
        var pixel = 0
        for (i in 0 until inputImageWidth) {
            for (j in 0 until inputImageWidth) {
                val pixelVal = pixels[pixel++]
                byteBuffer.putFloat(((pixelVal shr 16 and 0xFF)- IMAGE_MEAN) / IMAGE_STD)
                byteBuffer.putFloat(((pixelVal shr 8 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                byteBuffer.putFloat(((pixelVal and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                //byteBuffer.put((pixelVal shr 16 and 0xFF).toByte())
                //byteBuffer.put((pixelVal shr 8 and 0xFF).toByte())
                //byteBuffer.put((pixelVal and 0xFF).toByte())
            }
        }
        //imgBitmap.recycle()
        return byteBuffer
    }


    override fun onResume() {
        super.onResume()
        if (!OpenCVLoader.initDebug()) {
            //Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization")
            Toast.makeText(this, "No se encuentra Opencv", Toast.LENGTH_SHORT).show()
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, cvBaseLoaderCallback)
        } else {
            //Log.d(TAG, "OpenCV library found inside package. Using it!")
            Toast.makeText(this, "Libreria de Opencv encontrada", Toast.LENGTH_SHORT).show()
            cvBaseLoaderCallback.onManagerConnected(BaseLoaderCallback.SUCCESS)
        }
    }

    override fun onPause() {
        super.onPause()
        if (imCameraView != null){
            imCameraView?.disableView()
        }
        if (faceDir.exists()) faceDir.delete()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (imCameraView != null){
            imCameraView?.disableView()
        }
    }

    private fun loadFaceLib() {
        try {
            val modelInputStream =
                    resources.openRawResource(
                            R.raw.haarcascade_frontalface_alt2)

            // create a temp directory
            faceDir = getDir(FACE_DIR, Context.MODE_PRIVATE)

            // create a model file
            val faceModel = File(faceDir, FACE_MODEL)

            if (!faceModel.exists()) { // copy model
                // copy model to new face library
                val modelOutputStream = FileOutputStream(faceModel)

                val buffer = ByteArray(byteSize)
                var byteRead = modelInputStream.read(buffer)
                while (byteRead != -1) {
                    modelOutputStream.write(buffer, 0, byteRead)
                    byteRead = modelInputStream.read(buffer)
                }

                modelInputStream.close()
                modelOutputStream.close()
            }

            faceDetector = CascadeClassifier(faceModel.absolutePath)
        } catch (e: IOException) {
            Toast.makeText(this, "Error loading cascade face model...$e", Toast.LENGTH_SHORT).show()
        }
    }
}