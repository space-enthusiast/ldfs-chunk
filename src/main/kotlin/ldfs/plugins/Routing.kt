package ldfs.plugins

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.readAllParts
import io.ktor.http.content.streamProvider
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.io.File
import java.util.UUID

fun Application.configureRouting() {
    val cashFolder = environment.config.property("cash-folder-path").getString()

    routing {
        get("/hello") {
            call.respondText("Hello World!")
        }

        post("/upload") {
            val allPart = call.receiveMultipart().readAllParts()

            val chunkUuid =
                (
                    allPart.firstOrNull {
                        it is PartData.FormItem && it.name == "chunkUuid"
                    } as PartData.FormItem?
                )?.value?.let {
                    runCatching { UUID.fromString(it) }.onFailure {
                        call.respondText(
                            text = "Chunk UUID wrong format",
                            contentType = ContentType.Text.Plain,
                            status = HttpStatusCode.BadRequest,
                        )
                        return@post
                    }
                } ?: run {
                    call.respondText(
                        text = "Chunk UUID not found",
                        contentType = ContentType.Text.Plain,
                        status = HttpStatusCode.BadRequest,
                    )
                    return@post
                }

            allPart.mapNotNull { part ->
                when (part) {
                    is PartData.FileItem -> {
                        File("$cashFolder/${System.currentTimeMillis()}-$chunkUuid").also {
                            part.streamProvider().use { input ->
                                it.outputStream().buffered().use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    }
                    else -> null
                }
            }
            call.respondText("File uploaded successfully", ContentType.Text.Plain)
        }
    }
}
