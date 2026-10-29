This is the source code for a Discord bot used by the [SkyHanni](https://github.com/SkyHanniStudios/SkyHanni) staff on
the [SkyHanni Support Server](https://discord.gg/skyhanni-997079228510117908).

## Technical Info

This Discord bot is written in [Kotlin](https://kotlinlang.org/).  
We use [JDA](https://github.com/discord-jda/JDA) to communicate with the Discord Bot API.  
We use [Gson](https://github.com/google/gson) to parse JSON responses from the APIs provided
by [Discord](https://discord.com/developers/docs/intro) and [GitHub](https://docs.github.com/en/rest).  
We use [SQLite](https://www.sqlite.org/index.html) to store tag information locally.

## Message Content Intent

The message content intent is currently disabled for this bot in the Discord developer portal. Requesting it made the
gateway reject the connection, so the request is commented out in `DiscordBot.kt`.

While it is disabled, the bot cannot read the content of regular messages. Everything that reacts to what people write,
rather than to an explicit command, is inactive:

- Automatic pull request detection (`#1234` and pull request links)
- The reminder to use `/server <keyword>` when a known server link is posted
- Mod list detection
- The pleading face reaction
- Handling of the bot's own messages
- Commands with the `!` prefix. `debugmods` has no slash variant and cannot be used at all

All of that code is still in place and unchanged. Once the intent is enabled again in the developer portal and the
`enableIntents` call in `DiscordBot.kt` is uncommented, these features work again without any further changes.

### Help

`/help`  
Lists all commands the user may use.

`/help <command>`  
Displays the usage, description, and parameters of a command.

### Tag

Used to display a longer text in response to a short command.

`/tag <tag> [user]`  
Displays the response associated with the tag.
The optional `user` parameter pings that user in front of the response, for cases where replying to a message is not
possible.

`/tagadd <tag> <response>`  
Creates a tag.

`/tagedit <tag> <response>`  
Edits an existing tag.

`/tagdelete <tag>`  
Deletes a tag.

`/taglist`/`/tags`  
Shows a list of all tags.
Supports the `-i` parameter to include and sort by uses.

`/undo`  
Removes the last tag message sent by the user.

### Server

Displays information about a SkyBlock-related Discord server.  
Excludes servers from streamers, YouTubers, and Hypixel/Skyblock guilds.  
The list is saved as JSON in this repository under `data/discord_servers.json`.  
When users send a server URL directly, they are reminded to use `/server <keyword>` instead, and unknown server links
are logged for future inclusion.  
Checks on startup of the bot via Discord API if the server id has changed, indicating a fake server that stole the
vanity url.

`/server <server>`  
Displays information about a server.  
Supports the `-d` parameter to show debug info for the server.

`/serverlist`/`/servers`  
Lists all servers.

`/updateservers`  
Moderator command to reload the server list JSON file from GitHub.

### Pull Request

`/pr <number>`  
Displays information about the pull request with the given number, including a link to download the artifact.

### Changelog

`/changelog [preview|announcement]`  
Code reviewers post the changelog of the next beta to the preview channel. The bot starts a thread under that message,
and the webhook posts the internal changelog into it, one message per category. A second run for the same version
edits all of these messages instead of posting new ones.
With `announcement`, admins post the changelog of the latest released beta to the announcement channel.
The posted messages are remembered in `changelog_messages.json`, next to `config.json`.

Setup:

- In the settings of the preview channel and of the announcement channel, create a webhook under Integrations and
  copy its url.
- Put the URLs into `changelogWebhookPreview` and `changelogWebhookAnnouncement` in `config.json`.
- Give the bot the permission to create public threads in the preview channel.
