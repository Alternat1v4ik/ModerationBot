package org.ModersHelperBot.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ModeratorStatistics {
    private String moderatorName; // Поле теперь можно задавать через сеттер
    private final List<DetailedReport> reports = new ArrayList<>();

    // Конструктор по умолчанию (нужен для Jackson)
    public ModeratorStatistics() {
    }

    // Конструктор с именем модератора
    public ModeratorStatistics(String moderatorName) {
        this.moderatorName = moderatorName;
    }

    // Добавление отчёта
    public void addDetailedReport(String offender, String point, String type, String details) {
        reports.add(new DetailedReport(offender, point, type, details, LocalDate.now()));
    }

    // Получение отчётов за указанный период
    public List<DetailedReport> getReportsBetween(LocalDate start, LocalDate end) {
        List<DetailedReport> filteredReports = new ArrayList<>();
        for (DetailedReport report : reports) {
            if (!report.getDate().isBefore(start) && !report.getDate().isAfter(end)) {
                filteredReports.add(report);
            }
        }
        return filteredReports;
    }
    public void clearReportsBefore(LocalDate date) {
        reports.removeIf(report -> report.getDate().isBefore(date));
    }

    // Получение имени модератора
    public String getModeratorName() {
        return moderatorName;
    }

    // Сеттер для имени модератора
    public void setModeratorName(String moderatorName) { // Сеттер для Jackson
        this.moderatorName = moderatorName;
    }

    // Получение всех отчётов
    public List<DetailedReport> getAllReports() {
        return reports;
    }

    // Вложенный класс для представления детализированного отчёта
    public static class DetailedReport {
        private String offender; // Ник нарушителя
        private String point;    // Пункт
        private String type;     // Тип нарушения
        private String details;  // Дополнительные данные
        private LocalDate date;  // Дата итогов

        // Конструктор по умолчанию (нужен для Jackson)
        public DetailedReport() {
        }

        public DetailedReport(String offender, String point, String type, String details, LocalDate date) {
            this.offender = offender;
            this.point = point;
            this.type = type;
            this.details = details;
            this.date = date;
        }


        // Геттеры и сеттеры
        public String getOffender() {
            return offender;
        }

        public void setOffender(String offender) { // Сеттер для Jackson
            this.offender = offender;
        }

        public String getPoint() {
            return point;
        }

        public void setPoint(String point) { // Сеттер для Jackson
            this.point = point;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) { // Сеттер для Jackson
            this.type = type;
        }

        public String getDetails() {
            return details;
        }

        public void setDetails(String details) { // Сеттер для Jackson
            this.details = details;
        }

        public LocalDate getDate() {
            return date;
        }

        public void setDate(LocalDate date) { // Сеттер для Jackson
            this.date = date;
        }

        @Override
        public String toString() {
            return "Отчёт: " +
                    "Нарушитель='" + offender + '\'' +
                    ", Пункт='" + point + '\'' +
                    ", Тип='" + type + '\'' +
                    ", Детали='" + details + '\'' +
                    ", Дата=" + date;
        }
    }
}