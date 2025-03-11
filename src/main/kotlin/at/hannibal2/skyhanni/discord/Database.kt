package at.hannibal2.skyhanni.discord

import java.sql.Connection
import java.sql.DriverManager

object Database {
    private val connection: Connection = DriverManager.getConnection("jdbc:sqlite:bot.db")
    private val keywordCache = mutableMapOf<String, String>()

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
    }

    private fun loadKeywordCache() {
        val stmt = connection.prepareStatement("SELECT keyword, response FROM keywords")
        val rs = stmt.executeQuery()
        while (rs.next()) {
            keywordCache[rs.getString("keyword").lowercase()] = rs.getString("response")
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

    fun containsKeyword(keyword: String): Boolean = keywordCache.containsKey(keyword.lowercase())
}
