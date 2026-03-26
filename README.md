# HEART: Hypermedia Enforced Actions for Reliable Transactions
## An Architectural Manifesto for the Agent-to-Agent (A2A) Economy

In the rapidly evolving landscape of Agentic AI, the industry has hit a wall: **The Hallucination Gap**. While Large Language Models (LLMs) are exceptional at reasoning, they are inherently probabilistic. When an agent is tasked with navigating a complex business system, it often "guesses" the next step, attempts invalid state transitions, or submits malformed data based on stale documentation.

**HEART** (Hypermedia Enforced Actions for Reliable Transactions) is a corrective architecture. It posits that for an agent to be truly autonomous and reliable, its "intelligence" must be constrained by the **Server’s State**. 

By utilizing **HAL-Forms**, HEART transforms the API from a passive data source into a **Dynamic State Machine** that physically enforces the boundaries of what an agent can and cannot do.

---

## The Core Problem: Brittle Autonomy
Traditional "Agentic" workflows rely on giving an LLM a massive OpenAPI/Swagger definition and a prompt. This is a recipe for failure:
* **Out-of-Sync State:** The agent tries to "Cancel" an order that has already "Shipped."
* **Schema Guessing:** The agent misses a mandatory field or uses the wrong data type.
* **Tight Coupling:** The agent is hardcoded to specific URLs, breaking the moment the API evolves.

## The Solution: HEART via HAL-Forms
HEART utilizes the **HAL-Forms** specification to provide a "Nervous System" for the agent. Instead of the agent deciding what is possible, the server provides a set of `_templates` that define the only valid actions available in the current context.

### The HEART Principles

| Principle | Technical Implementation |
| :--- | :--- |
| **Atomic Actions** | Every business process is broken down into discrete, self-describing HAL-Form templates. |
| **State-as-a-Guardrail** | If the action is not in the `_templates` block, it is logically impossible for the agent to execute. |
| **Schema Injection** | The server provides the exact field requirements (types, options, regex) inside the form, eliminating "hallucinated" payloads. |
| **A2A Decentralization** | Agents only need to understand the **HAL-Forms media type**, allowing them to work across any HEART-compliant system (e.g., StarbornAg). |

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

### The Vision: Beyond Centralized Control

​HEART is the exit strategy from proprietary, centralized "Intelligence Clouds." By making the web itself the platform for reliable transactions, we enable a future where independent agents—be they for agriculture, finance, or logistics—can collaborate with 100% deterministic reliability.

​HEART is currently being incubated within the StarbornAg project. It is the blueprint for a web where actions aren't just suggested—they are enforced.