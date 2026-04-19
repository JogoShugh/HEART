    import ai.koog.agents.chatMemory.feature.ChatMemory
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.core.tools.ToolRegistry
    import ai.koog.agents.core.tools.annotations.LLMDescription
    import ai.koog.agents.core.tools.annotations.Tool
    import ai.koog.prompt.executor.clients.google.GoogleModels
    import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
    import kotlinx.coroutines.runBlocking
    import kotlinx.serialization.json.Json
    import kotlinx.serialization.json.jsonObject
    import kotlinx.serialization.json.jsonPrimitive
    import java.net.URI
    import java.net.URLEncoder
    import java.net.http.HttpClient
    import java.net.http.HttpRequest
    import java.net.http.HttpResponse

    // 1. SHARED HTTP CLIENT
    val httpClient: HttpClient = HttpClient.newBuilder().build()

    // 2. THE TOOLS

    @Tool
    @LLMDescription("Ask the user a question by sending it to stdout and return the answer from stdin")
    fun askUser(
        @LLMDescription("Question from the agent to the user") question: String
    ): String {
        println("\n🤖 Agent asks: $question")
        print("👤 You: ")
        return readln()
    }

    @Tool
    @LLMDescription("Fetches the current state and available actions (HAL Schema Forms) for a given system URL.")
    fun fetchApiState(
        @LLMDescription("The URL of the API endpoint to fetch") url: String
    ): String {
        println("🌐 [System] Fetching state from: $url")
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/hal+json")
            .GET()
            .build()

        return try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            response.body()
        } catch (e: Exception) {
            "{\"error\": \"Failed to fetch URL: ${e.message}\"}"
        }
    }

    @Tool
    @LLMDescription("Executes an HTTP request against a remote system.")
    fun executeAction(
        @LLMDescription("The HTTP method. Either GET or POST.") method: String,
        @LLMDescription("The target URL for the action") url: String,
        @LLMDescription("The strict JSON payload matching the schema. If method is GET, this JSON will be converted to query parameters.") payload: String
    ): String {
        println("🚀 [System] Executing $method to $url")
        println("📦 [System] Payload: $payload")

        return try {
            var finalUrl = url
            var requestBuilder = HttpRequest.newBuilder()

            // Handle GET (Convert JSON payload to Query String)
            if (method.equals("GET", ignoreCase = true)) {
                if (payload.isNotBlank() && payload != "{}") {
                    val jsonElements = Json.parseToJsonElement(payload).jsonObject
                    val queryParams = jsonElements.entries.joinToString("&") { (key, value) ->
                        val encodedValue = URLEncoder.encode(value.jsonPrimitive.content, "UTF-8")
                        "$key=$encodedValue"
                    }
                    finalUrl = if (url.contains("?")) "$url&$queryParams" else "$url?$queryParams"
                }
                requestBuilder = requestBuilder.uri(URI.create(finalUrl)).GET()
            }
            // Handle POST
            else {
                requestBuilder = requestBuilder.uri(URI.create(finalUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
            }

            val response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
            println("📥 [System] Response Code: ${response.statusCode()}")
            response.body()
        } catch (e: Exception) {
            "{\"error\": \"Execution failed: ${e.message}\"}"
        }
    }

    // 3. THE AGENT FACTORY

    fun buildAgent(): AIAgent<String, String> {
        val apiKey = System.getenv("GEMINI_API_KEY")
            ?: throw IllegalArgumentException("Missing GEMINI_API_KEY environment variable")

        return AIAgent(
            promptExecutor = simpleGoogleAIExecutor(apiKey),
            systemPrompt = """
                # Persona
                You are ARTIE (Autonomous REST Traversal & Interaction Engine). 
                Your ONLY contract with reality is the HAL Schema Forms protocol.
                
                # Core Loop
                1. Discover: Use `fetchApiState` to read the HAL document at the user's requested URL.
                2. Analyze: Look at the `_forms` block to see what actions are currently available. Look at `_links` to see where you can navigate.
                3. Evaluate: Match the user's intent to an available action or navigation link. Read its JSON Schema.
                4. Gather: If the user did not provide all required fields defined in a form's schema, use `askUser` to prompt them for the exact missing data.
                5. Execute: Once you have all data, construct the JSON payload and use `executeAction` to fire it at the target `href`.
                6. Verify: The output of `executeAction` may contain new links or forms. Continue exploring if the user's intent is not fully resolved.
                7. Report: Summarize the final outcome to the user. You MUST explicitly list the NEW state and the newly available actions (forms) discovered from the final fetch so the user knows what they can do next. If the response caontains only a self rel, then you MUST follow that and use its response for this summary of what can be done next
                
                # Constraints
                - You MUST NOT guess URLs. You may only interact with URLs explicitly provided in `_links` or `_forms.href`.
                - You cannot execute an action if it is not explicitly listed in the `_forms` block of the current state.
                - If a schema requires a field you don't know, you MUST ask the user.
            """.trimIndent(),
            llmModel = GoogleModels.Gemini3_Flash_Preview,
            toolRegistry = ToolRegistry {
                tool(::askUser)
                tool(::fetchApiState)
                tool(::executeAction)
            },
            temperature = 0.1
        ) {
            // THIS IS THE KOOG MAGIC
            // Installs the memory interceptor into the agent's execution graph.
            install(ChatMemory)
        }
    }

    fun main() = runBlocking {
        println("==================================================")
        println(" ARTIE (H.E.A.R.T. Protocol) Initialized")
        println("==================================================")

        val agent = buildAgent()
        val sessionId = "artie-session-01" // Used to key the memory

        val initialGoal = """
            Go to http://localhost:3000/.
            Find what's available at the root URL and summarize it to the user, then ask for their request.
        """.trimIndent()

        println("\n[Sending Initial Goal to Agent]")
        println(initialGoal)
        println("--------------------------------------------------\n")

        // 1. Kick off the conversation
        // Koog automatically creates the session history and saves the turn
        var result = agent.run(initialGoal, sessionId)
        println("\n✅ ARTIE:\n$result")

        // 2. The Clean Interactive Loop
        while (true) {
            print("\n👤 You: ")
            val userInput = readln()

            if (userInput.equals("quit", ignoreCase = true) || userInput.equals("exit", ignoreCase = true)) {
                println("ARTIE shutting down. Goodbye!")
                break
            }

            // 3. We just pass the NEW input along with the same session ID.
            // Koog automatically pulls the structured history (including all tool JSON payloads!),
            // appends the new message, and sends it to Gemini.
            result = agent.run(userInput, sessionId)
            println("\n✅ ARTIE:\n$result")
        }
    }



    //import ai.koog.agents.core.agent.AIAgent
    //import ai.koog.agents.core.tools.ToolRegistry
    //import ai.koog.agents.core.tools.annotations.LLMDescription
    //import ai.koog.agents.core.tools.annotations.Tool
    //import ai.koog.prompt.executor.clients.google.GoogleModels
    //import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
    //import kotlinx.coroutines.runBlocking
    //
    //@Tool
    //@LLMDescription("Ask the user a question by sending it to stdout and return the answer from stdin")
    //fun askUser(
    //    @LLMDescription("Question from the agent") question: String
    //): String {
    //    println("🤖 Agent asks: $question")
    //    return readln()
    //}
    //
    //// 1. THE NEW TOOL: Fetching the HAL Schema Forms Entry Point
    //@Tool
    //@LLMDescription("Fetches the current state and available actions (HAL Schema Forms) for a given system URL.")
    //fun fetchApiState(
    //    @LLMDescription("The URL of the API endpoint to fetch") url: String
    //): String {
    //    println("🌐 [System] Fetching HAL Forms from: $url")
    //
    //    // Returning a mocked StarbornAg cell response for the MVP
    //    return """
    //    {
    //      "cellId": "bed-1-cell-4",
    //      "status": "EMPTY",
    //      "_forms": {
    //        "sowSeed": {
    //          "method": "POST",
    //          "href": "/api/cells/bed-1-cell-4/sow",
    //          "schema": {
    //            "type": "object",
    //            "required": ["cropType", "seedCount"],
    //            "properties": {
    //              "cropType": { "type": "string", "description": "The type of crop being sown" },
    //              "seedCount": { "type": "integer", "description": "Number of seeds placed in the cell" }
    //            }
    //          }
    //        }
    //      }
    //    }
    //    """.trimIndent()
    //}
    //
    //fun buildAgent(): AIAgent<String, String> {
    //    // 2. USE ENV VARS FOR SAFETY
    //    val apiKey = System.getenv("GEMINI_API_KEY") ?: throw IllegalArgumentException("Missing API Key")
    //
    //    val agent = AIAgent(
    //        promptExecutor = simpleGoogleAIExecutor(apiKey),
    //        systemPrompt = """
    //            # Persona
    //            You are an Agentic API Client. Your job is to help users execute actions on remote systems.
    //
    //            # Typical flow
    //            1. The user will ask you to do something on a system (e.g., a URL).
    //            2. You MUST use the `fetchApiState` tool to get the HAL Schema Forms document for that URL.
    //            3. Read the `_forms` block to find the action that matches the user's intent.
    //            4. Look at the `schema` for that action. If the user didn't provide all the `required` properties in their initial prompt, use the `askUser` tool to gather the missing information.
    //            5. Once you have all the data, construct the final JSON payload that perfectly matches the schema.
    //
    //            # Expected output
    //            Return a final string that says: "I am ready to POST to [href]. Here is the payload: [JSON]"
    //        """.trimIndent(),
    //        llmModel = GoogleModels.Gemini3_Flash_Preview,
    //        toolRegistry = ToolRegistry {
    //            tool(::askUser)
    //            tool(::fetchApiState)
    //        },
    //        temperature = 0.2
    //    )
    //    return agent
    //}
    //
    //fun main() = runBlocking {
    //    println("Hello, StarbornAg!")
    //    val agent = buildAgent()
    //
    //    // 4. TRIGGER THE FLOW
    //    val prompt = "I need to plant some seeds in https://api.starborn.ag/cells/4. I want to plant tomatoes."
    //    println("👤 User: $prompt\n")
    //
    //    val result = agent.run(prompt)
    //
    //    println("\n✅ Final Result:\n$result")
    //}
