package imagemodels

import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import javax.imageio.ImageIO

class SimpleGrayscaleImage(width: Int, height: Int) {
    var width: Int = 0
        private set
    var height: Int = 0
        private set
    private var data: ByteBuffer? = null
    private var numPixels: Int = 0

    init {
        this.width = width
        this.height = height
        this.numPixels = width * height
        this.data = ByteBuffer.allocateDirect(width * height)
    }

    constructor(image: BufferedImage) : this(image.width, image.height) {
        loadImage(image)
        resizeToNextSize()
        blur()
    }

    fun blur() {
        if (width <= 6 || height <= 6) {
            return
        }
        // First a horizontal pass
        var buffer = IntArray(width)
        var buffer_1 = IntArray(width)
        var buffer_2 = IntArray(width)
        this.data!!.rewind()
        for (y in 0 until height) {
            // Copy this horizontal line
            for (i in 0 until width) {
                buffer[i] = this.data!!.get(width * y + i) and 0xFF
                buffer_1[i] = (buffer[i] shr 1).toByte().toInt()  // buffer * 0.5
                buffer_2[i] = (buffer[i] shr 2).toByte().toInt()  // buffer * 0.25
            }
            // idx: 0
            var t = ((buffer[0] + buffer_1[1] + buffer_2[2]) * 585 shr 10).toLong() // 1024/585 = 1.75042
            this.data!!.put(width * y, (if (t > 255) 255 else t).toByte())
            t = ((buffer_1[0] + buffer[1] + buffer_1[2] + buffer_2[3]) * 455 shr 10).toLong()  // 1024/455 = 2.250549
            this.data!!.put(width * y + 1, (if (t > 255) 255 else t).toByte())
            for (x in 2 until width - 2) {
                t =
                        (((buffer_2[x - 2] + buffer_1[x - 1] + buffer[x] + buffer_1[x + 1] + buffer_2[x + 2]).toLong() * 409).toInt() shr 10).toLong() // 1024/409 = 2.503 ~ 1 + 2*0.5 + 2*0.25
                this.data!!.put(width * y + x, (if (t > 255) 255 else t).toByte())
            }
            var x = width - 2
            t =
                    ((buffer_2[x - 2] + buffer_1[x - 1] + buffer[x] + buffer_1[x + 1]) * 455 shr 10).toLong() // 1024/455 = 2.250549
            this.data!!.put(width * y + x, (if (t > 255) 255 else t).toByte())
            x++
            t = ((buffer_2[x - 2] + buffer_1[x - 1] + buffer[x]) * 585 shr 10).toLong() // 1024/585 = 1.75042
            this.data!!.put(width * y + x, (if (t > 255) 255 else t).toByte())
        }

        // Now a vertical pass
        buffer = IntArray(height)
        buffer_1 = IntArray(height)
        buffer_2 = IntArray(height)
        for (x in 0 until width) {
            // Copy this vertical line
            for (i in 0 until height) {
                buffer[i] = this.data!!.get(width * i + x) and 0xFF
                buffer_1[i] = (buffer[i] shr 1).toByte().toInt()  // buffer * 0.5
                buffer_2[i] = (buffer[i] shr 2).toByte().toInt()  // buffer * 0.25
            }
            // y = 0
            this.data!!.put(x, ((buffer[0] + buffer_1[1] + buffer_2[2]) * 585 shr 10).toByte()) // 1024/585 = 1.75042
            // y = 1
            this.data!!.put(
                width + x,
                ((buffer_1[0] + buffer[1] + buffer_1[2] + buffer_2[3]) * 455 shr 10).toByte()
            ) // 1024/455 = 2.250549
            for (y in 2 until height - 2) {
                val t =
                    (buffer_2[y - 2] + buffer_1[y - 1] + buffer[y] + buffer_1[y + 1] + buffer_2[y + 2]).toLong() * 409 shr 10
                this.data!!.put(
                    width * y + x,
                    (if (t > 255) 255 else t).toByte()
                ) // 1024/409 = 2.503 ~ 1 + 2*0.5 + 2*0.25
            }
            var y = height - 2
            this.data!!.put(
                width * y + x,
                ((buffer_2[y - 2] + buffer_1[y - 1] + buffer[y] + buffer_1[y + 1]) * 455 shr 10).toByte()
            ) // 1024/455 = 2.250549
            y++
            this.data!!.put(
                width * y + x,
                ((buffer_2[y - 2] + buffer_1[y - 1] + buffer[y]) * 585 shr 10).toByte()
            ) // 1024/585 = 1.75042
        }
    }

    private fun resizeToNextSize() {
        var min = if (width < height) width else height
        min = getClosestSmallerPowerOf2(min)
        this.resize(min, min)
    }

    private fun getClosestSmallerPowerOf2(value: Int): Int {
        var i = 0
        var v = 1
        while (v < value && i < 24) {
            i++
            v = v shl 1
        }
        return v shr 1
    }

