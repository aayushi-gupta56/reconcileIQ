import { themeQuartz } from "ag-grid-community";

/**
 * PayPal brand palette (https://brand.paypal.com)
 * navy  #003087  – primary / header
 * blue  #0070E0  – interactive accent
 * sky   #009CDE  – secondary accent
 * gold  #FFC439  – PayPal button yellow
 * ink   #2C2E2F  – body text
 */
export const paypal = {
  navy: "#003087",
  navyDark: "#001C64",
  blue: "#0070E0",
  sky: "#009CDE",
  gold: "#FFC439",
  ink: "#2C2E2F",
  slate: "#687173",
  line: "#E6EAEE",
  surface: "#F5F7FA",
  success: "#0B7A43",
  danger: "#C72E2E",
  warning: "#9A5B00",
} as const;

export const reconcileGridTheme = themeQuartz.withParams({
  // surfaces
  backgroundColor: "#FFFFFF",
  oddRowBackgroundColor: "#FAFBFD",
  foregroundColor: paypal.ink,
  accentColor: paypal.blue,

  // header: PayPal navy
  headerBackgroundColor: paypal.navy,
  headerTextColor: "#FFFFFF",
  headerFontSize: 12,
  headerFontWeight: 600,
  headerHeight: 48,
  headerColumnResizeHandleColor: "rgba(255,255,255,0.35)",

  // rows
  rowHeight: 56,
  rowHoverColor: "#EEF5FD",
  selectedRowBackgroundColor: "rgba(0, 112, 224, 0.10)",
  rowBorder: { style: "solid", width: 1, color: paypal.line },

  // spacing and shape
  fontSize: 13,
  spacing: 8,
  cellHorizontalPadding: 20,
  wrapperBorder: { style: "solid", width: 1, color: paypal.line },
  wrapperBorderRadius: 12,
  borderRadius: 8,
  columnBorder: false,

  // filters, menus, pagination
  inputFocusBorder: { color: paypal.blue },
  inputFocusShadow: { spread: 3, color: "rgba(0, 112, 224, 0.20)" },
  chromeBackgroundColor: paypal.surface,
  pickerListBorder: { color: paypal.line },
  menuShadow: { radius: 16, offsetY: 6, color: "rgba(0, 28, 100, 0.16)" },
  fontFamily: ["Inter", "ui-sans-serif", "system-ui", "-apple-system", "Segoe UI", "sans-serif"],
});
