package org.totschnig.myexpenses.next.data.receipts

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import org.totschnig.itemgroupmodel.Features
import org.totschnig.itemgroupmodel.ProductGroupModel

/**
 * Models in the format of product-group-modell (docs/interface.md there) whose predictions do
 * not depend on the item name, for testing how the classifier uses them
 */
object TestModels {

    private const val BUCKETS = 1 shl 16
    private const val SURE = 0.9f
    private const val SUGGEST = 0.5f

    /** Predicts [groups] first with [confidence], the others share the rest */
    fun constant(confidence: Float, vararg groups: String): ProductGroupModel =
        ProductGroupModel.load(constantFile(confidence, *groups))

    /** The file of [constant] */
    fun constantFile(confidence: Float, vararg groups: String): ByteArray {
        val rest = (1 - confidence) / (groups.size - 1)
        val bias = FloatArray(groups.size) {
            if (it == 0) {
                kotlin.math.ln(confidence / rest)
            } else {
                0f
            }
        }
        return write(groups.toList(), bias = bias)
    }

    /**
     * Sure of the first of [groups] for items of [chain], without a shop the groups are equally
     * likely and nothing is suggested
     */
    fun byChain(chain: String, vararg groups: String): ProductGroupModel {
        val storeBucket = (
            Features.fnv1a32(
                "__store=$chain".toByteArray()
            ) % BUCKETS.toUInt()
            ).toInt()
        return ProductGroupModel.load(
            write(groups.toList(), storeBucket = storeBucket, chain = chain)
        )
    }

    private fun write(
        groups: List<String>,
        bias: FloatArray = FloatArray(groups.size),
        storeBucket: Int? = null,
        chain: String? = null
    ): ByteArray {
        val out = ByteArrayOutputStream()
        fun bytes(size: Int, block: ByteBuffer.() -> Unit) =
            out.write(ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).apply(block).array())
        fun str8(s: String) = bytes(1 + s.length) {
            put(s.length.toByte())
            put(s.toByteArray())
        }

        out.write("PGM1".toByteArray())
        bytes(2) { putShort(2) }
        bytes(2 + 4) {
            putShort(4)
            put("test".toByteArray())
        }
        bytes(8 + 4 + 2 + 1 + 1 + 2 + 3 * 4) {
            putLong(0)
            putInt(BUCKETS)
            putShort(1) // dim
            put(2)
            put(2) // minn, maxn
            putShort(2) // flags: shop feature
            putFloat(1f) // temperature
            putFloat(SURE)
            putFloat(SUGGEST)
        }
        bytes(2) { putShort(groups.size.toShort()) }
        groups.forEach {
            str8("class_$it")
            str8(it)
        }
        bytes(2) { putShort(if (chain == null) 0 else 1) }
        chain?.let { str8(it) }
        bytes(2) { putShort(if (chain == null) 0 else 1) }
        chain?.let {
            str8(it)
            bytes(2) { putShort(0) }
        }
        bytes(4 * BUCKETS) { repeat(BUCKETS) { putFloat(if (it == storeBucket) 1f / 127 else 0f) } }
        bytes(BUCKETS) { repeat(BUCKETS) { put(if (it == storeBucket) 127.toByte() else 0) } }
        // The shop feature is one of a few features of a short name, so it needs a large weight
        bytes(4 * groups.size) {
            groups.indices.forEach {
                putFloat(
                    if (it == 0 && storeBucket != null) {
                        100f
                    } else {
                        0f
                    }
                )
            }
        }
        bytes(4 * groups.size) { bias.forEach { putFloat(it) } }
        val crc = CRC32().apply { update(out.toByteArray()) }.value
        bytes(4) { putInt(crc.toInt()) }
        return out.toByteArray()
    }
}
