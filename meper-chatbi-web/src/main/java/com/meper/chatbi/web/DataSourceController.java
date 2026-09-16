package com.meper.chatbi.web;

import com.meper.chatbi.domain.DataSourceService;
import com.meper.chatbi.spi.SslMode;
import com.meper.chatbi.spi.model.CapabilityDescriptor;
import com.meper.chatbi.spi.model.DataSourceProfile;
import com.meper.chatbi.web.auth.AuthInterceptor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 数据源管理 API（响应不含任何凭据内容）。 */
@RestController
@RequestMapping("/api/datasources")
public class DataSourceController {

    record CreateDatasourceRequest(
            @NotBlank String name,
            @NotNull com.meper.chatbi.spi.DatabaseType type,
            @NotBlank String host,
            @NotNull @Positive Integer port,
            String databaseName,
            @NotBlank String username,
            @NotBlank String password,
            SslMode sslMode,
            Map<String, String> extendInfo) {

        DataSourceService.DatasourceCommand toCommand() {
            return new DataSourceService.DatasourceCommand(name, type, host, port,
                    databaseName, username, password, sslMode == null ? SslMode.DISABLED : sslMode,
                    extendInfo == null ? Map.of() : extendInfo);
        }
    }

    record TestConnectionRequest(
            @NotNull com.meper.chatbi.spi.DatabaseType type,
            @NotBlank String host,
            @NotNull @Positive Integer port,
            String databaseName,
            @NotBlank String username,
            @NotBlank String password,
            SslMode sslMode,
            Map<String, String> extendInfo) {

        DataSourceService.DatasourceCommand toCommand() {
            return new DataSourceService.DatasourceCommand(null, type, host, port,
                    databaseName, username, password, sslMode == null ? SslMode.DISABLED : sslMode,
                    extendInfo == null ? Map.of() : extendInfo);
        }
    }

    record RotateCredentialRequest(@NotBlank String password) {
    }

    private final DataSourceService dataSources;

    public DataSourceController(DataSourceService dataSources) {
        this.dataSources = dataSources;
    }

    @PostMapping
    public DataSourceProfile register(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String principal,
                                      @Valid @RequestBody CreateDatasourceRequest request) {
        return dataSources.register(principal, request.toCommand());
    }

    @GetMapping
    public List<DataSourceProfile> list() {
        return dataSources.list();
    }

    @GetMapping("/{id}")
    public DataSourceProfile get(@PathVariable long id) {
        return dataSources.get(id);
    }

    /** 登记前预检：临时配置测试连接，不落库。 */
    @PostMapping("/test")
    public DataSourceService.TestResult testTemporary(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String principal,
            @Valid @RequestBody TestConnectionRequest request) {
        return dataSources.testTemporary(principal, request.toCommand());
    }

    @PostMapping("/{id}/test")
    public DataSourceService.TestResult testStored(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String principal,
            @PathVariable long id) {
        return dataSources.test(principal, id);
    }

    @GetMapping("/{id}/capabilities")
    public CapabilityDescriptor capabilities(@PathVariable long id) {
        return dataSources.capabilities(id);
    }

    @PostMapping("/{id}/rotate-credential")
    public DataSourceProfile rotate(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String principal,
                                    @PathVariable long id,
                                    @Valid @RequestBody RotateCredentialRequest request) {
        return dataSources.rotateCredential(principal, id, request.password());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String principal,
                                      @PathVariable long id) {
        dataSources.delete(principal, id);
        return Map.of("deleted", true, "id", id);
    }
}
