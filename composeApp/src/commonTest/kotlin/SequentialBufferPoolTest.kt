import com.ethossoftworks.land.service.filetransfer.SequentialBufferPool
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class SequentialBufferPoolTest {
    @Test
    fun test() = runBlocking {
        val pool = SequentialBufferPool(5, 10)

        launch {
            delay(5_000)
            pool.markBufferFull(0, 7)
        }

        println(pool.getFullBuffer())
        println("Got filled buffer")
    }
}