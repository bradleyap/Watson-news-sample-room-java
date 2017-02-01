/*******************************************************************************
 * Copyright (c) 2016 IBM Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package org.gameontext.sample;

import java.util.Locale;
import java.util.logging.Level;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonArray;
import javax.websocket.Session;
import java.util.Iterator;

import org.gameontext.sample.map.client.MapClient;
import org.gameontext.sample.protocol.Message;
import org.gameontext.sample.protocol.RoomEndpoint;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;

import java.net.*;


/**
 * Here is where your room implementation lives. The WebSocket endpoint
 * is defined in {@link RoomEndpoint}, with {@link Message} as the text-based
 * payload being sent on the wire.
 * <p>
 * This is an ApplicationScoped CDI bean, which means it will be started
 * when the server/application starts, and stopped when it stops.
 *
 */
@ApplicationScoped
public class RoomImplementation {

    public static final String LOOK_UNKNOWN = "It doesn't look interesting";
    public static final String UNKNOWN_COMMAND = "This room is a basic model. It doesn't understand `%s`";
    public static final String UNSPECIFIED_DIRECTION = "You didn't say which way you wanted to go.";
    public static final String UNKNOWN_DIRECTION = "There isn't a door in that direction (%s)";
    public static final String GO_FORTH = "You head %s";
    public static final String HELLO_ALL = "%s is here";
    public static final String HELLO_USER = "Welcome!";
    public static final String GOODBYE_ALL = "%s has gone";
    public static final String GOODBYE_USER = "Bye!";

    /**
     * The room id: this is translated from the ROOM_ID environment variable into
     * a JNDI value by server.xml (Liberty)
     */
    @Resource(lookup = "roomId")
    protected String roomId;

    @Inject
    protected MapClient mapClient;

    protected RoomDescription roomDescription = new RoomDescription();

    protected String currentQuery = "";

    @PostConstruct
    protected void postConstruct() {

        if ( roomId == null || roomId.contains("ROOM_ID") ) {
            // The room id was not set by the environment; make one up.
            roomId = "TheGeneratedIdForThisRoom";
        } else {
            // we have a custom room id! let's see what the map thinks.
            mapClient.updateRoom(roomId, roomDescription);
        }

        // Customize the room
        roomDescription.addCommand("/ping", "Does this work?");
        roomDescription.addCommand("/news", "Query the Waston Alchemy News Service to get current information on a company; `e.g.: /news IBM`");

        Log.log(Level.INFO, this, "Room initialized: {0}", roomDescription);
    }

    @PreDestroy
    protected void preDestroy() {
        Log.log(Level.FINE, this, "Room to be destroyed");
    }

    public void handleMessage(Session session, Message message, RoomEndpoint endpoint) {

        // If this message isn't for this room, TOSS IT!
//        if ( !roomId.equals(message.getTargetId()) ) {
//            Log.log(Level.FINEST, this, "Received message for the wrong room ({0}): {1}", message.getTargetId(), message);
//            return;
//        }

        // Fetch the userId and the username of the sender.
        // The username can change overtime, so always use the sent username when
        // constructing messages
        JsonObject messageBody = message.getParsedBody();
        String userId = messageBody.getString(Message.USER_ID);
        String username = messageBody.getString(Message.USERNAME);

        Log.log(Level.FINEST, this, "Received message from {0}({1}): {2}", username, userId, messageBody);

        // Who doesn't love switch on strings in Java 8?
        switch(message.getTarget()) {

        case roomHello:
            //		roomHello,<roomId>,{
            //		    "username": "username",
            //		    "userId": "<userId>",
            //		    "version": 1|2
            //		}
            // See RoomImplementationTest#testRoomHello*

            // Send location message
            endpoint.sendMessage(session, Message.createLocationMessage(userId, roomDescription));

            // Say hello to a new person in the room
            endpoint.sendMessage(session,
                    Message.createBroadcastEvent(
                            String.format(HELLO_ALL, username),
                            userId, HELLO_USER));
            break;

        case roomJoin:
            //		roomJoin,<roomId>,{
            //		    "username": "username",
            //		    "userId": "<userId>",
            //		    "version": 2
            //		}
            // See RoomImplementationTest#testRoomJoin

            // Send location message
            endpoint.sendMessage(session, Message.createLocationMessage(userId, roomDescription));

            break;

        case roomGoodbye:
            //		roomGoodbye,<roomId>,{
            //		    "username": "username",
            //		    "userId": "<userId>"
            //		}
            // See RoomImplementationTest#testRoomGoodbye

            // Say goodbye to person leaving the room
            endpoint.sendMessage(session,
                    Message.createBroadcastEvent(
                            String.format(GOODBYE_ALL, username),
                            userId, GOODBYE_USER));
            break;

        case roomPart:
            //		room,<roomId>,{
            //		    "username": "username",
            //		    "userId": "<userId>"
            //		}
            // See RoomImplementationTest#testRoomPart

            break;

        case room:
            //		room,<roomId>,{
            //		    "username": "username",
            //		    "userId": "<userId>"
            //		    "content": "<message>"
            //		}
            String content = messageBody.getString(Message.CONTENT);

            if ( content.charAt(0) == '/' ) {
                // command
                processCommand(userId, username, content, endpoint, session);
            } else {
                // See RoomImplementationTest#testHandleChatMessage

                // echo back the chat message
                endpoint.sendMessage(session,
                        Message.createChatMessage(username, content));
            }
            break;

        default:
            // unknown message type. discard, don't break.
            break;
        }
    }

