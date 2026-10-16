package com.meper.chatbi.agent.gateway;

import com.meper.chatbi.agent.chat.AgentChatService;
import com.meper.chatbi.agent.chat.ChatEvent;
import com.meper.chatbi.agent.chat.ChatSessionStore;
import com.meper.chatbi.agent.definition.ModelProviderService;
import com.meper.chatbi.domain.DataSourceService;
import com.meper.chatbi.domain.MetadataService;
import com.meper.chatbi.domain.WorkbenchService;
import com.meper.chatbi.query.SqlClassifier;
import com.meper.chatbi.spi.model.DataSourceProfile;
import com.meper.chatbi.storage.repository.AuditRepository;
import com.meper.chatbi.storage.repository.ChatSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentChatThinkingTest {

    @Test
    void streamPersistsThinkingSeparatelyFromAnswer() {
        var providers = mock(ModelProviderService.class);
        var gateway = mock(SpringAiModelGateway.class);
        var sessions = mock(ChatSessionStore.class);
        var dataSources = mock(DataSourceService.class);
        var profile = mock(DataSourceProfile.class);
        when(dataSources.get(1L)).thenReturn(profile);
        when(profile.databaseName()).thenReturn("testdb");
        var credential = new ModelProviderCredential("ANTHROPIC", "http://localhost", "test",
                new char[0], Map.of());
        when(providers.credentialOf(2L)).thenReturn(credential);
        var model = mock(ChatModel.class);
        when(gateway.build(credential)).thenReturn(model);
        when(model.getDefaultOptions()).thenReturn(ChatOptions.builder().build());
        var thinking = new Generation(AssistantMessage.builder().content("先查询表目录")
                .properties(Map.of("signature", "")).build());
        var answer = new Generation(new AssistantMessage("业务表有 orders"));
        when(model.stream(any(Prompt.class))).thenReturn(Flux.just(new ChatResponse(List.of(thinking)),
                new ChatResponse(List.of(answer))));
        var service = new AgentChatService(providers, gateway, sessions, dataSources,
                mock(MetadataService.class), mock(WorkbenchService.class), mock(SqlClassifier.class),
                mock(AuditRepository.class), mock(ChatSessionRepository.class), mock(com.meper.chatbi.wiki.WikiService.class));

        var events = service.chatStream("admin", "s", 2L, 1L, null, "有哪些表")
                .collectList().block(Duration.ofSeconds(5));
        assertEquals(List.of(ChatEvent.start("s"), ChatEvent.reasoning("s", "先查询表目录"),
                ChatEvent.token("s", "业务表有 orders"), ChatEvent.finalOf("s")), events);
        verify(sessions).append("s", "admin", 1L, 2L, "testdb", false,
                "业务表有 orders", null, null, "先查询表目录", null);
    }
}