    fun resize(dest_width: Int, dest_height: Int) {
        val newData = ByteBuffer.allocateDirect(dest_width * dest_height)

        val tx = width.toDouble() / dest_width
        val ty = height.toDouble() / dest_height

        var Cc: Int
        val C = IntArray(5)
        var d0: Int
        var d2: Int
        var d3: Int
        var a0: Int
        var a1: Int
        var a2: Int
        var a3: Int

        for (i in 0 until dest_height) {
            for (j in 0 until dest_width) {
                val x = (tx * j).toInt()
                val y = (ty * i).toInt()
                val dx = tx * j - x
                val dy = ty * i - y

                for (jj in 0..3) {
                    d0 = safeGet((y - 1 + jj) * width + (x - 1)) - safeGet((y - 1 + jj) * width + x)
                    d2 = safeGet((y - 1 + jj) * width + (x + 1)) - safeGet((y - 1 + jj) * width + x)
                    d3 = safeGet((y - 1 + jj) * width + (x + 2)) - safeGet((y - 1 + jj) * width + x)
                    a0 = safeGet((y - 1 + jj) * width + x)
                    a1 = (-1.0 / 3 * d0 + d2 - 1.0 / 6 * d3).toInt()
                    a2 = (1.0 / 2 * d0 + 1.0 / 2 * d2).toInt()
                    a3 = (-1.0 / 6 * d0 - 1.0 / 2 * d2 + 1.0 / 6 * d3).toInt()
                    C[jj] = (a0.toDouble() + a1 * dx + a2.toDouble() * dx * dx + a3.toDouble() * dx * dx * dx).toInt()

                    d0 = C[0] - C[1]
                    d2 = C[2] - C[1]
                    d3 = C[3] - C[1]
                    a0 = C[1]
                    a1 = (-1.0 / 3 * d0 + d2 - 1.0 / 6 * d3).toInt()
                    a2 = (1.0 / 2 * d0 + 1.0 / 2 * d2).toInt()
                    a3 = (-1.0 / 6 * d0 - 1.0 / 2 * d2 + 1.0 / 6 * d3).toInt()
                    Cc = (a0.toDouble() + a1 * dy + a2.toDouble() * dy * dy + a3.toDouble() * dy * dy * dy).toInt()
                    newData.put(i * dest_width + j, (Cc and 0xFF).toByte())
                }
            }
        }
        this.data = newData
        this.width = dest_width
        this.height = dest_height
        this.numPixels = width * height
    }


    private fun safeGet(index: Int): Int {
        if (index < 0) {
            return 0
        }
        return if (index >= numPixels) {
            0
        } else this.data!!.get(index) and 0xFF
    }

    fun loadImage(image: BufferedImage) {
        val numPixels = width * height
        val numComponents = image.colorModel.numComponents
        var maxPixel = 0
        if (image.colorModel.getComponentSize(0) == BYTE_SIZE) {
            // Components are byte sized
            val bufferSize = numPixels * numComponents
            val tempBuffer: ByteArray
            try {
                tempBuffer = ByteArray(bufferSize)
            } catch (e: OutOfMemoryError) {
                println("Died trying to allocate a buffer of size: $bufferSize. Please increas heap size!!")
                throw e
            }

            image.raster.getDataElements(0, 0, width, height, tempBuffer)

            if (numComponents == 1) {
                // Already byte gray
                this.data!!.put(tempBuffer, 0, numPixels)
            } else if (image.type == BufferedImage.TYPE_3BYTE_BGR) {
                var j = 0
                var i = 0
                while (i < bufferSize) {
                    // V1
                    //                        long yTemp = ((tempBuffer[i]& 0xFF)*BLUE_Y_COEFF +
                    //                                (tempBuffer[i+1]& 0xFF)*GREEN_Y_COEFF +
                    //                                (tempBuffer[i+2]& 0xFF)*RED_Y_COEFF);
                    //                        int y = (int) (((yTemp >> 10) & 0xFF) + ((yTemp >> 9) & 0x1));
                    //                    where:
                    //                    private static final long BLUE_Y_COEFF = (long) Math.floor(0.114d * 1024);
                    //                    private static final long GREEN_Y_COEFF = (long) Math.floor(0.587d * 1024);
                    //                    private static final long RED_Y_COEFF = (long) Math.floor(0.299d * 1024);

                    // V2: CImg version
                    var y = ((tempBuffer[i + 2] and 0xFF) * 66 + (tempBuffer[i + 1] and 0xFF) * 129 +
                            (tempBuffer[i] and 0xFF) * 25 shr 8) + 16

                    // if (y < 0) y = 0;
                    // else

                    if (y > 255) y = 255

                    if (y > maxPixel) {
                        maxPixel = y
                    }
                    this.data!!.put(j++, y.toByte())
                    i += numComponents
                }
            } else {
                throw IllegalArgumentException("Can't work with this type of byte image: " + image.type)
            }
        } else {
            throw IllegalArgumentException("Can't work with non-byte image buffers")
        }
        if (maxPixel > 0) {
            // Let's normalize amount of light
            // V1: with double math
            for (i in 0 until numPixels) {
                val temp = ((this.data!!.get(i) shl 8) / maxPixel).toLong()
                this.data!!.put(i, (temp and 0xFF).toByte())
            }
            // V2: with int only math
            //            for (int i =0; i < numPixels; i++) {
            //                long temp = (this.data.get(i) * NORMALIZATION_APROX[this.maxPixel]) >> NORMALIZATION_DENOMINATOR_POWER;
            //                this.data.put(i, (byte) (temp & 0xFF));
            //            }
        }

    }

    fun save(path: String) {
        val temp = BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY)
        val buffer = ByteArray(width * height)
        this.data!!.rewind()
        this.data!!.get(buffer)
        temp.raster.setDataElements(0, 0, width, height, buffer)
        try {
            ImageIO.write(temp, "jpg", File(path))
        } catch (e: IOException) {
        }

    }

    operator fun get(x: Int, y: Int): Int {
        return this.data!!.get(width * y + x) and 0xFF
    }

    companion object {
        private val BYTE_SIZE = 8
    }

}