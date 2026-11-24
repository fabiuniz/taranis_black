package com.taranisblack.finance.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class GeminiApiClient {

    private final String apiKey;
    private final String modelName;
    private final WebClient webClient;

    public GeminiApiClient(
            @Value("${google.api.key}") String apiKey,
            @Value("${google.gemini.model:gemini-2.5-flash}") String modelName) {
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.webClient = WebClient.builder().build();
    }

    public Mono<String> generateContent(String model, String prompt) {
        String targetModel = (model != null && !model.isBlank()) ? model : this.modelName;

        Map<String, Object> requestBody = Map.of(
            "contents", List.of(
                Map.of(
                    "parts", List.of(
                        Map.of("text", prompt)
                    )
                )
            ),
            "generationConfig", Map.of(
                "temperature", 0.2
            )
        );

        String url = String.format("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s", targetModel, apiKey);

        return this.webClient.post()
            .uri(url)
            .header("Content-Type", "application/json")
            .header("x-goog-api-key", apiKey)
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(String.class)
            .timeout(Duration.ofSeconds(90)) // Timeout resiliente para evitar travamentos
            .doOnNext(response -> System.out.println("✅ Sucesso absoluto na resposta do Gemini (" + targetModel + ")!"))
            .onErrorResume(e -> {
                String errorMsg = e.getMessage() != null ? e.getMessage() : "Erro desconhecido";
                System.err.println("❌ Erro detalhado na API do Gemini: " + errorMsg);
                
                String statusDetalhe;
                if (errorMsg.contains("TimeoutException") || errorMsg.contains("timeout")) {
                    statusDetalhe = "Tempo limite de conexão com a API do Gemini excedido (mais de 90s). Tente novamente.";
                } else if (errorMsg.contains("429") || errorMsg.contains("RESOURCE_EXHAUSTED") || errorMsg.contains("quota")) {
                    statusDetalhe = "Cota gratuita do Gemini esgotada no momento. Tente novamente mais tarde.";
                } else if (errorMsg.contains("400") || errorMsg.contains("API_KEY_INVALID") || errorMsg.contains("401") || errorMsg.contains("403")) {
                    statusDetalhe = "Chave de API inválida, não autorizada ou não configurada no arquivo .env.";
                } else if (errorMsg.contains("404")) {
                    statusDetalhe = "Modelo (" + targetModel + ") não encontrado na API. Verifique os modelos disponíveis para sua conta.";
                } else {
                    statusDetalhe = "Falha na comunicação com a API: " + errorMsg.replace("\"", "'");
                }

                // Retorna um JSON informando o diagnóstico sem quebrar o dashboard
                String jsonErro = String.format(
                    "{\"alunoNome\":\"Consultor ONE G11\",\"alunoEmail\":\"aluno@one.com.br\",\"rotaEscolhida\":\"Rota 4 — Finanças e Operações\",\"cenarioProblema\":\"Indisponibilidade da IA\",\"resumoExecutivo\":\"Não foi possível obter resposta em tempo real da IA: %s\",\"acaoPrioritaria\":\"Verifique a chave GOOGLE_API_KEY no arquivo .env.\",\"metricaSucesso\":\"N/A\"}",
                    statusDetalhe
                );

                return Mono.just(jsonErro);
            });
    }
}
