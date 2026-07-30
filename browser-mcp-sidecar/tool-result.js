export function textFromContent(result) {
  const content = Array.isArray(result?.content) ? result.content : [];
  return content.map((item) => item?.text || "").filter(Boolean).join("\n").trim();
}

export function structuredFromEvaluateResult(result) {
  const text = textFromContent(result);
  const parsed = valueFromEvaluateResult(result);
  if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
    return {
      success: parsed.success ?? true,
      message: parsed.message || text || "Browser action completed",
      ...parsed
    };
  }
  if (isChromeTargetError(text)) {
    return {
      success: false,
      message: text
    };
  }
  return {
    success: true,
    message: text || "Browser action completed"
  };
}

export function valueFromEvaluateResult(result) {
  return parseEvaluateJson(textFromContent(result));
}

function parseEvaluateJson(text) {
  const value = text || "";
  const fenced = value.match(/```json\s*([\s\S]*?)```/i);
  const jsonText = fenced ? fenced[1].trim() : value.trim();
  if (!jsonText || (!fenced && !(jsonText.startsWith("{") || jsonText.startsWith("[") || jsonText.startsWith("\"")))) {
    return undefined;
  }
  try {
    return JSON.parse(jsonText);
  } catch {
    return undefined;
  }
}

function isChromeTargetError(text) {
  const value = text || "";
  return value.includes("Protocol error") || value.includes("Target closed");
}