    private void processCommand(String userId, String username, String content, RoomEndpoint endpoint, Session session) {
        // Work mostly off of lower case.
        String contentToLower = content.toLowerCase(Locale.ENGLISH).trim();

        String firstWord;
        String remainder;

        int firstSpace = contentToLower.indexOf(' '); // find the first space
        if ( firstSpace < 0 || contentToLower.length() <= firstSpace ) {
            firstWord = contentToLower;
            remainder = null;
        } else {
            firstWord = contentToLower.substring(0, firstSpace);
            remainder = contentToLower.substring(firstSpace+1);
        }

        switch(firstWord) {
            case "/go":
                // See RoomCommandsTest#testHandle*Go*
                // Always process the /go command.
                String exitId = getExitId(remainder);

                if ( exitId == null ) {
                    // Send error only to source session
                    if ( remainder == null ) {
                        endpoint.sendMessage(session,
                                Message.createSpecificEvent(userId, UNSPECIFIED_DIRECTION));
                    } else {
                        endpoint.sendMessage(session,
                                Message.createSpecificEvent(userId, String.format(UNKNOWN_DIRECTION, remainder)));
                    }
                } else {
                    // Allow the exit
                    endpoint.sendMessage(session,
                            Message.createExitMessage(userId, exitId, String.format(GO_FORTH, prettyDirection(exitId))));
                }
                break;

            case "/look":
            case "/examine":
                // See RoomCommandsTest#testHandle*Look*

                // Treat look and examine the same (though you could make them do different things)
                if ( remainder == null || remainder.contains("room") ) {
                    // This is looking at or examining the entire room. Send the player location message,
                    // which includes the room description and inventory
                    endpoint.sendMessage(session, Message.createLocationMessage(userId, roomDescription));
                } else {
                    endpoint.sendMessage(session,
                            Message.createSpecificEvent(userId, LOOK_UNKNOWN));
                }
                break;
            case "/news":
                // Custom command! /news is added to the room description in the @PostConstruct method
                // See RoomCommandsTest#testHandlePing*

                String msg = "No can read Watson news";
		String textKeywd = remainder.replaceAll("(^\n | ^ +)","");
		textKeywd = textKeywd.replaceAll("(\n$| +$)","");  
		String query = "";
		try{
			//based on:
			//https://access.alchemyapi.com/calls/data/GetNews?apikey=YOUR_API_KEY_HERE&return=enriched.url.title,enriched.url.url&start=1484611200&end=1485298800&q.enriched.url.entities.entity=|text=IBM,type=company|&count=25&outputMode=json
			String baseURL = "https://gateway-a.watsonplatform.net/calls/data/GetNews";
			String apikey = "YOUR_KEY_HERE";
			String outputMode = "json";
			String start = "now-1d";
			String end = "now";
			String count = "100";
			//String q_enriched_url_enrichedTitle_relations_relation = "|action.verb.text=";
			String q_enriched_url_entities_entity = "|text=";
			String type = "company";
			String re_turn = "enriched.url.title,enriched.url.url";
			currentQuery = query = baseURL + "?apikey=" + apikey + "&outputMode=" + outputMode + "&start=" + start + "&end=" + end + "&count=" + 
					count + "&q.enriched.url.entities.entity=" + q_enriched_url_entities_entity +
					textKeywd + "|&return=" + re_turn;
			URL url = new URL(query);
			InputStream is = url.openStream();
			msg = extractURLsAndTitles(is);
		}
		catch(java.net.MalformedURLException e){

		}
		catch(IOException ioe){

		}

                if ( remainder == null ) {
		    endpoint.sendMessage(session,Message.createBroadcastEvent("The '/news' command expects a company name after it."));
                } else {
                    endpoint.sendMessage(session,
                        Message.createBroadcastEvent("Watson news response to " + username + ":\n " + msg));
                }
                break;

            case "/ping":
                // Custom command! /ping is added to the room description in the @PostConstruct method
                // See RoomCommandsTest#testHandlePing*

                if ( remainder == null ) {
                    endpoint.sendMessage(session,
                            Message.createBroadcastEvent("Ping! Pong sent to " + username, userId, "Ping! Pong!"));
                } else {
                    endpoint.sendMessage(session,
                            Message.createBroadcastEvent("Ping! Pong sent to " + username + ": " + remainder, userId, "Ping! Pong! " + remainder));
                }

                break;

            default:
                endpoint.sendMessage(session,
                        Message.createSpecificEvent(userId, String.format(UNKNOWN_COMMAND, content)));
                break;
        }
    }


