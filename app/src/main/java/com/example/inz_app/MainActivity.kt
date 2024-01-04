package com.example.inz_app

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import com.example.inz_app.ml.Model
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.*
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.lang.Thread.sleep
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis

class MainActivity : AppCompatActivity() {
//    private lateinit var viewModel: MlHandlerViewModel
    private lateinit var sensorHandler: SensorsHandler
    private lateinit var neuralNetworkInput: MutableList<List<Float>>
    lateinit var model: Model
    private lateinit var classifyTimer : Timer
    private lateinit var tvFall : TextView
    private lateinit var sensorsJob:Job
    var isFall = 0.0f
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        model = Model.newInstance(applicationContext)
        neuralNetworkInput = createListOfLists(70, 8, 0.0f)
        sensorHandler = SensorsHandler(applicationContext)
        tvFall = findViewById<TextView>(R.id.tvFall)


        findViewById<Button>(R.id.get_Synch_data).setOnClickListener {
            val readings = sensorHandler.getSyncData()
            Log.i("sync", readings.joinToString(", "))
        }
        findViewById<Button>(R.id.classify).setOnClickListener {
            classify()
            tvFall.text = "this signal is a fall with $isFall % chance"

        }
        val otherBtn = findViewById<SwitchMaterial>(R.id.launchCoroutineForSensors)
            otherBtn.setOnClickListener {
                val coroutineScope = CoroutineScope(Dispatchers.Default)
                if(otherBtn.isChecked){
                    sensorsJob = coroutineScope.launch {
                        val intervalNano = 1_000_000_000L / 70L //equivalent to 70Hz

                        while(isActive) {
                            updateSensors(intervalNano)
                        }
                    }
                }
                else{
                    sensorsJob.cancel("btn clicked")
                }
            }
        val btn = findViewById<SwitchMaterial>(R.id.launchCoroutineForNetwork)
            btn.setOnClickListener {
                if(btn.isChecked){
                    fullLoopClassify()
                }
                else{
                    classifyTimer.cancel()
                    tvFall.text ="Fall detection toggled off"
                    Toast.makeText(applicationContext,"classifying toggled off",Toast.LENGTH_SHORT).show()
                }

        }
    }

    private fun arrayToByteBuffer(samples: MutableList<List<Float>>): ByteBuffer {
        val byteBuffer =
            ByteBuffer.allocateDirect(samples.size * samples[0].size * 4) // Assuming 4 bytes per float
        byteBuffer.order(ByteOrder.nativeOrder()) //not sure if needed

        for (i in samples.indices) {
            for (j in samples[i].indices) {
                byteBuffer.putFloat(samples[i][j])
            }
        }
        byteBuffer.flip()
        return byteBuffer
    }

    fun classify() {
//        Log.i("rnn_input_sizes","${neuralNetworkInput.size} x ${neuralNetworkInput[0].size}")
        // Creates inputs for reference.
        val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 70, 8), DataType.FLOAT32)
        var byteBuffer = arrayToByteBuffer(neuralNetworkInput)
        inputFeature0.loadBuffer(byteBuffer)

        // Runs model inference and gets result.
        val outputs = model.process(inputFeature0)
        val outputFeature0 = outputs.outputFeature0AsTensorBuffer
        isFall = outputFeature0.floatArray[0]
        Log.i("classify", "this signal is a $isFall")
    }

    private fun createListOfLists(rows: Int, cols: Int, value: Float): MutableList<List<Float>> {
        val outerList = mutableListOf<List<Float>>()
        for (i in 0 until rows) {
            val innerList =
                MutableList<Float>(cols) { value } // Initialize inner list with zeros (you can change this as needed)
            outerList.add(innerList)
        }
        return outerList
    }

    fun shiftMatrixByRows(matrix: List<List<Float>>, shiftRows: Int): List<List<Float>> {
        val numRows = matrix.size
        val numCols = if (matrix.isNotEmpty()) matrix[0].size else 0

        return List(numRows) { row ->
            List(numCols) { col ->
                matrix[(row + shiftRows + numRows) % numRows][(col + numCols) % numCols]
            }
        }
    }
    fun updateSensors(intervalNano:Long){
        var elapsed = measureNanoTime {
            val readings= sensorHandler.getSyncData().toList()
            shiftMatrixByRows(neuralNetworkInput,1)
            neuralNetworkInput[0] = readings
            Log.i("sync", readings.joinToString(", "))
        }
        if(elapsed < intervalNano){
            elapsed = intervalNano - elapsed
            sleep(elapsed / 1_000_000L,(elapsed % 1_000_000).toInt())
            Log.i("timing","had to wait $elapsed")
        }
        else{
            Log.i("timing","exceeded ${elapsed-intervalNano} ns ")
        }
    }
    fun fullLoopClassify(){
        classifyTimer = Timer()
        classifyTimer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                val time = measureTimeMillis { classify()
                }
                runOnUiThread {
                    tvFall.text = "this signal is a fall with $isFall chance"
                }
                Log.i("classifyTime", "classify took $time ms")
            } },10L,500L,)
    }

    override fun onResume() {
        model = Model.newInstance(applicationContext)
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
        classifyTimer.cancel()
        sensorsJob.cancel("onPause method")
        model.close()
    }
}
