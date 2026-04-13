package com.eaglepoint.exam.imports.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility that generates the sample XLSX file used for roster import templates.
 * <p>
 * Run with: {@code mvn exec:java -Dexec.mainClass="com.eaglepoint.exam.imports.util.SampleXlsxGenerator"}
 * <p>
 * The generated file is written to {@code src/main/resources/samples/roster_import_sample.xlsx}.
 */
public class SampleXlsxGenerator {

    private static final String[] HEADERS = {
            "student_username",
            "class_name",
            "term_name",
            "student_id_number",
            "guardian_contact",
            "accommodation_notes"
    };

    private static final String[][] SAMPLE_ROWS = {
            {"jsmith", "Math 101 - Section A", "Fall 2026", "STU-10001", "+86-138-0000-1111", "Extra time (1.5x)"},
            {"ewang", "Science 201 - Section B", "Fall 2026", "STU-10002", "+86-139-0000-2222", ""},
            {"lchen", "English 301 - Section C", "Fall 2026", "STU-10003", "+86-137-0000-3333", "Preferential seating near front"}
    };

    public static void main(String[] args) throws IOException {
        // Determine output path relative to project root
        String outputDir = "src/main/resources/samples";
        if (args.length > 0) {
            outputDir = args[0];
        }

        Path dirPath = Paths.get(outputDir);
        Files.createDirectories(dirPath);
        Path outputPath = dirPath.resolve("roster_import_sample.xlsx");

        generateSampleXlsx(outputPath);

        System.out.println("Sample XLSX generated at: " + outputPath.toAbsolutePath());
    }

    /**
     * Generates a sample XLSX workbook with headers and sample data rows,
     * writing it to the specified path.
     *
     * @param outputPath the file path to write the XLSX to
     * @throws IOException if writing fails
     */
    public static void generateSampleXlsx(Path outputPath) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Roster Import");

            // Create bold header style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            // Write header row
            Row headerRow = sheet.createRow(0);
            for (int col = 0; col < HEADERS.length; col++) {
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(HEADERS[col]);
                cell.setCellStyle(headerStyle);
            }

            // Write sample data rows
            for (int rowIdx = 0; rowIdx < SAMPLE_ROWS.length; rowIdx++) {
                Row dataRow = sheet.createRow(rowIdx + 1);
                String[] rowData = SAMPLE_ROWS[rowIdx];
                for (int col = 0; col < rowData.length; col++) {
                    Cell cell = dataRow.createCell(col);
                    cell.setCellValue(rowData[col]);
                }
            }

            // Auto-size columns for readability
            for (int col = 0; col < HEADERS.length; col++) {
                sheet.autoSizeColumn(col);
            }

            // Write to file
            try (OutputStream out = new FileOutputStream(outputPath.toFile())) {
                workbook.write(out);
            }
        }
    }
}
