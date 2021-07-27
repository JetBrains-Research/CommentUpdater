package org.jetbrains.research.commentupdater.models

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment

/**
 * Converts 1d, 2d or 3d List to ONNXTensor
 * This tensors must be closed!
 */
object ONNXTensorUtils {

    fun twoDListToTensor(data: List<List<Long>>, environment: OrtEnvironment): OnnxTensor? {
        if (data.isEmpty())
            throw Exception("Can't handle empty lists")
        val arrayData = Array(data.size) {
            LongArray(data[0].size)
        }
        for (i in 0 until data.size) {
            for (j in 0 until data[0].size) {
                arrayData[i][j] = data[i][j]
            }
        }
        return OnnxTensor.createTensor(environment, arrayData)
    }

    fun oneDListToTensor(data: List<Long>, environment: OrtEnvironment): OnnxTensor? {
        if (data.isEmpty())
            throw Exception("Can't handle empty lists")
        val arrayData = LongArray(data.size)
        for (i in 0 until data.size) {
            arrayData[i] = data[i]
        }
        return OnnxTensor.createTensor(environment, arrayData)
    }

    fun threeDListToTensor(data: List<List<List<Float>>>, environment: OrtEnvironment): OnnxTensor? {
        if (data.isEmpty() || data[0].isEmpty())
            throw Exception("Can't handle empty lists")
        val arrayData = Array(data.size) {
            Array(data[0].size) {
                FloatArray(data[0][0].size)
            }
        }
        for (i in 0 until data.size) {
            for (j in 0 until data[0].size) {
                for (k in 0 until data[0][0].size) {
                    arrayData[i][j][k] = data[i][j][k]
                }
            }
        }
        return OnnxTensor.createTensor(environment, arrayData)
    }
}