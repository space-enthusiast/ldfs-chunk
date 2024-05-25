package ldfs.plugins

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.log
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Paths

fun Application.startHeartBeat() {
    val totalSizeInByte = environment.config.property("totalSizeInByte").getString()
    val saveFolder = environment.config.property("save-folder-path").getString()
    val ip = InetAddress.getLocalHost().hostAddress
    val port =
        environment.config.propertyOrNull("ktor.deployment.port")?.getString()
            ?: throw Exception("can not get port")
    val masterAddress = environment.config.property("masterAddress").getString()

    log.info("Server is running on port: $port, Server is running on IP: $ip, Master address: $masterAddress")

    launch {
        while (true) {
            val folderSize = getFolderSize(saveFolder)
            val remainingSize = totalSizeInByte.toLong() - folderSize
            delay(1000)
            kotlin.runCatching {
                sendHeartBeat(
                    masterAddress = masterAddress,
                    chunkServerIp = ip,
                    chunkServerPort = port,
                    remainingSize = remainingSize,
                )
                log.info("Server remaining size: $remainingSize, Server folder size: $folderSize".trimIndent())
            }.onFailure {
                log.error("Heartbeat error", it)
            }
        }
    }
}

@Serializable
data class HeartBeatRequest(
    val ip: String,
    val port: String,
    val remainingStorageSize: Long,
)

private suspend fun sendHeartBeat(
    masterAddress: String,
    chunkServerIp: String,
    chunkServerPort: String,
    remainingSize: Long,
) {
    val client =
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json()
            }
            expectSuccess = true
        }
    runCatching {
        val req =
            client.post("http://$masterAddress/chunkServer/heartBeat") {
                contentType(ContentType.Application.Json)
                setBody(
                    HeartBeatRequest(
                        ip = chunkServerIp,
                        port = chunkServerPort,
                        remainingStorageSize = remainingSize,
                    ),
                )
            }
    }.onFailure {
        client.close()
        throw it
    }.onSuccess {
        client.close()
    }
}

fun getFolderSize(folderPath: String): Long {
    val folder = File(folderPath)
    return folder.walk()
        .filter { it.isFile && !Files.isHidden(Paths.get(it.absolutePath)) }
        .map {
            it.length()
        }
        .sum()
}
