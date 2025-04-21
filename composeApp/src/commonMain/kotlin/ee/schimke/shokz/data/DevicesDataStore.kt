package ee.schimke.shokz.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.OkioStorage
import com.squareup.wire.Message
import com.squareup.wire.ProtoAdapter
import ee.schimke.shokz.datastore.proto.Settings
import okio.BufferedSink
import okio.BufferedSource
import okio.ByteString
import okio.FileSystem
import okio.IOException
import okio.Path

/**
 * Gets the singleton DataStore instance, creating it if necessary.
 */
fun createFilesDataStore(producePath: () -> Path): DataStore<Settings> =
    DataStoreFactory.create(
        OkioStorage(
            FileSystem.SYSTEM,
            serializer = Settings.ADAPTER.toOkioSerializer(),
            producePath = producePath
        ),
        corruptionHandler = onCorrupt(produceNewData = { Settings() })
    )

fun <T> onCorrupt(produceNewData: () -> T): ReplaceFileCorruptionHandler<T> {
    return ReplaceFileCorruptionHandler<T>({ produceNewData() })
}

fun <T : Message<T, Nothing>> ProtoAdapter<T>.toOkioSerializer(): OkioSerializer<T> {
    return object : OkioSerializer<T> {
        override val defaultValue: T = this@toOkioSerializer.decode(ByteString.EMPTY)

        override suspend fun readFrom(source: BufferedSource): T {
            try {
                return this@toOkioSerializer.decode(source)
            } catch (exception: IOException) {
                throw CorruptionException("Cannot read proto", exception)
            }
        }

        override suspend fun writeTo(t: T, sink: BufferedSink) {
            sink.write(t.encode())
        }
    }
}