# Game On! Microservices and Java

[![Codacy Badge](https://api.codacy.com/project/badge/Grade/4d099084aab34a57893e8fd29df79ae3)](https://www.codacy.com/app/gameontext/sample-room-java?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=gameontext/sample-room-java&amp;utm_campaign=Badge_Grade)

[Game On!](https://gameontext.org/) is both a sample microservices application, and a throwback text adventure brought to you by the WASdev team at IBM. This application demonstrates how microservice architectures work from two points of view:

1. As a Player: navigate through a network/maze of rooms, and interact with other players and the items or actions available in each room.
2. As a Developer: extend the game by creating simple services that define rooms. Learn about microservice architectures and their supporting infrastructure as you build and scale your service.

You can learn more about Game On! at [http://gameontext.org/](http://gameontext.org/).

## Introduction

This walkthrough will guide you through creating and deploying a java room (a microservice) that allows visitors to query the Watson Alchemy Data News service for the Game On! application. This microservice is written in Java as a web application deployed on Websphere Liberty.

The microservice in this example will be built into a docker container.

Game On! communicates with this service (a room) over WebSockets using the [Game On! WebSocket protocol](https://book.gameontext.org/microservices/WebSocketProtocol.html). Consider this a stand-in for asynchronous messaging like MQTT, which requires a lot more setup than a simple WebSocket does.

## Requirements

- [Maven](https://maven.apache.org/install.html)
- Java 8: Any compliant JVM should work.
  * [Java 8 JDK from Oracle](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
  * [Java 8 JDK from IBM (AIX, Linux, z/OS, IBM i)](http://www.ibm.com/developerworks/java/jdk/),
    or [Download a Liberty server package](https://developer.ibm.com/assets/wasdev/#filter/assetTypeFilters=PRODUCT)
    that contains the IBM JDK (Windows, Linux)

## Let's get started!

1. Create a clone or your own fork of this repository ([what's a fork?](https://help.github.com/articles/fork-a-repo/))
2. If applicable, create a local clone of your fork ([Cloning a repository](https://help.github.com/articles/cloning-a-repository/))

## Add your own Watson Alchemy API key to the code
1. Create a Bluemix account
2. Create an instance of the Alchemy API service... "free' version is fine for checking out the service using this GameOn! room
3. Open RoomImplementation.java 
    Note: this is located at src/main/java/org/gameontext/sample
4. Place your Watson Alchemy API key in the `apikey` String assignment within the `processCommand` method
    Note: this is located in the `/news` case block

## Build the service locally

1. `cd Watson-news-sample-room-java`
2. `mvn install -DskipTests`
    Note: currently the Room Description changes that are permanently part of this java room will require you to skip the tests that are testing on hard coded descriptions of a room
3. `mvn liberty:run-server`

After running this, the server will be running locally at [http://localhost:9080/](http://localhost:9080/).
* Visiting this page provides a small form you can use to test the WebSocket endpoint in your service directly.
* A health URL is also defined by the service, at http://localhost:9080/health

## Interacting with the Watson News Service Room

This room has a command "/news" that will precede the name of a company you will type as an argument. 

```
/news Pivotal
```

The text you get back in the form test page will have markdown in it, but will be formatted nicely when you follow the steps below and visitors go to the GameOn! Watson News room  

## Make your room public!

For Game On! to include your room, you need to tell it where the publicly reachable WebSocket endpoint is. This usually requires two steps:

* [hosting your service somewhere with a publicly reachable endpoint](https://book.gameontext.org/walkthroughs/createRoom.html#deployRoom.html), and then
* [registering your room with the game](https://book.gameontext.org/v/walkthrough/walkthroughs/createRoom.html#_register_your_room).

## Development walkthrough - going from a basic java room to the news room

The basic 'sample-room-java' Git repository at [http://github.com/gameontext/sample-room-java](http://github.com/gameontext/sample-room-java) would normally be forked and then cloned locally. This room, once compiled and stood up in a publicly accessible location, and then registered with GameOn!, can be visited by anyone who goes to the gameontext.org site, but there's not much that is special about the basic room. It needs to be made unique and fun, and there are limitless ways to do that. 

This room adds a custom command, and adds code to create a query and then access a Watson Alchemy Data service. Here are the specific details for doing this.

### &nbsp;&nbsp;&nbsp;&nbsp;1. Change the room description and full name

The basic room description information for the java room is located in "RoomDescription.java". That can be found at the *'src/main/java/org/gameontext/sample'* directory. I changed the initialized values to what made sense for the Watson Alchemy API news room. Both the 'fullName' and the 'description' member variables for the RoomDescription class were updated.

Note: once you edit this string the automated tests will begin to fail. Remember to skip the tests when you compile: 

&nbsp;&nbsp;&nbsp;&nbsp;`mvn install -DskipTests` 


### &nbsp;&nbsp;&nbsp;&nbsp;2. Copy the '/ping' command code to create a '/news' command

Opening the 'RoomImplementation.java' file in the same directory, I copied the case statements from the "/ping" code block for my new '/news' command. The '/ping' command is inside the switch statment that is inside the processCommand() method. 

### &nbsp;&nbsp;&nbsp;&nbsp;3. Add String variables to build the query

I could have concatenated 5 parts of the query together, but instead I decided to break out each URL section or argument into it's own variable for clarity.

```
String baseURL = "https://gateway-a.watsonplatform.net/calls/data/GetNews";
String apikey = "1aa8d3a512615040a135b384096d50aedb6e5c43";
String outputMode = "json";
String start = "now-1d";
String end = "now";
String count = "100";
String q_enriched_url_entities_entity = "|text=";
String type = "company";
String re_turn = "enriched.url.title,enriched.url.url";
	currentQuery = query = baseURL + "?apikey=" + apikey + "&outputMode=" + outputMode + "&start=" + start + "&end=" + end + "&count=" + count + "&q.enriched.url.entities.entity=" + q_enriched_url_entities_entity + textKeywd + "|&return=" + re_turn;
```

Notice that 'currentQuery' is used only if there were problems trying to get data back, or parse it corrrectly, at which time it would be returned for help with debugging.  

Finding out about how the query needed to be structured was basically a matter of looking at the demo here [http://querybuilder.alchemyapi.com/builder](http://querybuilder.alchemyapi.com/builder). I hard coded the base URL, the apiKey provided by Bluemix for the Alchemy service, and everything except the "keyword" that would be passed in via the "/news" command as a parameter. The basic room already parses the parameter for you and inside a variable called 'remainder'.

### &nbsp;&nbsp;&nbsp;&nbsp;4. Add import statements for Json and URL objects

These import statements were added:
```
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonArray;
import java.net.URL;
```

### &nbsp;&nbsp;&nbsp;&nbsp;5. Set-up the API call to your Watson service

```
URL url = new URL(query);
InputStream is = url.openStream();
```

### &nbsp;&nbsp;&nbsp;&nbsp;5. Add an 'extractURLsAndTitles' method
```
    public String extractURLsAndTitles(InputStream is){
        String results = "";
        JsonReader jsonReader = Json.createReader(is);
        JsonObject jsonob = jsonReader.readObject();
        if(jsonob.containsKey("result")){
            if((jsonob.getJsonObject("result")).containsKey("docs")){
                JsonArray arr = (JsonArray)jsonob.getJsonObject("result").get("docs");
                }
                if(arr != null){
                    try{
		        for(int i = 0; !arr.isNull(i); i++){
                            //double quotes surround getString return values
	                    results += "[" + arr.getJsonObject(i).getJsonObject("source").getJsonObject("enriched").getJsonObject("url").getString("title","title") + "]";
	                    results += "(" + arr.getJsonObject(i).getJsonObject("source").getJsonObject("enriched").getJsonObject("url").getString("url","not available") + ")  \n";
		        }
                    }
                    catch(IndexOutOfBoundsException e){
			
                    }
                }
		else results += "problem with this query: " + currentQuery;
            }
        }
        else results += "problem with this query: " + currentQuery;
        jsonReader.close();
        return results;
    }
```

### &nbsp;&nbsp;&nbsp;&nbsp;6. Call the 'extract' method

Once you've made the call to the 'extractURLsAndTitles' method you take the string that is returned, already formatted in Markdown, and passit back to the room visitor in a message.

```
msg = extractURLsAndTitles(is);
...
endpoint.sendMessage(session,Message.createBroadcastEvent("Watson news response to " + username + ":\n " + msg));

```


## Tip

Note that it is faster to do most of your code changes and periodically just run the maven commands rather than using the methods described above to "Make your room public" for pushing and testing each code update. If something can be tested without joining the room to GameOn!, then use the faster approach!


## Build a docker container

Creating a Docker image is straight-up: `docker build .` right from the root menu.

A `docker-compose.yml` file is also there, which can be used to specify overlay volumes to allow local development without restarting the container. See the [Advanced Adventure for local development with Docker](https://book.gameontext.org/v/walkthrough/walkthroughs/local-docker.html) for a more detailed walkthrough.

## Other ways to make a Java room your own...

The purpose of this text-based adventure is to help you grapple with microservices concepts and technologies
while building something other than what you do for your day job (it can be easier to learn new things
when not bogged down with old habits). This means that the simple service that should be humming along
merrily with your name on it is the beginning of your adventures, rather than the end.

Here is the original roadmap to the basic java room, for making something of your own:

* `org.gameontext.sample.RoomImplementation`
   This is class contains the core elements that make your microservice unique from others.
   Custom commands and items can be added here (via the `org.gameontext.sample.RoomDescription`
   member variable). The imaginatively named `handleMessage` method, in particular, is called
   when new messages arrive.

* `org.gameontext.sample.protocol.*`
   This package contains a collection of classes that deal with the mechanics of the websocket
   connection that the game uses to allow players to interact with this service. `RoomEndpoint`
   is what it says: that is the WebSocket endpoint for the service.

* `org.gameontext.sample.rest.*`
   This package defines a REST endpoint, with a single defined path: /health

* `org.gameontext.sample.map.client.*`
   This package contains a client for the Map service. As the service is defined, it doesn't
   have the credentials necessary to perform mutable operations on the Map service.

* `src/main/liberty` contains configuration for Liberty, a lightweight Java EE composable app server

* `src/test` -- Yes! There are tests!

Things you might try:

* Use RxJava to manage all of the connected WebSockets together as one event stream.
* Call out to another API (NodeRed integration, Watson API, Weather API) to perform actions in the room.
* Integrate this room with IFTTT, or Slack, or ...
* .. other [Advanced Adventures](https://book.gameontext.org/v/walkthrough/walkthroughs/createMore.html)!

Remember our https://gameontext.org/#/terms. Most importantly, there are kids around: make your parents proud.

## How the build works

This project is built using Maven and makes use of the [Liberty Maven plugin](https://github.com/WASdev/ci.maven) and the [Cloud Foundry Maven plugin](https://docs.run.pivotal.io/buildpacks/java/build-tool-int.html#maven) to integrate with Liberty and Bluemix.

### Server feature definitions

For those of you familiar with the Liberty server configuration you will know that features are enabled in the server by adding a <featureManager/> element to the server.xml. For this project the <featureManager/> is provided by snippets from the [Liberty app accelerator](http://liberty-app-accelerator.wasdev.developer.ibm.com/start/). This means that there is no <featureManager/> element in the [server.xml](src/main/liberty/config/server.xml) file. When the build is run these will appear in the server's configDropins/defaults directory.

### Testing

You can write two types of tests: unit and integration tests.  The unit tests will use the maven-surefire-plugin to run any tests found in packages that include "unit" in their name. The integration tests will:
1. Start a Liberty server
2. Use the maven-failsafe-plugin to run any tests that have packages that include "it" in their names
3. Stop the Liberty server
As integration tests are longer running they can be skipped by providing the skipTests flag: `mvn install -DskipTests`.

### Build phases

The following shows what goals run at which phases in the [default Maven lifecycle](https://maven.apache.org/guides/introduction/introduction-to-the-lifecycle.html).

| Phase                 | Plugin                  | Goal              | Profile          | Notes |
| --------------------- | ----------------------- | ----------------- | ---------------- | --- |
| initialize            | maven-dependency-plugin | properties        | All              | Enables the copy of server snippets later on |
| initialize            | maven-enforcer-plugin   | enforce           | bluemix          | Makes sure the properties are set if deploying to Bluemix |
| initialize            | maven-antrun-plugin     | run               | bluemix          | Prints out what is going to be pushed |
| initialize            | maven-enforcer-plugin   | enforce           | existing-install | Checks that if the liberty.install property is set that it points to an existing directory. |
| test                  | maven-surefire-plugin   | test              | All              |  |
| prepare-package       | liberty-maven-plugin    | install-server    | All              | Creates the server using the server.xml in the src directory |
| package               | maven-war-plugin        | war               | All              |  |
| package               | maven-dependency-plugin | copy-server-files | All              | Copies the server.xml snippets that contain the <featureManager/> elements |
| package               | maven-resources-plugin  | copy-resources    | All              | Copies the WAR into the server |
| package               | liberty-maven-plugin    | package-server    | All              | Creates a ZIP or JAR (depending of if the `runnable` profile is enabled) containing the server and WAR |
| package               | cf-maven-plugin         | push              | bluemix          | Pushes the server up to bluemix |
| pre-integration-test  | liberty-maven-plugin    | start-server      | liberty-test     | Doesn't run when -DskipTests is set |
| integration-test      | maven-failsafe-plugin   | integration-test  | All              |  |
| post-integration-test | liberty-maven-plugin    | stop-server       | liberty-test     | Doesn't run when -DskipTests is set |
| verify                | maven-failsafe-plugin   | verify            | All              |  |
| n/a                   | liberty-maven-plugin    | n/a               | runnable         | Just sets properties to indicate that a runnable JAR should be made rather than a ZIP when packaging the server |
| n/a                   | liberty-maven-plugin    | n/a               | downloadLiberty  | Just sets properties that are used in the install-server goal to installs the Liberty runtime. Doesn't run if liberty.install is set to an existing install of Liberty |
| n/a                   | liberty-maven-plugin    | n/a               | existing-install | Just sets properties that are used in the other Liberty goals to point to an existing Liberty install. Only runs if liberty.install is set to an existing install of Liberty |


