package com.example.inz_app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.system.measureTimeMillis

class SensorsHandler(private val context: Context) : SensorEventListener {

    private lateinit var sensorManager: SensorManager

    private var accelerometerReading = FloatArray(3)
    private var magnetometerReading = FloatArray(3)
    private var gyroscopeReadings = FloatArray(3)
    private val scalingArray : Array<Pair<Float,Float>> = arrayOf(
        -13.73f to 15.74f,
        -16.00f to 14.24f,
        -13.73f to 15.74f,
        -1844.21f to 1997.99f,
//        -768.47f to 1172.01f,
        -1844.21f to 1997.99f,
        -1844.21f to 1997.99f,
        -87.71f to 89.85f,
        0.00f to 360f
    )

    private lateinit var magneticFieldSensor: Sensor
    private lateinit var acceleraionSensor: Sensor
    private lateinit var gyroscopeSensor: Sensor

    init {
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        magneticFieldSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)!!
        acceleraionSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)!!
        gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)!!

        sensorManager.registerListener(
            this, magneticFieldSensor,
            SensorManager.SENSOR_DELAY_FASTEST
        )
        sensorManager.registerListener(
            this, acceleraionSensor,
            SensorManager.SENSOR_DELAY_FASTEST
        )
        sensorManager.registerListener(
            this, gyroscopeSensor,
            SensorManager.SENSOR_DELAY_FASTEST
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Handle accuracy changes if needed
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
            }
            Sensor.TYPE_GYROSCOPE -> {
                System.arraycopy(event.values, 0, gyroscopeReadings, 0, gyroscopeReadings.size)
            }
        }
    }


    fun getSyncData():FloatArray{
        var orientationReadings = floatArrayOf(0.0f,0.0f)
        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)
        val time = measureTimeMillis {
        SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)
        val orientation = SensorManager.getOrientation(rotationMatrix, orientationAngles)
//        ignore first argument, azimuth is not necessary
        var pitch = Math.toDegrees(orientation[1].toDouble())
        var roll = Math.toDegrees(orientation[2].toDouble())

//        val pitch = orientation[1]
//        val roll = orientation[2]

//        scale acc to g
        accelerometerReading = accelerometerReading.map { it / 9.81f }.toFloatArray()

        roll = if (roll > 0) roll else  roll //convert to range (0,360)
        orientationReadings = floatArrayOf(pitch.toFloat(),roll.toFloat())
        }
        Log.i("getSyncTime","sync function takes $time ms")
        var arr = accelerometerReading + gyroscopeReadings +orientationReadings
        var array = arr.toList().toMutableList()
        for (i in 0 until array.size){
            array[i] = 2* (arr[i] - scalingArray[i].first) / (scalingArray[i].second - scalingArray[i].first) -1
        }
        return array.toFloatArray()
    }

    fun unregisterListener() {
        sensorManager.unregisterListener(this)
    }
//    debug method to get sensors info and resolution
//    fun getAllSensorsInfo() {
//        val sensorList: List<Sensor> = sensorManager.getSensorList(Sensor.TYPE_ALL)
//
//        for (sensor in sensorList) {
//            Log.d("SensorInfo", "Sensor Name: ${sensor.name}")
//            Log.d("SensorInfo", "Sensor Type: ${sensor.type}")
//            Log.d("SensorInfo", "Sensor Vendor: ${sensor.vendor}")
//            Log.d("SensorInfo", "Sensor Version: ${sensor.version}")
//            Log.d("SensorInfo", "Sensor Resolution: ${sensor.resolution}")
//            Log.d("SensorInfo", "Sensor Power: ${sensor.power} mA")
//            Log.d("SensorInfo", "---------------------------------")
//        }
//    }
}