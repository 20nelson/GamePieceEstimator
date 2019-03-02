import com.google.gson.Gson
import com.google.gson.JsonArray
import io.ktor.application.call
import io.ktor.client.HttpClient
import io.ktor.client.call.call
import io.ktor.client.engine.apache.Apache
import io.ktor.client.response.readText
import io.ktor.http.ContentType
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

suspend fun main() {
    val server = embeddedServer(Netty, 8080){
        routing {
            // event is requested
            get("/event/{event}") {
                if(call.parameters["event"] != null) call.respondText(processEvent(call.parameters["event"]!!), ContentType.Text.Html)
            }
        }
    }
    server.start(wait = true)
    println("Started server on port 8080.")
}

/**
 * Gets HTML rankings for an event
 */
suspend fun processEvent(event: String): String {

    // request event matches
    val client = HttpClient(Apache)
    val res = client.call("https://www.thebluealliance.com/api/v3/event/$event/matches?X-TBA-Auth-Key=coxBFPyK9XSvIEJFtneXpFv9jIZ37MfTMoQtzaweu2yrnXA18nAzOuMzsA8AEp8D").response
    val text = res.readText()

    // parse json
    val data = Gson().fromJson(text, JsonArray::class.java)


    val alliances = arrayListOf<Alliance>()
    var lastMatch = "null"

    // extract alliances from match data
    data.forEach {
        if(!it.asJsonObject["score_breakdown"].isJsonNull) {
            val red = it.asJsonObject["alliances"].asJsonObject["red"].asJsonObject["team_keys"].asJsonArray
            val blue = it.asJsonObject["alliances"].asJsonObject["blue"].asJsonObject["team_keys"].asJsonArray

            val redHatch = it.asJsonObject["score_breakdown"].asJsonObject["red"].asJsonObject["hatchPanelPoints"].asInt/2
            val redCargo = it.asJsonObject["score_breakdown"].asJsonObject["red"].asJsonObject["cargoPoints"].asInt/3

            val blueHatch = it.asJsonObject["score_breakdown"].asJsonObject["blue"].asJsonObject["hatchPanelPoints"].asInt/2
            val blueCargo = it.asJsonObject["score_breakdown"].asJsonObject["blue"].asJsonObject["cargoPoints"].asInt/3

            alliances.add(Alliance(red[0].asString, red[1].asString, red[2].asString, redHatch, redCargo))
            alliances.add(Alliance(blue[0].asString, blue[1].asString, blue[2].asString, blueHatch, blueCargo))
        } else {
            if(lastMatch == "null") lastMatch = it.asJsonObject["key"].asString
        }
    }

    // perform piece calculations
    val mm = Calculations.createMatchMatrices(alliances)
    val gm = Calculations.calculatePieceScores(mm)
    val tm = Calculations.mapPieceScoresToTeams(gm)

    var output = ""

    // create html
    output += "<script>window.setTimeout(function() { window.location.reload() }, ${res.headers["Cache-Control"]?.split("=")?.get(1)?.toInt()}*1000);</script>" +
            "<style>* { font-family: sans-serif; } h1 { margin: 0; } p {margin-top:0;}</style><h1>$event</h1><p>Next match: $lastMatch</p><table border=1><thead><tr><th>Team</th><th>Hatches</th><th></th><th>Team</th><th>Cargo</th><tr></thead><tbody>"

    // sort hatch panel scores
    val hatchMap = tm.first.map {
        Pair(it.key, it.value)
    }.sortedBy { -it.second }

    // sort cargo scores
    val cargoMap = tm.second.map {
        Pair(it.key, it.value)
    }.sortedBy { -it.second }

    for (i in 0 until cargoMap.size) {
        // add table rows
        output += "<tr><td>${hatchMap.elementAt(i).first}</td><td>${"%.2f".format(hatchMap.elementAt(i).second)}</td><td></td><td>${cargoMap.elementAt(i).first}</td><td>${"%.2f".format(cargoMap.elementAt(i).second)}</td>"
    }

    // finish html
    output += "</tbody></table>"
    return output
}