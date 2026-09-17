package com.meper.chatbi.web;

import com.meper.chatbi.domain.WorkbenchService;
import com.meper.chatbi.storage.model.ExecutionRecord;
import com.meper.chatbi.web.auth.AuthInterceptor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** SQL 工作台 API：preview → execute → 执行历史。 */
@RestController
@RequestMapping("/api/workbench")
public class WorkbenchController {

    record PreviewRequest(@NotNull Long datasourceId,
                          @NotBlank @Size(max = 1_000_000) String sql) {
    }

    record ExecuteRequest(@NotNull Long datasourceId,
                          @NotBlank @Size(max = 1_000_000) String sql,
                          Integer maxRows) {
    }

    record FormatRequest(@NotNull Long datasourceId,
                         @NotBlank @Size(max = 1_000_000) String sql) {
    }

    private final WorkbenchService workbench;

    public WorkbenchController(WorkbenchService workbench) {
        this.workbench = workbench;
    }

    @PostMapping("/preview")
    public WorkbenchService.PreviewResult preview(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String principal,
            @Valid @RequestBody PreviewRequest request) {
        return workbench.preview(principal, request.datasourceId(), request.sql());
    }

    @PostMapping("/format")
    public WorkbenchService.FormatResult format(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String principal,
            @Valid @RequestBody FormatRequest request) {
        return workbench.format(principal, request.datasourceId(), request.sql());
    }

    @PostMapping("/execute")
    public WorkbenchService.ExecuteResult execute(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String principal,
            @Valid @RequestBody ExecuteRequest request) {
        return workbench.execute(principal, request.datasourceId(), request.sql(), request.maxRows());
    }

    @GetMapping("/executions/{id}")
    public ExecutionRecord execution(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String principal,
                                     @PathVariable long id) {
        return workbench.getExecution(principal, id);
    }

    @GetMapping("/executions")
    public List<ExecutionRecord> executions(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String principal,
                                            @RequestParam(required = false) Long datasourceId,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return workbench.listExecutions(principal, datasourceId, page, size);
    }
}
