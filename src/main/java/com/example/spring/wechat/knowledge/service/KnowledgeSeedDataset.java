package com.example.spring.wechat.knowledge.service;

import com.example.spring.wechat.knowledge.model.KnowledgeSeedDocument;

import java.util.List;

public final class KnowledgeSeedDataset {

    private static final List<KnowledgeSeedDocument> DOCUMENTS = List.of(
            new KnowledgeSeedDocument(
                    "OpenClaw RAG 工作流",
                    """
                            RAG 的核心流程是检索、增强、生成。用户提问先经过 query planning，把原始问题改写成更适合检索的多个查询，再从知识库召回相关片段。
                            召回后要做过滤和重排，优先保留和问题最相关、标题和正文同时命中的内容。最后把证据片段整理成紧凑上下文，再交给 LLM 生成回复。

                            在 OpenClaw 里，RAG 需要保持 fail-open：检索失败不能阻塞正常对话。知识增强只是上下文补充，不是强依赖。
                            """.strip(),
                    "text",
                    "local://knowledge-samples/rag-workflow",
                    "rag,workflow,knowledge,search"),
            new KnowledgeSeedDocument(
                    "知识库入库规范",
                    """
                            知识库入库时，标题、正文、来源类型、来源地址和标签都要尽量完整。正文太短会影响切分和召回，正文太长则应该先做清洗和分段。
                            同一 session 下，重复内容要基于内容哈希去重，避免反复写入相同文档。标签建议包含主题词，例如 rag、qdrant、wechat、tooling。

                            如果来源是网页，最好保留原始 URL；如果是本地笔记或测试样本，可以使用 local:// 形式的伪来源，方便调试和区分数据集。
                            """.strip(),
                    "text",
                    "local://knowledge-samples/ingestion-guidelines",
                    "knowledge,ingestion,metadata,test"),
            new KnowledgeSeedDocument(
                    "微信消息处理流程",
                    """
                            微信消息先进入消息处理层，再判断是否需要调用工具。普通问答直接走 LLM；涉及天气、图片、语音、文档或知识库时，则先进入对应工具链。
                            对于知识类问题，推荐先做 RAG 检索增强上下文，再调用 LLM 生成最终回复。这样可以减少幻觉，也方便在回复里保留来源线索。

                            如果检索没有命中，也要继续给出正常回答，或者明确说明没有找到资料，而不是让整条消息失败。
                            """.strip(),
                    "text",
                    "local://knowledge-samples/message-flow",
                    "wechat,conversation,rag,tooling"),
            new KnowledgeSeedDocument(
                    "Qdrant 检索调优笔记",
                    """
                            Qdrant 检索调优通常围绕 chunk size、chunk overlap、topK、min score 和 query rewrite 展开。chunk 太大，召回会变粗；chunk 太小，上下文又容易丢失。
                            多查询规划可以提升覆盖率，特别是当用户问题包含“怎么做”“流程是什么”“如何接入”这类模糊表达时。

                            重排时可以给标题命中和正文命中额外加分，再把多个相邻分片合并成更连贯的证据包，最后交给模型生成。
                            """.strip(),
                    "text",
                    "local://knowledge-samples/qdrant-tuning",
                    "qdrant,retrieval,ranking,vector"),
            new KnowledgeSeedDocument(
                    "RAG 排查清单",
                    """
                            当 RAG 检索结果太少时，先确认 sessionKey 是否一致，再看知识是否已经完成入库、向量是否写入、标签过滤是否过严。
                            如果召回结果很多但答案仍然不准，通常需要检查 query planner、rerank 以及证据拼接长度。也要确认模型拿到的是原始证据，而不是被截断的摘要。

                            这类排查最适合做成固定测试集：同一批问题反复跑，观察命中率、排序和最终回复是否稳定。
                            """.strip(),
                    "text",
                    "local://knowledge-samples/rag-troubleshooting",
                    "rag,debug,checklist,test"));

    private KnowledgeSeedDataset() {
    }

    public static List<KnowledgeSeedDocument> documents() {
        return DOCUMENTS;
    }
}
