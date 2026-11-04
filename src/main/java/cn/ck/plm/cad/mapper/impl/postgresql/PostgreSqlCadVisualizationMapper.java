package cn.ck.plm.cad.mapper.impl.postgresql;

import cn.ck.plm.cad.dto.CadConvertJobVO;
import cn.ck.plm.cad.dto.CadConvertStatsVO;
import cn.ck.plm.cad.dto.ContainerCadModelVO;
import cn.ck.plm.cad.entity.CadVisualization;
import cn.ck.plm.cad.mapper.CadVisualizationMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlCadVisualizationMapper extends CadVisualizationMapper {

    @Override
    @Insert("INSERT INTO ck_cad_visualization (oid, tenant_oid, eng_doc_iteration_oid, " +
            "source_ckfile_oid, source_sha1, source_file_name, status, progress, attempt_count, " +
            "creator, created_at, updater, updated_at, submitted_at) " +
            "VALUES (#{oid}, #{tenantOid}, #{engDocIterationOid}, " +
            "#{sourceCkfileOid}, #{sourceSha1}, #{sourceFileName}, #{status}, #{progress}, #{attemptCount}, " +
            "#{creator}, #{createdAt}, #{updater}, #{updatedAt}, #{submittedAt})")
    int insert(CadVisualization record);

    /**
     * 全字段更新。
     *
     * <p>产物 oid 用 COALESCE：回调可能只携带部分产物（比如缩略图生成失败但 GLB 成功），
     * 直接覆盖会把已有的产物引用抹成 NULL。
     */
    @Override
    @Update("UPDATE ck_cad_visualization SET " +
            "status = #{status}, progress = #{progress}, stage = #{stage}, " +
            "forge_job_id = COALESCE(#{forgeJobId}, forge_job_id), " +
            "glb_ckfile_oid = COALESCE(#{glbCkfileOid}, glb_ckfile_oid), " +
            "structure_ckfile_oid = COALESCE(#{structureCkfileOid}, structure_ckfile_oid), " +
            "thumbnail_ckfile_oid = COALESCE(#{thumbnailCkfileOid}, thumbnail_ckfile_oid), " +
            "lod_ckfile_oid = COALESCE(#{lodCkfileOid}, lod_ckfile_oid), " +
            "units = COALESCE(#{units}, units), " +
            "triangle_count = COALESCE(#{triangleCount}, triangle_count), " +
            "part_count = COALESCE(#{partCount}, part_count), " +
            "output_size = COALESCE(#{outputSize}, output_size), " +
            "duration_ms = COALESCE(#{durationMs}, duration_ms), " +
            "converter_version = COALESCE(#{converterVersion}, converter_version), " +
            "error_code = #{errorCode}, error_message = #{errorMessage}, " +
            "attempt_count = #{attemptCount}, converted_at = #{convertedAt}, " +
            "updater = #{updater}, updated_at = #{updatedAt} " +
            "WHERE oid = #{oid}")
    int update(CadVisualization record);

    String SELECT_COLUMNS = "SELECT oid, tenant_oid, eng_doc_iteration_oid, source_ckfile_oid, source_sha1, " +
            "source_file_name, status, progress, stage, forge_job_id, glb_ckfile_oid, structure_ckfile_oid, " +
            "thumbnail_ckfile_oid, lod_ckfile_oid, units, triangle_count, part_count, source_size, output_size, " +
            "duration_ms, converter_version, error_code, error_message, attempt_count, " +
            "submitted_at, converted_at, creator, created_at, updater, updated_at " +
            "FROM ck_cad_visualization ";

    @Select(SELECT_COLUMNS + "WHERE oid = #{oid}")
    @Results(id = "cadVisResult", value = {
            @Result(property = "oid",                  column = "oid"),
            @Result(property = "tenantOid",            column = "tenant_oid"),
            @Result(property = "engDocIterationOid",   column = "eng_doc_iteration_oid"),
            @Result(property = "sourceCkfileOid",      column = "source_ckfile_oid"),
            @Result(property = "sourceSha1",           column = "source_sha1"),
            @Result(property = "sourceFileName",       column = "source_file_name"),
            @Result(property = "status",               column = "status"),
            @Result(property = "progress",             column = "progress"),
            @Result(property = "stage",                column = "stage"),
            @Result(property = "forgeJobId",           column = "forge_job_id"),
            @Result(property = "glbCkfileOid",         column = "glb_ckfile_oid"),
            @Result(property = "structureCkfileOid",   column = "structure_ckfile_oid"),
            @Result(property = "thumbnailCkfileOid",   column = "thumbnail_ckfile_oid"),
            @Result(property = "lodCkfileOid",         column = "lod_ckfile_oid"),
            @Result(property = "units",                column = "units"),
            @Result(property = "triangleCount",        column = "triangle_count"),
            @Result(property = "partCount",            column = "part_count"),
            @Result(property = "sourceSize",           column = "source_size"),
            @Result(property = "outputSize",           column = "output_size"),
            @Result(property = "durationMs",           column = "duration_ms"),
            @Result(property = "converterVersion",     column = "converter_version"),
            @Result(property = "errorCode",            column = "error_code"),
            @Result(property = "errorMessage",         column = "error_message"),
            @Result(property = "attemptCount",         column = "attempt_count"),
            @Result(property = "submittedAt",          column = "submitted_at"),
            @Result(property = "convertedAt",          column = "converted_at"),
            @Result(property = "creator",              column = "creator"),
            @Result(property = "createdAt",            column = "created_at"),
            @Result(property = "updater",              column = "updater"),
            @Result(property = "updatedAt",            column = "updated_at")
    })
    @Override
    CadVisualization selectByOid(@Param("oid") String oid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE eng_doc_iteration_oid = #{engDocIterationOid} AND source_sha1 = #{sourceSha1} " +
            "ORDER BY created_at DESC LIMIT 1")
    @ResultMap("cadVisResult")
    CadVisualization selectByIterationAndSha1(@Param("engDocIterationOid") String engDocIterationOid,
                                              @Param("sourceSha1") String sourceSha1);

    @Override
    @Select(SELECT_COLUMNS + "WHERE eng_doc_iteration_oid = #{engDocIterationOid} " +
            "ORDER BY created_at DESC LIMIT 1")
    @ResultMap("cadVisResult")
    CadVisualization selectLatestByIteration(@Param("engDocIterationOid") String engDocIterationOid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE forge_job_id = #{forgeJobId} ORDER BY created_at DESC LIMIT 1")
    @ResultMap("cadVisResult")
    CadVisualization selectByForgeJobId(@Param("forgeJobId") String forgeJobId);

    @Override
    @Select(SELECT_COLUMNS +
            "WHERE status IN ('QUEUED','RUNNING') AND submitted_at < #{cutoff} " +
            "ORDER BY submitted_at ASC LIMIT #{limit}")
    @ResultMap("cadVisResult")
    List<CadVisualization> selectStaleInFlight(@Param("cutoff") LocalDateTime cutoff,
                                               @Param("limit") int limit);

    /**
     * 容器 → 工程数据（ENG_DOCUMENT）最新迭代 → CAD 主文件 → 最近一次转换记录。
     *
     * <p><b>为什么从 {@code ck_eng_document ed} 起手</b>：三维数模在 Windchill 里是
     * EPMDocument，落 {@code ck_eng_document} + {@code ck_eng_document_iteration}
     * （主文件列 {@code ckfile_oid}），与通用文档 {@code ck_document} 是并列的两个类。
     * 通用文档不带 CAD 专有属性（cadName / cadType / cadTool），不能拿来当数模载体。
     *
     * <p>租户拦截器按 {@code FROM} 主表别名注入 {@code ed.tenant_oid}
     * （见 {@code TenantStatementInterceptor#extractMainAlias}），多表 join 时列名必须带前缀。
     *
     * <p><b>已知脏数据风险</b>：{@code ck_cad_visualization.eng_doc_iteration_oid} 是逻辑外键、
     * 无约束，历史上有调用方（通用文档的三维预览入口）把 {@code ck_document_iteration.oid}
     * 传进来过。这类记录在本查询里匹配不到任何工程数据迭代，只会沉淀成孤儿行 ——
     * 不影响结果正确性，但排查"为什么转换记录对不上"时要想到这一层。
     *
     * <p><b>为什么用 {@code LATERAL ... LIMIT 1}</b>：一个迭代可能有多条转换记录
     * （{@code uk_cadvis_iter_sha1} 允许同一迭代存多份不同 sha1 的记录）。
     * 直接 join 会把一行数模复制成多行，列表上就是"同一个 STEP 出现三次"。
     *
     * <p><b>格式过滤放在 SQL</b>：CAD 源文件按后缀白名单识别。
     * 白名单比 {@code CadForgeProperties.supportedExtensions}（.step/.stp/.iges/.igs/.stl）**更宽** ——
     * CATIA / Creo / NX / SolidWorks 原生数模也挂在迭代上，只是转换器读不了（状态恒为"未转换"）。
     * 若这里仍只认中性交换格式，用户上传原生数模后本页签看不到它，会以为没保存成功。
     * 拉到前端再筛等于把整张工程数据表搬过去，且"共 N 份数模"会算错。
     *
     * <p><b>转换记录走 LEFT JOIN</b>：没有转换记录说明"还没生成预览"，这类行必须出现 ——
     * 否则用户看到的是"这个产品没有数模"，而不是"有数模但还没转"。
     */
    @Override
    @Select("SELECT ed.oid AS \"engDocOid\", ed.number AS \"engDocNumber\", ed.name AS \"engDocName\", "
            + "ed.container_type AS \"containerType\", ed.container_oid AS \"containerOid\", "
            + "ei.oid AS \"iterationOid\", ei.display_version AS \"iterationVersion\", "
            + "ei.status AS \"iterationStatus\", "
            // 检出状态挂在工程数据迭代上（与物料清单的零件迭代同一套语义），
            // 数模列表要据此显示「已检出: 谁 / 已检入」
            + "ei.checked_out AS \"checkedOut\", ei.checked_out_by AS \"checkedOutBy\", "
            + "ei.checked_out_comment AS \"checkedOutComment\", "
            + "ei.cad_name AS \"cadName\", ei.cad_type AS \"cadType\", ei.cad_tool AS \"cadTool\", "
            + "f.oid AS \"sourceCkfileOid\", f.file_name AS \"fileName\", f.file_size AS \"fileSize\", "
            + "cv.oid AS \"cadVisOid\", cv.status AS \"status\", cv.progress AS \"progress\", "
            + "cv.stage AS \"stage\", cv.error_message AS \"errorMessage\", "
            + "cv.glb_ckfile_oid AS \"glbCkfileOid\", cv.structure_ckfile_oid AS \"structureCkfileOid\", "
            + "cv.thumbnail_ckfile_oid AS \"thumbnailCkfileOid\", cv.lod_ckfile_oid AS \"lodCkfileOid\", "
            + "cv.triangle_count AS \"triangleCount\", cv.part_count AS \"partCount\", cv.units AS \"units\", "
            + "cv.converted_at AS \"convertedAt\", cv.updated_at AS \"statusUpdatedAt\" "
            + "FROM ck_eng_document ed "
            + "JOIN ck_eng_document_iteration ei "
            + "  ON ei.master_oid = ed.oid AND ei.latest = TRUE AND ei.delete_mark = FALSE "
            + "JOIN ck_file f ON f.oid = ei.ckfile_oid "
            + "LEFT JOIN LATERAL ("
            + "  SELECT c.oid, c.status, c.progress, c.stage, c.error_message, c.glb_ckfile_oid, "
            + "         c.structure_ckfile_oid, c.thumbnail_ckfile_oid, c.lod_ckfile_oid, "
            + "         c.triangle_count, c.part_count, c.units, c.converted_at, c.updated_at "
            + "  FROM ck_cad_visualization c "
            // 本迭代自己的记录优先；没有就沿「检出副本 → 源版本」复用一次。
            // 检出（EngineeringDocumentCheckoutProvider#checkout）会把迭代 +1 并**继承同一个 ckfile_oid**：
            // 文件没变、几何没变，轻量化产物不跟着搬过去的话，用户一检出就变成"未转换 + 缩略图丢失"，
            // 看起来像转换结果被检出弄坏了。ckfile_oid 相等是"内容确实没变"的判据。
            + "  WHERE (c.eng_doc_iteration_oid = ei.oid "
            + "     OR (c.eng_doc_iteration_oid = ei.derived_from_oid "
            + "         AND EXISTS (SELECT 1 FROM ck_eng_document_iteration s "
            + "                     WHERE s.oid = ei.derived_from_oid AND s.ckfile_oid = ei.ckfile_oid))) "
            // 有本迭代自己的记录时一定用它（哪怕更旧），否则会出现"检入后的新转换结果被旧版产物顶掉"
            + "  ORDER BY (c.eng_doc_iteration_oid = ei.oid) DESC, c.created_at DESC LIMIT 1"
            + ") cv ON TRUE "
            + "WHERE (LOWER(f.file_name) LIKE '%.step' OR LOWER(f.file_name) LIKE '%.stp' "
            + "    OR LOWER(f.file_name) LIKE '%.iges' OR LOWER(f.file_name) LIKE '%.igs' "
            + "    OR LOWER(f.file_name) LIKE '%.stl' "
            // 原生数模：仅归档，不能转轻量化预览（见方法注释的白名单口径）
            + "    OR LOWER(f.file_name) LIKE '%.catpart' OR LOWER(f.file_name) LIKE '%.catproduct' "
            + "    OR LOWER(f.file_name) LIKE '%.prt' OR LOWER(f.file_name) LIKE '%.asm' "
            + "    OR LOWER(f.file_name) LIKE '%.sldprt' OR LOWER(f.file_name) LIKE '%.sldasm') "
            // 容器口径三条并集：自己、父系列（型号页要看到系列级整机数模）、
            // 以及下属型号（系列页要汇总各型号的数模；历史数据有直接挂在型号上的）
            + "  AND (ed.container_oid = #{containerOid} "
            + "    OR ed.container_oid = (SELECT m.parent_oid FROM ck_product_model m WHERE m.oid = #{containerOid}) "
            + "    OR ed.container_oid IN (SELECT m2.oid FROM ck_product_model m2 WHERE m2.parent_oid = #{containerOid})) "
            + "ORDER BY ed.number NULLS LAST, f.file_name")
    List<ContainerCadModelVO> selectModelsByContainer(@Param("containerOid") String containerOid);

    /**
     * 零件版本 → 关联工程数据上的数模 + 最近一次转换状态（单个零件版本的「轻量化」页签）。
     *
     * <p>与 {@code selectModelsByContainer} 只差"从哪张表起手"：
     * 这里从 {@code ck_eng_doc_part_link}（EPMBuildRule）切入，所以先按零件迭代找工程数据；
     * 主表仍是 {@code ck_eng_document ed}，租户拦截器按主别名注入 {@code ed.tenant_oid}。
     *
     * <p>三个细节：
     * <ul>
     *   <li><b>跟随最新 vs 锁定版本</b>：{@code eng_doc_iteration_oid} 为空 = 跟随工程数据最新版，
     *       否则锁定到指定迭代（与 EngDocPartLinkController#toVO 同一口径）；</li>
     *   <li><b>检出后仍能预览</b>：关联记在零件迭代上，检出会新建迭代而不复制关联，
     *       所以放宽到同一零件主对象的其它迭代，{@code ORDER BY (精确命中) DESC} 保证当前版本优先；</li>
     *   <li><b>格式白名单与容器口径一致</b>：STEP/IGES/STL 可转；CATIA/Creo/NX/SW 原生数模只归档。</li>
     * </ul>
     */
    @Override
    @Select("SELECT ed.oid AS \"engDocOid\", ed.number AS \"engDocNumber\", ed.name AS \"engDocName\", "
            + "ed.container_type AS \"containerType\", ed.container_oid AS \"containerOid\", "
            + "ei.oid AS \"iterationOid\", ei.display_version AS \"iterationVersion\", "
            + "ei.status AS \"iterationStatus\", "
            + "ei.checked_out AS \"checkedOut\", ei.checked_out_by AS \"checkedOutBy\", "
            + "ei.checked_out_comment AS \"checkedOutComment\", "
            + "ei.cad_name AS \"cadName\", ei.cad_type AS \"cadType\", ei.cad_tool AS \"cadTool\", "
            + "f.oid AS \"sourceCkfileOid\", f.file_name AS \"fileName\", f.file_size AS \"fileSize\", "
            + "cv.oid AS \"cadVisOid\", cv.status AS \"status\", cv.progress AS \"progress\", "
            + "cv.stage AS \"stage\", cv.error_message AS \"errorMessage\", "
            + "cv.glb_ckfile_oid AS \"glbCkfileOid\", cv.structure_ckfile_oid AS \"structureCkfileOid\", "
            + "cv.thumbnail_ckfile_oid AS \"thumbnailCkfileOid\", cv.lod_ckfile_oid AS \"lodCkfileOid\", "
            + "cv.triangle_count AS \"triangleCount\", cv.part_count AS \"partCount\", cv.units AS \"units\", "
            + "cv.converted_at AS \"convertedAt\", cv.updated_at AS \"statusUpdatedAt\" "
            + "FROM ck_eng_document ed "
            + "JOIN ck_eng_doc_part_link l ON l.eng_document_oid = ed.oid "
            + "JOIN ck_part_iteration pi ON pi.oid = l.part_iteration_oid "
            + "JOIN ck_eng_document_iteration ei ON ei.master_oid = ed.oid AND ei.delete_mark = FALSE "
            + " AND ((l.eng_doc_iteration_oid IS NOT NULL AND ei.oid = l.eng_doc_iteration_oid) "
            + "   OR (l.eng_doc_iteration_oid IS NULL AND ei.latest = TRUE)) "
            + "JOIN ck_file f ON f.oid = ei.ckfile_oid "
            + "LEFT JOIN LATERAL ("
            + "  SELECT c.oid, c.status, c.progress, c.stage, c.error_message, c.glb_ckfile_oid, "
            + "         c.structure_ckfile_oid, c.thumbnail_ckfile_oid, c.lod_ckfile_oid, "
            + "         c.triangle_count, c.part_count, c.units, c.converted_at, c.updated_at "
            + "  FROM ck_cad_visualization c "
            + "  WHERE (c.eng_doc_iteration_oid = ei.oid "
            + "     OR (c.eng_doc_iteration_oid = ei.derived_from_oid "
            + "         AND EXISTS (SELECT 1 FROM ck_eng_document_iteration s "
            + "                     WHERE s.oid = ei.derived_from_oid AND s.ckfile_oid = ei.ckfile_oid))) "
            + "  ORDER BY (c.eng_doc_iteration_oid = ei.oid) DESC, c.created_at DESC LIMIT 1"
            + ") cv ON TRUE "
            + "WHERE pi.master_oid = (SELECT master_oid FROM ck_part_iteration WHERE oid = #{partIterationOid}) "
            + "  AND (LOWER(f.file_name) LIKE '%.step' OR LOWER(f.file_name) LIKE '%.stp' "
            + "    OR LOWER(f.file_name) LIKE '%.iges' OR LOWER(f.file_name) LIKE '%.igs' "
            + "    OR LOWER(f.file_name) LIKE '%.stl' "
            + "    OR LOWER(f.file_name) LIKE '%.catpart' OR LOWER(f.file_name) LIKE '%.catproduct' "
            + "    OR LOWER(f.file_name) LIKE '%.prt' OR LOWER(f.file_name) LIKE '%.asm' "
            + "    OR LOWER(f.file_name) LIKE '%.sldprt' OR LOWER(f.file_name) LIKE '%.sldasm') "
            + "ORDER BY (l.part_iteration_oid = #{partIterationOid}) DESC, l.sort_order ASC, l.created_at DESC "
            + "LIMIT 1")
    ContainerCadModelVO selectModelByPartIteration(@Param("partIterationOid") String partIterationOid);

    /**
     * 零件版本 → 缩略图（物料清单列表用）。
     *
     * <p><b>为什么按零件主对象兜底</b>（2026-10-26 用户报"检出后缩略图没有了"）：
     * 工程数据关联记在**零件迭代**上（EPMBuildRule），而检出会新建一个小版本迭代
     * （A-Z.1 → A-Z.2，{@code derived_from_oid} 指向前一版），关联并不会跟着复制过去 ——
     * 严格按迭代匹配的话，一检出缩略图就消失。用户口径是"<b>Part 对象</b>关联了 CAD 描述就显示缩略图"，
     * 所以这里允许回退到同主对象的其它迭代，但仍<b>优先精确匹配当前迭代</b>
     * （见 {@code ORDER BY (l.part_iteration_oid = i.oid) DESC}）。
     *
     * <p>主表是 {@code ck_part_iteration}：租户拦截器按主表别名注入 {@code i.tenant_oid}，
     * join 进来的链接 / 工程数据 / 可视化记录随零件迭代天然同租户。
     *
     * <p>「跟随最新」与「钉住迭代」用同一个 JOIN 条件表达（{@code l.eng_doc_iteration_oid IS NULL} = 跟随最新）；
     * {@code DISTINCT ON} 保证一个零件版本只出一行（一个迭代可能有多条不同内容指纹的转换记录）。
     *
     * <p><b>工程数据被检出时同样要复用</b>（与 {@link #selectModelsByContainer} 同一口径）：
     * 检出会新建迭代 +1 并<b>继承同一个 {@code ckfile_oid}</b>，而转换记录仍挂在被检出的那一版上。
     * 只按 {@code cv.eng_doc_iteration_oid = ei.oid} 精确匹配，用户一检出整列缩略图就空了 ——
     * 明明文件没变、几何没变。所以这里也走 LATERAL：先取本迭代的记录，
     * 没有才沿 {@code derived_from_oid} 复用，且要求两版 {@code ckfile_oid} 相同（换了文件必须重新转换）。
     */
    @Override
    @Select("<script>" +
            "SELECT DISTINCT ON (i.oid) "
            + "       i.oid AS \"partIterationOid\", "
            + "       cv.thumbnail_ckfile_oid AS \"thumbnailCkfileOid\" "
            + "FROM ck_part_iteration i "
            // 本迭代自己的关联优先；没有就退回同主对象（同一零件的其它版本）的关联
            + "JOIN ck_eng_doc_part_link l ON l.part_iteration_oid = i.oid "
            + "     OR l.part_iteration_oid IN ("
            + "         SELECT s.oid FROM ck_part_iteration s WHERE s.master_oid = i.master_oid) "
            + "JOIN ck_eng_document_iteration ei ON ei.master_oid = l.eng_document_oid "
            + "     AND ei.delete_mark = FALSE "
            + "     AND (ei.oid = l.eng_doc_iteration_oid "
            + "          OR (l.eng_doc_iteration_oid IS NULL AND ei.latest = TRUE)) "
            + "LEFT JOIN LATERAL ("
            + "  SELECT c.thumbnail_ckfile_oid, c.created_at "
            + "  FROM ck_cad_visualization c "
            + "  WHERE c.thumbnail_ckfile_oid IS NOT NULL "
            + "    AND (c.eng_doc_iteration_oid = ei.oid "
            + "      OR (c.eng_doc_iteration_oid = ei.derived_from_oid "
            + "          AND EXISTS (SELECT 1 FROM ck_eng_document_iteration s2 "
            + "                      WHERE s2.oid = ei.derived_from_oid AND s2.ckfile_oid = ei.ckfile_oid))) "
            // 本迭代自己的记录优先（哪怕更旧），否则检入后的新图会被旧版产物顶掉
            + "  ORDER BY (c.eng_doc_iteration_oid = ei.oid) DESC, c.created_at DESC NULLS LAST "
            + "  LIMIT 1"
            + ") cv ON TRUE "
            + "WHERE i.oid IN "
            + "<foreach collection='partIterationOids' item='oid' open='(' separator=',' close=')'>#{oid}</foreach> "
            + "  AND cv.thumbnail_ckfile_oid IS NOT NULL "
            + "ORDER BY i.oid, (l.part_iteration_oid = i.oid) DESC, cv.created_at DESC NULLS LAST"
            + "</script>")
    List<Map<String, Object>> selectThumbnailsByPartIterations(
            @Param("partIterationOids") List<String> partIterationOids);

    // ==================== 转换中心（全局队列视图） ====================

    /**
     * 列表与计数共用的 FROM / JOIN —— 两处口径不允许漂。
     *
     * <p>全部用 LEFT JOIN：{@code eng_doc_iteration_oid} 是逻辑外键、没有约束，
     * 历史上有调用方把通用文档迭代的 oid 传进来过（见 {@link #selectModelsByContainer} 的说明）。
     * 内连接会让这类记录从队列里蒸发 —— 而"转不动的任务看不见"正是最需要看见的那类。
     *
     * <p>产品名走两条 LEFT JOIN（系列 / 型号），因为 {@code ed.container_type} 二选一；
     * 不用 UNION 是为了让分页与排序保持单条 SQL。
     */
    String JOB_FROM = "FROM ck_cad_visualization cv "
            + "LEFT JOIN ck_eng_document_iteration ei ON ei.oid = cv.eng_doc_iteration_oid "
            + "LEFT JOIN ck_eng_document ed ON ed.oid = ei.master_oid "
            + "LEFT JOIN ck_product_line pl ON ed.container_type = 'PRODUCT_LINE' AND pl.oid = ed.container_oid "
            + "LEFT JOIN ck_product_model pm ON ed.container_type = 'PRODUCT_MODEL' AND pm.oid = ed.container_oid ";

    String JOB_FILTER = "<if test='statuses != null and statuses.size() > 0'> AND cv.status IN "
            + "<foreach collection='statuses' item='st' open='(' separator=',' close=')'>#{st}</foreach></if>"
            + "<if test='keyword != null and keyword != \"\"'> AND ("
            + "cv.source_file_name ILIKE CONCAT('%', #{keyword}, '%') "
            + "OR ed.number ILIKE CONCAT('%', #{keyword}, '%') "
            + "OR ed.name ILIKE CONCAT('%', #{keyword}, '%'))</if>";

    /**
     * 转换中心行。
     *
     * <p>{@code queuePosition} 用子查询算"前面还排着几个"：转换器只在真的开始跑时才动，
     * 排队中的用户唯一想知道的就是"我前面还有几张"。同租户 + 更早提交 = 位次，
     * 与转换器的 FIFO 线程池一致。
     */
    String JOB_SELECT = "SELECT cv.oid AS \"visOid\", cv.status AS \"status\", cv.progress AS \"progress\", "
            + "cv.stage AS \"stage\", cv.error_code AS \"errorCode\", cv.error_message AS \"errorMessage\", "
            + "cv.source_file_name AS \"sourceFileName\", cv.source_size AS \"sourceSize\", "
            + "cv.eng_doc_iteration_oid AS \"iterationOid\", ei.display_version AS \"iterationVersion\", "
            + "ed.oid AS \"engDocOid\", ed.number AS \"engDocNumber\", ed.name AS \"engDocName\", "
            + "ed.container_type AS \"containerType\", ed.container_oid AS \"containerOid\", "
            + "COALESCE(pl.name, pm.name) AS \"containerName\", "
            + "cv.creator AS \"creator\", cv.submitted_at AS \"submittedAt\", cv.converted_at AS \"convertedAt\", "
            + "cv.updated_at AS \"updatedAt\", cv.duration_ms AS \"durationMs\", "
            + "cv.triangle_count AS \"triangleCount\", cv.part_count AS \"partCount\", "
            + "CASE WHEN cv.status = 'QUEUED' THEN ("
            + "  SELECT COUNT(*) FROM ck_cad_visualization q WHERE q.status = 'QUEUED' "
            + "    AND q.tenant_oid IS NOT DISTINCT FROM cv.tenant_oid "
            // XML 里裸的 '<' 会让整个 <script> 解析失败（元素内容必须是合法标记），
            // 与本仓其它动态 SQL 一样转义成 &lt;
            + "    AND q.submitted_at &lt; cv.submitted_at) END AS \"queuePosition\" ";

    /** 进行中优先，其次按更新倒序 —— 否则一页 20 条全被历史成功记录占满 */
    String JOB_ORDER = " ORDER BY CASE WHEN cv.status IN ('QUEUED','RUNNING') THEN 0 ELSE 1 END, "
            + "CASE WHEN cv.status IN ('QUEUED','RUNNING') THEN cv.submitted_at END ASC NULLS LAST, "
            + "cv.updated_at DESC ";

    @Override
    @Select("<script>" + JOB_SELECT + JOB_FROM + "WHERE 1 = 1" + JOB_FILTER + JOB_ORDER
            + " LIMIT #{limit} OFFSET #{offset}</script>")
    List<CadConvertJobVO> selectConvertJobs(@Param("statuses") List<String> statuses,
                                            @Param("keyword") String keyword,
                                            @Param("offset") int offset,
                                            @Param("limit") int limit);

    @Override
    @Select("<script>SELECT COUNT(*) " + JOB_FROM + "WHERE 1 = 1" + JOB_FILTER + "</script>")
    int countConvertJobs(@Param("statuses") List<String> statuses,
                         @Param("keyword") String keyword);

    /**
     * 顶部计数。
     *
     * <p>一次查询用 FILTER 聚合出五个数：分五次 count 会让顶部卡片在轮询时
     * 出现"排队 3、转换中 2、合计 4"这种自相矛盾的瞬间（各条 SQL 之间数据在变）。
     */
    @Override
    @Select("SELECT COUNT(*) FILTER (WHERE status = 'QUEUED') AS \"queued\", "
            + "COUNT(*) FILTER (WHERE status = 'RUNNING') AS \"running\", "
            + "COUNT(*) FILTER (WHERE status = 'SUCCESS' AND converted_at >= CURRENT_DATE) AS \"successToday\", "
            + "COUNT(*) FILTER (WHERE status IN ('FAILED','TIMEOUT')) AS \"failed\", "
            + "COUNT(*) FILTER (WHERE status = 'SKIPPED') AS \"skipped\", "
            + "COUNT(*) FILTER (WHERE status = 'CANCELLED') AS \"cancelled\", "
            + "COUNT(*) AS \"total\" "
            // WHERE 1 = 1 不是摆设：租户拦截器是把 "AND tenant_oid = ?" 直接追加到语句末尾的，
            // 没有 WHERE 的句子会被拼成 "... FROM t AND tenant_oid = ?"（语法错误）
            + "FROM ck_cad_visualization WHERE 1 = 1")
    CadConvertStatsVO selectConvertStats();

    @Override
    @Delete("DELETE FROM ck_cad_visualization WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);
}
