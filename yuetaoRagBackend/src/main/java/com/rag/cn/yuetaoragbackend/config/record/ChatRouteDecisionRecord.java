package com.rag.cn.yuetaoragbackend.config.record;

import com.rag.cn.yuetaoragbackend.dto.resp.IntentNodeTreeResp;

import java.util.List;

/**
 * 描述一次问题在意图识别之后的最终路由去向。
 */
public record ChatRouteDecisionRecord(RouteType routeType, String intentType, List<Long> knowledgeBaseIds,
                                      IntentNodeTreeResp systemLeaf, String promptSnippet, String promptTemplate) {

    public ChatRouteDecisionRecord {
        knowledgeBaseIds = knowledgeBaseIds == null ? List.of() : List.copyOf(knowledgeBaseIds);
    }

    public static ChatRouteDecisionRecord chitchat(String intentType) {
        return new ChatRouteDecisionRecord(RouteType.CHITCHAT, intentType, List.of(), null, null, null);
    }

    public static ChatRouteDecisionRecord globalKb(String intentType) {
        return new ChatRouteDecisionRecord(RouteType.GLOBAL_KB, intentType, List.of(), null, null, null);
    }

    public static ChatRouteDecisionRecord kb(String intentType, List<Long> knowledgeBaseIds,
                                             String promptSnippet, String promptTemplate) {
        return new ChatRouteDecisionRecord(RouteType.KB, intentType, knowledgeBaseIds, null, promptSnippet, promptTemplate);
    }

    public static ChatRouteDecisionRecord system(String intentType, IntentNodeTreeResp systemLeaf) {
        return new ChatRouteDecisionRecord(RouteType.SYSTEM, intentType, List.of(), systemLeaf,
                systemLeaf.getPromptSnippet(), systemLeaf.getPromptTemplate());
    }

    public boolean isChitchat() {
        return routeType == RouteType.CHITCHAT;
    }

    public boolean isSystem() {
        return routeType == RouteType.SYSTEM;
    }

    public boolean isKbScoped() {
        return routeType == RouteType.KB;
    }

    public boolean requiresRetrieval() {
        return isKbScoped() || routeType == RouteType.GLOBAL_KB;
    }

    public boolean requiresRewrite() {
        return requiresRetrieval() || isSystem();
    }

    public enum RouteType {
        CHITCHAT,
        GLOBAL_KB,
        KB,
        SYSTEM
    }
}
