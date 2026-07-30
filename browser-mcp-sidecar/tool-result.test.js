import { strict as assert } from "node:assert";
import test from "node:test";
import { structuredFromEvaluateResult } from "./tool-result.js";

test("structuredFromEvaluateResult parses fenced evaluate_script JSON", () => {
  const result = {
    content: [
      {
        type: "text",
        text: [
          "Script ran on page and returned:",
          "```json",
          "{\"success\":false,\"message\":\"Element not found: Delete\"}",
          "```"
        ].join("\n")
      }
    ]
  };

  assert.deepEqual(structuredFromEvaluateResult(result), {
    success: false,
    message: "Element not found: Delete"
  });
});

test("structuredFromEvaluateResult falls back to text message", () => {
  const result = { content: [{ type: "text", text: "Clicked page element" }] };

  assert.deepEqual(structuredFromEvaluateResult(result), {
    success: true,
    message: "Clicked page element"
  });
});
