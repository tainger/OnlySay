package com.onlysay.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 向量库辅助 SQL（接管旧 EmbeddingStoreFactory 中原生 JDBC 的 count/truncate）。
 * 向量读写仍由 PgVectorEmbeddingStore 负责，本 Mapper 只处理 EmbeddingStore 接口未暴露的统计/清空。
 *
 * ${table} 用于表名标识符注入（#{} 不支持表名参数）；
 * 值来自 application.yml 的 onlysay.pg.vector-table，可信。
 */
public interface VectorStatsMapper {

    /** 查询向量库记录数 */
    @Select("SELECT COUNT(*) FROM ${table}")
    int countByTable(@Param("table") String table);

    /** 清空向量表（重新录入前调用，避免重复向量） */
    @Update("TRUNCATE TABLE ${table}")
    void truncateByTable(@Param("table") String table);
}
