package com.onlysay.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 意图识别明细/修正配对落库 Mapper。
 * JSONB 列通过 #{...}::jsonb 由字符串强转写入；MyBatis 防注入仍由 #{} 保证。
 */
public interface IntentLogMapper {

    /** 写入一条识别明细 */
    @Insert("""
            INSERT INTO intent_recognition_log
                (session_id, user_input, intent, hit_layer, confidence, latency_ms, trace)
            VALUES
                (#{sessionId}, #{userInput}, #{intent}, #{hitLayer},
                 #{confidence}, #{latencyMs}, #{traceJson}::jsonb)
            """)
    void insertRecognitionLog(@Param("sessionId") String sessionId,
                              @Param("userInput") String userInput,
                              @Param("intent") String intent,
                              @Param("hitLayer") String hitLayer,
                              @Param("confidence") double confidence,
                              @Param("latencyMs") long latencyMs,
                              @Param("traceJson") String traceJson);

    /** 写入一条澄清-修正配对（数据回流） */
    @Insert("""
            INSERT INTO intent_correction
                (session_id, clarification_input, correction_input, final_intent, final_hit_layer)
            VALUES
                (#{sessionId}, #{clarificationInput}, #{correctionInput},
                 #{finalIntent}, #{finalHitLayer})
            """)
    void insertCorrection(@Param("sessionId") String sessionId,
                          @Param("clarificationInput") String clarificationInput,
                          @Param("correctionInput") String correctionInput,
                          @Param("finalIntent") String finalIntent,
                          @Param("finalHitLayer") String finalHitLayer);

    /** 最近 N 条识别明细（预留：前端趋势/调试列表） */
    @Select("""
            SELECT id, session_id AS sessionId, user_input AS userInput,
                   intent, hit_layer AS hitLayer, confidence,
                   latency_ms AS latencyMs, created_at AS createdAt
            FROM intent_recognition_log
            ORDER BY id DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> selectRecent(@Param("limit") int limit);
}
