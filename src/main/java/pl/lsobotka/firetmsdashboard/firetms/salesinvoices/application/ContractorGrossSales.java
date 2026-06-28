package pl.lsobotka.firetmsdashboard.firetms.salesinvoices.application;

import java.util.List;

public record ContractorGrossSales(String contractorName, List<Money> grossAmounts) {
}
