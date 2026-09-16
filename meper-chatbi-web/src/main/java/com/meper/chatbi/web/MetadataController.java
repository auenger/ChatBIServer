package com.meper.chatbi.web;

import com.meper.chatbi.domain.MetadataService;
import com.meper.chatbi.spi.model.TableDataPage;
import com.meper.chatbi.spi.model.TableDetail;
import com.meper.chatbi.spi.model.TableInfo;
import com.meper.chatbi.web.auth.AuthInterceptor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 元数据 API：支撑前端「数据源 → 库/Schema → 表」树与表数据/结构展示。 */
@RestController
@RequestMapping("/api/datasources/{id}/metadata")
public class MetadataController {

    private final MetadataService metadata;

    public MetadataController(MetadataService metadata) {
        this.metadata = metadata;
    }

    @GetMapping("/namespaces")
    public List<String> namespaces(@PathVariable long id) {
        return metadata.namespaces(id);
    }

    @GetMapping("/tables")
    public List<TableInfo> tables(@PathVariable long id,
                                  @RequestParam String namespace,
                                  @RequestParam(required = false) String pattern) {
        return metadata.tables(id, namespace, pattern);
    }

    @GetMapping("/table")
    public TableDetail tableDetail(@PathVariable long id,
                                   @RequestParam String namespace,
                                   @RequestParam String table) {
        return metadata.tableDetail(id, namespace, table);
    }

    @GetMapping("/data")
    public TableDataPage tableData(@PathVariable long id,
                                   @RequestParam String namespace,
                                   @RequestParam String table,
                                   @RequestParam(defaultValue = "1") int page,
                                   @RequestParam(defaultValue = "50") int size) {
        return metadata.tableData(id, namespace, table, page, size);
    }
}
