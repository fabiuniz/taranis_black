package com.taranisblack.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Service
public class TaranisFinanceService {

    private final GeminiApiClient geminiApiClient;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    private static final String PROMPT_TEMPLATE_ENTREGA = """
        Você é um consultor financeiro e de processos sênior de IA especializado na empresa simulada Taranis Black (Rota 4 — Finanças e Operações).
        
        Com base exclusivamente nos dados corporativos da empresa:
        1. 05_Financas_Orcado_vs_Realizado.txt (DRE, EBITDA realizado de R$ 9,85mi vs orçado de R$ 5,77mi, fretes expressos/duplicados gerando R$ 960k de custo, PDD de R$ 1,11M).
        2. 06_Descritivo_Processo_Operacional_SIPOC.txt (Processo em 5 etapas, gargalo de 24h a 48h para aprovação de crédito, ERP legado e 3 planilhas manuais).
        3. 04_Base_Colaboradores_People_Analytics.txt (Turnover de 22,5% e sobrecarga operacional).

        Gere os dados formatados em um objeto JSON válido contendo exatamente as chaves abaixo:
        - NomeCompleto
        - EmailAluno
        - TurmaCoorte
        - ResumoExecutivo
        - CenarioProblema (Detalhe explicitamente a conciliação manual entre 3 planilhas e o ERP legado, o atraso de 5 dias no fechamento, o ciclo de 48h em crédito, a PDD de R$ 1,11M e o turnover de 22,5%)
        - RecomendacaoPrioritaria
        - MetricasSucesso
        - CasosDeUso
        - ClassificacaoIA
        - SupervisaoHumana
        - MatrizEsforco
        - PlanoMudanca
        - PoliticaIA
        - RiscosLGPD
        - ComiteAuditoria
        - MatrizSIPOC
        - DiagramaIshikawa
        - TopCausas
        - PromptsProcessamento
        - DashboardRelatorioGerado
        - AnomaliasIdentificadas
        - ProjecaoTrimestre
        - Evidencia1_Artefato
        - Evidencia1_Descricao
        - Evidencia1_Link
        - Evidencia1_Print
        - Evidencia1_NotaUso
        - Evidencia2_Artefato
        - Evidencia2_Descricao
        - Evidencia2_Link
        - Evidencia2_Print
        - Evidencia2_NotaUso
        - Evidencia3_Artefato
        - Evidencia3_Descricao
        - Evidencia3_Link
        - Evidencia3_Print
        - Evidencia3_NotaUso
        - Evidencia4_Artefato
        - Evidencia4_Descricao
        - Evidencia4_Link
        - Evidencia4_Print
        - Evidencia4_NotaUso
        - Evidencia5_Artefato
        - Evidencia5_Descricao
        - Evidencia5_Link
        - Evidencia5_Print
        - Evidencia5_NotaUso

        Responda APENAS com o JSON válido, sem NENHUM texto adicional, sem crases, sem blocos de markdown.
        """;

    public TaranisFinanceService(GeminiApiClient geminiApiClient, ObjectMapper objectMapper, ResourceLoader resourceLoader) {
        this.geminiApiClient = geminiApiClient;
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
    }

    public Mono<String> gerarAnaliseParaTemplate(String foco) {
        StringBuilder promptBuilder = new StringBuilder(PROMPT_TEMPLATE_ENTREGA);

        // Enriquecimento dinâmico com arquivos locais (RAG local simplificado)
        String dadosSipoc = lerArquivoResource("classpath:dados/06_Descritivo_Processo_Operacional_SIPOC.txt");
        if (!dadosSipoc.isBlank()) {
            promptBuilder.append("\n\n--- DADOS REAIS DO PROCESSO OPERACIONAL (SIPOC) ---\n")
                         .append(dadosSipoc.substring(0, Math.min(dadosSipoc.length(), 2000)));
        }

        promptBuilder.append("\n\nFoco da solicitação atual: ").append(foco != null ? foco : "Geral");

        return geminiApiClient.generateContent(null, promptBuilder.toString())
            .map(jsonResponse -> {
                try {
                    JsonNode root = objectMapper.readTree(jsonResponse);
                    JsonNode textNode = root.at("/candidates/0/content/parts/0/text");
                    if (!textNode.isMissingNode() && !textNode.isNull()) {
                        String rawText = textNode.asText();
                        return rawText.replaceAll("(?s)```json\\s*(.*?)\\s*```", "$1")
                                      .replaceAll("(?s)```\\s*(.*?)\\s*```", "$1")
                                      .trim();
                    }
                } catch (Exception e) {
                    // Mantém o jsonResponse original caso já venha como JSON ou fallback
                }
                return jsonResponse;
            });
    }

    private String lerArquivoResource(String caminho) {
        try {
            Resource resource = resourceLoader.getResource(caminho);
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    return new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        } catch (Exception ignored) {
            // Se falhar a leitura, prossegue com os dados sumarizados do prompt base
        }
        return "";
    }
}
