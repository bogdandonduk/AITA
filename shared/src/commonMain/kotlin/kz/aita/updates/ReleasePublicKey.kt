package kz.aita.updates

/** Extract a DER RSA public-key sequence from a SubjectPublicKeyInfo envelope for Apple's Security API. */
fun clientRsaPkcs1PublicKey(spki: ByteArray): ByteArray? = runCatching {
    if (spki.size !in 256..2048) return null
    fun element(offset: Int, tag: Int): Pair<Int, Int> {
        require(offset + 2 <= spki.size && (spki[offset].toInt() and 255) == tag)
        var index = offset + 1
        val first = spki[index++].toInt() and 255
        val count = first and 127
        val length = if (first < 128) first else {
            require(count in 1..3 && index + count <= spki.size)
            var result = 0
            repeat(count) { result = (result shl 8) or (spki[index++].toInt() and 255) }
            require(result >= 128); result
        }
        require(length > 0 && index + length <= spki.size)
        return index to (index + length)
    }
    val root = element(0,0x30); require(root.second == spki.size)
    val algorithm = element(root.first,0x30)
    val rsaAlgorithm = byteArrayOf(0x06,0x09,0x2a,0x86.toByte(),0x48,0x86.toByte(),0xf7.toByte(),0x0d,0x01,0x01,0x01,0x05,0x00)
    require(spki.copyOfRange(algorithm.first,algorithm.second).contentEquals(rsaAlgorithm))
    val bits = element(algorithm.second,0x03); require(bits.second == root.second && spki[bits.first] == 0.toByte())
    val key = element(bits.first+1,0x30); require(key.second == bits.second)
    spki.copyOfRange(bits.first+1,bits.second)
}.getOrNull()
