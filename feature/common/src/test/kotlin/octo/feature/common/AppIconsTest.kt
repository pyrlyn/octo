package octo.feature.common

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

class AppIconsTest {
    private val png = byteArrayOf(-119, 80, 78, 71)

    private fun icns(vararg chunks: Pair<String, ByteArray>): ByteArray {
        val body = ByteArrayOutputStream()
        DataOutputStream(body).use { out ->
            chunks.forEach { (type, data) ->
                out.writeBytes(type)
                out.writeInt(data.size + 8)
                out.write(data)
            }
        }
        val file = ByteArrayOutputStream()
        DataOutputStream(file).use { out ->
            out.writeBytes("icns")
            out.writeInt(body.size() + 8)
            out.write(body.toByteArray())
        }
        return file.toByteArray()
    }

    @Test
    fun theSmallestPngAtLeastThatBigWins() {
        val small = png + 1
        val medium = png + 2
        val large = png + 3
        val file = icns("ic11" to small, "ic04" to byteArrayOf(9, 9), "ic07" to medium, "ic10" to large)

        assertContentEquals(medium, icnsPng(file, 64))
        assertContentEquals(small, icnsPng(file, 16))
        assertContentEquals(large, icnsPng(file, 2048), "the largest stands in when none is big enough")
    }

    @Test
    fun aBrokenFileGivesNothing() {
        assertNull(icnsPng("not an icon".toByteArray(), 32))
        assertNull(icnsPng(icns("ic04" to byteArrayOf(1, 2)), 32), "only raw bitmaps")
        val truncated = icns("ic07" to png + 1).copyOf(14)
        assertNull(icnsPng(truncated, 32))
    }
}
