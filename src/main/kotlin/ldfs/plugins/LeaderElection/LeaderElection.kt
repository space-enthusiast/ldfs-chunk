package ldfs.plugins.LeaderElection

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.gson.gson
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

enum class NodeState {
    FOLLOWER, CANDIDATE, LEADER
}

class Node(
    private val id: String,
    private val peers: List<ChunkServerEntity>,
    private val client: HttpClient
) {
    private var state = NodeState.FOLLOWER
    private var currentTerm = 0
    private var votedFor: String? = null
    private var log = mutableListOf<LogEntry>()
    private var commitIndex = 0
    private var lastApplied = 0

    private val electionTimeout = Random().nextInt(150) + 150 // 150ms to 300ms
    private val electionTimer = Timer()

    init {
        resetElectionTimeout()
    }

    private fun resetElectionTimeout() {
        electionTimer.schedule(object : TimerTask() {
            override fun run() {
                onElectionTimeout()
            }
        }, electionTimeout.toLong())
    }

    fun onElectionTimeout() {
        state = NodeState.CANDIDATE
        currentTerm += 1
        votedFor = id
        val votesReceived = AtomicInteger(1)

        val lastLogIndex = log.size - 1
        val lastLogTerm = if (lastLogIndex >= 0) log[lastLogIndex].term else 0

        val voteRequest = RequestVote(currentTerm, id, lastLogIndex, lastLogTerm)

        runBlocking {
            peers.forEach { peer ->
                launch {
                    try {
                        val response: HttpResponse = client.post("http://${peer.ip}:${peer.port}/vote") {
                            contentType(ContentType.Application.Json)
                            setBody(voteRequest)
                        }
                        val voteResponse: VoteResponse = response.body()
                        handleVoteResponse(voteResponse, votesReceived)
                    } catch (e: Exception) {
                        // Handle exception
                    }
                }
            }
        }
    }

    private fun handleVoteResponse(response: VoteResponse, votesReceived: AtomicInteger) {
        if (response.term > currentTerm) {
            currentTerm = response.term
            state = NodeState.FOLLOWER
            votedFor = null
            resetElectionTimeout()
        } else if (response.voteGranted) {
            if (votesReceived.incrementAndGet() > peers.size / 2) {
                becomeLeader()
            }
        }
    }

    private fun becomeLeader() {
        state = NodeState.LEADER
        peers.forEach { peer ->
            val appendEntries = AppendEntries(currentTerm, id, log.size - 1, if (log.isNotEmpty()) log.last().term else 0, listOf(), commitIndex)
            runBlocking {
                launch {
                    client.post("http://${peer.ip}:${peer.port}/append") {
                        contentType(ContentType.Application.Json)
                        setBody(appendEntries)
                    }
                }
            }
        }
    }

    suspend fun onRequestVote(requestVote: RequestVote): VoteResponse {
        return if (requestVote.term < currentTerm) {
            VoteResponse(currentTerm, false)
        } else {
            if (votedFor == null || votedFor == requestVote.candidateId) {
                votedFor = requestVote.candidateId
                VoteResponse(currentTerm, true)
            } else {
                VoteResponse(currentTerm, false)
            }
        }
    }

    suspend fun onAppendEntries(appendEntries: AppendEntries): AppendEntriesResponse {
        return if (appendEntries.term < currentTerm) {
            AppendEntriesResponse(currentTerm, false)
        } else {
            state = NodeState.FOLLOWER
            resetElectionTimeout()
            currentTerm = appendEntries.term
            votedFor = null
            AppendEntriesResponse(currentTerm, true)
        }
    }
}

fun main() {
    val node = Node("node1", listOf(
        ChunkServerEntity("localhost", 8081),
        ChunkServerEntity("localhost", 8082)
    ), HttpClient {
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
            gson()
        }
    })

    embeddedServer(Netty, port = 8080) {
        install(ContentNegotiation) {
            gson {
                setPrettyPrinting()
            }
        }
        routing {
            post("/start-election") {
                node.onElectionTimeout()
                call.respond(HttpStatusCode.OK)
            }
            post("/vote") {
                val requestVote = call.receive<RequestVote>()
                val response = node.onRequestVote(requestVote)
                call.respond(response)
            }
            post("/append") {
                val appendEntries = call.receive<AppendEntries>()
                val response = node.onAppendEntries(appendEntries)
                call.respond(response)
            }
        }
    }.start(wait = true)
}