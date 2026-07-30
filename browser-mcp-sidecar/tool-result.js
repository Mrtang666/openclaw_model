export function textFromContent(result) {
  const content = Array.isArray(result?.content) ? result.content : [];
  return content.map((item) => item?.text || "").filter(Boolean).join("\n").trim();
}

export function structuredFromEvaluateResult(result) {
  const text = textFromContent(result);
  const parsed = parseEvaluateJson(text);
  if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
    return {
      success: parsed.success ?? true,
      message: parsed.message || text || "Browser action completed",
      ...parsed
    };
  }
  return {
    success: true,
    message: text || "Browser action completed"
  };
}

function parseEvaluateJson(text) {
  const value = text || "";
  const fenced = value.match(/```json\s*([\s\S]*?)```/i);
  const jsonText = fenced ? fenced[1].trim() : value.trim();
  if (!jsonText || !(jsonText.startsWith("{") || jsonText.startsWith("["))) {
    return undefined;
  }
  try {
    return JSON.parse(jsonText);
  } catch {
    return undefined;
  }
}
