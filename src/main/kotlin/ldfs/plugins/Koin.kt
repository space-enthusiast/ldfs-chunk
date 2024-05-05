package ldfs.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureCoin() {
    install(Koin) {
        slf4jLogger()
    }
}
