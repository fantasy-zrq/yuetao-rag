/**
 * 流式对话前端控制常量。
 * `AbortError` 用来识别用户主动停止后的 fetch 中断；
 * trace 前缀则用于不支持 `crypto.randomUUID()` 的运行时兜底。
 */
export const CHAT_STREAM_ABORT_ERROR_NAME = "AbortError";
export const CHAT_STREAM_TRACE_PREFIX = "trace";
