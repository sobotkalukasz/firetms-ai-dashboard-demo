package pl.lsobotka.firetmsdashboard.firetms.salesinvoices.application;

import java.util.List;
import java.time.YearMonth;

public record MonthlyGrossSales(YearMonth month, List<Money> grossAmounts) {
}
