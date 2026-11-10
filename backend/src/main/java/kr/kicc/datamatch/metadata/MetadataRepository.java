package kr.kicc.datamatch.metadata;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Oracle 데이터 사전(ALL_* 뷰) 조회(기술 설계서 6.2절, TD-07). 단위 테스트에서 대체할 수 있도록 인터페이스로 둔다.
 * 연결 획득 실패는 {@code DataSourceRegistry}가 {@code DB_CONNECTION_FAILED}로 바꾸고, 조회 오류는 {@link SQLException}으로 던진다.
 */
public interface MetadataRepository {

    /** {@code UPPER(OWNER || '.' || TABLE_NAME) LIKE likePattern ESCAPE '\'}인 테이블을 소유자·이름 순으로 최대 {@code limit}건 조회한다. */
    List<TableName> searchTables(String env, String likePattern, int limit) throws SQLException;

    /** 주어진 테이블들의 PK 컬럼(컬럼명, 데이터 타입)을 조회한다. PK가 없는 테이블은 결과에 없다. */
    Map<TableName, List<PkColumn>> findPrimaryKeys(String env, List<TableName> tables) throws SQLException;

    /** 테이블의 컬럼을 COLUMN_ID 순으로 조회한다. 테이블이 없거나 조회 권한이 없으면 빈 목록이다. */
    List<ColumnMeta> findColumns(String env, TableName table) throws SQLException;
}
