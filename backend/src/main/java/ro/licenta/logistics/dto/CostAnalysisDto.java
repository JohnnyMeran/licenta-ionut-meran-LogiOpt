package ro.licenta.logistics.dto;

import java.util.List;

// Analiza de cost pe scenarii + recomandarea de flotă (secțiunea nouă din Rapoarte).
public record CostAnalysisDto(List<CostScenarioDto> scenarios, FleetRecommendationDto recommendation) {}
