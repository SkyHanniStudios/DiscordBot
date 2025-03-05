This is the source code for a Discord bot the [SkyHanni](https://github.com/hannibal002/SkyHanni) staff uses on the [SkyHanni Support Server](https://discord.gg/skyhanni-997079228510117908).

## Technical Infos
This Discord bot is written in [Kotlin](https://kotlinlang.org/).  
We use [JDA](https://github.com/discord-jda/JDA) to communicate with the Discord Bot API.  
We use [gson](https://github.com/google/gson) to parse the JSON responses from the APIs provided by [Discord](https://discord.com/developers/docs/intro) and [GitHub](https://docs.github.com/en/rest).  
We use [SQLite](https://www.sqlite.org/index.html) to store the information locally.

## Features

### Help

`!help`  
Lists all commands the user can use. This list is different for admins.

`!help <command>`  
Shows the exact usage and description of a command and its parameters.  

`!<command> -help`  
Shows the exact usage and description of a command and its parameters.

### Tag

Used to show a longer text as a response when running a small command.  

`!<tag>`  
Returns a response under the tag.  
Allows replying to another message so that the original message gets deleted and the response is a reply to that message.  
Allows the parameter `-d` to delete the user’s message.

`!tagadd <tag> <response>`  
Creates a tag.  

`!tagedit <tag> <response>`  
Edits an already existing tag.  

`!tagdelete <tag>`  
Deletes a tag.  

`!taglist`  
Shows a list of all tags.  

`!undo`  
Removes the last tag message the user has sent.

### Server

Shows information about another, previously defined Discord server.  

`!server <server>`  
Shows information about a server.  
Allows the parameter `-d` to show debug info for this server.  

`!serveradd <server> <display name> <invite url> <description>`  
Adds a server.  

`!serverdelete <server>`  
Deletes a server.  

`!serverlist`  
Lists all servers.  

`!serveraliasadd <server> <alias>`  
Adds an alias for a server.  

`!serveraliasremove <server> <alias>`  
Removes an alias for a server.

### Pull Request

`!pr <number>`  
Lists information about the pull request with the given number.  
Includes a link to download the artifact.