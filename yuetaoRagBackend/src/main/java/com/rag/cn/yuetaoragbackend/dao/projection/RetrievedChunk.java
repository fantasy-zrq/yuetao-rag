package com.rag.cn.yuetaoragbackend.dao.projection;

public record RetrievedChunk(Long chunkId, Long knowledgeBaseId, Long documentId, String documentTitle, Integer chunkNo,
                             String effectiveContent, Double vectorScore, Double lexicalScore,
                             Double finalScore) {

    public RetrievedChunk withScores(Double nextLexicalScore, Double nextFinalScore) {
        return new RetrievedChunk(
                this.chunkId,
                this.knowledgeBaseId,
                this.documentId,
                this.documentTitle,
                this.chunkNo,
                this.effectiveContent,
                this.vectorScore,
                nextLexicalScore,
                nextFinalScore);
    }
}
