package org.ModersHelperBot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ModerationBot extends TelegramLongPollingBot {
    private static final Logger logger = LoggerFactory.getLogger(ModerationBot.class);
    private final Map<String, ModeratorStatistics> stats = new HashMap<>();
    private static final String STATS_FILE = "stats.json";

    public ModerationBot() {
        loadStats();
    }

    @Override
    public String getBotUsername() {
        return "TestBot"; // Заменить на имя бота
    }

    @Override
    public String getBotToken() {
        return "8007760661:AAGVnIzt7s3BorI7W8BDmM6Mxc2QBQlhnwQ"; // Заменить на токен бота
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            Message message = update.getMessage();
            if (message.isCommand()) {
                handleCommand(message);
            } else {
                handleReport(message); // Если это отчет
            }
        }
    }

    private void handleCommand(Message message) {
        String command = message.getText().split(" ")[0];
        String chatId = message.getChatId().toString();

        if ("/getvio".equals(command)) {
            if (isAdmin(message)) {
                sendWeeklyReport(message); // Статистика за неделю
            } else {
                sendMessage(chatId, "Только администраторы могут использовать эту команду");
            }
        }
        if ("/vioclear".equals(command)) {
            stats.clear();
            sendMessage(chatId, "Статистика успешно очищена.");
            System.out.println("Статистика успешно очищена");
        }
    }

    private boolean isAdmin(Message message) {
        try {
            GetChatMember getChatMember = new GetChatMember();
            getChatMember.setChatId(message.getChatId().toString());
            getChatMember.setUserId(message.getFrom().getId());

            // Получаем объект ChatMember
            org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember chatMember = execute(getChatMember);

            // Проверяем статус
            String status = chatMember.getStatus();
            return "administrator".equals(status) || "creator".equals(status);
        } catch (TelegramApiException e) {
            logger.error("Ошибка при проверке прав администратора: ", e);
            return false;
        }
    }

    private void handleReport(Message message) {
        // Игнорируем пересланные сообщения
        if (message.getForwardDate() != null) {
            return;
        }

        // Проверяем наличие текста. Если текст отсутствует, пробуем извлечь его из подписи к фото
        String text = message.getText();
        if (text == null || text.trim().isEmpty()) {
            text = message.getCaption(); // Получаем подпись к фото, если она есть
        }

        if (text == null || text.trim().isEmpty()) {
            // Если текста в подписи, игнорируем сообщение
            return;
        }

        // Разделяем текст на строки
        String[] lines = text.split("\n");
        if (lines.length < 2) {
            // Если текста меньше двух строк, игнорируем
            return;
        }

        // Первая строка — ник и пункт
        String[] firstLineParts = lines[0].split("-");
        if (firstLineParts.length < 2) {
            // Если формат первой строки неправильный, игнорируем
            return;
        }

        String offenderName = firstLineParts[0].trim(); // Имя нарушителя
        String violationPoint = firstLineParts[1].trim(); // Пункт

        // Вторая строка
        String secondLine = lines[1].trim();

        // Определяем тип нарушения
        String violationType = null;

        switch (violationPoint) {
            case "3.1":
                if (secondLine.startsWith("https://www.youtube.com/") || secondLine.startsWith("https://youtu.be/")) {
                    violationType = "Видео";
                } else {
                    return; // Игнорируем, если формат неправильный
                }
                break;
            case "5.2":
            case "7.1":
                if (secondLine.startsWith("/warp")) {
                    violationType = "Warp";
                } else {
                    return; // Игнорируем, если формат неправильный
                }
                break;
            case "6.1(неактив)":
            case "6.1":
            case "6.1(в бане)":
                if (secondLine.startsWith("/ad")) {
                    violationType = "Ad";
                } else {
                    return; // Игнорируем, если формат неправильный
                }
                break;

            default:
                return; // Игнорируем неизвестные пункты
        }

        // Получаем имя модератора
        String moderator = message.getFrom().getUserName();

        // Обновляем стату
        stats.putIfAbsent(moderator, new ModeratorStatistics(moderator));
        stats.get(moderator).addDetailedReport(offenderName, violationPoint, violationType, secondLine);
        saveStats();

        // Отправляем подтверждение
        sendMessage(message.getChatId().toString(), "Отчёт принят");
    }
   //private void resetStats() {
   //    stats.clear(); // Очищаем все данные в статистике
   //}


    private void sendWeeklyReport(Message message) {
        String userId = message.getFrom().getId().toString(); // ID пользователя, запросившего статистику
        StringBuilder report = new StringBuilder("Статистика за неделю:\n");

        LocalDate today = LocalDate.now();
        LocalDate startOfWeek = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.SUNDAY));
        LocalDate endOfWeek = startOfWeek.plusDays(6); // До субботы включительно

        report.append("Период: с ").append(startOfWeek).append(" по ").append(endOfWeek).append("\n\n");

        boolean hasReports = false;

        for (ModeratorStatistics stat : stats.values()) {
            List<ModeratorStatistics.DetailedReport> weeklyReports = stat.getReportsBetween(startOfWeek, endOfWeek);

            if (weeklyReports.isEmpty()) {
                continue;
            }

            hasReports = true;

            int worldsDeleted = 0;
            int warpsDeletedFiveTwo = 0;
            int warpsDeletedSevenOne = 0;
            int cheatersBanned = 0;

            for (ModeratorStatistics.DetailedReport reportDetails : weeklyReports) {
                switch (reportDetails.getType()) {
                    case "Видео":
                        cheatersBanned++;
                        break;
                    case "Warp":
                        if ("5.2".equals(reportDetails.getPoint())) {
                            warpsDeletedFiveTwo++;
                        } else if ("7.1".equals(reportDetails.getPoint())) {
                            warpsDeletedSevenOne++;
                        }
                        break;
                    case "Ad":
                        worldsDeleted++;
                        break;
                }
            }

            report.append(stat.getModeratorName()).append(":\n")
                    .append("  Удаление миров - ").append(worldsDeleted).append(" шт.\n")
                    .append("  Удаление варпов (5.2) - ").append(warpsDeletedFiveTwo).append(" шт.\n")
                    .append("  Удаление варпов (7.1) - ").append(warpsDeletedSevenOne).append(" шт.\n")
                    .append("  Забаненных читеров - ").append(cheatersBanned).append(" шт.\n\n");
        }

        if (!hasReports) {
            sendMessage(userId, "За эту неделю отчёты отсутствуют.");
            return;
        }

        // Отправляем стату в личные сообщения
        sendMessage(userId, report.toString());

        // Информируем в чате стату
        sendMessage(message.getChatId().toString(), "Статистика отправлена в личные сообщения.");

        //if (today.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
        //    resetStats();
        //    saveStats(); // Сохраняем новый пустой файл
        //    System.out.println("Файл статистики перезаписан.");
        //}

    }

    private void sendMessage(String chatId, String text) {
        try {
            execute(SendMessage.builder().chatId(chatId).text(text).build());
        } catch (Exception e) {
            logger.error("Ошибка при отправке сообщения", e);
        }
    }

    private void saveStats() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        try {
            File file = new File(STATS_FILE);
            mapper.writeValue(file, stats);
            logger.info("Статистика успешно сохранена.");
        } catch (IOException e) {
            logger.error("Ошибка при сохранении статистики в файл.", e);
        }
    }

    private void loadStats() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        try {
            File file = new File(STATS_FILE);
            if (file.exists()) {
                Map<String, ModeratorStatistics> loadedStats = mapper.readValue(file, new TypeReference<Map<String, ModeratorStatistics>>() {});
                stats.putAll(loadedStats);
                logger.info("Статистика успешно загружена из файла.");
            } else {
                logger.info("Файл статистики не найден. Создаётся новый файл.");
            }
        } catch (IOException e) {
            logger.error("Ошибка при загрузке статистики из файла.", e);
        }
    }
}