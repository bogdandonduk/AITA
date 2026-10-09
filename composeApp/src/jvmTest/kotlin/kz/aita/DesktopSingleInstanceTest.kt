package kz.aita

import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.*
class DesktopSingleInstanceTest {
    @Test fun secondLaunchSignalsOwnerAndExitAllowsNextVersion() {
        val directory=Files.createTempDirectory("aita-instance-test").toFile()
        val activation=CountDownLatch(1)
        try {
            val first=assertNotNull(DesktopInstanceLease.acquire(directory) { activation.countDown() })
            try {
                assertNull(DesktopInstanceLease.acquire(directory) {})
                assertTrue(activation.await(2,TimeUnit.SECONDS))
            } finally {first.close()}
            assertNotNull(DesktopInstanceLease.acquire(directory) {}).close()
        } finally {directory.deleteRecursively()}
    }
}
