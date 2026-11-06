package kz.aita

import java.util.zip.Inflater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal actual suspend fun inflateStockFile(bytes: ByteArray, expectedSize: Int): ByteArray =
    withContext(Dispatchers.IO) {
        if (expectedSize !in 0..8 * 1024 * 1024) throw StockFileProblem("file_limit")
        val inflater = Inflater(true)
        try {
            inflater.setInput(bytes)
            val output = ByteArray(expectedSize + 1)
            var used = 0
            while (!inflater.finished()) {
                val n = inflater.inflate(output, used, output.size - used)
                used += n
                if (used > expectedSize) throw StockFileProblem("file_limit")
                if (n == 0 && !inflater.finished()) throw StockFileProblem("file_format")
            }
            if (used != expectedSize || inflater.remaining != 0)
                throw StockFileProblem("file_format")
            output.copyOf(used)
        } catch (e: StockFileProblem) {
            throw e
        } catch (_: Exception) {
            throw StockFileProblem("file_format")
        } finally {
            inflater.end()
        }
    }
