package ldfs.plugins.LeaderElection

data class ChunkServerEntity(val ip: String, val port: Int)

data class RequestVote(
    val term: Int,
    val candidateId: String,
    val lastLogIndex: Int,
    val lastLogTerm: Int
)

data class VoteResponse(
    val term: Int,
    val voteGranted: Boolean
)

data class AppendEntries(
    val term: Int,
    val leaderId: String,
    val prevLogIndex: Int,
    val prevLogTerm: Int,
    val entries: List<LogEntry>,
    val leaderCommit: Int
)

data class AppendEntriesResponse(
    val term: Int,
    val success: Boolean
)

data class LogEntry(val term: Int, val command: String)
