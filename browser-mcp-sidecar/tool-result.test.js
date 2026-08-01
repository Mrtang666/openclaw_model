import { strict as assert } from "node:assert";
import test from "node:test";
import { structuredFromEvaluateResult, valueFromEvaluateResult } from "./tool-result.js";

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

test("structuredFromEvaluateResult marks Chrome target errors as failed", () => {
  const result = { content: [{ type: "text", text: "Protocol error (Target.setDiscoverTargets): Target closed" }] };

  assert.deepEqual(structuredFromEvaluateResult(result), {
    success: false,
    message: "Protocol error (Target.setDiscoverTargets): Target closed"
  });
});

test("valueFromEvaluateResult parses primitive fenced evaluate_script values", () => {
  const result = {
    content: [
      {
        type: "text",
        text: [
          "Script ran on page and returned:",
          "```json",
          "\"{\\\"ok\\\":true}\"",
          "```"
        ].join("\n")
      }
    ]
  };

  assert.equal(valueFromEvaluateResult(result), "{\"ok\":true}");
});

test("valueFromEvaluateResult parses slim evaluate values", () => {
  const result = { content: [{ type: "text", text: "{\"ok\":true}" }] };

  assert.deepEqual(valueFromEvaluateResult(result), { ok: true });
});