    /**
     * Given a lower case string describing the direction someone wants
     * to go (/go N, or /go North), filter or transform that into a recognizable
     * id that can be used as an index into a known list of exits. Always valid
     * are n, s, e, w. If the string doesn't match a known exit direction,
     * return null.
     *
     * @param lowerDirection String read from the provided message
     * @return exit id or null
     */
    protected String getExitId(String lowerDirection) {
        if (lowerDirection == null) {
            return null;
        }

        switch(lowerDirection) {
            case "north" :
            case "south" :
            case "east" :
            case "west" :
                return lowerDirection.substring(0,1);

            case "n" :
            case "s" :
            case "e" :
            case "w" :
                // Assume N/S/E/W are managed by the map service.
                return lowerDirection;

            default  :
                // Otherwise unknown direction
                return null;
        }
    }

    /**
     * From the direction we used as a key
     * @param exitId The exitId in lower case
     * @return A pretty version of the direction for use in the exit message.
     */
    protected String prettyDirection(String exitId) {
        switch(exitId) {
            case "n" : return "North";
            case "s" : return "South";
            case "e" : return "East";
            case "w" : return "West";

            default  : return exitId;
        }
    }

    public boolean ok() {
        return mapClient.ok();
    }

    public String extractURLsAndTitles(InputStream is){
        String results = "";
        JsonReader jsonReader = Json.createReader(is);
        JsonObject jsonob = jsonReader.readObject();
        if(jsonob.containsKey("result")){
            if((jsonob.getJsonObject("result")).containsKey("docs")){
                JsonArray arr = (JsonArray)jsonob.getJsonObject("result").get("docs");
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


    private static String getStringFromInputStream(InputStream stream) {
        BufferedReader br = null;
        StringBuilder sb = new StringBuilder();
        int nullAttempts = 0;
        int openCurlies = 0;
        int closingCurlies = 0;
        String line;
        try {
            br = new BufferedReader(new InputStreamReader(stream));
            while(nullAttempts < 100000){
                while ((line = br.readLine()) != null) {
                    for(int i=0; i<line.length(); i++){
                        if(line.charAt(i) == '}')closingCurlies++;
                        if(line.charAt(i) == '{')openCurlies++;
                    }    
                    sb.append(line);
                }
                nullAttempts++;
                //json has an unmatched opening curly brace until the final closing brace
                if(openCurlies == closingCurlies)break;
            }
        } 
        catch (IOException ioe) {
            System.out.println(ioe.getMessage());
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException ioxptn) {
                     ioxptn.printStackTrace();
                }
            }
        }
        return sb.toString();
    }
}
