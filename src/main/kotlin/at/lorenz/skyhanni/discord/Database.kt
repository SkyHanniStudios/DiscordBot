package org.example.at.lorenz.skyhanni.discord

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
        loadCache() // Load existing keywords into cache
    }

    private fun loadCache() {
        val stmt = connection.prepareStatement("SELECT keyword, response FROM keywords")
        val rs = stmt.executeQuery()
        while (rs.next()) {
            keywordCache[rs.getString("keyword").lowercase()] = rs.getString("response")
        }
    }

    fun addKeyword(keyword: String, response: String): Boolean {
        val stmt = connection.prepareStatement("INSERT OR REPLACE INTO keywords (keyword, response) VALUES (?, ?)")
        stmt.setString(1, keyword.lowercase())
        stmt.setString(2, response)
        val updated = stmt.executeUpdate() > 0
        if (updated) keywordCache[keyword.lowercase()] = response // Update cache
        return updated
    }

    fun getResponse(keyword: String): String? {
        return keywordCache[keyword.lowercase()] // Fast lookup from cache
    }

    fun deleteKeyword(keyword: String): Boolean {
        val stmt = connection.prepareStatement("DELETE FROM keywords WHERE keyword = ?")
        stmt.setString(1, keyword.lowercase())
        val updated = stmt.executeUpdate() > 0
        if (updated) keywordCache.remove(keyword.lowercase()) // Remove from cache
        return updated
    }

    fun listKeywords(): List<String> {
        return keywordCache.keys.toList()
    }

}
