# HEART: Hypermedia Enforced Actions for Reliable Transactions
## An Architectural Manifesto for the Autonomous Transaction Economy

In the rapidly evolving landscape of AI, we have hit the **Hallucination Gap**. Whether it is an AI agent calling an API (**A2A**) or a human commanding an agent (**H2A**), the link between "intent" and "execution" is brittle. 

**HEART** is a corrective architecture. By utilizing **HAL-Forms**, HEART transforms APIs into **Dynamic State Machines** that physically enforce the boundaries of every interaction across A2A, H2A, and **A2H** (Agent-to-Human) workflows.

---

## 1. Agent-to-Agent (A2A): Deterministic Autonomy
In a decentralized economy, agents must collaborate without shared codebases. 
* **The Problem:** Agents "guess" payloads based on stale documentation.
* **The HEART Solution:** The server provides `_templates` that dictate the exact schema required for the next state transition. If an action isn't in the template, it is logically impossible for the agent to execute.

## 2. Human-to-Agent (H2A): Intent-to-Action Mapping
HEART allows humans to interact with complex systems using natural language without the risk of the agent "going rogue."
* **The AGENTS.md Layer:** A generic agent is taught how to interact with a HEART system via an `AGENTS.md` file. This file provides the "meta-skill" instructions: *"Always parse HAL-Forms templates to resolve user intents into valid state transitions."*
* **Reliable Execution:** When a user says, *"Harvest the tomatoes,"* the agent doesn't guess the endpoint. It locates the `harvest` template in the current resource and maps the user's intent to the strictly required fields.

## 3. Agent-to-Human (A2H): Dynamic UI & Communication
HEART enables agents to bridge the gap back to the user with functional precision.
* **Natural Translation:** A generic agent can take a technical HAL link and rephrase it: *"The tomatoes are mature; would you like me to record a harvest for you?"*
* **Dynamic UI Rendering:** Because HAL-Forms provide metadata (types, prompts, options, and requirements), a generic agent can **dynamically render UI fields** on the fly. If a `_templates` element requires a field, the agent knows exactly what to ask the human.

---

## The HEART Principles

| Principle | Technical Implementation |
| :--- | :--- |
| **Atomic Actions** | Business processes are discrete, self-describing HAL-Form templates. |
| **State-as-a-Guardrail** | Transitions are only valid if provided in the current `_templates` block. |
| **Schema Injection** | The server dictates field requirements and types, eliminating "hallucinated" payloads. |
| **Enforced Requirements** | Strict use of `required: true` in templates to ensure transactional integrity. |

---

## Technical Proof of Concept (StarbornAg)

A human tells the agent: *"I just picked 5kg of tomatoes."* The agent GETs the crop status:

```json
{
  "status": "MATURE",
  "_templates": {
    "harvest": {
      "title": "Record Harvest",
      "method": "POST",
      "target": "/api/crops/tomato-04/harvest",
      "properties": [
        { 
          "name": "yield_kg", 
          "type": "number", 
          "required": true, 
          "prompt": "Weight in kg" 
        },
        { 
          "name": "quality", 
          "type": "text", 
          "required": true, 
          "options": { "inline": ["A", "B"] } 
        }
      ]
    }
  }
}


---

## Technical Proof of Concept
In this **StarbornAg** example, a gardening agent is managing a crop. The agent doesn't need to know the business logic for "Harvesting"—it simply follows the template provided by the server.

```http
GET /api/crops/heirloom-tomato-04
Content-Type: application/prs.hal-forms+json
```

```json
{
  "_links": {
    "self": { "href": "/api/crops/heirloom-tomato-04" }
  },
  "status": "MATURE",
  "method": "VEGANIC",
  
  "_templates": {
    "harvest": {
      "title": "Record Harvest Action",
      "method": "POST",
      "target": "/api/crops/heirloom-tomato-04/harvest",
      "properties": [
        {
          "name": "yield_kg",
          "type": "number",
          "required": true,
          "prompt": "Enter the weight of the harvest"
        },
        {
          "name": "quality_grade",
          "type": "text",
          "options": {
            "inline": ["A", "B", "C"]
          }
        }
      ]
    }
  }
}
```

**The A2H/H2A Flow:**
1. **Agent identifies** that `quality` is `required: true` but missing from the user's statement.
2. **Agent renders UI/Asks:** "Great! I need to know the quality grade to finish the record. Was it Grade A or Grade B?"
3. **User responds:** "Grade A."
4. **Agent executes** the POST with 100% deterministic accuracy because the schema was enforced.

---

## The Vision: The End of Proprietary Silos
HEART is the exit strategy from proprietary "Intelligence Clouds." By making the web itself the platform for reliable transactions, we enable a future where humans and agents collaborate with absolute certainty.

**HEART is currently being incubated within the StarbornAg project.**
