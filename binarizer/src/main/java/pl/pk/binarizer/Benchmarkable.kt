package pl.pk.binarizer

interface Benchmarkable {
    var processedFrames: Int
    var totalProcessingTime: Long
    var lastFrameTime: Long

    fun getAverageTime(): Double {
        return if (processedFrames != 0 && totalProcessingTime != 0L) totalProcessingTime.toDouble() / processedFrames
        else 0.0
    }

    fun getAverageFPS(): Double {
        return if (getAverageTime() != 0.0) 1000.0 / getAverageTime()
        else 0.0
    }

}
