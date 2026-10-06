package com.twistmeet.api.history;

import java.util.List;

/**
 * Minimal RFC 4180 CSV writer with CSV-injection ("formula injection") mitigation. No CSV library
 * exists elsewhere in this codebase (confirmed by repo search) and no product doc addresses
 * formula-injection; this is a deliberate addition recorded in DECISIONS.md, since a competitor's
 * free-text display name is attacker-controlled input that lands directly in an exported cell a
 * staff member may open in a spreadsheet application.
 */
final class CsvWriter {

  private static final String BOM = "﻿";

  private CsvWriter() {}

  static String write(List<String> header, List<List<String>> rows) {
    StringBuilder sb = new StringBuilder(BOM);
    sb.append(row(header));
    for (List<String> row : rows) {
      sb.append(row(row));
    }
    return sb.toString();
  }

  private static String row(List<String> cells) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < cells.size(); i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(cell(cells.get(i)));
    }
    sb.append("\r\n");
    return sb.toString();
  }

  private static String cell(String value) {
    String safe = sanitizeForFormulaInjection(value == null ? "" : value);
    if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
      return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
    return safe;
  }

  /**
   * A cell beginning with {@code = + - @} (or a tab/CR, which some spreadsheet apps also treat as a
   * formula prefix) is interpreted as a formula by Excel/Sheets/LibreOffice when the file is
   * opened, not as literal text — a classic CSV-injection vector. Prefixing a single quote
   * neutralizes it while keeping the visible text intact.
   */
  private static String sanitizeForFormulaInjection(String value) {
    if (value.isEmpty()) {
      return value;
    }
    char first = value.charAt(0);
    if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t') {
      return "'" + value;
    }
    return value;
  }
}
