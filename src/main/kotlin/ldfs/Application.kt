package ldfs

import io.ktor.server.application.Application
import ldfs.plugins.configureRouting
import ldfs.plugins.configureSwagger

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureSwagger()
    configureRouting()
}
