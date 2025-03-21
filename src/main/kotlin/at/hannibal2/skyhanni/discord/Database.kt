package at.hannibal2.skyhanni.discord

import java.sql.Connection
import java.sql.DriverManager

data class Tag(val keyword: String, var response: String, var uses: Int)
data class Link(val channel: String, val pr: Int)

object Database {
    private val connection: Connection = DriverManager.getConnection("jdbc:sqlite:bot.db")
    private val tags = mutableMapOf<String, Tag>()
    private val links = mutableMapOf<String, Link>()

    init {
        val statement = connection.createStatement()
        ensureCountColumnExists()
        statement.execute(buildString {
            append("CREATE TABLE IF NOT EXISTS keywords (")
            append("id INTEGER PRIMARY KEY AUTOINCREMENT, ")
            append("keyword TEXT UNIQUE, ")
            append("response TEXT, ")
            append("count INTEGER DEFAULT 0)")
        })

        statement.execute(buildString {
            append("CREATE TABLE IF NOT EXISTS linkedposts (")
            append("id INTEGER PRIMARY KEY AUTOINCREMENT, ")
            append("channelid STRING UNIQUE, ")
            append("pullrequestid INTEGER UNIQUE)")
        })

        loadTagCache()
        loadLinkCache()
    }

    private fun loadTagCache() {
        val statement = connection.prepareStatement("SELECT keyword, response, count FROM keywords")
        val resultSet = statement.executeQuery()
        while (resultSet.next()) {
            val key = resultSet.getString("keyword").lowercase()
            val response = resultSet.getString("response")
            val count = resultSet.getInt("count")
            tags[key] = Tag(key, response, count)
        }
        resultSet.close()
    }

    private fun loadLinkCache() {
        val statement = connection.prepareStatement("SELECT channelid, pullrequestid FROM linkedposts")
        val resultSet = statement.executeQuery()
        while (resultSet.next()) {
            val channel = resultSet.getString("channelid")
            val pr = resultSet.getInt("pullrequestid")
            links[channel] = Link(channel, pr)
        }
        resultSet.close()
    }

    private fun ensureCountColumnExists() {
        val statement = connection.prepareStatement("PRAGMA table_info(keywords)")
        val resultSet = statement.executeQuery()
        var countExists = false
        while (resultSet.next()) {
            if (resultSet.getString("name") == "count") {
                countExists = true
                break
            }
        }
        resultSet.close()
        if (!countExists) {
            val statement1 = connection.createStatement()
            statement1.execute("ALTER TABLE keywords ADD COLUMN count INTEGER DEFAULT 0")
        }
    }

    fun addTag(keyword: String, response: String): Boolean {
        val key = keyword.lowercase()
        val statement = connection.prepareStatement(
            "INSERT OR REPLACE INTO keywords (keyword, response, count) VALUES (?, ?, ?)"
        )
        statement.setString(1, key)
        statement.setString(2, response)
        statement.setInt(3, 0)
        val updated = statement.executeUpdate() > 0
        if (updated) {
            tags[key] = Tag(key, response, 0)
        }
        return updated
    }

    fun addLink(channel: String, pr: Int): Boolean {
        val statement = connection.prepareStatement(
            "INSERT OR REPLACE INTO linkedposts (channelid, pullrequestid) VALUES (?, ?)"
        )
        statement.setString(1, channel)
        statement.setInt(2, pr)
        val updated = statement.executeUpdate() > 0
        if (updated) {
            links[channel] = Link(channel, pr)
        }
        return updated
    }

    fun getResponse(keyword: String, increment: Boolean = false): String? {
        val key = keyword.lowercase()
        val kObj = tags[key] ?: return null
        if (increment) {
            kObj.uses++
            val statement = connection.prepareStatement(
                "UPDATE keywords SET count = ? WHERE keyword = ?"
            )
            statement.setInt(1, kObj.uses)
            statement.setString(2, key)
            statement.executeUpdate()
        }
        return kObj.response
    }

    fun getLink(prNumber: Int): Link? = links.values.find { it.pr == prNumber }

    fun getPullrequest(channel: String): Int? = links[channel]?.pr

    fun deleteTag(keyword: String): Boolean {
        val key = keyword.lowercase()
        val statement = connection.prepareStatement("DELETE FROM keywords WHERE keyword = ?")
        statement.setString(1, key)
        val updated = statement.executeUpdate() > 0
        if (updated) tags.remove(key)
        return updated
    }

    fun deleteLink(channel: String): Boolean {
        val statement = connection.prepareStatement("DELETE FROM linkedposts WHERE channelid = ?")
        statement.setString(1, channel)
        val updated = statement.executeUpdate() > 0
        if (updated) links.remove(channel)
        return updated
    }

    fun listTags(): List<Tag> = tags.values.toList()

    fun listLinks(): List<Link> = links.values.toList()

    fun getTagCount(keyword: String): Int? {
        return tags[keyword.lowercase()]?.uses
    }

    fun containsKeyword(keyword: String): Boolean = tags.containsKey(keyword.lowercase())

    fun isLinked(channel: String): Boolean = links.containsKey(channel)
}
