package ldfs

import io.ktor.server.application.Application
import ldfs.plugins.configureCoin
import ldfs.plugins.configureRouting
import ldfs.plugins.configureSwagger
import ldfs.plugins.startHeartBeat

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureCoin()
    configureSwagger()
    configureRouting()
    startHeartBeat()
}
