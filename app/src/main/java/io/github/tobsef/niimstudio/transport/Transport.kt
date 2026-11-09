package io.github.tobsef.niimstudio.transport

/** A byte pipe to the printer. Incoming bytes are delivered to [onData] on a background thread. */
interface Transport {
    val name: String

    /** Opens the connection, blocking until connected or throwing. */
    suspend fun open(onData: (ByteArray) -> Unit, onClosed: (Throwable?) -> Unit)

    /** Writes one frame; returns once the bytes have been handed to the stack. */
    suspend fun write(bytes: ByteArray)

    fun close()
}
