package kr.kicc.datamatch.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import kr.kicc.datamatch.config.DataMatchProperties;
import kr.kicc.datamatch.config.DataSourceRegistry;
import kr.kicc.datamatch.config.LocalOnlyFilter;
import kr.kicc.datamatch.error.DataMatchException;
import kr.kicc.datamatch.error.ErrorCode;
import kr.kicc.datamatch.metadata.ColumnMeta;
import kr.kicc.datamatch.metadata.MetadataRepository;
import kr.kicc.datamatch.metadata.MetadataService;
import kr.kicc.datamatch.metadata.PkColumn;
import kr.kicc.datamatch.metadata.TableName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** DB-02: 환경 목록·테이블 검색·컬럼 조회 API(기술 설계서 8장). */
@WebMvcTest(controllers = {EnvironmentController.class, MetadataController.class})
@Import({GlobalExceptionHandler.class, LocalOnlyFilter.class, MetadataService.class, DataSourceRegistry.class,
        DataMatchProperties.class})
@TestPropertySource(properties = {
    "datamatch.environments.test.url=jdbc:oracle:thin:@//secret-host:1521/SECRETSVC",
    "datamatch.environments.test.username=SECRET_USER",
    "datamatch.environments.test.password=SECRET_PW",
    "datamatch.environments.prod.url=jdbc:oracle:thin:@//secret-host:1521/SECRETSVC",
    "datamatch.environments.prod.username=SECRET_USER",
    "datamatch.environments.prod.password=SECRET_PW"})
class MetadataControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private MetadataRepository repository;

    @Test
    void environmentsReturnOnlyIdNameAndProductionFlag() throws Exception {
        String body = mvc.perform(get("/api/environments").header("Host", "localhost"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("test"))
                .andExpect(jsonPath("$[0].name").value("테스트"))
                .andExpect(jsonPath("$[0].production").value(false))
                .andExpect(jsonPath("$[1].id").value("prod"))
                .andExpect(jsonPath("$[1].name").value("운영"))
                .andExpect(jsonPath("$[1].production").value(true))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).doesNotContain("SECRET").doesNotContain("jdbc").doesNotContain("url")
                .doesNotContain("username").doesNotContain("password");
    }

    @Test
    void shortQueryIs400WithoutDbAccess() throws Exception {
        mvc.perform(get("/api/tables").param("env", "test").param("q", "T").header("Host", "localhost"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mvc.perform(get("/api/tables").param("env", "test").header("Host", "localhost"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        verifyNoInteractions(repository);
    }

    @Test
    void searchReturnsCandidatesWithReasons() throws Exception {
        when(repository.searchTables("test", "%TB%", 31)).thenReturn(List.of(new TableName("O", "TB_A"), new TableName("O", "TB_B")));
        when(repository.findPrimaryKeys("test", List.of(new TableName("O", "TB_A"), new TableName("O", "TB_B"))))
                .thenReturn(Map.of(new TableName("O", "TB_A"), List.of(new PkColumn("ID", "VARCHAR2"))));
        mvc.perform(get("/api/tables").param("env", "test").param("q", "tb").header("Host", "localhost"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.truncated").value(false))
                .andExpect(jsonPath("$.items[0].owner").value("O"))
                .andExpect(jsonPath("$.items[0].name").value("TB_A"))
                .andExpect(jsonPath("$.items[0].selectable").value(true))
                .andExpect(jsonPath("$.items[0].pkColumn").value("ID"))
                .andExpect(jsonPath("$.items[1].selectable").value(false))
                .andExpect(jsonPath("$.items[1].reason").value("NO_PK"));
    }

    @Test
    void columnsReturnTypePkAndClobInOrder() throws Exception {
        TableName t = new TableName("O", "TB_A");
        when(repository.findColumns("test", t)).thenReturn(List.of(
                new ColumnMeta("ID", "VARCHAR2", 20, null, null, 20),
                new ColumnMeta("AMOUNT", "NUMBER", 22, 15, 2, 0),
                new ColumnMeta("APPR_MSG", "CLOB", 4000, null, null, 0)));
        when(repository.findPrimaryKeys("test", List.of(t))).thenReturn(Map.of(t, List.of(new PkColumn("ID", "VARCHAR2"))));
        mvc.perform(get("/api/tables/columns").param("env", "test").param("owner", "O").param("table", "TB_A")
                        .header("Host", "localhost"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pkColumn").value("ID"))
                .andExpect(jsonPath("$.columns.length()").value(3))
                .andExpect(jsonPath("$.columns[0].name").value("ID"))
                .andExpect(jsonPath("$.columns[0].type").value("VARCHAR2(20)"))
                .andExpect(jsonPath("$.columns[0].pk").value(true))
                .andExpect(jsonPath("$.columns[0].clob").value(false))
                .andExpect(jsonPath("$.columns[1].type").value("NUMBER(15,2)"))
                .andExpect(jsonPath("$.columns[1].pk").value(false))
                .andExpect(jsonPath("$.columns[2].clob").value(true))
                .andExpect(jsonPath("$.columns[0].dataType").doesNotExist());
    }

    @Test
    void errorsMapToApiCodes() throws Exception {
        mvc.perform(get("/api/tables").param("env", "dev").param("q", "TB").header("Host", "localhost"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ENV_NOT_CONFIGURED"));
        when(repository.searchTables(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt()))
                .thenThrow(new DataMatchException(ErrorCode.DB_CONNECTION_FAILED, "DB connection failed (env=test)", Map.of("env", "test")))
                .thenThrow(new java.sql.SQLException("ORA-00942", "42000", 942));
        mvc.perform(get("/api/tables").param("env", "test").param("q", "TB").header("Host", "localhost"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DB_CONNECTION_FAILED"))
                .andExpect(jsonPath("$.details.env").value("test"));
        mvc.perform(get("/api/tables").param("env", "test").param("q", "TB").header("Host", "localhost"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("DB_QUERY_FAILED"));
        when(repository.findColumns(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        mvc.perform(get("/api/tables/columns").param("env", "test").param("owner", "O").param("table", "NOPE")
                        .header("Host", "localhost"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
}
