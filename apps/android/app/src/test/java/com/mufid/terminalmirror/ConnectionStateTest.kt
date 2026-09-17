package com.mufid.terminalmirror

import com.mufid.terminalmirror.network.OutboundQueue
import com.mufid.terminalmirror.network.ReconnectPolicy
import org.junit.Assert.*
import org.junit.Test

class ConnectionStateTest {

    @Test
    fun `backoff base doubles with ceiling`() {
        assertEquals(1000L, ReconnectPolicy.baseDelayMs(1))
        assertEquals(2000L, ReconnectPolicy.baseDelayMs(2))
        assertEquals(30000L, ReconnectPolicy.baseDelayMs(10))
        assertEquals(30000L, ReconnectPolicy.baseDelayMs(100))
    }

    @Test
    fun `backoff delay stays within base plus jitter`() {
        repeat(50) {
            val d = ReconnectPolicy.nextDelayMs(2)
            assertTrue(d in 2000L until 3000L)
        }
    }

    @Test
    fun `queue drains in fifo order`() {
        val q = OutboundQueue(capacity = 4)
        q.enqueue(byteArrayOf(0x01))
        q.enqueue(byteArrayOf(0x02))
        q.enqueue(byteArrayOf(0x03))
        val out = q.drain()
        assertEquals(3, out.size)
        assertArrayEquals(byteArrayOf(0x01), out[0])
        assertArrayEquals(byteArrayOf(0x03), out[2])
        assertEquals(0, q.size)
    }

    @Test
    fun `queue drops oldest beyond capacity and counts honestly`() {
        val q = OutboundQueue(capacity = 2)
        q.enqueue(byteArrayOf(0x01))
        q.enqueue(byteArrayOf(0x02))
        q.enqueue(byteArrayOf(0x03))
        assertEquals(1, q.droppedCount)
        val out = q.drain()
        assertEquals(2, out.size)
        assertArrayEquals(byteArrayOf(0x02), out[0])
        assertArrayEquals(byteArrayOf(0x03), out[1])
    }

    @Test
    fun `queue clear resets drops`() {
        val q = OutboundQueue(capacity = 1)
        q.enqueue(byteArrayOf(0x01))
        q.enqueue(byteArrayOf(0x02))
        q.clear()
        assertEquals(0, q.size)
        assertEquals(0, q.droppedCount)
    }
}
