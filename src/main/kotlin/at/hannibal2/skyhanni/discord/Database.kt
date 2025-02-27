package at.hannibal2.skyhanni.discord

import java.sql.Connection
import java.sql.DriverManager

data class Server(
    val id: Int? = null,
    val keyword: String,
    val displayName: String,
    val inviteLink: String,
    val description: String
)

object Database {
    private val connection: Connection = DriverManager.getConnection("jdbc:sqlite:bot.db")
    private val keywordCache = mutableMapOf<String, String>()
    private val serverCache = mutableMapOf<String, Server>()
    private val aliasCache = mutableMapOf<String, MutableList<String>>()

    init {
        val stmt = connection.createStatement()
        stmt.execute(
            "CREATE TABLE IF NOT EXISTS keywords (id INTEGER PRIMARY KEY AUTOINCREMENT, keyword TEXT UNIQUE, response TEXT)"
        )
        stmt.execute(
            "CREATE TABLE IF NOT EXISTS servers (id INTEGER PRIMARY KEY AUTOINCREMENT, keyword TEXT UNIQUE, display_name TEXT, inviteLink TEXT, description TEXT)"
        )
        stmt.execute("CREATE TABLE IF NOT EXISTS server_aliases (alias TEXT UNIQUE, server_keyword TEXT)")
        loadKeywordCache()
        loadServerCache()
        loadAliasCache()
    }

    private fun loadKeywordCache() {
        val stmt = connection.prepareStatement("SELECT keyword, response FROM keywords")
        val rs = stmt.executeQuery()
        while (rs.next()) {
            keywordCache[rs.getString("keyword").lowercase()] = rs.getString("response")
        }
        rs.close()
    }

    private fun loadServerCache() {
        val stmt = connection.prepareStatement("SELECT id, keyword, display_name, inviteLink, description FROM servers")
        val rs = stmt.executeQuery()
        while (rs.next()) {
            val server = Server(
                id = rs.getInt("id"),
                keyword = rs.getString("keyword"),
                displayName = rs.getString("display_name"),
                inviteLink = rs.getString("inviteLink") ?: "",
                description = rs.getString("description")
            )
            serverCache[server.keyword] = server
        }
        rs.close()
    }

    private fun loadAliasCache() {
        val stmt = connection.prepareStatement("SELECT alias, server_keyword FROM server_aliases")
        val rs = stmt.executeQuery()
        while (rs.next()) {
            val alias = rs.getString("alias")
            val canonical = rs.getString("server_keyword")
            serverCache[canonical]?.let { server ->
                serverCache[alias] = server
                aliasCache.getOrPut(canonical) { mutableListOf() }.add(alias)
            }
        }
        rs.close()
    }

    fun addKeyword(keyword: String, response: String): Boolean {
        val stmt = connection.prepareStatement("INSERT OR REPLACE INTO keywords (keyword, response) VALUES (?, ?)")
        stmt.setString(1, keyword.lowercase())
        stmt.setString(2, response)
        val updated = stmt.executeUpdate() > 0
        if (updated) keywordCache[keyword.lowercase()] = response
        return updated
    }

    fun getResponse(keyword: String): String? = keywordCache[keyword.lowercase()]

    fun deleteKeyword(keyword: String): Boolean {
        val stmt = connection.prepareStatement("DELETE FROM keywords WHERE keyword = ?")
        stmt.setString(1, keyword.lowercase())
        val updated = stmt.executeUpdate() > 0
        if (updated) keywordCache.remove(keyword.lowercase())
        return updated
    }

    fun listKeywords(): List<String> = keywordCache.keys.toList()

    fun addServer(server: Server): Boolean {
        val stmt = connection.prepareStatement(
            "INSERT OR REPLACE INTO servers (keyword, display_name, inviteLink, description) VALUES (?, ?, ?, ?)"
        )
        stmt.setString(1, server.keyword)
        stmt.setString(2, server.displayName)
        stmt.setString(3, server.inviteLink)
        stmt.setString(4, server.description)
        val updated = stmt.executeUpdate() > 0
        if (updated) {
            for (entry in serverCache.toMap()) {
                if (entry.value.keyword == server.keyword) {
                    serverCache[entry.key] = server
                }
            }
        }
        return updated
    }

    fun getServer(keyword: String): Server? = serverCache[keyword]

    fun deleteServer(keyword: String): Boolean {
        val stmt = connection.prepareStatement("DELETE FROM servers WHERE keyword = ?")
        stmt.setString(1, keyword)
        val updated = stmt.executeUpdate() > 0
        if (updated) {
            serverCache.entries.removeIf { it.value.keyword == keyword }
            aliasCache.remove(keyword)
            val aliasStmt = connection.prepareStatement("DELETE FROM server_aliases WHERE server_keyword = ?")
            aliasStmt.setString(1, keyword)
            aliasStmt.executeUpdate()
        }
        return updated
    }

    fun listServers(): List<Server> {
        val stmt = connection.prepareStatement("SELECT keyword FROM servers")
        val rs = stmt.executeQuery()
        val canonical = mutableSetOf<String>()
        while (rs.next()) {
            canonical.add(rs.getString("keyword"))
        }
        rs.close()
        return serverCache.filterKeys { it in canonical }.values.toList()
    }

    fun addServerAlias(serverKeyword: String, alias: String): Boolean {
        val server = getServer(serverKeyword) ?: return false
        val stmt = connection.prepareStatement("INSERT OR REPLACE INTO server_aliases (alias, server_keyword) VALUES (?, ?)")
        stmt.setString(1, alias)
        stmt.setString(2, serverKeyword)
        val updated = stmt.executeUpdate() > 0
        if (updated) {
            serverCache[alias] = server
            aliasCache.getOrPut(serverKeyword) { mutableListOf() }.add(alias)
        }
        return updated
    }

    fun deleteServerAlias(serverKeyword: String, alias: String): Boolean {
        val stmt = connection.prepareStatement("DELETE FROM server_aliases WHERE server_keyword = ? AND alias = ?")
        stmt.setString(1, serverKeyword)
        stmt.setString(2, alias)
        val updated = stmt.executeUpdate() > 0
        if (updated) {
            aliasCache[serverKeyword]?.remove(alias)
            serverCache.remove(alias)
        }
        return updated
    }

    fun getServerAliases(canonical: String): List<String> {
        return aliasCache[canonical]?.toList() ?: emptyList()
    }
}
